import { TextDocumentShowOptions, ThemeIcon, TreeItem, TreeItemCollapsibleState, Uri } from "vscode";
import { Location } from "vscode-languageclient";
import { LsStereoTypedNode } from "./structure-tree-manager";
import * as ls from 'vscode-languageserver-protocol';

export class StereotypedNode {
    constructor(private n: LsStereoTypedNode, public children: StereotypedNode[], protected parent?: StereotypedNode) {}
        
    getTreeItem(savedState?: TreeItemCollapsibleState): TreeItem {
        const defaultState = savedState !== undefined ? savedState : TreeItemCollapsibleState.Collapsed;
        const item = new TreeItem(this.label, Array.isArray(this.children) && this.children.length ? defaultState : TreeItemCollapsibleState.None);
        item.iconPath = new ThemeIcon(this.n.attributes.icon);
        item.id = this.nodeId;
        
        // Add context value if reference attribute exists
        if (this.n.attributes.reference) {
            item.contextValue = "stereotypedNodeWithReference";
        }

        if (this.projectId) {
            item.contextValue = "project";
        }
        
        if (this.n.attributes.location) {
            const location = this.n.attributes.location as Location;
            item.command = {
                command: "vscode.open",
                title: "Navigate",
                arguments: [Uri.parse(location.uri), {
                    selection: location.range
                } as TextDocumentShowOptions]
            };
        }
        return item;
    }

    get projectId(): string {
        return this.n.attributes.projectId;
    }
    
    get nodeId(): string {
        return this.n.attributes.nodeId || this.n.attributes.text;
    }
    
    get label(): string {
        return this.n.attributes.text || '';
    }

    get referenceValue(): ls.Location | undefined {
        return this.n.attributes.reference as ls.Location;
    }

}
