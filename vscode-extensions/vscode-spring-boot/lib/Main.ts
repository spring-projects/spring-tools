'use strict';

import * as OS from "os";
import {
    commands,
    window,
    workspace,
    ExtensionContext,
    Uri,
    lm,
    TextDocumentContentProvider
} from 'vscode';

import * as commons from '@pivotal-tools/commons-vscode';
import * as liveHoverUi from './live-hover-connect-ui';

import { startDebugSupport } from './debug-config-provider';
import { ApiManager } from "./apiManager";
import { ExtensionAPI } from "./api";
import {registerClasspathService} from "@pivotal-tools/commons-vscode/lib/classpath";
import {registerJavaDataService} from "@pivotal-tools/commons-vscode/lib/java-data";
import * as setLogLevelUi from './set-log-levels-ui';
import { startTestJarSupport } from "./test-jar-launch";
import { startPropertiesConversionSupport } from "./convert-props-yaml";
import { activateCopilotFeatures } from "./copilot";
import * as springBootAgent from './copilot/springBootAgent';
import { applyLspEdit } from "./copilot/guideApply";
import { isLlmApiReady } from "./copilot/util";
import { logger } from "./copilot/copilotRequest";
import { StructureManager } from "./explorer/structure-tree-manager";
import { ExplorerTreeProvider } from "./explorer/explorer-tree-provider";

const PROPERTIES_LANGUAGE_ID = "spring-boot-properties";
const YAML_LANGUAGE_ID = "spring-boot-properties-yaml";
const JAVA_LANGUAGE_ID = "java";
const XML_LANGUAGE_ID = "xml";
const FACTORIES_LANGUAGE_ID = "spring-factories";
const JPA_QUERY_PROPERTIES_LANGUAGE_ID = "jpa-query-properties";

const STOP_ASKING = "Stop Asking";

