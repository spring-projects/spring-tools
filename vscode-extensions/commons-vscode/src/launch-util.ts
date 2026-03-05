'use strict';

import {
    Disposable,
    Event,
    EventEmitter,
    ExtensionContext,
    FileType,
    OutputChannel,
    Selection,
    Uri,
    WorkspaceConfiguration,
    extensions,
    languages,
    window,
    workspace
} from 'vscode';
import * as Path from 'path';
import * as Crypto from 'crypto';
import PortFinder from 'portfinder';
import * as Net from 'net';
import * as CommonsCommands from './commands';
import { RequestType, LanguageClientOptions, Position } from 'vscode-languageclient';
import {LanguageClient, StreamInfo, ServerOptions, ExecutableOptions, Executable} from 'vscode-languageclient/node';
import { Trace, NotificationType } from 'vscode-jsonrpc';
import * as P2C from 'vscode-languageclient/lib/common/protocolConverter';
import {HighlightService, HighlightParams} from './highlight-service';
import { JVM, findJvm, findJdk } from '@pivotal-tools/jvm-launch-utils';
import {HighlightCodeLensProvider} from "./code-lens-service";

const p2c = P2C.createConverter(undefined, false, false);

PortFinder.basePort = 45556;

const LOG_RESOLVE_VM_ARG_PREFIX = '-Xlog:jni+resolve=';
const LOG_AOT_VM_ARG_PREFIX = '-Xlog:aot';
const DEBUG_ARG = '-agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=y';

async function fileExists(filePath: string): Promise<boolean> {
    try {
        await workspace.fs.stat(Uri.file(filePath));
        return true;
    } catch {
        return false;
    }
}

async function isDirectory(filePath: string): Promise<boolean> {
    try {
        const stat = await workspace.fs.stat(Uri.file(filePath));
        return (stat.type & FileType.Directory) !== 0;
    } catch {
        return false;
    }
}

async function readDirectory(dirPath: string): Promise<[string, FileType][]> {
    return workspace.fs.readDirectory(Uri.file(dirPath));
}

export interface ActivatorOptions {
    DEBUG: boolean;
    CONNECT_TO_LS?: boolean;
    TRACE?: boolean;
    extensionId: string;
    clientOptions: LanguageClientOptions;
    jvmHeap?: string;
    workspaceOptions: WorkspaceConfiguration;
    checkjvm?: (context: ExtensionContext, jvm: JVM) => any;
    preferJdk?: boolean;
    highlightCodeLensSettingKey?: string;
    explodedLsJarData?: ExplodedLsJarData;
    vmArgs?: string[];
}

export interface ExplodedLsJarData {
    lsLocation: string;
    mainClass: string;
    configFileName?: string;
}

type JavaOptions = {
    heap?: string
    home?: string
    vmargs?: string[]
}

function getUserDefinedJvmHeap(wsOpts : WorkspaceConfiguration,  dflt : string) : string {
    if (!wsOpts) {
        return dflt;
    }
    const javaOptions : JavaOptions = wsOpts.get("java");
    return (javaOptions && javaOptions.heap) || dflt;
}

function isCheckingJVM(wsOpts : WorkspaceConfiguration): boolean {
    if (!wsOpts) {
        return true;
    }
    return wsOpts.get("checkJVM");
}

function getUserDefinedJvmArgs(wsOpts : WorkspaceConfiguration) : string[] {
    const dflt = [];
    if (!wsOpts) {
        return dflt;
    }
    const javaOptions : JavaOptions = wsOpts.get("java");
    return javaOptions && javaOptions.vmargs || dflt;
}

async function getSpringUserDefinedJavaHome(wsOpts : WorkspaceConfiguration, log: OutputChannel) : Promise<string> {
    let javaHome: string = null;
    if (wsOpts) {
        const javaOptions: JavaOptions = wsOpts.get("java");
        javaHome = javaOptions && javaOptions.home;
    }
    if (!javaHome) {
        log.appendLine('"spring-boot.ls.java.home" setting not specified or empty value');
    } else if (!await fileExists(javaHome)) {
        log.appendLine('"spring-boot.ls.java.home" points to folder that does NOT exist: ' + javaHome);
        javaHome = null;
    } else {
        log.appendLine('Trying to use "spring-boot.ls.java.home" value: ' + javaHome);
    }
    return javaHome;
}

