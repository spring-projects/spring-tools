import {
    VersionedTextDocumentIdentifier,
    Position as LspPosition,
    Range as LspRange,
    CodeLens as LspCodeLens
} from 'vscode-languageclient'
import {
    Position,
    Range,
    TextEditor,
    TextEditorDecorationType,
    Uri,
    window
} from 'vscode';

export function toVSRange(rng : LspRange) : Range {
    return new Range(toPosition(rng.start), toPosition(rng.end));
}

function toPosition(p : LspPosition) : Position {
    return new Position(p.line, p.character);
}
 
export interface HighlightParams {
    doc: VersionedTextDocumentIdentifier;
    codeLenses: LspCodeLens[];
}

export class HighlightService {

    DECORATION : TextEditorDecorationType;

    highlights : Map<string, HighlightParams>;

    dispose() {
        this.DECORATION.dispose();
    }

    constructor() {
        this.DECORATION = window.createTextEditorDecorationType({
            // before: {
            //     contentIconPath: path.resolve(__dirname, "../icons/boot-12h.png"),
            //     margin: '2px 2px 0px 0px'
            // },
            backgroundColor: 'rgba(109,179,63,0.25)',
            borderColor: 'rgba(109,179,63,0.25)',
            borderSpacing: '4px',
            borderRadius: '4px',
            borderWidth: '4px'
        });
        this.highlights = new Map();

        window.onDidChangeActiveTextEditor(editor => this.updateHighlightsForEditor(editor));
    }

    handle(params : HighlightParams) : void {
        this.highlights.set(Uri.parse(params.doc.uri).toString(), params);
        this.refresh(params.doc);
    }

    refresh(docId: VersionedTextDocumentIdentifier) {
        for (const editor of window.visibleTextEditors) {
            const activeUri = editor.document.uri.toString();
            const activeVersion = editor.document.version;
            if (Uri.parse(docId.uri).toString() === activeUri && docId.version === activeVersion) {
                //We only update highlights in the active editor for now
                this.updateHighlightsForEditor(editor);
            }
        }
    }

    private updateHighlightsForEditor(editor: TextEditor) {
        if (editor) {
            const highlightParams: HighlightParams = this.highlights.get(editor.document.uri.toString());
            const highlights: LspCodeLens[] = highlightParams?.codeLenses || [];
            const decorations = highlights.map(hl => toVSRange(hl.range));
            editor.setDecorations(this.DECORATION, decorations);
        }
    }
}
