'use strict';

import * as VSCode from 'vscode';
import * as Path from 'path';
import * as FS from 'fs';
import PortFinder = require('portfinder');
import * as Net from 'net';
import * as CommonsCommands from './commands';
import { RequestType, LanguageClientOptions, Position } from 'vscode-languageclient';
import {LanguageClient, StreamInfo, ServerOptions, ExecutableOptions, Executable} from 'vscode-languageclient/node';
import {
    Disposable,
    Event,
    EventEmitter
} from 'vscode';
import { Trace, NotificationType } from 'vscode-jsonrpc';
import * as P2C from 'vscode-languageclient/lib/common/protocolConverter';
import {HighlightService, HighlightParams} from './highlight-service';
import { log } from 'util';
import { JVM, findJvm, findJdk } from '@pivotal-tools/jvm-launch-utils';
import {HighlightCodeLensProvider} from "./code-lens-service";

const p2c = P2C.createConverter(undefined, false, false);

PortFinder.basePort = 45556;

const LOG_RESOLVE_VM_ARG_PREFIX = '-Xlog:jni+resolve=';
const DEBUG_ARG = '-agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=y';

export interface ActivatorOptions {
    DEBUG: boolean;
    CONNECT_TO_LS?: boolean;
    TRACE?: boolean;
    extensionId: string;
    clientOptions: LanguageClientOptions;
    jvmHeap?: string;
    workspaceOptions: VSCode.WorkspaceConfiguration;
    checkjvm?: (context: VSCode.ExtensionContext, jvm: JVM) => any;
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

function getUserDefinedJvmHeap(wsOpts : VSCode.WorkspaceConfiguration,  dflt : string) : string {
    if (!wsOpts) {
        return dflt;
    }
    let javaOptions : JavaOptions = wsOpts.get("java");
    return (javaOptions && javaOptions.heap) || dflt;
}

function isCheckingJVM(wsOpts : VSCode.WorkspaceConfiguration): boolean {
    if (!wsOpts) {
        return true;
    }
    return wsOpts.get("checkJVM");
}

function getUserDefinedJvmArgs(wsOpts : VSCode.WorkspaceConfiguration) : string[] {
    const dflt = [];
    if (!wsOpts) {
        return dflt;
    }
    let javaOptions : JavaOptions = wsOpts.get("java");
    return javaOptions && javaOptions.vmargs || dflt;
}

function getSpringUserDefinedJavaHome(wsOpts : VSCode.WorkspaceConfiguration, log: VSCode.OutputChannel) : string {
    let javaHome: string = null;
    if (wsOpts) {
        let javaOptions: JavaOptions = wsOpts.get("java");
        javaHome = javaOptions && javaOptions.home;
    }
    if (!javaHome) {
        log.appendLine('"spring-boot.ls.java.home" setting not specified or empty value');
    } else if (!FS.existsSync(javaHome)) {
        log.appendLine('"spring-boot.ls.java.home" points to folder that does NOT exist: ' + javaHome);
        javaHome = null;
    } else {
        log.appendLine('Trying to use "spring-boot.ls.java.home" value: ' + javaHome);
    }
    return javaHome;
}

function getJdtUserDefinedJavaHome(log: VSCode.OutputChannel): string {
    let javaHome: string = VSCode.workspace.getConfiguration('java')?.get('home');
    if (!javaHome) {
        log.appendLine('"java.home" setting not specified or empty value');
    } else if (!FS.existsSync(javaHome)) {
        log.appendLine('"java.home" points to folder that does NOT exist: ' + javaHome);
        javaHome = null;
    } else {
        log.appendLine('Trying to use "java.home" value: ' + javaHome);
    }
    return javaHome;
}

function findJdtEmbeddedJRE(): string | undefined{
    const javaExtension = VSCode.extensions.getExtension('redhat.java');
    if (javaExtension) {
        const jreHome = Path.resolve(javaExtension.extensionPath, 'jre');
        if (FS.existsSync(jreHome) && FS.statSync(jreHome).isDirectory()) {
            const candidates = FS.readdirSync(jreHome);
            for (const candidate of candidates) {
                if (FS.existsSync(Path.join(jreHome, candidate, "bin"))) {
                    return Path.join(jreHome, candidate);
                }
            }
        }
    }
}

export function activate(options: ActivatorOptions, context: VSCode.ExtensionContext): Thenable<LanguageClient> {
    if (options.CONNECT_TO_LS) {
        return VSCode.window.showInformationMessage("Start language server")
        .then((_) => connectToLS(context, options));
    } else {
        const clientOptions = options.clientOptions;

        const outChennalName = options.extensionId + "-debug-log"
        clientOptions.outputChannel = VSCode.window.createOutputChannel(outChennalName);
        clientOptions.outputChannelName = outChennalName;
        clientOptions.outputChannel.appendLine("Activating '" + options.extensionId + "' extension");

        let findJRE = options.preferJdk ? findJdk : findJvm;

        return findJRE(getSpringUserDefinedJavaHome(options.workspaceOptions, clientOptions.outputChannel)
            || findJdtEmbeddedJRE()
            || getJdtUserDefinedJavaHome(clientOptions.outputChannel),
            msg => clientOptions.outputChannel.appendLine(msg))
        .catch(error => {
            VSCode.window.showErrorMessage("Error trying to find JVM: "+error);
            return Promise.reject(error);
        })
        .then((jvm) => {
            if (!jvm) {
                VSCode.window.showErrorMessage("Couldn't locate java in $JAVA_HOME or $PATH");
                return;
            }
            let javaExecutablePath = jvm.getJavaExecutable();
            clientOptions.outputChannel.appendLine("Found java executable: " + javaExecutablePath);

            clientOptions.outputChannel.appendLine("isJavaEightOrHigher => true");

            if (process.env['SPRING_LS_USE_SOCKET']) {
                return setupLanguageClient(context, createServerOptionsForPortComm(options, context, jvm), options);
            } else {
                return setupLanguageClient(context, createServerOptions(options, context, jvm), options);
            }
        });
    }
}

function createServerOptions(options: ActivatorOptions, context: VSCode.ExtensionContext, jvm: JVM, port? : number): Executable {
    const executable: Executable = Object.create(null);
    const execOptions: ExecutableOptions = Object.create(null);
    execOptions.env = Object.assign(process.env);
    execOptions.cwd = context.extensionPath
    executable.options = execOptions;
    executable.command = jvm.getJavaExecutable();
    const vmArgs = prepareJvmArgs(options, context, jvm, port);
    addCpAndLauncherToJvmArgs(vmArgs, options, context);
    executable.args = vmArgs;
    return executable;

}

function createServerOptionsForPortComm(options: ActivatorOptions, context: VSCode.ExtensionContext, jvm: JVM): ServerOptions {
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
                        let processLaunchoptions = {
                            cwd: context.extensionPath
                        };
                        const args = prepareJvmArgs(options, context, jvm, port);
                        if (options.explodedLsJarData) {
                            const explodedLsJarData = options.explodedLsJarData;
                            const lsRoot = Path.resolve(context.extensionPath, explodedLsJarData.lsLocation);

                            // Add classpath
                            const classpath: string[] = [];
                            classpath.push(Path.resolve(lsRoot, 'BOOT-INF/classes'));
                            classpath.push(`${Path.resolve(lsRoot, 'BOOT-INF/lib')}${Path.sep}*`);

                            jvm.mainClassLaunch(explodedLsJarData.mainClass, classpath, args, processLaunchoptions);
                        } else {
                            // Start the child java process
                            const launcher = findServerJar(Path.resolve(context.extensionPath, 'language-server'));
                            jvm.jarLaunch(launcher, args, processLaunchoptions);
                        }
                    });
            });
        });
}