/** Called when extension is activated */
export async function activate(context: ExtensionContext): Promise<ExtensionAPI> {

    // registerPipelineGenerator(context);

    const specificVmArgs = ["-Dspring.config.location=classpath:/application.properties"];

    const aiPreferencesConfig = workspace.getConfiguration("boot-java.ai");
    const mcpServerEnabled = aiPreferencesConfig.get("mcp-server-enabled");
    const mcpServerPort = aiPreferencesConfig.get<number>("mcp-server-port", 50627);

    if (mcpServerEnabled === true && mcpServerPort >= 0 && mcpServerPort <= 655536) {
        specificVmArgs.push("-Dserver.port=" + mcpServerPort);
    }
    else {
        specificVmArgs.push("-Dspring.main.web-application-type=NONE");
    }

    const options : commons.ActivatorOptions = {
        DEBUG: false,
        CONNECT_TO_LS: false,
        extensionId: 'vscode-spring-boot',
        preferJdk: true,
        jvmHeap: '1024m',
        vmArgs: specificVmArgs,
        checkjvm: (context: ExtensionContext, jvm: commons.JVM) => {
            const version = jvm.getMajorVersion();
            if (version < 21) {
                throw Error(`Spring Tools Language Server requires Java 21 or higher to be launched. Current Java version is ${version}`);
            }

            if (!jvm.isJdk()) {
                window.showWarningMessage(
                    'JAVA_HOME or PATH environment variable seems to point to a JRE. A JDK is required, hence Boot Hints are unavailable.',
                    STOP_ASKING).then(selection => {
                        if (selection === STOP_ASKING) {
                            options.workspaceOptions.update('checkJVM', false);
                        }
                    }
                );
            }
        },
        workspaceOptions: workspace.getConfiguration("spring-boot.ls"),
        clientOptions: {
            markdown: {
                isTrusted: true
            },
            uriConverters: {
                code2Protocol: (uri) => {
           		        			/*
                    * Workaround for docUri coming from vscode-languageclient on Windows
                    * 
                    * It comes in as "file:///c%3A/Users/ab/spring-petclinic/src/main/java/org/springframework/samples/petclinic/owner/PetRepository.java"
                    * 
                    * While symbols index would have this uri instead:
                    * - "file:///C:/Users/ab/spring-petclinic/src/main/java/org/springframework/samples/petclinic/owner/PetRepository.java"
                    * 
                    * i.e. lower vs upper case drive letter and escaped drive colon
                    */
                    if (OS.platform() === "win32" && uri.scheme === 'file') {
                        let uriStr = uri.toString();
                        let idx = 5; // skip through `file:
                        for (; idx < uriStr.length - 1 && uriStr.charAt(idx) === '/'; idx++) {}
                        if (idx < uriStr.length - 1) {
                            // replace c%3A with C: or c: with C:
                            const replaceEscapedColon = idx < uriStr.length - 4 && uriStr.substring(idx + 1, idx + 4) === '%3A';
                            uriStr = `${uriStr.substring(0, idx)}${uriStr.charAt(idx).toUpperCase()}${replaceEscapedColon ? ':' : ''}${uriStr.substring(idx + (replaceEscapedColon ? 4 : 1))}`
                        }
                        return uriStr;
                    }
                    return uri.toString();
                },
                protocol2Code: uri => Uri.parse(uri)
            },
            // See PT-158992999 as to why a scheme is added to the document selector
            // documentSelector: [ PROPERTIES_LANGUAGE_ID, YAML_LANGUAGE_ID, JAVA_LANGUAGE_ID ],
            documentSelector: [
                {
                    language: PROPERTIES_LANGUAGE_ID,
                    scheme: 'file'
                },
                {
                    language: YAML_LANGUAGE_ID,
                    scheme: 'file'
                },
                {
                    language: JAVA_LANGUAGE_ID,
                    scheme: 'file'
                },
                {
                    language: JAVA_LANGUAGE_ID,
                    scheme: 'jdt'
                },
                {
                    language: XML_LANGUAGE_ID,
                    scheme: 'file'
                },
                {
                    language: FACTORIES_LANGUAGE_ID,
                    scheme: 'file'
                },
                {
                    language: JPA_QUERY_PROPERTIES_LANGUAGE_ID,
                    pattern: "**/jpa-named-queries.properties"
                }
            ],
            synchronize: {
                configurationSection: ['boot-java', 'spring-boot', 'http']
            },
            initializationOptions: () => ({
                workspaceFolders: workspace.workspaceFolders ? workspace.workspaceFolders.map(f => f.uri.toString()) : null,
                // Do not enable JDT classpath listeners at the startup - classpath service would enable it later if needed based on the Java extension mode
                // Classpath service registration requires commands to be registered and Boot LS needs to register classpath 
                // listeners when client has callbacks for STS4 extension java related messages registered via JDT classpath and Data Service registration
                enableJdtClasspath: false
            })
        },
        highlightCodeLensSettingKey: 'boot-java.highlight-codelens.on'
    };

    // Register launch config contributior to java debug launch to be able to connect to JMX
    context.subscriptions.push(startDebugSupport());

    // Start Spring Boot Language Server
    const client = await commons.activate(options, context)

    context.subscriptions.push(commands.registerCommand('vscode-spring-boot.ls.start', () => client.start().then(() => {
        // Boot LS is fully started
        registerClasspathService(client);
        registerJavaDataService(client);

        activateCopilotFeatures();

        // Force classpath listener to be enabled. Boot LS can only be launched iff classpath is available and there Spring-Boot on the classpath somewhere.
        commands.executeCommand('sts.vscode-spring-boot.enableClasspathListening', true);

        commands.executeCommand('setContext', 'vscode-spring-boot.ls.started', true);

        // Register TestJars launch support
        context.subscriptions.push(startTestJarSupport());

    })));

    context.subscriptions.push(commands.registerCommand('vscode-spring-boot.ls.stop', () => {
        commands.executeCommand('setContext', 'vscode-spring-boot.ls.started', false);
        client.stop();
    }));
    liveHoverUi.activate(client, options, context);
    setLogLevelUi.activate(client, options, context);
    startPropertiesConversionSupport(context);
    if(isLlmApiReady)
        activateSpringBootParticipant(context);
    else 
        window.showInformationMessage("Spring Boot chat participant is not available. Please use the vscode insiders version 1.90.0 or above and make sure all `lm` API is enabled.");

    registerMiscCommands(context);

    context.subscriptions.push(commands.registerCommand('vscode-spring-boot.agent.apply', applyLspEdit));

	// Register content loader for URIs of type `spring-boot-ls:...` (load JAR content via Boot LS)
    context.subscriptions.push(workspace.registerTextDocumentContentProvider('spring-boot-ls', new (class implements TextDocumentContentProvider {
        provideTextDocumentContent(uri: Uri) {
            return commands.executeCommand<string>('sts/jar/fetch-content', uri.toString());
        }
    })()));

    const api = new ApiManager(client).api

            // Activation of structure explorer
    const structureManager = new StructureManager(context, api);
    new ExplorerTreeProvider(structureManager).createTreeView(context, 'explorer.spring');

    return api;
}

function registerMiscCommands(context: ExtensionContext) {
    context.subscriptions.push(
        commands.registerCommand('vscode-spring-boot.spring.modulith.metadata.refresh', async () => {
            const modulithProjects = await commands.executeCommand('sts/modulith/projects');
            const projectNames = Object.keys(modulithProjects);
            if (projectNames.length === 0) {
                window.showErrorMessage('No Spring Modulith projects found');
            } else {
                const projectName = projectNames.length === 1 ? projectNames[0] : await window.showQuickPick(
                    projectNames,
                    { placeHolder: "Select the target project." },
                );
                commands.executeCommand('sts/modulith/metadata/refresh', modulithProjects[projectName]);
            }
        }),

        commands.registerCommand('vscode-spring-boot.open.url', (openUrl) => {
            const openWithExternalBrowser = workspace.getConfiguration("spring.tools").get("openWith") === "external";
            const browserCommand = openWithExternalBrowser ? "vscode.open" : "simpleBrowser.api.open";
            return commands.executeCommand(browserCommand, Uri.parse(openUrl));
        }),
    );
}

async function activateSpringBootParticipant(context: ExtensionContext) {
    const models = await lm.selectChatModels();
    if (!models || models.length === 0) {
        logger.error(`No suitable model available. Please make sure you have installed the latest "GitHub Copilot Chat" (v0.16.0 or later) and all \`lm\` API is enabled.`);
        return;
    }
    springBootAgent.activate(context);
}
