import * as deepEqual from 'deep-equal';
import {
    CancellationToken,
    CodeLens,
    CodeLensProvider,
    Event,
    EventEmitter,
    ProviderResult,
    TextDocument,
    Uri
} from "vscode";
import {HighlightParams, toVSRange} from './highlight-service';
import * as Lsp from 'vscode-languageclient';

export class HighlightCodeLensProvider implements CodeLensProvider {

    private highlights : Map<string, HighlightParams> = new Map<string, HighlightParams>();

    private _onDidChangeCodeLenses = new EventEmitter<void>();
    public get onDidChangeCodeLenses(): Event<void> {
        return this._onDidChangeCodeLenses.event;
    }

    handle(highlghtParams: HighlightParams) {
        if (!deepEqual(this.highlights.get(highlghtParams.doc.uri), highlghtParams)) {
            this.highlights.set(Uri.parse(highlghtParams.doc.uri).toString(), highlghtParams);
            this._onDidChangeCodeLenses.fire();
        }
    }

    static toVSCodeLens(cl: Lsp.CodeLens): CodeLens {
        return {
            range: toVSRange(cl.range),
            isResolved: true,
            command: cl.command
        } as CodeLens;
    }

    provideCodeLenses(document: TextDocument, _token: CancellationToken): ProviderResult<CodeLens[]> {
        const activeUri = document.uri.toString();
        const activeVersion = document.version;
        const highlightParams = this.highlights.get(activeUri);
        if (highlightParams && highlightParams.doc.version === activeVersion) {
            const codeLenses = highlightParams.codeLenses || [];
            return codeLenses.filter(cl => cl.command).map(cl => HighlightCodeLensProvider.toVSCodeLens(cl));
        }
        return [];
    };

}
