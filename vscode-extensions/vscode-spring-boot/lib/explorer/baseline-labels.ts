/**
 * How a captured baseline snapshot is described to the user, shared by the picker that lists them
 * and the tooltip that names the selected one, so the two cannot drift apart.
 *
 * Deliberately its own module: `nodes.ts` and `structure-tree-manager.ts` already import from each
 * other for their types, and pulling a *value* across that cycle would make the two modules
 * sensitive to load order.
 */

export interface BaselineDescription {
    commitSha?: string;
    commitMessage?: string;
    capturedAt?: string;
}

export function shortSha(sha: string): string {
    return sha.substring(0, 7);
}

export function formatCapturedAt(capturedAt: string): string {
    const captured = new Date(capturedAt);
    return isNaN(captured.getTime()) ? capturedAt : captured.toLocaleString();
}

/**
 * One line describing a retained snapshot: its commit if it has one, otherwise the fact that it was
 * captured manually, plus when. `undefined` means "no snapshot pinned".
 */
export function describeBaseline(entry?: BaselineDescription): string {
    if (!entry) {
        return 'most recent snapshot';
    }
    if (entry.commitSha) {
        return `${shortSha(entry.commitSha)} - ${entry.commitMessage || '(no commit message)'}`;
    }
    return `manual snapshot from ${entry.capturedAt ? formatCapturedAt(entry.capturedAt) : 'an unknown time'}`;
}
