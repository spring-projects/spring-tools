import { commands, EventEmitter, Event, ExtensionContext, window, Memento, QuickPickItem, QuickPickItemKind } from "vscode";
import { JavaElementReference, OPEN_JAVA_ELEMENT_CMD, StereotypedNode } from "./nodes";
import { ExtensionAPI } from "../api";
import * as ls from "vscode-languageserver-protocol";
import { showChangesAgainstHead } from "./git-diff";
import { describeBaseline, formatCapturedAt, shortSha } from "./baseline-labels";

const SPRING_STRUCTURE_CMD = "sts/spring-boot/structure";
const SPRING_STRUCTURE_CAPTURE_BASELINE_CMD = "sts/spring-boot/structure/captureBaseline";
const SPRING_STRUCTURE_CLEAR_BASELINE_CMD = "sts/spring-boot/structure/clearBaseline";
const SPRING_STRUCTURE_BASELINE_HISTORY_CMD = "sts/spring-boot/structure/baselineHistory";
const SPRING_STRUCTURE_DEPENDENCIES_CMD = "sts/spring-boot/structure/dependencies";
const SPRING_STRUCTURE_RESOLVE_LOCATION_CMD = "sts/spring-boot/structure/resolveLocation";

const HIDE_UNCHANGED_KEY = "vscode-spring-boot.structure.hideUnchanged";
const HIGHLIGHT_CHANGES_KEY = "vscode-spring-boot.structure.highlightChanges";
const COMPARE_AGAINST_KEY = "vscode-spring-boot.structure.compareAgainst";
const DEPENDENCIES_KEY = "vscode-spring-boot.structure.dependencies";
const INCLUDE_DEPENDENCIES_KEY = "vscode-spring-boot.structure.includeDependencies";

interface StructureCommandParams {
    updateMetadata: boolean;
    groups?: Record<string, string[]>;
    affectedProjects?: string[];
    compareAgainst?: Record<string, string>;
    // per project, the ids of the dependencies to include in its tree - sent in dependency mode
    // only, and a non-empty one is what puts the server into that mode: no change information on
    // any tree then (see docs/structure-view-dependencies.md)
    dependencies?: Record<string, string[]>;
}

/**
 * A boolean switch that is persisted per workspace, mirrored into a `when`-clause context key
 * (same name as the storage key) so package.json can show/hide the two commands that flip it, and
 * fires an event so the tree view can react.
 */
class PersistedToggle {

    private value: boolean;
    private readonly emitter = new EventEmitter<boolean>();

    constructor(private workspaceState: Memento, private key: string, defaultValue: boolean) {
        this.value = this.workspaceState.get<boolean>(this.key, defaultValue);
        // nothing to await in a constructor, but an unobserved rejection would surface as a
        // spurious extension error
        Promise.resolve(commands.executeCommand('setContext', this.key, this.value)).then(undefined, () => {});
    }

    get(): boolean {
        return this.value;
    }

    get onDidChange(): Event<boolean> {
        return this.emitter.event;
    }

    async set(value: boolean): Promise<void> {
        this.value = value;
        await this.workspaceState.update(this.key, value);
        await commands.executeCommand('setContext', this.key, value);
        this.emitter.fire(value);
    }
}

export class StructureManager {

    private _rootElementsRequest: Thenable<StereotypedNode[]>
    private _rootElements: StereotypedNode[] = [];
    private _requestCounter = 0;
    // per project, the number of the request its current node came from - see refresh
    private readonly _projectVersions = new Map<string, number>();
    private _onDidChange = new EventEmitter<undefined | StereotypedNode | StereotypedNode[]>();
    private workspaceState: Memento;
    private hideUnchangedToggle: PersistedToggle;
    private highlightChangesToggle: PersistedToggle;
    private includeDependenciesToggle: PersistedToggle;

