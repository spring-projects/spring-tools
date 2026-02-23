import {VersionedTextDocumentIdentifier, Position, Range, CodeLens} from 'vscode-languageclient'
import * as VSCode from 'vscode';
import { TextEditor } from 'vscode';

export function toVSRange(rng : Range) : VSCode.Range {
    return new VSCode.Range(toPosition(rng.start), toPosition(rng.end));
}

function toPosition(p : Position) : VSCode.Position {
    return new VSCode.Position(p.line, p.character);
}
 
export interface HighlightParams {
    doc: VersionedTextDocumentIdentifier;
    codeLenses: CodeLens[];
}

export class HighlightService {

    DECORATION : VSCode.TextEditorDecorationType;

    highlights : Map<string, HighlightParams>;

    dispose() {
        this.DECORATION.dispose();
    }

    constructor() {
        this.DECORATION = VSCode.window.createTextEditorDecorationType({
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

        VSCode.window.onDidChangeActiveTextEditor(editor => this.updateHighlightsForEditor(editor));
    }

    handle(params : HighlightParams) : void {
        this.highlights.set(VSCode.Uri.parse(params.doc.uri).toString(), params);
        this.refresh(params.doc);
    }

    refresh(docId: VersionedTextDocumentIdentifier) {
        for (const editor of VSCode.window.visibleTextEditors) {
            const activeUri = editor.document.uri.toString();
            const activeVersion = editor.document.version;
            if (VSCode.Uri.parse(docId.uri).toString() === activeUri && docId.version === activeVersion) {
                //We only update highlights in the active editor for now
                this.updateHighlightsForEditor(editor);
            }
        }
    }

    private updateHighlightsForEditor(editor: TextEditor) {
        if (editor) {
            const highlightParams: HighlightParams = this.highlights.get(editor.document.uri.toString());
            const highlights: CodeLens[] = highlightParams?.codeLenses || [];
            const decorations = highlights.map(hl => toVSRange(hl.range));
            editor.setDecorations(this.DECORATION, decorations);
        }
    }
}
