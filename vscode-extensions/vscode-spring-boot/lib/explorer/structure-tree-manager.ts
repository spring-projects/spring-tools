import { commands, EventEmitter, Event } from "vscode";
import { SpringNode, StereotypedNode } from "./nodes";

const SPRING_STRUCTURE_CMD = "sts/spring-boot/structure";

export class StructureManager {

    private _rootElements: Thenable<SpringNode[]>
    private _onDidChange: EventEmitter<SpringNode | undefined> = new EventEmitter<SpringNode | undefined>();

    get rootElements(): Thenable<SpringNode[]> {
        return this._rootElements;
    }

    refresh(updateMetadata: boolean): void {
        this._rootElements = commands.executeCommand(SPRING_STRUCTURE_CMD,
                {
                    "updateMetadata" : updateMetadata,
                    "groups" : [
                    ]
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

}

export interface LsStereoTypedNode {
    readonly attributes: Record<string, any>;
    readonly children: LsStereoTypedNode[];
}