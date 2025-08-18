import { commands, EventEmitter, Event, Uri } from "vscode";
import { AotProcessorNode, BeanMethodContainerNode, BeanNode, BeanRegistrarNode, ConfigPropertyNode, DocumentNode, EventListenerNode, EventPublisherNode, ProjectNode, QueryMethodNode, RequestMappingNode, SpringNode, StereotypedNode, WebfluxRoutesNode } from "./nodes";

const SPRING_STRUCTURE_CMD = "sts/spring-boot/structure2";

export class StructureManager {

    private _rootElements: Thenable<SpringNode[]>
    private _onDidChange: EventEmitter<SpringNode | undefined> = new EventEmitter<SpringNode | undefined>();

    get rootElements(): Thenable<SpringNode[]> {
        return this._rootElements;
    }

    refresh(): void {
        this._rootElements = commands.executeCommand(SPRING_STRUCTURE_CMD).then(json => {
            const nodes = this.parseArray(json);
            this._onDidChange.fire(undefined);
            return nodes;
        });
    }



    private parseNode(json: any): SpringNode | undefined {
        if (typeof (json._internal_node_type) === 'string') {
            switch (json._internal_node_type) {
                case "org.springframework.ide.vscode.commons.protocol.spring.ProjectElement":
                    return new ProjectNode(json.projectName as string, this.parseArray(json.children));
                case "org.springframework.ide.vscode.commons.protocol.spring.DocumentElement":
                    return new DocumentNode(Uri.parse(json.docURI as string), this.parseArray(json.children));
                case "org.springframework.ide.vscode.commons.protocol.spring.Bean":
                    return new BeanNode(
                        this.parseArray(json.children),
                        json.name,
                        json.type,
                        json.location,
                        json.injectionPoints,
                        json.supertypes,
                        json.annotations,
                        json.isConfiguration,
                        json.symbolLabel
                    );
                case "org.springframework.ide.vscode.commons.protocol.spring.AotProcessorElement":
                    return new AotProcessorNode(
                        this.parseArray(json.children),
                        json.name,
                        Uri.parse(json.docUri)
                    );
                case "org.springframework.ide.vscode.commons.protocol.spring.BeanMethodContainerElement":
                    return new BeanMethodContainerNode(
                        this.parseArray(json.children),
                        json.type,
                        json.location
                    );
                case "org.springframework.ide.vscode.commons.protocol.spring.BeanRegistrarElement":
                    return new BeanRegistrarNode(
                        this.parseArray(json.children),
                        json.name,
                        json.type,
                        json.location

                    );
                case "org.springframework.ide.vscode.boot.java.beans.ConfigPropertyIndexElement":
                    return new ConfigPropertyNode(
                        this.parseArray(json.children),
                        json.name,
                        json.type,
                        json.range
                    );
                case "org.springframework.ide.vscode.boot.java.events.EventListenerIndexElement":
                    return new EventListenerNode(
                        this.parseArray(json.children),
                        json.eventType,
                        json.location,
                        json.containerBeanType,
                        json.annotations
                    );
                case "org.springframework.ide.vscode.boot.java.events.EventPublisherIndexElement":
                    return new EventPublisherNode(
                        this.parseArray(json.children),
                        json.eventType,
                        json.location,
                        json.eventTypesFromHierarchy,
                    );
                case "org.springframework.ide.vscode.boot.java.data.QueryMethodIndexElement":
                    return new QueryMethodNode(
                        this.parseArray(json.children),
                        json.methodName,
                        json.queryString,
                        json.range
                    );
                case "org.springframework.ide.vscode.boot.java.requestmapping.RequestMappingIndexElement":
                    return new RequestMappingNode(
                        this.parseArray(json.children),
                        json.path,
                        json.httpMethods,
                        json.contentTypes,
                        json.acceptTypes,
                        json.symbolLabel,
                        json.range
                    );
                case "org.springframework.ide.vscode.boot.java.requestmapping.WebfluxRouteElementRangesIndexElement":
                    return new WebfluxRoutesNode(
                        this.parseArray(json.children),
                        json.path,
                        json.httpMethods,
                        json.contentTypes,
                        json.acceptTypes,
                        json.symbolLabel,
                        json.range,
                        json.ranges
                    );
            }
        } else {
            // parse stereotype nodes
            return new StereotypedNode(json as LsStereoTypedNode, this.parseArray(json.children));
        }
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