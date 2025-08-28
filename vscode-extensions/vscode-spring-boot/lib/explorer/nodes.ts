import { TextDocumentShowOptions, ThemeIcon, TreeItem, TreeItemCollapsibleState } from "vscode";
import { Location } from "vscode-languageclient";
import { LsStereoTypedNode } from "./structure-tree-manager";

export class SpringNode {
    constructor(readonly children: SpringNode[]) {}
    getTreeItem(): TreeItem {
        return new TreeItem("<node>", this.computeState(TreeItemCollapsibleState.Expanded));
    }
    computeState(defaultState: TreeItemCollapsibleState.Collapsed | TreeItemCollapsibleState.Expanded): TreeItemCollapsibleState {
        return Array.isArray(this.children) && this.children.length ? defaultState : TreeItemCollapsibleState.None;
    }
}

export class StereotypedNode extends SpringNode {
    constructor(private n: LsStereoTypedNode, children: SpringNode[]) {
        super(children);
    }
    getTreeItem(): TreeItem {
        const item = super.getTreeItem();
        item.label = this.n.attributes.text;
        item.iconPath = this.computeIcon();
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

    computeIcon() {
        switch (this.n.attributes.icon) {
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
        }
    }

}
