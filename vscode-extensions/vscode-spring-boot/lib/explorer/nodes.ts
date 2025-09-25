import { TextDocumentShowOptions, ThemeIcon, TreeItem, TreeItemCollapsibleState } from "vscode";
import { Location } from "vscode-languageclient";
import { LsStereoTypedNode } from "./structure-tree-manager";

export class SpringNode {
    constructor(readonly children: SpringNode[]) {}
    
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
}

export class StereotypedNode extends SpringNode {
    constructor(private n: LsStereoTypedNode, children: SpringNode[]) {
        super(children);
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
        // Create a unique identifier based on node attributes
        // Use text, icon, and location as identifying factors
        const textId = this.n.attributes.text || '';
        const iconId = this.n.attributes.icon || '';
        const locationId = this.n.attributes.location ? 
            `${this.n.attributes.location.uri}:${this.n.attributes.location.range.start.line}:${this.n.attributes.location.range.start.character}` : '';
        const referenceId = this.n.attributes.reference ? String(this.n.attributes.reference) : '';
        
        // Create a stable ID that can be used to match nodes across refreshes
        return `${textId}|${iconId}|${locationId}|${referenceId}`.replace(/\|+$/, ''); // Remove trailing separators
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
