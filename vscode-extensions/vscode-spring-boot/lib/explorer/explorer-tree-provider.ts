import { Event, EventEmitter, ProviderResult, TreeDataProvider, TreeItem, TreeItemCollapsibleState } from "vscode";
import { StructureManager } from "./structure-tree-manager";
import { SpringNode } from "./nodes";

export class ExplorerTreeProvider implements TreeDataProvider<SpringNode> {

    private emitter: EventEmitter<undefined | SpringNode | SpringNode[]>;
    public readonly onDidChangeTreeData: Event<undefined | SpringNode | SpringNode[]>;
    private expansionStates: Map<string, TreeItemCollapsibleState> = new Map();

    constructor(private manager: StructureManager) {
        this.emitter = new EventEmitter<undefined | SpringNode | SpringNode[]>();
        this.onDidChangeTreeData = this.emitter.event;
        this.manager.onDidChange(e => {
            // Expansion states are tracked via onDidExpandElement/onDidCollapseElement events
            this.emitter.fire(e);
        });
    }

    getTreeItem(element: SpringNode): TreeItem | Thenable<TreeItem> {
        const nodeId = element.getNodeId();
        const savedState = this.expansionStates.get(nodeId);
        return element.getTreeItem(savedState);
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


    getExpansionState(nodeId: string): TreeItemCollapsibleState | undefined {
        return this.expansionStates.get(nodeId);
    }

    setExpansionState(nodeId: string, state: TreeItemCollapsibleState): void {
        this.expansionStates.set(nodeId, state);
    }

    // getParent?(element: SpringNode): ProviderResult<SpringNode> {
    //     throw new Error("Method not implemented.");
    // }

    // resolveTreeItem?(item: TreeItem, element: SpringNode, token: CancellationToken): ProviderResult<TreeItem> {
    //     throw new Error("Method not implemented.");
    // }

}