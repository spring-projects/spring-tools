import { CancellationToken, FileDecoration, FileDecorationProvider, ProviderResult, ThemeColor, Uri } from "vscode";

/**
 * Scheme of the synthetic URIs the structure tree items use as their `resourceUri` when they
 * changed since the captured baseline. A real file URI cannot be used for this: many nodes
 * (stereotypes, groups, packages) have no file of their own, and for the ones that do, decorating
 * the file itself would collide with the SCM decorations of that file.
 */
export const STRUCTURE_DIFF_SCHEME = "spring-structure-diff";

export type StructureChange = "added" | "removed" | "modified";

/**
 * The URI to attach to a tree item so that {@link StructureDiffDecorationProvider} decorates it.
 * The change is part of the URI, so a node whose change state differs also has a different URI -
 * no decoration invalidation events needed.
 */
export function structureDiffUri(change: StructureChange, nodeId: string): Uri {
    return Uri.parse(`${STRUCTURE_DIFF_SCHEME}:/${change}/${encodeURIComponent(nodeId)}`);
}

/**
 * Colors the labels of structure tree nodes that changed since the captured baseline, and puts a
 * small badge next to them. Reuses the SCM decoration colors, so added/changed nodes read the same
 * way as added/changed files do and the colors follow the active theme.
 */
export class StructureDiffDecorationProvider implements FileDecorationProvider {

    provideFileDecoration(uri: Uri, _token: CancellationToken): ProviderResult<FileDecoration> {
        if (uri.scheme !== STRUCTURE_DIFF_SCHEME) {
            return undefined;
        }

        switch (changeOf(uri)) {
            case "added":
                return {
                    badge: "+",
                    color: new ThemeColor("gitDecoration.addedResourceForeground"),
                    tooltip: "Added since the captured baseline"
                };
            case "removed":
                return {
                    badge: "-",
                    color: new ThemeColor("gitDecoration.deletedResourceForeground"),
                    tooltip: "Removed since the captured baseline"
                };
            case "modified":
                return {
                    badge: "~",
                    color: new ThemeColor("gitDecoration.modifiedResourceForeground"),
                    tooltip: "Contains changes since the captured baseline"
                };
            default:
                return undefined;
        }
    }

}

function changeOf(uri: Uri): StructureChange | undefined {
    // path is `/<change>/<encoded node id>`
    const change = uri.path.split("/")[1];
    return change === "added" || change === "removed" || change === "modified" ? change : undefined;
}
