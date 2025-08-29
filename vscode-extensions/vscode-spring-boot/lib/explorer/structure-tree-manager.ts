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
        this._rootElements = commands.executeCommand(SPRING_STRUCTURE_CMD, updateMetadata).then(json => {
            const nodes = this.parseArray(json);
            this._onDidChange.fire(undefined);
            return nodes;
        });
    }

    private parseNode(json: any): SpringNode | undefined {
        return new StereotypedNode(json as LsStereoTypedNode, this.parseArray(json.children));
    }

    private parseArray(json: any): SpringNode[] {
        return Array.isArray(json) ? (json as []).map(j => this.parseNode(j)).filter(e => !!e) : [];
    }

    public get onDidChange(): Event<SpringNode | undefined> {
        return this._onDidChange.event;
    }

}

export interface LsStereoTypedNode {
    readonly attributes: Record<string, any>;
    readonly children: LsStereoTypedNode[];
}