async function getJdtUserDefinedJavaHome(log: OutputChannel): Promise<string> {
    let javaHome: string = workspace.getConfiguration('java')?.get('home');
    if (!javaHome) {
        log.appendLine('"java.home" setting not specified or empty value');
    } else if (!await fileExists(javaHome)) {
        log.appendLine('"java.home" points to folder that does NOT exist: ' + javaHome);
        javaHome = null;
    } else {
        log.appendLine('Trying to use "java.home" value: ' + javaHome);
    }
    return javaHome;
}

async function findJdtEmbeddedJRE(): Promise<string | undefined> {
    const javaExtension = extensions.getExtension('redhat.java');
    if (javaExtension) {
        const jreHome = Path.resolve(javaExtension.extensionPath, 'jre');
        if (await isDirectory(jreHome)) {
            const entries = await readDirectory(jreHome);
            for (const [candidate] of entries) {
                if (await fileExists(Path.join(jreHome, candidate, "bin"))) {
                    return Path.join(jreHome, candidate);
                }
            }
        }
    }
}

function hashString(value: string): string {
    return Crypto.createHash('sha256').update(value).digest('hex').substring(0, 12);
}

function getAotCachePath(extensionPath: string, jvm: JVM): string {
    const javaHomeHash = hashString(jvm.getJavaHome());
    return Path.join(extensionPath, 'language-server', `spring-boot-ls_${javaHomeHash}.aot`);
}

async function prepareCdsArgs(options: ActivatorOptions, context: ExtensionContext, jvm: JVM, useSocket: boolean): Promise<CdsResult> {
    if (!options.workspaceOptions.get("cds.enabled")) {
        return new CdsResult([]);
    }

    if (jvm.getMajorVersion() < 25) {
        window.showInformationMessage(
            'Spring Boot Language Server: CDS is enabled but requires Java 25+. ' +
            `Current Java version is ${jvm.getMajorVersion()}. Starting without CDS.`
        );
        return new CdsResult([]);
    }

    const aotCachePath = getAotCachePath(context.extensionPath, jvm);
    const cdsArgs: string[] = [];

    if (await fileExists(aotCachePath)) {
        options.clientOptions.outputChannel.appendLine(`CDS: Using existing AOT cache: ${aotCachePath}`);
        cdsArgs.push(`-XX:AOTCache=${aotCachePath}`);
    } else {
        options.clientOptions.outputChannel.appendLine(`CDS: No AOT cache found, will record cache on this run: ${aotCachePath}`);
        cdsArgs.push(`-XX:AOTCacheOutput=${aotCachePath}`);
        window.showInformationMessage(
            'Language Server: CDS training run in progress. ' +
            'To ensure the AOT cache is saved correctly, please exit VS Code normally when done rather than using "Reload Window".',
            'Got it'
        );
    }

    if (!useSocket) {
        cdsArgs.push(`${LOG_AOT_VM_ARG_PREFIX}*=off`);
    }

    return new CdsResult(cdsArgs);
}

class CdsResult {
    private readonly _trainingRun: boolean;

    constructor(readonly cdsArgs: string[]) {
        this._trainingRun = cdsArgs.some(a => a.startsWith('-XX:AOTCacheOutput'));
    }

    get isTrainingRun(): boolean {
        return this._trainingRun;
    }
}

function addCdsArgs(vmArgs: string[], cdsResult?: CdsResult): void {
    if (!cdsResult?.cdsArgs.length) {
        return;
    }
    for (const arg of cdsResult.cdsArgs) {
        if (arg.startsWith(LOG_AOT_VM_ARG_PREFIX) && hasVmArg(LOG_AOT_VM_ARG_PREFIX, vmArgs)) {
            continue;
        }
        vmArgs.push(arg);
    }
}

