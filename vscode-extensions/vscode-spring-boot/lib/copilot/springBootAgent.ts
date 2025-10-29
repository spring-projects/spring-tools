import CopilotRequest from "./copilotRequest";
import { CancellationToken, chat, ChatContext, ChatRequest, ChatResponseStream, ChatResult, commands, ExtensionContext, l10n, LanguageModelChatMessage, LanguageModelChatMessageRole, Uri, workspace } from "vscode";
import { systemBoot2Prompt, systemBoot3Prompt, systemPrompt } from "./system-ai-prompt";
import { userPrompt } from "./user-ai-prompt";
import { getWorkspaceRoot, writeResponseToFile } from "./util";

const PARTICIPANT_ID = 'springboot.agent';
const SYSTEM_PROMPT = systemPrompt;
const USER_PROMPT = userPrompt;

interface BootProjectInfo {
    name: string;
    uri: string;
    mainClass: string;
    buildTool: string;
    springBootVersion: string;
    javaVersion: string;
}

interface SpringBootChatAgentResult extends ChatResult {
    metadata: {
        command: string;
    }
}

export default class SpringBootChatAgent {

    copilotRequest: CopilotRequest;

    constructor(copilotRequest: CopilotRequest) {
        this.copilotRequest = copilotRequest;
    }

    public async handlePrompts(request: ChatRequest, context: ChatContext, stream: ChatResponseStream, cancellationToken: CancellationToken): Promise<SpringBootChatAgentResult> {

        const selectedProject = (await getWorkspaceRoot());
        if(!selectedProject) {
            stream.markdown('No project selected from the workspace');
            return;
        }
        const selectedProjectUri = Uri.file(selectedProject?.fsPath).toString();

        // Fetch project related information from the Spring Boot language server
        const bootProjInfo = await commands.executeCommand("sts/spring-boot/bootProjectInfo", selectedProjectUri) as BootProjectInfo;
        const projectContext = `
            Use the following project information for the solution: Please suggest code compatible with the project version.

            Main Spring project name: ${bootProjInfo.name}
            Root Package name: ${bootProjInfo.mainClass.substring(0, bootProjInfo.mainClass.lastIndexOf('.'))}
            Build tool: ${bootProjInfo.buildTool}
            Spring Boot version: ${bootProjInfo.springBootVersion}
            Java version: ${bootProjInfo.javaVersion}
            User prompt: ${request.prompt}
        `;

        // Enhance prompt with project information and user prompt. Provide spring boot 3 speicifc context when necessary
        const messages = [
            LanguageModelChatMessage.User(projectContext),
            bootProjInfo.springBootVersion.startsWith('3') ? LanguageModelChatMessage.User(systemBoot3Prompt) : LanguageModelChatMessage.User(systemBoot2Prompt),
            LanguageModelChatMessage.User('User Input: ' +request.prompt),
            
        ];

        stream.progress('Generating code....This will take a few minutes');

        // Chat request to copilot LLM
        const response = await this.copilotRequest.chatRequest(messages, {}, cancellationToken);
        let documentContent;

        if (response == null || response === '') {
            documentContent = 'Failed to process the request. Please try again.';
        } else {
            if (bootProjInfo.springBootVersion.startsWith('3')) {
                documentContent = await commands.executeCommand("sts/copilot/agent/enhanceResponse", response) as string;
            } else {
                documentContent = 'Note: The code provided is just an example and may not be suitable for production use. \n ' + response;
            }
        }

        // write the final response to markdown file
        await writeResponseToFile(documentContent, bootProjInfo.name, selectedProject.fsPath);

        const chatResponse = Buffer.from(documentContent).toString();
        stream.markdown(chatResponse);
        stream.button({
            command: 'vscode-spring-boot.agent.apply',
            title: l10n.t('Apply Changes')
        });
        return { metadata: { command: '' } };
    }
}

export function activate(
    context: ExtensionContext
) {
    const systemPrompts: LanguageModelChatMessage[] = [
        new LanguageModelChatMessage(LanguageModelChatMessageRole.User, SYSTEM_PROMPT),
        // new LanguageModelChatMessage(LanguageModelChatMessageRole.User, USER_PROMPT)
    ];
    const copilotRequest = new CopilotRequest(systemPrompts);
    const springBootChatAgent = new SpringBootChatAgent(copilotRequest);

    const agent = chat.createChatParticipant(PARTICIPANT_ID, async (request, context, progress, token) => {
        return springBootChatAgent.handlePrompts(request, context, progress, token);
    });
    agent.iconPath = Uri.joinPath(context.extensionUri, 'readme-imgs', 'sts4-32.png');
}