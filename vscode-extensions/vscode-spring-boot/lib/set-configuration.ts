import { ConfigurationTarget, workspace } from "vscode";
import { LanguageClient, RequestType } from "vscode-languageclient/node";

/**
 * What the language server sends with `sts/setConfiguration`: the full key of a setting and the
 * value to set it to.
 */
interface SetConfigurationParams {
    key: string;
    value: unknown;
}

const SetConfigurationRequest = new RequestType<SetConfigurationParams, void, void>("sts/setConfiguration");

/**
 * Lets the language server set one of the extension's settings - for when it asked the user about
 * one (`window/showMessageRequest`) and the user decided. Sets it in the user settings, like the
 * `boot-ls.client.set-configuration` command quick fixes use.
 */
export function registerSetConfigurationRequest(client: LanguageClient): void {
    client.onRequest(SetConfigurationRequest, async (params: SetConfigurationParams) => {
        const lastDot = params.key.lastIndexOf('.');
        const section = params.key.substring(0, lastDot);
        const settingName = params.key.substring(lastDot + 1);
        await workspace.getConfiguration(section).update(settingName, params.value, ConfigurationTarget.Global);
    });
}