export async function activate(options: ActivatorOptions, context: ExtensionContext): Promise<LanguageClient> {
    const clientOptions = options.clientOptions;

    const outputChannelName = options.extensionId + "-debug-log";
    clientOptions.outputChannel = window.createOutputChannel(outputChannelName);
    clientOptions.outputChannelName = outputChannelName;
    clientOptions.outputChannel.appendLine("Activating '" + options.extensionId + "' extension");

    if (options.CONNECT_TO_LS) {
        await window.showInformationMessage("Start language server");
        return connectToLS(context, options);
    }

    const findJRE = options.preferJdk ? findJdk : findJvm;

    const javaHome = await getSpringUserDefinedJavaHome(options.workspaceOptions, clientOptions.outputChannel)
        || await findJdtEmbeddedJRE()
        || await getJdtUserDefinedJavaHome(clientOptions.outputChannel);

    let jvm: JVM;
    try {
        jvm = await findJRE(javaHome, msg => clientOptions.outputChannel.appendLine(msg));
    } catch (error) {
        window.showErrorMessage("Error trying to find JVM: " + error);
        throw error;
    }

    if (!jvm) {
        window.showErrorMessage("Couldn't locate java in $JAVA_HOME or $PATH");
        return;
    }

    clientOptions.outputChannel.appendLine("Found java executable: " + jvm.getJavaExecutable());
    clientOptions.outputChannel.appendLine("isJavaEightOrHigher => true");

    const useSocket = !!process.env['SPRING_LS_USE_SOCKET'];
    const cdsResult = await prepareCdsArgs(options, context, jvm, useSocket);

    if (useSocket) {
        return setupLanguageClient(context, await createServerOptionsForPortComm(options, context, jvm, cdsResult), options, cdsResult);
    } else {
        return setupLanguageClient(context, await createServerOptions(options, context, jvm, undefined, cdsResult), options, cdsResult);
    }
}

async function createServerOptions(options: ActivatorOptions, context: ExtensionContext, jvm: JVM, port?: number, cdsResult?: CdsResult): Promise<Executable> {
    const executable: Executable = Object.create(null);
    const execOptions: ExecutableOptions = Object.create(null);
    execOptions.env = Object.assign(process.env);
    execOptions.cwd = context.extensionPath
    executable.options = execOptions;
    executable.command = jvm.getJavaExecutable();
    const vmArgs = prepareJvmArgs(options, context, jvm, port);
    addCdsArgs(vmArgs, cdsResult);
    await addCpAndLauncherToJvmArgs(vmArgs, options, context);
    executable.args = vmArgs;
    return executable;
}

async function createServerOptionsForPortComm(options: ActivatorOptions, context: ExtensionContext, jvm: JVM, cdsResult?: CdsResult): Promise<ServerOptions> {
    const launcher = options.explodedLsJarData
        ? undefined
        : await findServerJar(Path.resolve(context.extensionPath, 'language-server'));

    return () =>
        new Promise((resolve) => {
            PortFinder.getPort((err, port) => {
                Net.createServer(socket => {
                    options.clientOptions.outputChannel.appendLine('Child process connected on port ' + port);

                    resolve({
                        reader: socket,
                        writer: socket
                    });
                })
                    .listen(port, () => {
                        const processLaunchoptions = {
                            cwd: context.extensionPath
                        };
                        const args = prepareJvmArgs(options, context, jvm, port);
                        addCdsArgs(args, cdsResult);
                        if (options.explodedLsJarData) {
                            const explodedLsJarData = options.explodedLsJarData;
                            const lsRoot = Path.resolve(context.extensionPath, explodedLsJarData.lsLocation);

                            const classpath: string[] = [];
                            classpath.push(Path.resolve(lsRoot, 'BOOT-INF/classes'));
                            classpath.push(`${Path.resolve(lsRoot, 'BOOT-INF/lib')}${Path.sep}*`);

                            jvm.mainClassLaunch(explodedLsJarData.mainClass, classpath, args, processLaunchoptions);
                        } else {
                            jvm.jarLaunch(launcher, args, processLaunchoptions);
                        }
                    });
            });
        });
}

