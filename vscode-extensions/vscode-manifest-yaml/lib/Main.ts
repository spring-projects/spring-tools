'use strict';
// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below

import * as VSCode from 'vscode';
import * as commons from '@pivotal-tools/commons-vscode';

/** Called when extension is activated */
export function activate(context: VSCode.ExtensionContext) {
    const options : commons.ActivatorOptions = {
        DEBUG : false,
        CONNECT_TO_LS: false,
        extensionId: 'vscode-manifest-yaml',
        workspaceOptions: VSCode.workspace.getConfiguration("cloudfoundry-manifest.ls"),
        jvmHeap: '64m',
        vmArgs: [
            "-Dspring.config.location=classpath:/application.properties"
        ],
        clientOptions: {
            documentSelector: [
                {
                    language: 'manifest-yaml',
                    scheme: 'file'
                }
            ]
        },
        checkjvm: (context: VSCode.ExtensionContext, jvm: commons.JVM) => {
            const version = jvm.getMajorVersion();
            if (version < 17) {
                throw Error(`Cloudfoundry Manifest YAML Language Server requires Java 17 or higher to be launched. Current Java version is ${version}`);
            }
        }
    };
    commons.activate(options, context).then(client => client.start());
}

