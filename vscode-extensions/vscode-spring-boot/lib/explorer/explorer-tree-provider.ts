import { CancellationToken, commands, Event, EventEmitter, ProviderResult, TreeDataProvider, TreeItem, TreeItemCollapsibleState } from "vscode";
import { StructureManager } from "./structure-tree-manager";
import { DocumentNode, ProjectNode, SpringNode } from "./nodes";
import * as Path from "path";

export class ExplorerTreeProvider implements TreeDataProvider<SpringNode> {

    private emitter: EventEmitter<undefined | SpringNode | SpringNode[]>;
    public readonly onDidChangeTreeData: Event<undefined | SpringNode | SpringNode[]>;

    constructor(private manager: StructureManager) {
        this.emitter = new EventEmitter<undefined | SpringNode | SpringNode[]>();
        this.onDidChangeTreeData = this.emitter.event;
        this.manager.onDidChange(e => this.emitter.fire(e));
    }

    getTreeItem(element: SpringNode): TreeItem | Thenable<TreeItem> {
        return element.getTreeItem();
    }

    getChildren(element?: SpringNode): ProviderResult<SpringNode[]> {
        if (element) {
            return element.children;
        }
        return this.getRootElements();
    }

    getRootElements(): ProviderResult<SpringNode[]> {
        return this.manager.rootElements;
    }

    // getParent?(element: SpringNode): ProviderResult<SpringNode> {
    //     throw new Error("Method not implemented.");
    // }

    // resolveTreeItem?(item: TreeItem, element: SpringNode, token: CancellationToken): ProviderResult<TreeItem> {
    //     throw new Error("Method not implemented.");
    // }

}