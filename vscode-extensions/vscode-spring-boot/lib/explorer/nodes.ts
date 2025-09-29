import { TextDocumentShowOptions, ThemeIcon, TreeItem, TreeItemCollapsibleState } from "vscode";
import { Location } from "vscode-languageclient";
import { LsStereoTypedNode } from "./structure-tree-manager";

export class SpringNode {
    constructor(public children: SpringNode[], private parent?: SpringNode) {}
    
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
        
        if (this.n.attributes.location) {
            const location = this.n.attributes.location as Location;
            // Hard-coded range. Not present... likely not serialized correctly.
            item.command = {
                command: "vscode.open",
                title: "Navigate",
                arguments: [location.uri, {
                    selection: location.range
                } as TextDocumentShowOptions]
            };
        }
        return item;
    }
    
    getNodeId(): string {
        // Create a unique identifier based on node attributes, excluding icon
        // Include parent path in the computation for better uniqueness
        const textId = this.n.attributes.text || '';
        const locationId = this.n.attributes.location ? 
            `${this.n.attributes.location.uri}:${this.n.attributes.location.range.start.line}:${this.n.attributes.location.range.start.character}` : '';
        const referenceId = this.n.attributes.reference ? String(this.n.attributes.reference) : '';
        
        // Build the node-specific part of the ID (without icon)
        const nodeSpecificId = `${textId}|${locationId}|${referenceId}`.replace(/\|+$/, ''); // Remove trailing separators
        
        // Include parent path for better uniqueness
        const parentPath = this.getParentPath();
        return parentPath ? `${parentPath}/${nodeSpecificId}` : nodeSpecificId;
    }
    
    protected getNodeText(): string {
        return this.n.attributes.text || '';
    }

    getReferenceValue(): any {
        return this.n.attributes.reference;
    }

    computeIcon() {
        return new ThemeIcon(this.n.attributes.icon);
/*        switch (this.n.attributes.icon) {
            retur
            case "fa-named-interface": // specify the case
                return new ThemeIcon("symbol-interface");
            case "fa-package":
                return new ThemeIcon("symbol-constant");
            case "fa-stereotype":
                return new ThemeIcon("mention");
            case "fa-application":
                return new ThemeIcon("folder");
            case "fa-method":
                return new ThemeIcon("symbol-method");
            default:
                return new ThemeIcon("symbol-class");
        } */
    }

}
