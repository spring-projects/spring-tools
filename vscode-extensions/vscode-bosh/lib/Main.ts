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
        extensionId: 'vscode-bosh',
        jvmHeap: "48m",
        vmArgs: [
            "-Dspring.config.location=classpath:/application.properties"
        ],
        workspaceOptions: VSCode.workspace.getConfiguration("bosh.ls"),
        clientOptions: {
            documentSelector: [
                {
                    language: 'bosh-deployment-manifest',
                    scheme: 'file'
                },
                {
                    language: 'bosh-cloud-config',
                    scheme: 'file'
                }
            ],
            synchronize: {
                configurationSection: "bosh"
            }
        },
        checkjvm: (context: VSCode.ExtensionContext, jvm: commons.JVM) => {
            const version = jvm.getMajorVersion();
            if (version < 17) {
                throw Error(`Bosh Language Server requires Java 17 or higher to be launched. Current Java version is ${version}`);
            }
        }
    };
    commons.activate(options, context).then(client => client.start());
}

