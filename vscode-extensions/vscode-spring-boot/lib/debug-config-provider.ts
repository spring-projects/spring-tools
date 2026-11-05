import { debug,
    commands,
    CancellationToken,
    DebugConfiguration,
    DebugConfigurationProvider,
    WorkspaceFolder,
    DebugConfigurationProviderTriggerKind,
    DebugSessionCustomEvent,
    Disposable,
    workspace,
    DebugSession
} from "vscode";
import * as path from "path";
import psList from 'ps-list';
import { ListenablePreferenceSetting } from "@pivotal-tools/commons-vscode/lib/launch-util";
import { RemoteBootApp } from "./live-hover-connect-ui";

const JMX_VM_ARG = '-Dspring.jmx.enabled=';
const ACTUATOR_JMX_EXPOSURE_ARG = '-Dmanagement.endpoints.jmx.exposure.include=';
const ADMIN_VM_ARG = '-Dspring.application.admin.enabled=';
const BOOT_PROJECT_ARG = '-Dspring.boot.project.name=';

// Set on every java debug configuration when it is resolved: whether spring-boot-actuator is on the classpath
const ACTUATOR_DETECTED_PROP = 'springBootActuatorDetected';

export const TEST_RUNNER_MAIN_CLASSES = [
    'org.eclipse.jdt.internal.junit.runner.RemoteTestRunner',
    'com.microsoft.java.test.runner.Launcher'
];

interface ProcessEvent {
    type: string;
    processId: number;
    shellProcessId: number
}


class SpringBootDebugConfigProvider implements DebugConfigurationProvider {

    async resolveDebugConfigurationWithSubstitutedVariables(folder: WorkspaceFolder | undefined, debugConfiguration: DebugConfiguration, _token?: CancellationToken) {
        // Running app live hovers support. No JMX port is picked here: the language server
        // attaches to the process id on-demand instead (see handleCustomDebugEvent), once the
        // process actually exists and has bound its own local management agent port.
        if (!TEST_RUNNER_MAIN_CLASSES.includes(debugConfiguration.mainClass)) {
            debugConfiguration[ACTUATOR_DETECTED_PROP] = await hasActuatorOnDebugConfigClasspath(debugConfiguration);
            if (debugConfiguration[ACTUATOR_DETECTED_PROP]) {
                if (typeof debugConfiguration.vmArgs === 'string') {
                    if (debugConfiguration.vmArgs.indexOf(JMX_VM_ARG) < 0) {
                        debugConfiguration.vmArgs += ` ${JMX_VM_ARG}true`;
                    }
                    if (debugConfiguration.vmArgs.indexOf(ACTUATOR_JMX_EXPOSURE_ARG) < 0) {
                        debugConfiguration.vmArgs += ` ${ACTUATOR_JMX_EXPOSURE_ARG}*`;
                    }
                    if (debugConfiguration.vmArgs.indexOf(ADMIN_VM_ARG) < 0) {
                        debugConfiguration.vmArgs += ` ${ADMIN_VM_ARG}true`;
                    }
                    if (debugConfiguration.projectName && debugConfiguration.vmArgs.indexOf(BOOT_PROJECT_ARG) < 0) {
                        debugConfiguration.vmArgs += ` ${BOOT_PROJECT_ARG}${debugConfiguration.projectName}`;
                    }
                } else {
                    debugConfiguration.vmArgs = `${JMX_VM_ARG}true ${ACTUATOR_JMX_EXPOSURE_ARG}* ${ADMIN_VM_ARG}true`
                        + (debugConfiguration.projectName ? ` ${BOOT_PROJECT_ARG}${debugConfiguration.projectName}` : '');
                }
            }
        }
        return debugConfiguration;
    }

}

export function hookListenerToBooleanPreference(setting: string, listenerCreator: () => Disposable): Disposable {
    const listenableSetting =  new ListenablePreferenceSetting<boolean>(setting);
    let listener: Disposable | undefined = listenableSetting.value ? listenerCreator() : undefined;
    listenableSetting.onDidChangeValue(() => {
        if (listenableSetting.value) {
            if (!listener) {
                listener = listenerCreator();
            }
        } else {
            if (listener) {
                listener.dispose();
                listener = undefined;
            }
        }
    });

    return {
        dispose: () => {
            if (listener) {
                listener.dispose();
            }
            listenableSetting.dispose();
        }
    };
}

