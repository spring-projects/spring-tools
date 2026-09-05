import { basename } from "path";
import { commands, extensions, Range, Uri, window } from "vscode";

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
 * Note this diffs against `HEAD`, not against the commit a structure baseline was captured at.
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

    // a file that isn't committed yet simply has empty contents at HEAD, which renders as an
    // all-added diff - the same as what the SCM view shows for it
    await commands.executeCommand("vscode.diff",
        git.toGitUri(uri, "HEAD"),
        uri,
        `${basename(uri.fsPath)} (Working Tree)`,
        { selection });
}

async function gitApi(): Promise<GitApi | undefined> {
    const extension = extensions.getExtension<GitExtension>("vscode.git");
    if (!extension) {
        return undefined;
    }

    const exports = extension.isActive ? extension.exports : await extension.activate();
    return exports?.getAPI(1);
}
