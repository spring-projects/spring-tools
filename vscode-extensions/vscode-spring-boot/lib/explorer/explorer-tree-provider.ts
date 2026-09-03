import { ConfigurationChangeEvent, Event, EventEmitter, ExtensionContext, ProviderResult, TreeDataProvider, TreeItem, TreeView, window, workspace } from "vscode";
import { StructureManager } from "./structure-tree-manager";
import { deepestChangedNodes, StereotypedNode } from "./nodes";
import { StructureDiffDecorationProvider } from "./diff-decorations";

/**
 * Upper bound on how many nodes are revealed after a refresh. A huge set of changes (a baseline
 * captured before a large refactoring, for example) would otherwise turn into a long series of
 * reveal round trips to the tree widget.
 */
const MAX_REVEALED_NODES = 50;

const HIDE_UNCHANGED_NODES_SETTING = "boot-java.structure.hide-unchanged-nodes";

export class ExplorerTreeProvider implements TreeDataProvider<StereotypedNode> {

    private emitter: EventEmitter<undefined | StereotypedNode | StereotypedNode[]>;
    public readonly onDidChangeTreeData: Event<undefined | StereotypedNode | StereotypedNode[]>;

    private treeView?: TreeView<StereotypedNode>;
    private lastRevealedChanges?: string;

    constructor(private manager: StructureManager) {
        this.emitter = new EventEmitter<undefined | StereotypedNode | StereotypedNode[]>();
        this.onDidChangeTreeData = this.emitter.event;
        this.manager.onDidChange(e => {
            this.emitter.fire(e);
            this.revealChangedNodes();
        });
    }

    createTreeView(context: ExtensionContext, viewId: string) {
        const treeView = window.createTreeView(viewId, { treeDataProvider: this, showCollapseAll: true });
        this.treeView = treeView;
        context.subscriptions.push(treeView);
        context.subscriptions.push(window.registerFileDecorationProvider(new StructureDiffDecorationProvider()));
        context.subscriptions.push(treeView.onDidChangeVisibility(e => {
            if (e.visible) {
                this.revealChangedNodes();
            }
        }));
        context.subscriptions.push(workspace.onDidChangeConfiguration(e => this.onConfigurationChanged(e)));
        return treeView;
    }

    private onConfigurationChanged(event: ConfigurationChangeEvent) {
        if (event.affectsConfiguration(HIDE_UNCHANGED_NODES_SETTING)) {
            // the tree keeps the same data, but which of it is visible changes
            this.lastRevealedChanges = undefined;
            this.emitter.fire(undefined);
            this.revealChangedNodes();
        }
    }

    /**
     * Whether unchanged nodes are hidden once a baseline was captured, showing only the changes
     * and the path leading to them.
     */
    private get hideUnchanged(): boolean {
        return workspace.getConfiguration().get<boolean>(HIDE_UNCHANGED_NODES_SETTING, true);
    }

    /**
     * Expands the tree just far enough to show the nodes that changed since the captured baseline,
     * leaving unchanged branches as the user left them.
     */
    private async revealChangedNodes(): Promise<void> {
        const treeView = this.treeView;
        if (!treeView || !treeView.visible) {
            // revealing would pop the view open, and there is nothing to expand while it is hidden.
            // Once it becomes visible again, onDidChangeVisibility brings us back here.
            return;
        }

        let changed: StereotypedNode[];
        try {
            changed = deepestChangedNodes(await this.getRootElements());
        } catch (_e) {
            // fetching the structure failed, which the tree itself already reports - nothing to expand
            return;
        }

        // Only expand when the set of changes itself is different from what was expanded last time,
        // so that a branch the user collapsed on purpose doesn't spring open again on every
        // unrelated index update.
        const changeKey = changed.map(node => node.nodeId).join("\n");
        if (changeKey === this.lastRevealedChanges) {
            return;
        }
        this.lastRevealedChanges = changeKey;

        for (const node of changed.slice(0, MAX_REVEALED_NODES)) {
            try {
                // expand: false - the node itself doesn't need expanding, only its ancestors do,
                // and any changed node with changed children is an ancestor of one of these
                await treeView.reveal(node, { select: false, focus: false, expand: false });
            } catch (_e) {
                // the tree may have been refreshed again in the meantime and this node be gone
            }
        }
    }

    getTreeItem(element: StereotypedNode): TreeItem | Thenable<TreeItem> {
        return element.getTreeItem(undefined, this.hideUnchanged);
    }

    getChildren(element?: StereotypedNode): ProviderResult<StereotypedNode[]> {
        if (element) {
            return element.visibleChildren(this.hideUnchanged);
        }
        return this.getRootElements();
    }

    getParent(element: StereotypedNode): ProviderResult<StereotypedNode> {
        return element.getParent();
    }

    async getRootElements(): Promise<StereotypedNode[]> {
        if (!this.manager.rootElements) {
            return [];
        }
        const nodes = await this.manager.rootElements;
        return nodes ? nodes.slice().sort((n1, n2) => n1.label < n2.label ? -1 : (n1.label > n2.label ? 1 : 0)) : nodes;
    }
}
