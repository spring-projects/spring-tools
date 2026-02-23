import { LanguageClient } from "vscode-languageclient/node";
import { ActivatorOptions } from '@pivotal-tools/commons-vscode';
import { LiveProcessLogLevelUpdatedNotification, LiveProcessUpdatedLogLevel } from './notification';
import { BootAppQuickPick, CONNECT_CMD, DISCONNECT_CMD, LIST_CMD } from './live-hover-connect-ui';
import { ExtensionContext, ProgressLocation, commands, window } from "vscode";

interface ProcessCommandInfo {
    processKey : string;
	label: string;
	action: string;
    projectName: string;
}

export interface Logger {
    configuredLevel: string;
    effectiveLevel: string;
}

export interface Loggers {
    [propName: string]: Logger;
}

export interface LoggersData {
    levels: string[];
    loggers: Loggers;
}

export interface ProcessLoggersData {
    loggers: LoggersData;
    processID: string;
    processName: string;
    processType: number;
}

export interface LoggerItem {
    logger: Logger;
    name: string;
}

async function setLogLevelHandler() {
    const processInfo = await selectRunningProcess();
    if (processInfo) {
        try {
            const loggers: ProcessLoggersData = await getLoggers(processInfo);
            await displayLoggers(loggers, processInfo.processKey);
        } catch (_error) {
            window.showErrorMessage("Failed to fetch loggers for the process " + processInfo.processKey);
        }
    }
}

async function selectRunningProcess(): Promise<ProcessCommandInfo | undefined> {
    const quickPick = window.createQuickPick<BootAppQuickPick>();
    quickPick.title = 'Searching for running Spring Boot Apps...';
    quickPick.canSelectMany = false;
    quickPick.busy = true;
    quickPick.show();

    const items = ((await commands.executeCommand(LIST_CMD)) as ProcessCommandInfo[]).filter(cp => CONNECT_CMD === cp.action || DISCONNECT_CMD === cp.action).map(cp => ({
        label: cp.label,
        commandInfo: cp
    } as BootAppQuickPick));

    quickPick.busy = false;

    quickPick.title = items.length ? "Select running Spring Boot App" : "No running Spring Boot Apps found";

    quickPick.items = items;

    if (!items.length) {
        quickPick.hide();
        window.showInformationMessage("No running Spring Boot Apps found");
        return;
    }

    return new Promise((resolve, _reject) => {
        quickPick.onDidChangeSelection(() => quickPick.hide());
        quickPick.onDidHide(async () => resolve(Array.isArray(quickPick.selectedItems) ? quickPick.selectedItems[0]?.commandInfo : undefined))
    });
}

async function getLoggers(processInfo: ProcessCommandInfo): Promise<ProcessLoggersData> {

    return new Promise(async (resolve, reject) => {
        await window.withProgress({
          location: ProgressLocation.Window,
          title: "Fetching Loggers Data for process "+processInfo.processKey,
          cancellable: false
        }, async (progress) => {
          try {
            const loggers: ProcessLoggersData = await commands.executeCommand('sts/livedata/getLoggers', processInfo, {"endpoint": "loggers"}); 
            progress.report({});
            resolve(loggers);
          } catch (error) {
            reject(error);
          }
        });
      });
}

async function displayLoggers(processLoggersData: ProcessLoggersData, processKey: string) {
    let items;
    const loggersData = processLoggersData.loggers;
    if(loggersData.loggers) {
        items = Object.keys(loggersData.loggers).map(packageName => {
            const logger: Logger = loggersData.loggers[packageName];
            const effectiveLevel = logger.effectiveLevel;
            const label = packageName + ' (' + effectiveLevel + ')';
            return {
                packageName,
                effectiveLevel,
                label
            };
        });
    }
    if(items) {
        const chosenPackage = await window.showQuickPick(items, {
            canPickMany: false,
            title: "Select Logger"
        });
        if (chosenPackage) {
            const chosenlogLevel = await window.showQuickPick(loggersData.levels, {
                canPickMany: false,
                title: "Select Level"
            });
            await commands.executeCommand('sts/livedata/configure/logLevel', {"processKey": processKey}, chosenPackage, {"configuredLevel":chosenlogLevel});
        }
    }

}

async function logLevelUpdated(process: LiveProcessUpdatedLogLevel) {
    window.showInformationMessage("The Log level for " + process.packageName + " has been updated from " + 
    process.effectiveLevel + " to " + process.configuredLevel);
}

/** Called when extension is activated */
export function activate(
        client: LanguageClient,
        options: ActivatorOptions,
        context: ExtensionContext
) {
    client.onNotification(LiveProcessLogLevelUpdatedNotification, logLevelUpdated)
    context.subscriptions.push(
        commands.registerCommand('vscode-spring-boot.set.log-levels', () => {
            if (client.isRunning()) {
                return setLogLevelHandler();
            } else {
                window.showErrorMessage("No Spring Boot project found. Action is only available for Spring Boot Projects");
            }
        })
    );
}