function prepareJvmArgs(options: ActivatorOptions, context: ExtensionContext, jvm: JVM, port?: number): string[] {
    const DEBUG = options.DEBUG;
    const jvmHeap = getUserDefinedJvmHeap(options.workspaceOptions, options.jvmHeap);
    const jvmArgs = getUserDefinedJvmArgs(options.workspaceOptions);

    if (Array.isArray(options.vmArgs)) {
        jvmArgs.push(...options.vmArgs);
    }

    const args = [
        '-Dsts.lsp.client=vscode'
    ];
    const logfile = options.workspaceOptions.get("logfile");
    if (logfile) {
        options.clientOptions.outputChannel.appendLine(`Redirecting server logs to ${logfile}`);
        args.push(
            '-Dspring.profiles.active=file-logging',
            `-Dlogging.file.name=${logfile}`
        );
    }
    const rootLogLevel = options.workspaceOptions.get("logLevel");
    if (rootLogLevel) {
        args.push(`-Dlogging.level.root=${rootLogLevel}`);
    }
    if (port && port > 0) {
        args.push(
            `-Dspring.lsp.client-port=${port}`,
            `-Dserver.port=${port}`
        );
    }
    if (isCheckingJVM(options.workspaceOptions) && options.checkjvm) {
        options.checkjvm(context, jvm);
    }
    if (jvmHeap && !hasHeapArg(jvmArgs)) {
        args.unshift("-Xmx"+jvmHeap);
    }
    if (jvmArgs) {
        args.unshift(...jvmArgs);
    }
    if (DEBUG) {
        args.unshift(DEBUG_ARG);
    }
    // Below is to fix: https://github.com/spring-projects/sts4/issues/811
    if (!hasVmArg(LOG_RESOLVE_VM_ARG_PREFIX, args)) {
        args.push(`${LOG_RESOLVE_VM_ARG_PREFIX}off`);
    }

    if (options.explodedLsJarData) {
        const explodedLsJarData = options.explodedLsJarData;
        const lsRoot = Path.resolve(context.extensionPath, explodedLsJarData.lsLocation);
        // Add config file if needed
        if (explodedLsJarData.configFileName) {
            args.push(`-Dspring.config.location=file:${Path.resolve(lsRoot, `BOOT-INF/classes/${explodedLsJarData.configFileName}`)}`);
        }
    }
    return args;
}

async function addCpAndLauncherToJvmArgs(args: string[], options: ActivatorOptions, context: ExtensionContext) {
    if (options.explodedLsJarData) {
        const explodedLsJarData = options.explodedLsJarData;
        const lsRoot = Path.resolve(context.extensionPath, explodedLsJarData.lsLocation);

        const classpath: string[] = [];
        classpath.push(Path.resolve(lsRoot, 'BOOT-INF/classes'));
        classpath.push(`${Path.resolve(lsRoot, 'BOOT-INF/lib')}${Path.sep}*`);

        args.unshift(classpath.join(Path.delimiter));
        args.unshift('-cp');
        args.push(explodedLsJarData.mainClass);
    } else {
        args.push('-jar');
        const launcher = await findServerJar(Path.resolve(context.extensionPath, 'language-server'));
        args.push(launcher);
   }
}

function hasHeapArg(vmargs?: string[]) : boolean {
    return hasVmArg('-Xmx', vmargs);
}

function hasVmArg(argPrefix: string, vmargs?: string[]): boolean {
    if (vmargs) {
        return vmargs.some(a => a.startsWith(argPrefix));
    }
    return false;

}

async function findServerJar(jarsDir: string) : Promise<string> {
    const entries = await readDirectory(jarsDir);
    const serverJars = entries
        .map(([name]) => name)
        .filter(name => name.indexOf('language-server') >= 0 && name.endsWith(".jar"));
    if (serverJars.length === 0) {
        throw new Error("Server jar not found in " + jarsDir);
    }
    if (serverJars.length > 1) {
        throw new Error("Multiple server jars found in " + jarsDir);
    }
    return Path.resolve(jarsDir, serverJars[0]);
}