    constructor(context: ExtensionContext, api: ExtensionAPI) {
        this.workspaceState = context.workspaceState;
        // both default to off, matching the tree's pre-diff-feature behavior: everything shown,
        // nothing highlighted, until the user explicitly turns diffing on
        this.hideUnchangedToggle = new PersistedToggle(this.workspaceState, HIDE_UNCHANGED_KEY, false);
        this.highlightChangesToggle = new PersistedToggle(this.workspaceState, HIGHLIGHT_CHANGES_KEY, false);
        // off by default as well: the view shows each project's own elements, as it always did,
        // until the user opts into including dependencies
        this.includeDependenciesToggle = new PersistedToggle(this.workspaceState, INCLUDE_DEPENDENCIES_KEY, false);

        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.refresh", () => this.refresh(true)));
        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.openReference", (node: StereotypedNode) => {
            const reference = node?.referenceValue;
            if (reference) {
                const location = api.client.protocol2CodeConverter.asLocation(reference)
                window.showTextDocument(location.uri, { selection: location.range });
            }
        }));

        // a node read from a JAR dependency: the language server has the Java tooling resolve it
        // into a location in the class file only now, when it is opened
        context.subscriptions.push(commands.registerCommand(OPEN_JAVA_ELEMENT_CMD, async (reference: JavaElementReference, label?: string) => {
            try {
                const resolved = await commands.executeCommand<ls.Location | null>(SPRING_STRUCTURE_RESOLVE_LOCATION_CMD, reference);
                if (resolved) {
                    const location = api.client.protocol2CodeConverter.asLocation(resolved);
                    await window.showTextDocument(location.uri, { selection: location.range });
                    return;
                }
            } catch (error) {
                console.error(`Failed to open ${reference.bindingKey}`, error);
            }
            window.setStatusBarMessage(`Cannot open '${label || reference.bindingKey}': not found by the Java tooling`, 5000);
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

        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.dependencies", (node: StereotypedNode) => this.selectDependencies(node)));

        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.showChanges", (node: StereotypedNode) => {
            const location = node?.location;
            if (!location) {
                return undefined;
            }
            const converted = api.client.protocol2CodeConverter.asLocation(location);
            return showChangesAgainstHead(converted.uri, converted.range);
        }));

        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.captureBaseline", (node: StereotypedNode) => this.captureBaseline(node)));
        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.clearBaseline", (node: StereotypedNode) => this.clearBaseline(node)));
        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.selectBaseline", (node: StereotypedNode) => this.selectBaseline(node)));

        // including dependencies and the diff feature are mutually exclusive: turning on one turns
        // off the other (see docs/structure-view-dependencies.md)
        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.hideUnchangedNodes", () => this.turnOnDiffToggle(this.hideUnchangedToggle)));
        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.showAllNodes", () => this.hideUnchangedToggle.set(false)));

        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.highlightChanges", () => this.turnOnDiffToggle(this.highlightChangesToggle)));
        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.stopHighlightingChanges", () => this.highlightChangesToggle.set(false)));

        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.includeDependencies", () => this.setIncludeDependencies(true)));
        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.excludeDependencies", () => this.setIncludeDependencies(false)));

        context.subscriptions.push(api.getSpringIndex().onSpringIndexUpdated(indexUpdateDetails => this.refresh(false, indexUpdateDetails.affectedProjects)));

    }

    /**
     * Whether unchanged nodes are hidden once a baseline was captured, showing only the changes
     * and the path leading to them. Toggled from the "Logical Structure" view's title bar.
     */
    get hideUnchanged(): boolean {
        return this.hideUnchangedToggle.get();
    }

    get onHideUnchangedChanged(): Event<boolean> {
        return this.hideUnchangedToggle.onDidChange;
    }

    /**
     * Whether nodes that changed since the captured baseline are visually highlighted (colored
     * icon/label plus a badge). Independent of {@link hideUnchanged} - either, both or neither can
     * be on. Toggled from the "Logical Structure" view's title bar.
     */
    get highlightChanges(): boolean {
        return this.highlightChangesToggle.get();
    }

    get onHighlightChangesChanged(): Event<boolean> {
        return this.highlightChangesToggle.onDidChange;
    }

    /**
     * Whether each project's tree includes the elements of the dependencies selected for it -
     * dependency mode, in which the trees carry no change information and the diff feature is off.
     * Toggled from the "Logical Structure" view's title bar.
     */
    get includeDependencies(): boolean {
        return this.includeDependenciesToggle.get();
    }

    private async setIncludeDependencies(include: boolean): Promise<void> {
        if (include === this.includeDependencies) {
            return;
        }
        if (include) {
            await this.hideUnchangedToggle.set(false);
            await this.highlightChangesToggle.set(false);
        }
        await this.includeDependenciesToggle.set(include);
        // a different tree: with or without the dependencies, and without or with change information
        this.refresh(false);
    }

    private async turnOnDiffToggle(toggle: PersistedToggle): Promise<void> {
        if (this.includeDependencies) {
            await this.includeDependenciesToggle.set(false);
            await toggle.set(true);
            // the trees at hand carry no change information to show
            this.refresh(false);
        } else {
            await toggle.set(true);
        }
    }

    private async captureBaseline(node: StereotypedNode): Promise<void> {
        const projectName = node?.projectId;
        if (!projectName) {
            return;
        }

        try {
            const result = await commands.executeCommand<CaptureBaselineResult>(SPRING_STRUCTURE_CAPTURE_BASELINE_CMD, projectName);
            // re-fetch the tree so that change markers left over from a previous baseline disappear
            this.refresh(false);
            window.showInformationMessage(`Captured logical structure baseline for '${projectName}' (${result.elementCount} element(s)). Changes since this point are highlighted in the Logical Structure view.`);
        } catch (e) {
            window.showErrorMessage(`Failed to capture logical structure baseline for '${projectName}': ${e}`);
        }
    }

    private async clearBaseline(node: StereotypedNode): Promise<void> {
        const projectName = node?.projectId;
        if (!projectName) {
            return;
        }

        try {
            const result = await commands.executeCommand<ClearBaselineResult>(SPRING_STRUCTURE_CLEAR_BASELINE_CMD, projectName);
            // re-fetch the tree so that leftover change markers disappear (a git-backed project may
            // get a fresh baseline right back on this same refresh, which is expected)
            this.refresh(false);
            window.showInformationMessage(result.hadBaseline
                ? `Cleared the logical structure baseline for '${projectName}'.`
                : `Project '${projectName}' had no logical structure baseline to clear.`);
        } catch (e) {
            window.showErrorMessage(`Failed to clear logical structure baseline for '${projectName}': ${e}`);
        }
    }

    private async selectDependencies(node: StereotypedNode): Promise<void> {
        const projectName = node?.projectId;
        if (!projectName) {
            return;
        }

        let dependencies: Dependencies;
        try {
            dependencies = await commands.executeCommand<Dependencies>(SPRING_STRUCTURE_DEPENDENCIES_CMD, projectName);
        } catch (e) {
            window.showErrorMessage(`Failed to load the dependencies of '${projectName}': ${e}`);
            return;
        }

        const offered = dependencies?.dependencies || [];
        if (offered.length === 0) {
            window.showInformationMessage(`Project '${projectName}' has no dependencies to include.`);
            return;
        }

        const selected = this.getSelectedDependencies(projectName);
        const toItem = (d: DependencyDescriptor) => ({
            label: d.displayName,
            description: d.kind === 'WORKSPACE_PROJECT'
                ? 'workspace project'
                : (d.groupId && d.artifactId ? [d.groupId, d.artifactId, d.version].filter(Boolean).join(':') : undefined),
            detail: d.location,
            picked: selected.includes(d.id),
            dependency: d
        } as DependencyQuickPickItem);

        // the selected dependencies first, so they are seen without scrolling through a long list of
        // libraries - and within the selected and the other ones alike, workspace projects before
        // libraries, each under its category. The server sends workspace projects first, then jars,
        // both sorted by name, and filtering keeps that order.
        const isSelected = (d: DependencyDescriptor) => selected.includes(d.id);
        const groups: [string, DependencyDescriptor[]][] = [
            ['Selected workspace projects', offered.filter(d => d.kind === 'WORKSPACE_PROJECT' && isSelected(d))],
            ['Selected libraries', offered.filter(d => d.kind === 'JAR' && isSelected(d))],
            ['Workspace projects', offered.filter(d => d.kind === 'WORKSPACE_PROJECT' && !isSelected(d))],
            ['Libraries', offered.filter(d => d.kind === 'JAR' && !isSelected(d))]
        ];
        const items: (DependencyQuickPickItem | QuickPickItem)[] = [];
        for (const [label, dependenciesOfGroup] of groups) {
            if (dependenciesOfGroup.length > 0) {
                items.push({ label, kind: QuickPickItemKind.Separator }, ...dependenciesOfGroup.map(toItem));
            }
        }

        const picked = await window.showQuickPick(items, {
            canPickMany: true,
            ignoreFocusOut: true,
            title: `Select dependencies to include in the structure of project ${projectName}`,
            placeHolder: 'Select dependencies to include',
            matchOnDescription: true
        }) as DependencyQuickPickItem[] | undefined;

        if (picked) {
            // ids selected earlier that aren't offered right now (a project closed meanwhile, say)
            // are kept, so the selection comes back once they are offered again
            const notOffered = selected.filter(id => !offered.some(d => d.id === id));
            await this.setSelectedDependencies(projectName, [...notOffered, ...picked.map(i => i.dependency.id)]);
            if (picked.length && !this.includeDependencies) {
                // picking something and seeing nothing happen would be confusing
                await this.setIncludeDependencies(true);
            } else {
                this.refresh(false);
            }
        }
    }

    private async selectBaseline(node: StereotypedNode): Promise<void> {
        const projectName = node?.projectId;
        if (!projectName) {
            return;
        }

        let history: BaselineHistoryEntry[];
        try {
            history = await commands.executeCommand<BaselineHistoryEntry[]>(SPRING_STRUCTURE_BASELINE_HISTORY_CMD, projectName);
        } catch (e) {
            window.showErrorMessage(`Failed to load the baseline history for '${projectName}': ${e}`);
            return;
        }

        if (!history || history.length === 0) {
            window.showInformationMessage(`Project '${projectName}' has no captured logical structure baseline yet.`);
            return;
        }

        const currentKey = this.getCompareAgainst(projectName);
        const current = history.find(entry => entry.capturedAt === currentKey);

        const items: BaselineQuickPickItem[] = [
            {
                label: "$(sync) Most recent snapshot",
                description: "always compare against the newest captured snapshot",
                snapshotKey: undefined
            },
            ...history.map(entry => ({
                // a manually captured snapshot has no commit: it is normally taken over
                // uncommitted work, so only its capture time says anything about it
                label: entry.commitSha ? `$(git-commit) ${shortSha(entry.commitSha)}` : '$(bookmark) Manual snapshot',
                description: entry.commitSha ? (entry.commitMessage || '(no commit message)') : undefined,
                detail: formatCapturedAt(entry.capturedAt),
                snapshotKey: entry.capturedAt
            } as BaselineQuickPickItem))
        ];

        const picked = await window.showQuickPick(items, {
            ignoreFocusOut: true,
            title: `Select the baseline to compare '${projectName}' against (currently: ${describeBaseline(current)})`,
            placeHolder: "Choose a snapshot, or 'Most recent snapshot' for the default"
        });

        if (picked) {
            await this.setCompareAgainst(projectName, picked.snapshotKey);
            this.refresh(false);
        }
    }

    get rootElements(): Thenable<StereotypedNode[]> | undefined {
        // the elements as merged by now, not as they were when the latest request completed: an
        // earlier request completing after it merges its result in later, and tells the view to
        // read again - which has to see that result too
        // (none before the first request, which the tree provider checks for)
        return this._rootElementsRequest?.then(() => this._rootElements);
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
            compareAgainst: this.includeDependencies ? undefined : this.getCompareAgainstMap(),
            dependencies: this.includeDependencies ? this.getDependenciesMap() : undefined,
        } as StructureCommandParams;
        // requests can overlap - an index update's partial refresh while a full load is still
        // being computed, say - and complete in any order. Each project's node is only ever
        // replaced by the result of a request started after the one it came from, so a slow older
        // request can neither roll a project back nor drop one a newer request brought in.
        const requestNumber = ++this._requestCounter;
        const request: Thenable<StereotypedNode[]> = commands.executeCommand(SPRING_STRUCTURE_CMD, params).then(json => {
            const nodes = this.parseArray(json);
            const answered = new Map<string, StereotypedNode>();
            nodes.forEach(n => answered.set(n.projectId, n));
            // the projects this request speaks for: in dependency mode, a partial one also rebuilds
            // the projects that include an affected one
            const covered = isPartialLoad ? new Set<string>([...affectedProjects, ...answered.keys()]) : undefined;
            const isNewer = (projectId: string) => (this._projectVersions.get(projectId) ?? 0) < requestNumber;

            const newNodes = [] as StereotypedNode[];
            this._rootElements.forEach(n => {
                const speaksFor = !covered || covered.has(n.projectId);
                if (speaksFor && isNewer(n.projectId)) {
                    const newN = answered.get(n.projectId);
                    if (newN) {
                        newNodes.push(newN);
                        this._projectVersions.set(n.projectId, requestNumber);
                    } else {
                        // element removed
                        this._projectVersions.delete(n.projectId);
                    }
                } else {
                    newNodes.push(n);
                }
                answered.delete(n.projectId);
            });
            // elements added
            answered.forEach((n, projectId) => {
                if (isNewer(projectId)) {
                    newNodes.push(n);
                    this._projectVersions.set(projectId, requestNumber);
                }
            });
            this._rootElements = newNodes;

            // the view waits for the latest full load itself; for every other result it has to be
            // told - a full load finishing after a later partial refresh included
            if (isPartialLoad || this._rootElementsRequest !== request) {
                // TODO: Partial tree refresh didn't work for restbucks it remains either without children or without the full text label
                // (test with `spring-restbucks` project in a workspace with other boot projects, i.e. demo, spring-petclinic)
                this._onDidChange.fire(undefined);
            }
            return this._rootElements;
        });
        this._rootElementsRequest = request;
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

    private getSelectedDependencies(projectName: string): string[] {
        return this.getDependenciesMap()?.[projectName] || [];
    }

    private getDependenciesMap(): Record<string, string[]> | undefined {
        return this.workspaceState.get<Record<string, string[]>>(DEPENDENCIES_KEY, undefined);
    }

    private async setSelectedDependencies(projectName: string, ids: string[]): Promise<void> {
        const dependencies = { ...(this.getDependenciesMap() || {}) };
        if (ids.length) {
            dependencies[projectName] = ids;
        } else {
            delete dependencies[projectName];
        }
        await this.workspaceState.update(DEPENDENCIES_KEY, Object.keys(dependencies).length ? dependencies : undefined);
    }

    /**
     * Identifies the snapshot the given project is pinned to compare against, if the user picked one
     * via "Select Baseline to Compare Against" - `undefined` means "the most recent one", the
     * default the server itself falls back to.
     */
    private getCompareAgainst(projectName: string): string | undefined {
        return this.getCompareAgainstMap()?.[projectName];
    }

    private getCompareAgainstMap(): Record<string, string> | undefined {
        return this.workspaceState.get<Record<string, string>>(COMPARE_AGAINST_KEY, undefined);
    }

    private async setCompareAgainst(projectName: string, snapshotKey: string | undefined): Promise<void> {
        let compareAgainst = this.getCompareAgainstMap();
        if (compareAgainst) {
            if (snapshotKey) {
                compareAgainst[projectName] = snapshotKey;
            } else {
                delete compareAgainst[projectName];
            }
        } else {
            if (snapshotKey) {
                compareAgainst = { [projectName]: snapshotKey };
            }
        }
        await this.workspaceState.update(COMPARE_AGAINST_KEY, compareAgainst);
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

interface CaptureBaselineResult {
    projectName: string;
    elementCount: number;
    capturedAt: string;
}

interface ClearBaselineResult {
    projectName: string;
    hadBaseline: boolean;
}

interface BaselineHistoryEntry {
    commitSha: string;
    commitMessage: string;
    capturedAt: string;
    elementCount: number;
}

interface DependencyDescriptor {
    id: string;
    kind: 'WORKSPACE_PROJECT' | 'JAR';
    displayName: string;
    groupId?: string;
    artifactId?: string;
    version?: string;
    projectName?: string;
    location?: string;
}

interface Dependencies {
    projectName: string;
    dependencies?: DependencyDescriptor[];
}

interface DependencyQuickPickItem extends QuickPickItem {
    dependency: DependencyDescriptor;
}

interface GroupQuickPickItem extends QuickPickItem {
    group: Group;
}

interface BaselineQuickPickItem extends QuickPickItem {
    /**
     * Identifies the retained snapshot to pin the comparison to (its capture time), or `undefined`
     * for "whichever is the most recent". Not the commit sha: a manually captured snapshot has no
     * commit, and still has to be selectable.
     */
    snapshotKey: string | undefined;
}

