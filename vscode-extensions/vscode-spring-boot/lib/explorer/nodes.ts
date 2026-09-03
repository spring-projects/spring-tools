import { TextDocumentShowOptions, ThemeColor, ThemeIcon, TreeItem, TreeItemCollapsibleState, Uri } from "vscode";
import { Location } from "vscode-languageclient";
import { LsStereoTypedNode } from "./structure-tree-manager";
import { StructureChange, structureDiffUri } from "./diff-decorations";
import * as ls from 'vscode-languageserver-protocol';

const CHANGE_ICON_COLORS: Record<StructureChange, string> = {
    added: "gitDecoration.addedResourceForeground",
    removed: "gitDecoration.deletedResourceForeground",
    modified: "gitDecoration.modifiedResourceForeground"
};

const CHANGE_TOOLTIPS: Record<StructureChange, string> = {
    added: "added since the captured baseline",
    removed: "removed since the captured baseline",
    modified: "contains changes since the captured baseline"
};

/**
 * The deepest changed nodes of the given trees, i.e. the changed nodes that have no changed child
 * of their own.
 *
 * Revealing exactly these is enough to make every changed node visible: revealing a node expands
 * all of its ancestors, and the ancestors of a changed node are reported as changed as well. Nodes
 * in branches without any change are left alone.
 */
export function deepestChangedNodes(nodes: StereotypedNode[]): StereotypedNode[] {
    const deepest: StereotypedNode[] = [];

    const visit = (node: StereotypedNode) => {
        let hasChangedChild = false;

        for (const child of node.children) {
            if (child.change) {
                hasChangedChild = true;
            }
            visit(child);
        }

        if (node.change && !hasChangedChild) {
            deepest.push(node);
        }
    };

    nodes.forEach(visit);
    return deepest;
}

export class StereotypedNode {
    constructor(private n: LsStereoTypedNode, public children: StereotypedNode[], protected parent?: StereotypedNode) {}
        
    getTreeItem(savedState?: TreeItemCollapsibleState, hideUnchanged = false, highlightChanges = true): TreeItem {
        const defaultState = savedState !== undefined ? savedState : TreeItemCollapsibleState.Collapsed;
        const visibleChildren = this.visibleChildren(hideUnchanged);
        const item = new TreeItem(this.label, visibleChildren.length ? defaultState : TreeItemCollapsibleState.None);
        item.iconPath = new ThemeIcon(this.n.attributes.icon);
        item.id = this.nodeId;

        // nodes that changed since the captured baseline get their icon colored, and a colored
        // label plus a badge via the file decoration provider for the synthetic resource URI -
        // unless highlighting is turned off, in which case the tree looks entirely normal
        const change = highlightChanges ? this.change : undefined;
        if (change) {
            item.iconPath = new ThemeIcon(this.n.attributes.icon, new ThemeColor(CHANGE_ICON_COLORS[change]));
            item.resourceUri = structureDiffUri(change, this.nodeId);
            // set explicitly, otherwise the synthetic resource URI shows up as the tooltip
            item.tooltip = `${this.label} (${CHANGE_TOOLTIPS[change]})`;
        }

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

    /**
     * How this node changed since the baseline captured for its project, if a baseline was
     * captured at all and this node is affected by a change.
     */
    get change(): StructureChange | undefined {
        const change = this.n.attributes.change;
        return change === "added" || change === "removed" || change === "modified" ? change : undefined;
    }

    /**
     * Whether a baseline was captured for this node's project, regardless of whether anything
     * actually changed since then. Only the root of a project's tree carries this attribute, so
     * nodes further down walk up to it.
     */
    get hasBaseline(): boolean {
        return !!(this.parent ? this.parent.hasBaseline : this.n.attributes.hasBaseline);
    }

    /**
     * The children to show for this node.
     *
     * With `hideUnchanged` on and a baseline captured for this node's project, only the children
     * that changed are shown - which is exactly the path towards the changes, since a node
     * containing a change is reported as changed too. If nothing changed at all since the
     * baseline, this legitimately hides every child. Projects without a captured baseline are
     * never filtered, otherwise their whole tree would look empty.
     */
    visibleChildren(hideUnchanged: boolean): StereotypedNode[] {
        if (!Array.isArray(this.children)) {
            return [];
        }
        if (!hideUnchanged || !this.hasBaseline) {
            return this.children;
        }
        return this.children.filter(child => !!child.change);
    }
    
    get label(): string {
        return this.n.attributes.text || '';
    }

    get referenceValue(): ls.Location | undefined {
        return this.n.attributes.reference as ls.Location;
    }

    getParent(): StereotypedNode | undefined {
        return this.parent;
    }

}
