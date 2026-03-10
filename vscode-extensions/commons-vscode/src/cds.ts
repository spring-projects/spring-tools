'use strict';

import {
    ExtensionContext,
    ProgressLocation,
    Uri,
    commands,
    window,
    workspace
} from 'vscode';
import * as Path from 'path';
import * as Crypto from 'crypto';
import { LanguageClient, State } from 'vscode-languageclient/node';
import { JVM } from '@pivotal-tools/jvm-launch-utils';
import { ActivatorOptions } from './launch-util';

const LOG_AOT_VM_ARG_PREFIX = '-Xlog:aot';
const AOT_CACHE_OUTPUT_PREFIX = '-XX:AOTCacheOutput=';
const AOT_CACHE_PREFIX = '-XX:AOTCache=';
const MIN_JAVA_VERSION = 25;

export class CdsResult {
    private readonly _trainingRun: boolean;

    constructor(readonly cdsArgs: string[]) {
        this._trainingRun = cdsArgs.some(a => a.startsWith(AOT_CACHE_OUTPUT_PREFIX));
    }

    get isTrainingRun(): boolean {
        return this._trainingRun;
    }
}

export class CdsSupport {
    private readonly aotCachePath: string;

    constructor(
        private readonly options: ActivatorOptions,
        private readonly context: ExtensionContext,
        private readonly jvm: JVM,
        private readonly useSocket: boolean
    ) {
        this.aotCachePath = CdsSupport.computeAotCachePath(context.extensionPath, jvm, options.extensionId);
    }

    async prepareArgs(): Promise<CdsResult> {
        if (!this.options.workspaceOptions.get('cds.enabled')) {
            return new CdsResult([]);
        }

        if (this.jvm.getMajorVersion() < MIN_JAVA_VERSION) {
            window.showInformationMessage(
                `${this.options.extensionId}: CDS is enabled but requires Java 25+. ` +
                `Current Java version is ${this.jvm.getMajorVersion()}. Starting without CDS.`
            );
            return new CdsResult([]);
        }

        const cdsArgs: string[] = [];

        if (await this.cacheExists()) {
            this.options.clientOptions.outputChannel.appendLine(`CDS: Using existing AOT cache: ${this.aotCachePath}`);
            cdsArgs.push(`${AOT_CACHE_PREFIX}${this.aotCachePath}`);
        } else {
            this.options.clientOptions.outputChannel.appendLine(`CDS: No AOT cache found, will record cache on this run: ${this.aotCachePath}`);
            cdsArgs.push(`${AOT_CACHE_OUTPUT_PREFIX}${this.aotCachePath}`);
        }

        if (!this.useSocket) {
            cdsArgs.push(`${LOG_AOT_VM_ARG_PREFIX}*=off`);
        }

        return new CdsResult(cdsArgs);
    }

    handleTrainingRun(client: LanguageClient): void {
        const disposable = client.onDidChangeState(e => {
            if (e.newState === State.Running) {
                disposable.dispose();
                setTimeout(() => {
                    window.showInformationMessage(
                        `${this.options.extensionId}: CDS training run in progress. Click "Finish Training Run" when done to reload the window with the AOT cache applied.`,
                        'Finish Training Run'
                    ).then(selection => {
                        if (selection === 'Finish Training Run') {
                            window.withProgress({
                                location: ProgressLocation.Notification,
                                title: `${this.options.extensionId}: CDS`,
                                cancellable: false
                            }, async progress => {
                                progress.report({ message: 'Stopping Language Server...' });
                                await client.stop();
                                await this.waitForAotCache(progress);
                            }).then(() => commands.executeCommand('workbench.action.reloadWindow'));
                        }
                    });
                }, 30000); // 30s timeout in ms
            }
        });
        this.context.subscriptions.push(disposable);
    }

    private async cacheExists(): Promise<boolean> {
        try {
            await workspace.fs.stat(Uri.file(this.aotCachePath));
            return true;
        } catch {
            return false;
        }
    }

    private async waitForAotCache(progress: { report(value: { message?: string }): void }): Promise<void> {
        const maxWaitMs = 30000;
        const intervalMs = 1000;
        let waited = 0;
        while (waited < maxWaitMs) {
            if (await this.cacheExists()) {
                progress.report({ message: 'AOT cache ready, reloading window...' });
                return;
            }
            waited += intervalMs;
            progress.report({ message: `Waiting for AOT cache... (${waited / 1000}s)` });
            await new Promise(resolve => setTimeout(resolve, intervalMs));
        }
        progress.report({ message: 'Timed out waiting for AOT cache.' });
    }

    private static computeAotCachePath(extensionPath: string, jvm: JVM, extensionId: string): string {
        const hash = Crypto.createHash('sha256').update(jvm.getJavaHome()).digest('hex').substring(0, 12);
        return Path.join(extensionPath, 'language-server', `${extensionId}_${hash}.aot`);
    }
}
