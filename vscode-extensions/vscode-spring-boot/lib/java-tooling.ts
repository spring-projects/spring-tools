import { commands, extensions, window } from 'vscode';

const LATER = 'Later';

/**
 * Describes a Java tooling extension option (e.g. Red Hat's Language Support for Java,
 * a JetBrains offering, Oracle's, etc.) that Spring Tools can rely on for JDT services.
 */
export interface JavaToolingDescriptor {
    /** id of the extension to detect / wait for activation, e.g. 'redhat.java' */
    extensionId: string;
    /** id of the extension to install when none of the descriptors are present, e.g. an extension pack */
    installExtensionId: string;
    /** label shown on the install button, e.g. 'Red Hat', 'JetBrains' */
    installLabel: string;
}

/**
 * Ensures at least one known Java tooling extension is installed and active before
 * Spring Tools proceeds with its own activation, without hard-depending on any single one.
 */
export class JavaToolingManager {

    constructor(private readonly descriptors: JavaToolingDescriptor[]) {}

    /**
     * Resolves with the descriptor of whichever Java tooling extension becomes active first.
     * Throws if none is installed (after prompting the user to install one).
     */
    async ensureReady(): Promise<JavaToolingDescriptor> {
        const installed = this.installedDescriptors();
        if (installed.length === 0) {
            await this.promptInstall();
            throw new Error('No Java tooling extension is installed.');
        }

        return Promise.race(installed.map(d => extensions.getExtension(d.extensionId).activate().then(() => d)));
    }

    private installedDescriptors(): JavaToolingDescriptor[] {
        return this.descriptors.filter(d => !!extensions.getExtension(d.extensionId));
    }

    private async promptInstall(): Promise<void> {
        const selection = await window.showWarningMessage(
            'Spring Tools works best with a Java tooling extension installed (e.g. Language Support for Java by Red Hat). Install one now, or continue without full language server support.',
            ...this.descriptors.map(d => d.installLabel),
            LATER
        );

        if (selection === LATER || !selection) {
            return;
        }

        const chosen = this.descriptors.find(d => d.installLabel === selection);
        if (chosen) {
            await commands.executeCommand('workbench.extensions.installExtension', chosen.installExtensionId);

            const reload = await window.showInformationMessage(
                'Please reload the window once installation has finished, to activate Spring Tools.',
                'Reload Window'
            );
            if (reload) {
                await commands.executeCommand('workbench.action.reloadWindow');
            }
        }
    }
}