function prepareJvmArgs(options: ActivatorOptions, context: VSCode.ExtensionContext, jvm: JVM, port?: number): string[] {
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

function addCpAndLauncherToJvmArgs(args: string[], options: ActivatorOptions, context: VSCode.ExtensionContext) {
    if (options.explodedLsJarData) {
        const explodedLsJarData = options.explodedLsJarData;
        const lsRoot = Path.resolve(context.extensionPath, explodedLsJarData.lsLocation);

        // Add classpath
        const classpath: string[] = [];
        classpath.push(Path.resolve(lsRoot, 'BOOT-INF/classes'));
        classpath.push(`${Path.resolve(lsRoot, 'BOOT-INF/lib')}${Path.sep}*`);


        args.unshift(classpath.join(Path.delimiter));
        args.unshift('-cp');
        args.push(explodedLsJarData.mainClass);
    } else {
        // Start the child java process
        args.push('-jar');
        const launcher = findServerJar(Path.resolve(context.extensionPath, 'language-server'));
        args.push(launcher);
   }
}

function hasHeapArg(vmargs?: string[]) : boolean {
    return hasVmArg('-Xmx');
}

function hasVmArg(argPrefix: string, vmargs?: string[]): boolean {
    if (vmargs) {
        return vmargs.some(a => a.startsWith(argPrefix));
    }
    return false;

}

function findServerJar(jarsDir) : string {
    let serverJars = FS.readdirSync(jarsDir).filter(jar =>
        jar.indexOf('language-server')>=0 &&
        jar.endsWith(".jar")
    );
    if (serverJars.length==0) {
        throw new Error("Server jar not found in "+jarsDir);
    }
    if (serverJars.length>1) {
        throw new Error("Multiple server jars found in "+jarsDir);
    }
    return Path.resolve(jarsDir, serverJars[0]);
}

function connectToLS(context: VSCode.ExtensionContext, options: ActivatorOptions): Promise<LanguageClient> {
    let connectionInfo = {
        port: 5007
    };

    let serverOptions = () => {
        let socket = Net.connect(connectionInfo);
        let result: StreamInfo = {
            writer: socket,
            reader: socket
        };
        return Promise.resolve(result);
    };

    return setupLanguageClient(context, serverOptions, options);
}

function setupLanguageClient(context: VSCode.ExtensionContext, createServer: ServerOptions, options: ActivatorOptions): Promise<LanguageClient> {
    // Create the language client and start the client.
    let client = new LanguageClient(options.extensionId, options.extensionId,
        createServer, options.clientOptions
    );
    client.registerProposedFeatures();
    log("Proposed protocol extensions loaded!");
    if (options.TRACE) {
        client.setTrace(Trace.Verbose);
    }

    let highlightNotification = new NotificationType<HighlightParams>("sts/highlight");
    let moveCursorRequest = new RequestType<MoveCursorParams,MoveCursorResponse,void>("sts/moveCursor");

    const codeLensListanableSetting = options.highlightCodeLensSettingKey ? new ListenablePreferenceSetting<boolean>(options.highlightCodeLensSettingKey) : undefined;

    let highlightService = new HighlightService();
    const codelensService = new HighlightCodeLensProvider();
    let codeLensProviderSubscription: Disposable;

    CommonsCommands.registerCommands(context);

    context.subscriptions.push({dispose: () => client.stop()});
    context.subscriptions.push(highlightService);

    function toggleHighlightCodeLens() {
        if (!codeLensProviderSubscription && codeLensListanableSetting.value) {
            codeLensProviderSubscription = VSCode.languages.registerCodeLensProvider(options.clientOptions.documentSelector, codelensService);
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
        for (let editor of VSCode.window.visibleTextEditors) {
            if (editor.document.uri.toString() == params.uri) {
                let cursor = p2c.asPosition(params.position);
                let selection: VSCode.Selection = new VSCode.Selection(cursor, cursor);
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
    onDidChangeValue: VSCode.Event<void>
}

export class ListenablePreferenceSetting<T> implements ListenableSetting<T> {

    private _onDidChangeValue = new EventEmitter<void>();
    private _disposable: Disposable;

    constructor(private section: string) {
        this._disposable = VSCode.workspace.onDidChangeConfiguration(e => {
           if (e.affectsConfiguration(this.section)) {
               this._onDidChangeValue.fire();
           }
        });
    }

    get value(): T {
        return VSCode.workspace.getConfiguration().get(this.section);
    }

    get onDidChangeValue(): Event<void> {
        return this._onDidChangeValue.event;
    }

    dispose(): any {
        return this._disposable.dispose();
    }

}