export function startDebugSupport(): Disposable {
    return Disposable.from(
        hookListenerToBooleanPreference(
            'boot-java.live-information.automatic-connection.on',
            () => debug.registerDebugConfigurationProvider('java', new SpringBootDebugConfigProvider(), DebugConfigurationProviderTriggerKind.Initial)
        ),
        debug.onDidReceiveDebugSessionCustomEvent(handleCustomDebugEvent),
        debug.onDidTerminateDebugSession(handleTerminateDebugSession)
    );
}

async function handleCustomDebugEvent(e: DebugSessionCustomEvent): Promise<void> {
    if (e.session?.type === 'java' && e?.body?.type === 'processid') {
        const debugConfiguration: DebugConfiguration = e.session.configuration;
        if (canConnect(debugConfiguration)) {
            setTimeout(async () => {
                const pid = await getAppPid(e.body as ProcessEvent);
                // No jmxurl: the language server attaches to processId on-demand instead of
                // connecting to a pre-picked port (see SpringProcessConnectorRemote on the LS side).
                await commands.executeCommand("vscode-spring-boot.live.activate", {
                    host: "127.0.0.1",
                    port: null,
                    urlScheme: "http",
                    jmxurl: null,
                    manualConnect: true,
                    processId: pid.toString(),
                    processName: debugConfiguration.mainClass,
                    projectName: debugConfiguration.projectName
                } as RemoteBootApp);
                if (workspace.getConfiguration("boot-java.live-information.automatic-connection").get("on")) {
                    await commands.executeCommand("vscode-spring-boot.live.show.active");
                }
            }, 1500);
        }
    }
}

function handleTerminateDebugSession(_session: DebugSession) {
    commands.executeCommand('vscode-spring-boot.live.deactivate');
}

async function getAppPid(e: ProcessEvent): Promise<number> {
    if (e.processId && e.processId > 0) {
        return e.processId;
    } else if (e.shellProcessId) {
        const processes = await psList();
        const appProcess = processes.find(p => p.ppid === e.shellProcessId);
        if (appProcess) {
            return appProcess.pid;
        }
        throw Error(`No child process found for parent shell process with pid = ${e.shellProcessId}`);
    } else {
        throw Error('No pid or parent shell process id available');
    }
}

/**
 * Provider order relative to java-debug is not guaranteed, so `classPaths` may not be populated yet
 * when this provider runs. In that case resolve the classpath the same way java-debug does, without
 * mutating the configuration (java-debug then still resolves it on its own).
 */
async function hasActuatorOnDebugConfigClasspath(debugConfiguration: DebugConfiguration): Promise<boolean> {
    if (Array.isArray(debugConfiguration.classPaths) && debugConfiguration.classPaths.length > 0) {
        return isActuatorOnClasspath(debugConfiguration.classPaths);
    }
    if (!debugConfiguration.mainClass
            || (Array.isArray(debugConfiguration.modulePaths) && debugConfiguration.modulePaths.length > 0)) {
        return false;
    }
    try {
        // Blank project name is fine: the handler finds the project from the main class type
        const resolved = await commands.executeCommand<[string[], string[]]>(
            'java.execute.workspaceCommand', 'vscode.java.resolveClasspath', debugConfiguration.mainClass, debugConfiguration.projectName || '');
        return isActuatorOnClasspath(resolved?.[1]);
    } catch (e) {
        console.warn('Failed to resolve classpath for Spring Boot actuator detection:', e);
        return false;
    }
}

function isActuatorOnClasspath(classPaths: unknown): boolean {
    if (Array.isArray(classPaths)) {
        return !!classPaths.find(isActuatorJarFile);
    }
    return false;
}

function isActuatorJarFile(f: string): boolean {
    const fileName = path.basename(f || "");
    return /^spring-boot-actuator-\d+\.\d+\.\d+(.*)?.jar$/.test(fileName);
}

function canConnect(debugConfiguration: DebugConfiguration): boolean {
    if (!TEST_RUNNER_MAIN_CLASSES.includes(debugConfiguration.mainClass) && debugConfiguration[ACTUATOR_DETECTED_PROP] === true) {
        return typeof debugConfiguration.vmArgs === 'string'
            && debugConfiguration.vmArgs.indexOf(`${JMX_VM_ARG}true`) >= 0
            && debugConfiguration.vmArgs.indexOf(`${ADMIN_VM_ARG}true`) >= 0
    }
    return false;
}
