'use strict';
// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below

import * as VSCode from 'vscode';
import * as commons from '@pivotal-tools/commons-vscode';
import {OutputChannel} from 'vscode';

var log_output : OutputChannel = null;

function log(msg : string) {
    if (log_output) {
        log_output.append(msg +"\n");
    }
}

function error(msg : string) {
    if (log_output) {
        log_output.append("ERR: "+msg+"\n");
    }
}

/** Called when extension is activated */
export function activate(context: VSCode.ExtensionContext) {
    let options : commons.ActivatorOptions = {
        DEBUG : false,
        CONNECT_TO_LS: false,
        extensionId: 'vscode-bosh',
        jvmHeap: "48m",
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
            let version = jvm.getMajorVersion();
            if (version < 17) {
                throw Error(`Bosh Language Server requires Java 17 or higher to be launched. Current Java version is ${version}`);
            }
        }
    };
    let clientPromise = commons.activate(options, context).then(client => client.start());
}

