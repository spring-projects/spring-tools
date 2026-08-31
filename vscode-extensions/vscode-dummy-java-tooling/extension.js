'use strict';

const vscode = require('vscode');
const path = require('path');
const os = require('os');
const fs = require('fs');

// Edit this to adapt the recorded message below to your own machine.
// recorded-classpath-message.json contains the placeholders __JDK_PATH__ and
// __MAVEN_REPO_PATH__ wherever the original recording had these absolute paths.
// Neither the project root (__PROJECT_PATH__) nor the JDK path are fixed constants -
// they're discovered at send time, see getProjectPath()/getJdkPath() below.
const MAVEN_REPO_PATH = path.join(os.homedir(), '.m2', 'repository');

const RECORDED_MESSAGE_TEMPLATE = fs.readFileSync(
    path.join(__dirname, 'recorded-classpath-message.json'), 'utf8'
);

let pendingClasspathCallbackCommandId;

function getProjectPath() {
    const folder = vscode.workspace.workspaceFolders && vscode.workspace.workspaceFolders[0];
    return folder ? folder.uri.fsPath : undefined;
}

// Finds an installed JDK the same way a real Java tooling extension would have to:
// prefer JAVA_HOME, otherwise look for a `java` command on PATH and resolve it back
// to its installation root (following symlinks - sdkman/jenv/asdf/homebrew all
// install `java` as one, and the real JDK lives wherever that link points).
function getJdkPath() {
    if (process.env.JAVA_HOME) {
        return process.env.JAVA_HOME;
    }
    const javaExecutable = process.platform === 'win32' ? 'java.exe' : 'java';
    for (const dir of (process.env.PATH || '').split(path.delimiter)) {
        if (!dir) {
            continue;
        }
        const candidate = path.join(dir, javaExecutable);
        try {
            fs.accessSync(candidate, fs.constants.X_OK);
            // java sits at <JDK_HOME>/bin/java(.exe).
            return path.dirname(path.dirname(fs.realpathSync(candidate)));
        } catch {
            // not here, keep looking
        }
    }
    return undefined;
}

// Verbatim recording of a real sts4.classpath.* callback (ClasspathListenerManager
// on the server side), captured from a real redhat.java + spring-petclinic session,
// with its machine-specific paths expanded from the constant/discovered paths above.
function buildRecordedMessage() {
    const projectPath = getProjectPath();
    if (!projectPath) {
        throw new Error('No workspace folder is open - open the folder you want reported as the project first.');
    }
    const jdkPath = getJdkPath();
    if (!jdkPath) {
        throw new Error('No installed JDK found (checked JAVA_HOME and PATH) - install one and make sure the java command is available.');
    }
    return JSON.parse(
        RECORDED_MESSAGE_TEMPLATE
            .replaceAll('__PROJECT_PATH__', projectPath)
            .replaceAll('__JDK_PATH__', jdkPath)
            .replaceAll('__MAVEN_REPO_PATH__', MAVEN_REPO_PATH)
    );
}

function activate(context) {
    context.subscriptions.push(
        vscode.commands.registerCommand('dummy-java-tooling.startLanguageServer', startLanguageServer),
        vscode.commands.registerCommand('dummy-java-tooling.sendClasspath', sendClasspathCommand),
        // Stand-in for redhat.java's own command of the same name. vscode-spring-boot's
        // commons-vscode bridge (classpath.ts / java-data.ts) calls this to delegate to
        // "the Java tooling extension" - we intercept it here instead.
        vscode.commands.registerCommand('java.execute.workspaceCommand', handleWorkspaceCommand)
    );
}

async function startLanguageServer() {
    // This is the exact client command the JDT LS extension invokes
    // (JdtLsExtensionPlugin#start) once it detects a Spring Boot project on
    // the classpath. Calling it directly here starts the Boot LS without
    // needing JDT LS to be present at all.
    await vscode.commands.executeCommand('vscode-spring-boot.ls.start');
    vscode.window.showInformationMessage('Dummy Java Tooling: sent vscode-spring-boot.ls.start');
}

function sendClasspathCommand() {
    if (!pendingClasspathCallbackCommandId) {
        vscode.window.showWarningMessage(
            "Dummy Java Tooling: no classpath listener registered yet - run 'Dummy Java Tooling: Start Spring Boot Language Server' first."
        );
        return;
    }
    sendFakeClasspath();
}

function handleWorkspaceCommand(workspaceCommand, ...args) {
    switch (workspaceCommand) {
        case 'sts.java.addClasspathListener':
            // Just remember the callback command id here - sending the fake classpath is
            // a separate, manually triggered action (see sendClasspathCommand above).
            pendingClasspathCallbackCommandId = args[0];
            vscode.window.showInformationMessage(
                "Dummy Java Tooling: classpath listener registered - run 'Dummy Java Tooling: Send Fake Project/Classpath Info' to report the project."
            );
            return Promise.resolve();
        case 'sts.java.removeClasspathListener':
            pendingClasspathCallbackCommandId = undefined;
            return Promise.resolve();
        // The remaining sts.java.* commands are only needed when the Boot LS resolves
        // types via callbacks to the Java tooling extension instead of indexing the
        // classpath itself (languageserver.boot.enable-jandex-index=false). Turn that
        // flag on for the open workspace folder if you want these to rarely if ever
        // fire - they're stubbed only so a stray hover/navigation doesn't error out.
        case 'sts.java.search.types':
        case 'sts.java.search.packages':
        case 'sts.java.hierarchy.subtypes':
        case 'sts.java.hierarchy.supertypes':
        case 'sts.java.code.completions':
        case 'sts.project.gav':
            console.warn(`Dummy Java Tooling: stubbed workspace command ${workspaceCommand} -> []`);
            return Promise.resolve([]);
        case 'sts.java.type':
        case 'sts.java.javadocHoverLink':
        case 'sts.java.location':
        case 'sts.java.javadoc':
            console.warn(`Dummy Java Tooling: stubbed workspace command ${workspaceCommand} -> null`);
            return Promise.resolve(null);
        default:
            console.warn(`Dummy Java Tooling: unhandled workspace command ${workspaceCommand}`);
            return Promise.resolve(null);
    }
}

function sendFakeClasspath() {
    let message;
    try {
        message = buildRecordedMessage();
    } catch (err) {
        vscode.window.showWarningMessage(`Dummy Java Tooling: ${err.message}`);
        return;
    }
    const { projectUri, name, deleted, classpath, projectBuild, javaCoreOptions } = message;

    // Positional 6-tuple expected by ClasspathListenerManager on the server side:
    // projectUri, name, deleted, classpath, projectBuild, javaCoreOptions.
    vscode.commands.executeCommand(
        pendingClasspathCallbackCommandId,
        projectUri, name, deleted, classpath, projectBuild, javaCoreOptions
    );

    vscode.window.showInformationMessage(
        `Dummy Java Tooling: sent recorded classpath for '${name}' (${classpath.entries.length} classpath entries).`
    );
}

function deactivate() {}

module.exports = { activate, deactivate };
