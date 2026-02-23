'use strict';

import * as VSCode from 'vscode';
import { RequestType } from 'vscode-languageclient';
import { LanguageClient } from 'vscode-languageclient/node';

const JDT_SERVER_STANDARD_MODE = 'Standard'
const CMD_STS_ENABLE_CLASSPATH_LISTENER = 'sts.vscode-spring-boot.enableClasspathListening';

export function registerClasspathService(client : LanguageClient) : void {

    const javaExt = VSCode.extensions.getExtension('redhat.java');
    const javaApi = javaExt?.exports;

    const addRequest = new RequestType<ClasspathListenerParams, ClasspathListenerResponse, void>("sts/addClasspathListener");
    client.onRequest(addRequest, async (params: ClasspathListenerParams) => {
        if (javaApi?.serverMode === 'LightWeight') {
            throw new Error('Classpath listener not supported while Java Language Server is in LightWeight mode');
        }
        return <ClasspathListenerResponse> await VSCode.commands.executeCommand("java.execute.workspaceCommand", "sts.java.addClasspathListener", params.callbackCommandId);
    });

    const removeRequest = new RequestType<ClasspathListenerParams, ClasspathListenerResponse, void>("sts/removeClasspathListener");
    client.onRequest(removeRequest, async (params: ClasspathListenerParams) => {
        return <ClasspathListenerResponse> await VSCode.commands.executeCommand("java.execute.workspaceCommand", "sts.java.removeClasspathListener", params.callbackCommandId);
    });

    if (javaApi) {
        VSCode.commands.executeCommand(CMD_STS_ENABLE_CLASSPATH_LISTENER, javaApi.serverMode === JDT_SERVER_STANDARD_MODE);
        javaApi.onDidServerModeChange(_e => VSCode.commands.executeCommand(CMD_STS_ENABLE_CLASSPATH_LISTENER, javaApi.serverMode === JDT_SERVER_STANDARD_MODE));
    }

}

interface ClasspathListenerParams {
    callbackCommandId: string
}

interface ClasspathListenerResponse {
}
