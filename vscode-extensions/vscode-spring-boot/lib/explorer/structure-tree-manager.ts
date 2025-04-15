import { commands, EventEmitter, Event, Uri } from "vscode";
import { AotProcessorNode, BeanMethodContainerNode, BeanNode, BeanRegistrarNode, ConfigPropertyNode, DocumentNode, EventListenerNode, EventPublisherNode, ProjectNode, QueryMethodNode, RequestMappingNode, SpringNode, WebfluxRoutesNode } from "./nodes";

const SPRING_STRUCTURE_CMD = "sts/spring-boot/structure";

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
                case "ProjectElement":
                    return new ProjectNode(json.projectName as string, this.parseArray(json.children));
                case "DocumentElement":
                    return new DocumentNode(Uri.parse(json.docURI as string), this.parseArray(json.children));
                case "Bean":
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
                case "AotProcessorElement":
                    return new AotProcessorNode(
                        this.parseArray(json.children),
                        json.name,
                        Uri.parse(json.docUri)
                    );
                case "BeanMethodContainerElement":
                    return new BeanMethodContainerNode(
                        this.parseArray(json.children),
                        json.type,
                        json.location
                    );
                case "BeanRegistrarElement":
                    return new BeanRegistrarNode(
                        this.parseArray(json.children),
                        json.name,
                        json.type,
                        json.location

                    );
                case "ConfigPropertyIndexElement":
                    return new ConfigPropertyNode(
                        this.parseArray(json.children),
                        json.name,
                        json.type,
                        json.range
                    );
                case "EventListenerIndexElement":
                    return new EventListenerNode(
                        this.parseArray(json.children),
                        json.eventType,
                        json.location,
                        json.containerBeanType,
                        json.annotations
                    );
                case "EventPublisherIndexElement":
                    return new EventPublisherNode(
                        this.parseArray(json.children),
                        json.eventType,
                        json.location,
                        json.eventTypesFromHierarchy,
                    );
                case "QueryMethodIndexElement":
                    return new QueryMethodNode(
                        this.parseArray(json.children),
                        json.methodName,
                        json.queryString,
                        json.range
                    );
                case "RequestMappingIndexElement":
                    return new RequestMappingNode(
                        this.parseArray(json.children),
                        json.path,
                        json.httpMethods,
                        json.contentTypes,
                        json.acceptTypes,
                        json.symbolLabel,
                        json.range
                    );
                case "WebfluxRouteElementRangesIndexElement":
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
        }
    }

    private parseArray(json: any): SpringNode[] {
        return Array.isArray(json) ? (json as []).map(j => this.parseNode(j)).filter(e => !!e) : [];
    }

    public get onDidChange(): Event<SpringNode | undefined> {
        return this._onDidChange.event;
    }

}