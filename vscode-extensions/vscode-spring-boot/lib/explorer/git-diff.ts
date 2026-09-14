import { basename } from "path";
import { commands, extensions, Range, TextDocumentShowOptions, Uri, window, workspace } from "vscode";

/**
 * The slice of the built-in Git extension's API that showing changes needs. Declared here instead
 * of depending on the extension's typings, so that a disabled or missing Git extension degrades
 * into a message rather than breaking anything.
 */
interface GitApi {
    /** A URI that resolves to the contents of the given file at the given ref. */
    toGitUri(uri: Uri, ref: string): Uri;
    /** The repository the given file belongs to, or null if it is not in one. */
    getRepository(uri: Uri): unknown | null;
}

interface GitExtension {
    getAPI(version: 1): GitApi;
}

/**
 * Shows the working tree version of the given file next to its last committed version, scrolled to
 * the given range - the same diff editor the SCM view opens for a modified file.
 *
 * <p>A file that has no version at `HEAD` at all - brand new, never committed, whether or not it
 * has been `git add`ed - has nothing to diff against: `git show HEAD:path` fails outright for a
 * path that never existed at that ref, it does not resolve to empty content. VS Code's own SCM view
 * recognizes exactly this case (a file whose status has no "original" side) and does not open a
 * diff editor for it either - it just opens the file normally. This does the same, rather than
 * surfacing that git failure as a broken or empty diff editor.
 *
 * <p>Note this diffs against `HEAD`, not against the commit a structure baseline was captured at.
 * Those are the same as long as baselines follow the git history (which they do by default, see
 * `GitBaselineTracker`), and differ only for a baseline pinned manually mid-branch.
 */
export async function showChangesAgainstHead(uri: Uri, selection?: Range): Promise<void> {
    const git = await gitApi();

    if (!git) {
        window.showWarningMessage("Cannot show the changes: the built-in Git extension is not available.");
        return;
    }

    if (!git.getRepository(uri)) {
        window.showInformationMessage(`Cannot show the changes: '${basename(uri.fsPath)}' is not in a Git repository.`);
        return;
    }

    const headUri = git.toGitUri(uri, "HEAD");

    if (await hasContentAt(headUri)) {
        await commands.executeCommand("vscode.diff", headUri, uri, `${basename(uri.fsPath)} (Working Tree)`, { selection });
    } else {
        await commands.executeCommand("vscode.open", uri, { selection } satisfies TextDocumentShowOptions);
    }
}

/**
 * Whether the git extension's own content provider can resolve anything for the given `git:` URI -
 * false for a file with no version at all at the ref it names, which it reports by rejecting
 * (mirroring `git show`'s own exit code for a path that never existed at that ref) rather than
 * resolving to an empty document.
 */
async function hasContentAt(gitUri: Uri): Promise<boolean> {
    try {
        await workspace.openTextDocument(gitUri);
        return true;
    } catch {
        return false;
    }
}

async function gitApi(): Promise<GitApi | undefined> {
    const extension = extensions.getExtension<GitExtension>("vscode.git");
    if (!extension) {
        return undefined;
    }

    const exports = extension.isActive ? extension.exports : await extension.activate();
    return exports?.getAPI(1);
}
