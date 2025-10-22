import { TextDocumentShowOptions, ThemeIcon, TreeItem, TreeItemCollapsibleState, Uri } from "vscode";
import { Location } from "vscode-languageclient";
import { LsStereoTypedNode } from "./structure-tree-manager";

export class SpringNode {
    constructor(public children: SpringNode[], protected parent?: SpringNode) {}
    
    getTreeItem(savedState?: TreeItemCollapsibleState): TreeItem {
        const defaultState = savedState !== undefined ? savedState : TreeItemCollapsibleState.Collapsed;
        return new TreeItem("<node>", this.computeState(defaultState));
    }
    
    computeState(defaultState: TreeItemCollapsibleState): TreeItemCollapsibleState {
        return Array.isArray(this.children) && this.children.length ? defaultState : TreeItemCollapsibleState.None;
    }
    
    getNodeId(): string {
        return "<base-node>";
    }
    
    protected getParentPath(): string {
        if (!this.parent) {
            return "";
        }
        
        const parentText = this.parent.getNodeText();
        // Recursively get the full path of all ancestors up to the root
        const ancestorPath = this.parent.getParentPath();
        
        return ancestorPath ? `${ancestorPath}/${parentText}` : parentText;
    }
    
    protected getNodeText(): string {
        return "<node>";
    }
}

export class StereotypedNode extends SpringNode {
    constructor(private n: LsStereoTypedNode, children: SpringNode[], parent?: SpringNode) {
        super(children, parent);
    }
        
    getTreeItem(savedState?: TreeItemCollapsibleState): TreeItem {
        const item = super.getTreeItem(savedState);
        item.label = this.n.attributes.text;
        item.iconPath = this.computeIcon();
        
        // Add context value if reference attribute exists
        if (this.n.attributes.reference) {
            item.contextValue = "stereotypedNodeWithReference";
        }

        if (this.n.attributes.icon === 'project') {
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

    getProjectId(): string {
        return this.n.attributes.projectId || this.n.attributes.text;
    }
    
    getNodeId(): string {
        return this.n.attributes.nodeId || this.n.attributes.text;
    }
    
    protected getNodeText(): string {
        return this.n.attributes.text || '';
    }

    getReferenceValue(): any {
        return this.n.attributes.reference;
    }

    computeIcon() {
        return new ThemeIcon(this.n.attributes.icon);
    }

}
