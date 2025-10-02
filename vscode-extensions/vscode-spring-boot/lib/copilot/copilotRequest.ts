import { LanguageModelChatRequestOptions, LanguageModelChatSelector, CancellationToken, Disposable, LanguageModelChatMessage, window, ProgressLocation, lm, LogOutputChannel, LanguageModelChatMessageRole, LanguageModelError } from "vscode";

export const logger: LogOutputChannel = window.createOutputChannel("Spring tools agent", { log: true });

export default class CopilotRequest {

    public static readonly DEFAULT_END_MARK = '<|endofresponse|>';
    public static readonly DEFAULT_MAX_ROUNDS = 2;
    public static readonly DEFAULT_MODEL_SELECTOR: LanguageModelChatSelector = { vendor: 'copilot', family: 'gpt-3.5-turbo' };
    public static readonly DEFAULT_MODEL_OPTIONS: LanguageModelChatRequestOptions = { modelOptions: {} };

    public static readonly NOT_CANCELLABLE: CancellationToken = { isCancellationRequested: false, onCancellationRequested: () => Disposable.from() };

    public constructor(
        private readonly systemMessagesOrPrompts: LanguageModelChatMessage[] = [],
        private readonly modelSelector: LanguageModelChatSelector = CopilotRequest.DEFAULT_MODEL_SELECTOR,
        private readonly modelOptions: LanguageModelChatRequestOptions = CopilotRequest.DEFAULT_MODEL_OPTIONS,
        private readonly endMark: string = CopilotRequest.DEFAULT_END_MARK,
        private readonly maxRounds: number = CopilotRequest.DEFAULT_MAX_ROUNDS,
    ) {
    }

    public async chatRequest(userMessage: LanguageModelChatMessage[], modelOptions: LanguageModelChatRequestOptions = CopilotRequest.DEFAULT_MODEL_OPTIONS, cancellationToken: CancellationToken = CopilotRequest.NOT_CANCELLABLE): Promise<string> {
        const messages = [...this.systemMessagesOrPrompts];
        let answer: string = '';
        let rounds: number = 0;

        return window.withProgress({
            location: ProgressLocation.Window,
            title: "Copilot request",
            cancellable: true
        }, async (progress, cancellation) => {
            progress.report({ message: "processing..." });

            if (cancellation.isCancellationRequested) {
                logger.info("Chat request cancelled");
                return 'Chat request cancelled';
            }
            const _send = async (message: LanguageModelChatMessage[]): Promise<boolean> => {
                rounds++;
                let response: string = '';
                messages.push(...message);
                try {
                    response = await this.sendRequest(messages, modelOptions, cancellationToken);
                    answer += response;
                    logger.info(`Response: \n`, response);
                } catch (e) {
                    if (e instanceof LanguageModelError) {
                        logger.error(e.message, e.code);
                        throw e;
                    } else {
                        const cause = e.cause || e;
                        logger.error(`Failed to chat with copilot`, e.message, e.stack);
                        throw cause;
                    }
                    
                }
                messages.push(new LanguageModelChatMessage(LanguageModelChatMessageRole.Assistant, response));
                return answer.trim().endsWith(this.endMark);
            }
            let completeResponse: boolean = await _send(userMessage);
            while (!completeResponse && rounds < this.maxRounds) {
                completeResponse = await _send([new LanguageModelChatMessage(LanguageModelChatMessageRole.User, `continue your response from where you left off, or end your response with "//${this.endMark}" to finish the conversation.`)]);
            }
            logger.debug('rounds', rounds);
            return answer.replace("//"+this.endMark, "");
            
        });
    }

    private async selectModel() {
        const model = (await lm.selectChatModels(this.modelSelector))?.[0];
        if (!model) {
            const models = await lm.selectChatModels();
            throw new Error(`No suitable model, available models: [${models.map(m => m.name).join(', ')}]. Please make sure you have installed the latest "GitHub Copilot Chat" (v0.16.0 or later).`);
        }
        return model;
    }

    private async sendRequest(messages: LanguageModelChatMessage[], modelOptions: LanguageModelChatRequestOptions, cancellationToken: CancellationToken) {
        const response = [];
        const model = await this.selectModel();
        const chatResponse = await model.sendRequest(messages, modelOptions ?? this.modelOptions, cancellationToken);
        for await (const fragment of chatResponse.text) {
            response.push(fragment);
        }
        return response.join('');
    }
}
