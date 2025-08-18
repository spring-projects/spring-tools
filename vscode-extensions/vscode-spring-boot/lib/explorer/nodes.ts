import { TextDocumentShowOptions, ThemeIcon, TreeItem, TreeItemCollapsibleState, Uri } from "vscode";
import { Location, Position, Range } from "vscode-languageclient";
import { LsStereoTypedNode } from "./structure-tree-manager";

export class SpringNode {
    constructor(readonly children: SpringNode[]) {}
    getTreeItem(): TreeItem {
        return new TreeItem("<node>", this.computeState(TreeItemCollapsibleState.Expanded));
    }
    computeState(defaultState: TreeItemCollapsibleState.Collapsed | TreeItemCollapsibleState.Expanded): TreeItemCollapsibleState {
        return Array.isArray(this.children) && this.children.length ? defaultState : TreeItemCollapsibleState.None;
    }
}

export class ProjectNode extends SpringNode {
    constructor(readonly name: string, children: SpringNode[]) {
        super(children);
    }
    getTreeItem(): TreeItem {
        const item = super.getTreeItem();
        item.label = this.name;
        return item;
    }
}

export class DocumentNode extends SpringNode {
    constructor(readonly docURI: Uri, children: SpringNode[]) {
        super(children);
    }
    getTreeItem(): TreeItem {
        const item = super.getTreeItem();
        item.label = undefined; // let VSCode derive the label from the resource URI
        item.resourceUri = this.docURI;
        item.iconPath = ThemeIcon.File;
        return item;
    }
}

export class AotProcessorNode extends SpringNode {
    constructor(
        children: SpringNode[],
        readonly type: string,
        readonly docUri: Uri
    ) {
        super(children);
    }
    getTreeItem(): TreeItem {
        const item = super.getTreeItem();
        item.label = this.type;
        item.resourceUri = this.docUri;
        return item;
    }
}

export class BeanMethodContainerNode extends SpringNode {
    constructor(
        children: SpringNode[],
        readonly type: string,
        readonly location: Location,
    ) {
        super(children);
    }
    getTreeItem(): TreeItem {
        const item = super.getTreeItem();
        item.label = this.type;
        return item;
    }
}

export class BeanRegistrarNode extends SpringNode {
    constructor(
        children: SpringNode[],
        readonly name: string,
        readonly type: string,
        readonly location: Location,
    ) {
        super(children);
    }
    getTreeItem(): TreeItem {
        const item = super.getTreeItem();
        item.label = this.name;
        return item;
    }
}

export class ConfigPropertyNode extends SpringNode {
    constructor(
        children: SpringNode[],
        readonly name: string,
        readonly type: string,
        readonly range: Range
    ) {
        super(children);
    }
    getTreeItem(): TreeItem {
        const item = super.getTreeItem();
        item.label = this.name;
        return item;
    }
}

export class EventListenerNode extends SpringNode {
    constructor(
        children: SpringNode[],
        readonly eventType: string,
        readonly location: Location,
        readonly containerBeanType: string,
        readonly annotations: AnnotationMetadata[]
    ) {
        super(children);
    }
    getTreeItem(): TreeItem {
        const item = super.getTreeItem();
        item.label = this.eventType;
        return item;
    }
}

export class EventPublisherNode extends SpringNode {
    constructor(
        children: SpringNode[],
        readonly eventType: string,
        readonly location: Location,
        readonly eventTypesFromHierarchy: string[]
    ) {
        super(children);
    }
    getTreeItem(): TreeItem {
        const item = super.getTreeItem();
        item.label = this.eventType;
        return item;
    }
}

export class QueryMethodNode extends SpringNode {
    constructor(
        children: SpringNode[],
        readonly methodName: string,
        readonly queryString: string,
        readonly range: Range
    ) {
        super(children);
    }
    getTreeItem(): TreeItem {
        const item = super.getTreeItem();
        item.label = this.methodName;
        return item;
    }
}

export class RequestMappingNode extends SpringNode {
    constructor(
        children: SpringNode[],
        readonly path: string,
        readonly httpMethods: string[],
        readonly contentTypes: string[],
        readonly acceptTypes: string[],
        readonly symbolLabel: string,
        readonly range: Range
    ) {
        super(children);
    }
    getTreeItem(): TreeItem {
        const item = super.getTreeItem();
        item.label = this.path;
        return item;
    }
}

export class WebfluxRoutesNode extends RequestMappingNode {
    constructor(
        children: SpringNode[],
        path: string,
        httpMethods: string[],
        contentTypes: string[],
        acceptTypes: string[],
        symbolLabel: string,
        range: Range,
        readonly ranges: Range[]
    ) {
        super(children, path, httpMethods, contentTypes, acceptTypes, symbolLabel, range);
    }
}

export class BeanNode extends SpringNode {
    constructor(
        children: SpringNode[],
        readonly name: string,
        readonly type: string,
        readonly location: Location,
        readonly injectionPoints: InjectionPoint[],
        readonly supertypes: string[],
        readonly annotations: AnnotationMetadata[],
        readonly isConfiguration: boolean,
        readonly symbolLabel: string
    ) {
        super(children);
    }
    getTreeItem(): TreeItem {
        const item = super.getTreeItem();
        item.label = this.name;
        return item;
    }
}

export interface InjectionPoint {
	readonly name: string;
	readonly type: string;
	readonly location: Location;
	readonly annotations: AnnotationMetadata[];
}

export interface AnnotationMetadata {
	readonly annotationType: string;
	readonly isMetaAnnotation: boolean;
	readonly location: Location;
	readonly attributes: {[key: string]: AnnotationAttributeValue[]};
}

export interface AnnotationAttributeValue {
    readonly name: string;
    readonly location: Location;
}

export class StereotypedNode extends SpringNode {
    constructor(private n: LsStereoTypedNode, children: SpringNode[]) {
        super(children);
    }
    getTreeItem(): TreeItem {
        const item = super.getTreeItem();
        item.label = this.n.attributes.text;
        item.iconPath = this.computeIcon();
        if (this.n.attributes.location) {
            const location = this.n.attributes.location as Location;
            // Hard-coded range. Not present... likely not serialized correctly.
            const range = {
                start: {line: 0, character: 0},
                end: {line: 0, character: 0}
            }
            // item.command = {
            //     command: "vscode.open",
            //     title: "Navigate",
            //     arguments: [location.uri, {
            //         selection: /*location.range*/range
            //     } as TextDocumentShowOptions]
            // };
        }
        return item;
    }

    computeIcon() {
        switch (this.n.attributes.icon) {
            case "fa-named-interface": // specify the case
                return new ThemeIcon("symbol-interface");
            case "fa-package":
                return new ThemeIcon("package");
            case "fa-stereotype":
                return new ThemeIcon("symbol-class");
            case "fa-application":
                return new ThemeIcon("folder");    
            default:
                return new ThemeIcon("symbol-object");
        }
    }

}
