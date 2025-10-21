import { commands, EventEmitter, Event, ExtensionContext, window, Memento, Uri, QuickPickItem } from "vscode";
import { SpringNode, StereotypedNode } from "./nodes";
import { ExtensionAPI } from "../api";
import * as ls from 'vscode-languageserver-protocol';


const SPRING_STRUCTURE_CMD = "sts/spring-boot/structure";

export class StructureManager {

    private _rootElements: Thenable<SpringNode[]>
    private _onDidChange: EventEmitter<SpringNode | undefined> = new EventEmitter<SpringNode | undefined>();
    private workspaceState: Memento;

    constructor(context: ExtensionContext, api: ExtensionAPI) {
        this.workspaceState = context.workspaceState;
        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.refresh", () => this.refresh(true)));
        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.openReference", (node) => {
            if (node && node.getReferenceValue) {
                const reference = node.getReferenceValue();
                if (reference) {
                    commands.executeCommand('vscode.open', api.client.protocol2CodeConverter.asLocation(reference as ls.Location));
                }
            }
        }));

        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.grouping", async (node: StereotypedNode) => {
            const projectName = node.getProjectId();
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

        context.subscriptions.push(api.getSpringIndex().onSpringIndexUpdated(e => this.refresh(false)));
        
    }

    get rootElements(): Thenable<SpringNode[]> {
        return this._rootElements;
    }

    refresh(updateMetadata: boolean): void {
        this._rootElements = commands.executeCommand(SPRING_STRUCTURE_CMD,
                {
                    "updateMetadata" : updateMetadata,
                    "groups" : this.getGroupings()
                }).then(json => {
            const nodes = this.parseArray(json);
            this._onDidChange.fire(undefined);
            return nodes;
        });
    }

    private parseNode(json: any, parent?: SpringNode): SpringNode | undefined {
        const node = new StereotypedNode(json as LsStereoTypedNode, [], parent);
        // Parse children after creating the node so we can pass it as parent
        node.children.push(...this.parseArray(json.children, node));
        return node;
    }

    private parseArray(json: any, parent?: SpringNode): SpringNode[] {
        return Array.isArray(json) ? (json as []).map(j => this.parseNode(j, parent)).filter(e => !!e) : [];
    }

    public get onDidChange(): Event<SpringNode | undefined> {
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
