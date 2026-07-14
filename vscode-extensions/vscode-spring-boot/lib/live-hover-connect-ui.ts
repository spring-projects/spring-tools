import { LanguageClient } from "vscode-languageclient/node";
import { ActivatorOptions } from '@pivotal-tools/commons-vscode';
import { ExtensionContext, QuickPickItem, commands, window } from 'vscode';

export const CONNECT_CMD = "sts/livedata/connect";
export const DISCONNECT_CMD = "sts/livedata/disconnect";
export const REFRESH_CMD = "sts/livedata/refresh";
export const LIST_CMD = "sts/livedata/listProcesses";

interface ProcessCommandInfo {
    processKey : string;
	label: string;
	action: string;
    projectName: string;
}

export interface RemoteBootApp {
    jmxurl: string | null;
    host: string;
    urlScheme: "https" | "http";
    port: string;
    manualConnect: boolean;
    keepChecking?: boolean;
    processId: string;
    processName: string;
    projectName?: string;
}

/**
 * Most callers pin a fixed jmxurl, which is a stable and unique identifier on its own. The
 * auto/dynamic JMX port case has no jmxurl since no port is picked by the extension; the
 * process id is used as the key instead (mirrors SpringProcessConnectorRemote.getProcessKey
 * on the language server side).
 */
function getProcessKey(appData: RemoteBootApp | undefined): string | undefined {
    return appData?.jmxurl || appData?.processId;
}

export interface BootAppQuickPick extends QuickPickItem {
    commandInfo: ProcessCommandInfo;
}

type BootAppState = "none" | "connecting" | "connected" | "disconnecting" | "disconnected";

let activeBootApp: RemoteBootApp | undefined;
let state: BootAppState

async function liveHoverConnectHandler() {
    const quickPick = window.createQuickPick<BootAppQuickPick>();
    quickPick.title = 'Searching for running Spring Boot Apps...';
    quickPick.canSelectMany = false;
    quickPick.busy = true;
    quickPick.show();

    const processData : ProcessCommandInfo[] = await commands.executeCommand(LIST_CMD);

    const items = processData.map(p => {
        let actionLabel = "";
        switch (p.action) {
            case CONNECT_CMD:
                actionLabel = "Show"
                break;
            case REFRESH_CMD:
                actionLabel = "Refresh";
                break;
            case DISCONNECT_CMD:
                actionLabel = "Hide";
                break;    
        }
        const choiceLabel = actionLabel + " Live Data from: " + p.label;
        return {
            commandInfo: p,
            label: choiceLabel
        } as BootAppQuickPick;

    });

    quickPick.busy = false;

    quickPick.title = items.length ? "Select action for running Spring Boot App" : "No running Spring Boot Apps found";

    quickPick.items = items;

    if (!items.length) {
        quickPick.hide();
        window.showInformationMessage("No running Spring Boot Apps found");
        return;
    }

    return await new Promise((resolve, reject) => {
        quickPick.onDidChangeSelection(() => quickPick.hide());
        quickPick.onDidHide(async () => {
            try {
                const chosen = quickPick.selectedItems ? quickPick.selectedItems[0] : undefined;
                if (chosen) {
                    executeLiveProcessAction(chosen.commandInfo);
                }
                resolve(undefined);
            } catch (error) {
                reject(error);
            }
        })
    });
}

async function executeLiveProcessAction(commandInfo: ProcessCommandInfo) {
    if (getProcessKey(activeBootApp) === commandInfo.processKey) {
        switch (commandInfo.action) {
            case CONNECT_CMD:
                await commands.executeCommand('vscode-spring-boot.live.show.active');
                break;
            case DISCONNECT_CMD:
                await commands.executeCommand('vscode-spring-boot.live.hide.active');
                break;
            default:
                await commands.executeCommand(commandInfo.action, commandInfo);
            }
    } else {
        await commands.executeCommand(commandInfo.action, commandInfo);
    }
}

async function updateBootAppState(newState: BootAppState) {
    if (newState !== state) {
        state = newState;
        commands.executeCommand('setContext', 'vscode-spring-boot.active-app-state', state);
    }
}

/** Called when extension is activated */
export function activate(
        client: LanguageClient,
        options: ActivatorOptions,
        context: ExtensionContext
) {
    context.subscriptions.push(

        commands.registerCommand('vscode-spring-boot.live-hover.connect', () => {
            if (client.isRunning()) {
                return liveHoverConnectHandler();
            } else {
                window.showErrorMessage("No Spring Boot project found. Action is only available for Spring Boot Projects");
            }
        }),

        commands.registerCommand("vscode-spring-boot.live.activate", async appData => {
            activeBootApp = appData;
            await commands.executeCommand('sts/livedata/localAdd', activeBootApp);
            updateBootAppState("disconnected");
        }),

        commands.registerCommand("vscode-spring-boot.live.deactivate", async () => {
            // Fires on every debug/run session termination, not just ones where live-hover was
            // ever activated, so activeBootApp may be unset here.
            if (activeBootApp) {
                await commands.executeCommand('sts/livedata/localRemove', getProcessKey(activeBootApp));
                activeBootApp = undefined;
            }
            updateBootAppState("none");
        }),

        commands.registerCommand("vscode-spring-boot.live.show.active", async () => {
            if (!activeBootApp) {
                return;
            }
            try {
                updateBootAppState("connecting");
                await commands.executeCommand(CONNECT_CMD, {
                    processKey: getProcessKey(activeBootApp)
                });
                updateBootAppState("connected");
            } catch (error) {
                updateBootAppState("disconnected");
                throw error;
            }
        }),

        commands.registerCommand("vscode-spring-boot.live.refresh.active", async () => {
            if (!activeBootApp) {
                return;
            }
            await commands.executeCommand(REFRESH_CMD, {
                processKey: getProcessKey(activeBootApp)
            });
        }),

        commands.registerCommand("vscode-spring-boot.live.hide.active", async () => {
            if (!activeBootApp) {
                return;
            }
            try {
                updateBootAppState("disconnecting");
                await commands.executeCommand(DISCONNECT_CMD, {
                    processKey: getProcessKey(activeBootApp)
                });
                updateBootAppState("disconnected");
            } catch (error) {
                updateBootAppState("connected");
                throw error;
            }
        }),

    );
}
