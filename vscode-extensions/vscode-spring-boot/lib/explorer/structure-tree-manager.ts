import { commands, EventEmitter, Event, ExtensionContext, window, Memento, QuickPickItem } from "vscode";
import { StereotypedNode } from "./nodes";
import { ExtensionAPI } from "../api";
import { showChangesAgainstHead } from "./git-diff";

const SPRING_STRUCTURE_CMD = "sts/spring-boot/structure";
const SPRING_STRUCTURE_CAPTURE_BASELINE_CMD = "sts/spring-boot/structure/captureBaseline";
const SPRING_STRUCTURE_CLEAR_BASELINE_CMD = "sts/spring-boot/structure/clearBaseline";
const SPRING_STRUCTURE_BASELINE_HISTORY_CMD = "sts/spring-boot/structure/baselineHistory";

const HIDE_UNCHANGED_KEY = "vscode-spring-boot.structure.hideUnchanged";
const HIGHLIGHT_CHANGES_KEY = "vscode-spring-boot.structure.highlightChanges";
const COMPARE_AGAINST_KEY = "vscode-spring-boot.structure.compareAgainst";

interface StructureCommandParams {
    updateMetadata: boolean;
    groups?: Record<string, string[]>;
    affectedProjects?: string[];
    compareAgainst?: Record<string, string>;
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
        commands.executeCommand('setContext', this.key, this.value);
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
    private _onDidChange = new EventEmitter<undefined | StereotypedNode | StereotypedNode[]>();
    private workspaceState: Memento;
    private hideUnchangedToggle: PersistedToggle;
    private highlightChangesToggle: PersistedToggle;

    constructor(context: ExtensionContext, api: ExtensionAPI) {
        this.workspaceState = context.workspaceState;
        // both default to on: once a baseline is captured, changes are highlighted and everything
        // else is hidden, which is the most useful state right after capturing one
        this.hideUnchangedToggle = new PersistedToggle(this.workspaceState, HIDE_UNCHANGED_KEY, true);
        this.highlightChangesToggle = new PersistedToggle(this.workspaceState, HIGHLIGHT_CHANGES_KEY, true);

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

        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.hideUnchangedNodes", () => this.hideUnchangedToggle.set(true)));
        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.showAllNodes", () => this.hideUnchangedToggle.set(false)));

        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.highlightChanges", () => this.highlightChangesToggle.set(true)));
        context.subscriptions.push(commands.registerCommand("vscode-spring-boot.structure.stopHighlightingChanges", () => this.highlightChangesToggle.set(false)));

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

    private async captureBaseline(node: StereotypedNode): Promise<void> {
        const projectName = node?.projectId;
        if (!projectName) {
            return;
        }

        try {
            const result = await commands.executeCommand<CaptureBaselineResult>(SPRING_STRUCTURE_CAPTURE_BASELINE_CMD, projectName);
            // re-fetch the tree so that change markers left over from a previous baseline disappear
            this.refresh(false);
            window.showInformationMessage(`Captured logical structure baseline for '${projectName}' (${result.nodeCount} node(s)). Changes since this point are highlighted in the Logical Structure view.`);
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
            compareAgainst: this.getCompareAgainstMap(),
        } as StructureCommandParams;
        this._rootElementsRequest = commands.executeCommand(SPRING_STRUCTURE_CMD, params).then(json => {
            const nodes = this.parseArray(json);
            if (isPartialLoad) {
                const newNodes = [] as StereotypedNode[];
                const nodesMap = {} as Record<string, StereotypedNode>;
                affectedProjects.forEach(projectName => nodesMap[projectName] = nodes.find(n => n.projectId === projectName));
                // merge old and newly fetched stereotype root nodes
                let _onlyMutations = true;
                this._rootElements.forEach(n => {
                    if (nodesMap.hasOwnProperty(n.projectId)) {
                        const newN = nodesMap[n.projectId];
                        delete nodesMap[n.projectId];
                        if (newN) {
                            newNodes.push(newN);
                        } else {
                            // element removed
                            _onlyMutations = false;
                        }
                    } else {
                        newNodes.push(n);
                    }
                });
                if (Object.values(nodesMap).length) {
                    // elements added
                    _onlyMutations = false;
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
    nodeCount: number;
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
    nodeCount: number;
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

export function shortSha(sha: string): string {
    return sha.substring(0, 7);
}

export function formatCapturedAt(capturedAt: string): string {
    const captured = new Date(capturedAt);
    return isNaN(captured.getTime()) ? capturedAt : captured.toLocaleString();
}

/**
 * How to describe a retained snapshot in one line: its commit if it has one, otherwise the fact
 * that it was captured manually, plus when. `undefined` means "no snapshot pinned".
 */
export function describeBaseline(entry?: { commitSha?: string, commitMessage?: string, capturedAt?: string }): string {
    if (!entry) {
        return 'most recent snapshot';
    }
    if (entry.commitSha) {
        return `${shortSha(entry.commitSha)} - ${entry.commitMessage || '(no commit message)'}`;
    }
    return `manual snapshot from ${entry.capturedAt ? formatCapturedAt(entry.capturedAt) : 'an unknown time'}`;
}
