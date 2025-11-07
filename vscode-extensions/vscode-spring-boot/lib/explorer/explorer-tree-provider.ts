import { Event, EventEmitter, ExtensionContext, ProviderResult, TreeDataProvider, TreeItem, window } from "vscode";
import { StructureManager } from "./structure-tree-manager";
import { StereotypedNode } from "./nodes";

export class ExplorerTreeProvider implements TreeDataProvider<StereotypedNode> {

    private emitter: EventEmitter<undefined | StereotypedNode | StereotypedNode[]>;
    public readonly onDidChangeTreeData: Event<undefined | StereotypedNode | StereotypedNode[]>;

    constructor(private manager: StructureManager) {
        this.emitter = new EventEmitter<undefined | StereotypedNode | StereotypedNode[]>();
        this.onDidChangeTreeData = this.emitter.event;
        this.manager.onDidChange(e => this.emitter.fire(e));
    }

    createTreeView(context: ExtensionContext, viewId: string) {
        const treeView = window.createTreeView(viewId, { treeDataProvider: this, showCollapseAll: true });            
        context.subscriptions.push(treeView);
        return treeView
    }

    getTreeItem(element: StereotypedNode): TreeItem | Thenable<TreeItem> {
        return element.getTreeItem();
    }

    getChildren(element?: StereotypedNode): ProviderResult<StereotypedNode[]> {
        if (element) {
            return element.children;
        }
        return this.getRootElements();
    }

    getRootElements(): ProviderResult<StereotypedNode[]> {
        return this.manager.rootElements;
    }
}