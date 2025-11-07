import { commands, EventEmitter, Event, ExtensionContext, window, Memento, QuickPickItem } from "vscode";
import { StereotypedNode } from "./nodes";
import { ExtensionAPI } from "../api";

const SPRING_STRUCTURE_CMD = "sts/spring-boot/structure";

interface StructureCommandParams {
    updateMetadata: boolean;
    groups?: Record<string, string[]>;
    affectedProjects?: string[];
}

export class StructureManager {

    private _rootElementsRequest: Thenable<StereotypedNode[]>
    private _rootElements: StereotypedNode[] = [];
    private _onDidChange = new EventEmitter<undefined | StereotypedNode | StereotypedNode[]>();
    private workspaceState: Memento;

    constructor(context: ExtensionContext, api: ExtensionAPI) {
        this.workspaceState = context.workspaceState;
        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.refresh", () => this.refresh(true)));
        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.openReference", (node: StereotypedNode) => {
            const reference = node?.referenceValue;
            if (reference) {
                const location = api.client.protocol2CodeConverter.asLocation(reference)
                window.showTextDocument(location.uri, { selection: location.range });
            }
        }));

        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.grouping", async (node: StereotypedNode) => {
            const projectName = node.projectId;
            const groups = await commands.executeCommand<Groups>("sts/spring-boot/structure/groups", projectName);
            const initialGroups: string[] | undefined = this.getVisibleGroups(projectName);
            const items = (groups?.groups || []).map(g => ({
                label: g.displayName,
                group: g,
                description: g.identifier,
                picked: initialGroups ? initialGroups.includes(g.identifier) : true
            } as GroupQuickPickItem));
            const selectedGroupItems = await window.showQuickPick(items, {
                canPickMany: true,
                ignoreFocusOut: true,
                title: `Select groups to show/hide for project ${projectName}`,
                placeHolder: 'Select groups to show/hide'
            });
            if (selectedGroupItems) {
                await this.setVisibleGroups(projectName, items.length === selectedGroupItems.length ? undefined : selectedGroupItems.map(i => i.group.identifier));
                this.refresh(false);
            }
        }));

        context.subscriptions.push(api.getSpringIndex().onSpringIndexUpdated(indexUpdateDetails => this.refresh(false, indexUpdateDetails.affectedProjects)));
        
    }

    get rootElements(): Thenable<StereotypedNode[]> {
        return this._rootElementsRequest;
    }

    // Serves 2 purposes: non UI triggered refresh as a result of the index update and a UI triggered refresh
    // The UI triggered refresh needs to proceed with an event fired such that tree view would kick off a new promise getting all new root elements and would show progress while promise is being resolved.
    // The index update typically would have a list of projects for which index has changed then the refresh can be silent with letting the tree know about new data once it is computed
    // If the index update event doesn't have a list of project then this is an edge case for which we'd show the preogress and treat it like UI triggered refresh
    refresh(updateMetadata: boolean, affectedProjects?: string[]): void {
        const isPartialLoad = !!(affectedProjects && affectedProjects.length);
        // Notify the tree to get the children to trigger "loading" bar in the view???
        const params = {
            updateMetadata,
            affectedProjects,
            groups: this.getGroupings(),
        } as StructureCommandParams;
        this._rootElementsRequest = commands.executeCommand(SPRING_STRUCTURE_CMD, params).then(json => {
            const nodes = this.parseArray(json);
            if (isPartialLoad) {
                const newNodes = [] as StereotypedNode[];
                const nodesMap = {} as Record<string, StereotypedNode>;
                affectedProjects.forEach(projectName => nodesMap[projectName] = nodes.find(n => n.projectId === projectName));
                // merge old and newly fetched stereotype root nodes
                let onlyMutations = true;
                this._rootElements.forEach(n => {
                    if (nodesMap.hasOwnProperty(n.projectId)) {
                        const newN = nodesMap[n.projectId];
                        delete nodesMap[n.projectId];
                        if (newN) {
                            newNodes.push(newN);
                        } else {
                            // element removed
                            onlyMutations = false;
                        }
                    } else {
                        newNodes.push(n);
                    }
                });
                if (Object.values(nodesMap).length) {
                    // elements added
                    onlyMutations = false;
                    Object.values(nodesMap).filter(n => !!n).forEach(n => newNodes.push(n));                       
                }
                this._rootElements = newNodes;
                // TODO: Partial tree refresh didn't work for restbucks it remains either without children or without the full text label
                // (test with `spring-restbucks` project in a workspace with other boot projects, i.e. demo, spring-petclinic)
                this._onDidChange.fire(/*onlyMutations ? nodes : */undefined);
            } else {
                this._rootElements = nodes;
                // No need to fire another event to update the UI since there is an event fired before refresh is triggered to reference the new promise
            }
            return this._rootElements;
        });
        if (!isPartialLoad) {
            // Fire an event for full reload to have a progress bar while the promise above is resolved
            this._onDidChange.fire(undefined);
        } 
    }

    private parseNode(json: any, parent?: StereotypedNode): StereotypedNode | undefined {
        const node = new StereotypedNode(json as LsStereoTypedNode, [], parent);
        // Parse children after creating the node so we can pass it as parent
        node.children.push(...this.parseArray(json.children, node));
        return node;
    }

    private parseArray(json: any, parent?: StereotypedNode): StereotypedNode[] {
        return Array.isArray(json) ? (json as []).map(j => this.parseNode(j, parent)).filter(e => !!e) : [];
    }

    public get onDidChange(): Event<undefined | StereotypedNode | StereotypedNode[]> {
        return this._onDidChange.event;
    }

    private getVisibleGroups(projectName: string): string[] | undefined {
        const groupings =  this.getGroupings();
        return groupings ? groupings[projectName] : undefined;
    }

    private getGroupings(): Record<string, string[]> | undefined {
        return this.workspaceState.get<Record<string, string[]>>(`vscode-spring-boot.structure.group`, undefined);
    }

    private async setVisibleGroups(projectName: string, groups: string[] | undefined): Promise<void> {
        let groupings = this.getGroupings();
        if (groupings) {
            if (groups) {
                groupings[projectName] = groups;
            } else {
                delete groupings[projectName];
            }
        } else {
            if (groups) {
                groupings = { [projectName]: groups };
            }
        }
        await this.workspaceState.update(`vscode-spring-boot.structure.group`, groupings);
    }

}

export interface LsStereoTypedNode {
    readonly attributes: Record<string, any>;
    readonly children: LsStereoTypedNode[];
}

interface Group {
    identifier: string;
    displayName: string;
}

interface Groups {
    projectName: string;
    groups?: Group[];
}

interface GroupQuickPickItem extends QuickPickItem {
    group: Group;
}
