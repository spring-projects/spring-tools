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
import { getPortPromise } from "portfinder";
import { RemoteBootApp } from "./live-hover-connect-ui";

const JMX_VM_ARG = '-Dspring.jmx.enabled=';
const ACTUATOR_JMX_EXPOSURE_ARG = '-Dmanagement.endpoints.jmx.exposure.include=';
const ADMIN_VM_ARG = '-Dspring.application.admin.enabled=';
const BOOT_PROJECT_ARG = '-Dspring.boot.project.name=';
const RMI_HOSTNAME = '-Djava.rmi.server.hostname=localhost';

const JMX_PORT_ARG = '-Dcom.sun.management.jmxremote.port=';

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
        // Running app live hovers support
        if (!TEST_RUNNER_MAIN_CLASSES.includes(debugConfiguration.mainClass) && isActuatorOnClasspath(debugConfiguration)) {
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
                if (debugConfiguration.vmArgs.indexOf(BOOT_PROJECT_ARG) < 0) {
                    debugConfiguration.vmArgs += ` ${BOOT_PROJECT_ARG}${debugConfiguration.projectName}`;
                }
                if (debugConfiguration.vmArgs.indexOf(RMI_HOSTNAME) < 0) {
                    debugConfiguration.vmArgs += ` ${RMI_HOSTNAME}`;
                }
                if (debugConfiguration.vmArgs.indexOf(JMX_PORT_ARG) < 0) {
                    debugConfiguration.vmArgs += ` ${JMX_PORT_ARG}${await getPortPromise({
                        startPort: 10000
                    })} -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false`
                }
            } else {
                debugConfiguration.vmArgs = `${JMX_VM_ARG}true ${ACTUATOR_JMX_EXPOSURE_ARG}* ${ADMIN_VM_ARG}true ${BOOT_PROJECT_ARG}${debugConfiguration.projectName} ${RMI_HOSTNAME} ${JMX_PORT_ARG}${await getPortPromise({
                    startPort: 10000
                })} -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false`;
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
                const vmArgs = debugConfiguration.vmArgs as string;
                let idx = vmArgs.indexOf(JMX_PORT_ARG) + JMX_PORT_ARG.length;
                let jmxPort = "";
                for (; idx < vmArgs.length && vmArgs.charAt(idx) <= "9" && vmArgs.charAt(idx) >= "0"; idx++) {
                    jmxPort += vmArgs.charAt(idx);
                }
                await commands.executeCommand("vscode-spring-boot.live.activate", {
                    host: "127.0.0.1",
                    port: null,
                    urlScheme: "http",
                    jmxurl: `service:jmx:rmi:///jndi/rmi://127.0.0.1:${jmxPort}/jmxrmi`,
                    manualConnect: true,
                    processId: pid.toString(),
                    processName: debugConfiguration.mainClass,
                    projectName: debugConfiguration.projectName
                } as RemoteBootApp);
                if (workspace.getConfiguration("boot-java.live-information.automatic-connection").get("on")) {
                    await commands.executeCommand("vscode-spring-boot.live.show.active");
                }
            }, 500);
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

function isActuatorOnClasspath(debugConfiguration: DebugConfiguration): boolean {
    if (Array.isArray(debugConfiguration.classPaths)) {
        return !!debugConfiguration.classPaths.find(isActuatorJarFile);
    }
    return false;
}

function isActuatorJarFile(f: string): boolean {
    const fileName = path.basename(f || "");
    return /^spring-boot-actuator-\d+\.\d+\.\d+(.*)?.jar$/.test(fileName);
}

function canConnect(debugConfiguration: DebugConfiguration): boolean {
    if (!TEST_RUNNER_MAIN_CLASSES.includes(debugConfiguration.mainClass) && isActuatorOnClasspath(debugConfiguration)) {
        return typeof debugConfiguration.vmArgs === 'string'
            && debugConfiguration.vmArgs.indexOf(`${JMX_VM_ARG}true`) >= 0
            && debugConfiguration.vmArgs.indexOf(JMX_PORT_ARG) >= 0
            && debugConfiguration.vmArgs.indexOf(`${ADMIN_VM_ARG}true`) >= 0
    }
    return false;
}
