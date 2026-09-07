import { TextDocumentShowOptions, ThemeColor, ThemeIcon, TreeItem, TreeItemCollapsibleState, Uri } from "vscode";
import { Location } from "vscode-languageclient";
import { LsStereoTypedNode, shortSha } from "./structure-tree-manager";
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
        }

        // the tooltip combines the change status (any row can have one) with which baseline the
        // project as a whole is being compared against (project rows only) - built as lines rather
        // than a single string so either part can be present without the other
        const tooltipLines: string[] = [this.label];
        if (change) {
            // set explicitly whenever there's a change, otherwise the synthetic resource URI set
            // above shows up as the tooltip instead
            tooltipLines.push(`(${CHANGE_TOOLTIPS[change]})`);
        }
        if (this.projectId) {
            tooltipLines.push(this.comparedAgainstSha
                ? `Comparing against ${shortSha(this.comparedAgainstSha)} - ${this.comparedAgainstMessage || '(no commit message)'}`
                : 'No logical structure baseline captured yet');
        }
        if (tooltipLines.length > 1) {
            item.tooltip = tooltipLines.join(' ');
        }

        // a space separated list of markers, matched by the `when` clauses of the context menu
        // commands with `viewItem =~ /\bmarker\b/`, so that a node can carry several of them
        const markers: string[] = [];
        if (this.n.attributes.reference) {
            markers.push("stereotypedNodeWithReference");
        }
        if (this.projectId) {
            markers.push("project");
        }
        if (this.change && this.location) {
            // deliberately keyed off the actual change state rather than the highlighting toggle:
            // the changes are there to look at either way
            markers.push("changed");
        }
        if (markers.length) {
            item.contextValue = markers.join(" ");
        }

        const location = this.location;
        if (location) {
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
     * The place in the source this node stands for, for the nodes that stand for one at all -
     * types, methods and the members below them, but not packages or stereotype groups.
     */
    get location(): Location | undefined {
        return this.n.attributes.location as Location;
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
     * The commit sha of the baseline this node's project tree was actually compared against - the
     * most recent one by default, or whichever one the user picked via "Select Baseline to Compare
     * Against". Only the root of a project's tree carries this attribute, so nodes further down
     * walk up to it, same as {@link hasBaseline}. `undefined` when there is no baseline at all.
     */
    get comparedAgainstSha(): string | undefined {
        return this.parent ? this.parent.comparedAgainstSha : this.n.attributes.comparedAgainstSha;
    }

    get comparedAgainstMessage(): string | undefined {
        return this.parent ? this.parent.comparedAgainstMessage : this.n.attributes.comparedAgainstMessage;
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