function connectToLS(context: ExtensionContext, options: ActivatorOptions): Promise<LanguageClient> {
    const connectionInfo = {
        port: 5007
    };

    const serverOptions = () => {
        const socket = Net.connect(connectionInfo);
        const result: StreamInfo = {
            writer: socket,
            reader: socket
        };
        return Promise.resolve(result);
    };

    return setupLanguageClient(context, serverOptions, options);
}

function setupLanguageClient(context: ExtensionContext, createServer: ServerOptions, options: ActivatorOptions, cdsResult?: CdsResult): Promise<LanguageClient> {
    // Create the language client and start the client.
    const client = new LanguageClient(options.extensionId, options.extensionId,
        createServer, options.clientOptions
    );
    client.registerProposedFeatures();
    options.clientOptions.outputChannel.appendLine("Proposed protocol extensions loaded!");
    if (options.TRACE) {
        client.setTrace(Trace.Verbose);
    }

    const highlightNotification = new NotificationType<HighlightParams>("sts/highlight");
    const moveCursorRequest = new RequestType<MoveCursorParams,MoveCursorResponse,void>("sts/moveCursor");

    const codeLensListanableSetting = options.highlightCodeLensSettingKey ? new ListenablePreferenceSetting<boolean>(options.highlightCodeLensSettingKey) : undefined;

    const highlightService = new HighlightService();
    const codelensService = new HighlightCodeLensProvider();
    let codeLensProviderSubscription: Disposable;

    CommonsCommands.registerCommands(context);

    context.subscriptions.push({dispose: () => cdsResult?.isTrainingRun ? client.stop(10000) : client.stop()});
    context.subscriptions.push(highlightService);

    function toggleHighlightCodeLens() {
        if (!codeLensProviderSubscription && codeLensListanableSetting.value) {
            codeLensProviderSubscription = languages.registerCodeLensProvider(options.clientOptions.documentSelector, codelensService);
            context.subscriptions.push(codeLensProviderSubscription);
        } else if (codeLensProviderSubscription) {
            codeLensProviderSubscription.dispose();
            const idx = context.subscriptions.indexOf(codeLensProviderSubscription);
            if (idx >= 0) {
                context.subscriptions.splice(idx, 1);
            }
            codeLensProviderSubscription = null;
        }
    }

    if (codeLensListanableSetting) {
        toggleHighlightCodeLens();
        codeLensListanableSetting.onDidChangeValue(() => toggleHighlightCodeLens())
    }

    client.onNotification(highlightNotification, (params: HighlightParams) => {
        highlightService.handle(params);
        if (codeLensListanableSetting && codeLensListanableSetting.value) {
            codelensService.handle(params);
        }
    });
    client.onRequest(moveCursorRequest, (params: MoveCursorParams) => {
        for (const editor of window.visibleTextEditors) {
            if (editor.document.uri.toString() == params.uri) {
                const cursor = p2c.asPosition(params.position);
                const selection = new Selection(cursor, cursor);
                editor.selections = [selection];
            }
        }
        return {applied: true};
    });
    return Promise.resolve(client);
}

interface MoveCursorParams {
    uri: string
    position: Position
}

interface MoveCursorResponse {
    applied: boolean
}

export interface ListenableSetting<T> {
    value: T;
    onDidChangeValue: Event<void>
}

export class ListenablePreferenceSetting<T> implements ListenableSetting<T> {

    private _onDidChangeValue = new EventEmitter<void>();
    private _disposable: Disposable;

    constructor(private section: string) {
        this._disposable = workspace.onDidChangeConfiguration(e => {
           if (e.affectsConfiguration(this.section)) {
               this._onDidChangeValue.fire();
           }
        });
    }

    get value(): T {
        return workspace.getConfiguration().get(this.section);
    }

    get onDidChangeValue(): Event<void> {
        return this._onDidChangeValue.event;
    }

    dispose(): any {
        return this._disposable.dispose();
    }

}
