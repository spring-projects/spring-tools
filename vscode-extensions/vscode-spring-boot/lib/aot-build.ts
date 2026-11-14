import * as fs from 'fs';
import * as path from 'path';
import {
    commands, extensions, ExtensionContext, ProcessExecution, ShellExecution, Task, TaskPanelKind, TaskRevealKind, tasks,
    TaskScope, Uri, window, workspace
} from 'vscode';

const MAVEN = 'maven';
const GRADLE = 'gradle';
const MAVEN_EXTENSION_ID = 'vscjava.vscode-maven';
const GRADLE_EXTENSION_ID = 'vscjava.vscode-gradle';
const IS_WINDOWS = process.platform === 'win32';

export interface BuildRequest {
    tool: typeof MAVEN | typeof GRADLE;
    buildFile: string;
    command: string;
    env: Record<string, string>;
}

export interface BuildProvider {
    run(request: BuildRequest): Promise<void>;
}

export const mavenGradleExtensionsBuildProvider: BuildProvider = {
    async run({ tool, buildFile, command, env }) {
        const extensionId = tool === MAVEN ? MAVEN_EXTENSION_ID : GRADLE_EXTENSION_ID;
        if (!extensions.getExtension(extensionId)) {
            const install = `Install ${tool === MAVEN ? 'Maven for Java' : 'Gradle for Java'}`;
            // Shown here and not thrown, so the lens click does not add a second error notification
            if (await window.showErrorMessage(`${install} to run ${tool} builds.`, install) === install) {
                await commands.executeCommand('workbench.extensions.installExtension', extensionId);
            }
            return;
        }
        await commands.executeCommand(tool === MAVEN ? 'maven.goal.custom' : 'gradle.runBuild', buildFile, command, env);
    }
};

export const fallbackBuildProvider: BuildProvider = {
    // runBuild() returns Promise, so we can leave run() signature non-async
    run({ tool, buildFile, command, env }) {
        const dir = path.dirname(buildFile);
        const trimmedCommand = command.trim();
        const args = trimmedCommand.length === 0 ? [] : trimmedCommand.split(/\s+/);
        if (tool === MAVEN) {
            return runBuild('Spring Boot: Maven', buildFile, findUp(dir, IS_WINDOWS ? 'mvnw.cmd' : 'mvnw') ?? 'mvn',
                [...args, '-f', buildFile], env);
        }
        // Run in the project of the build file, including Gradle subprojects.
        return runBuild('Spring Boot: Gradle', buildFile, findUp(dir, IS_WINDOWS ? 'gradlew.bat' : 'gradlew') ?? 'gradle',
            [...args, '-p', dir], env);
    }
};

export const intellijBuildProvider: BuildProvider = {
    // Delegate explicitly until IntelliJ exposes its native build API.
    run: fallbackBuildProvider.run
};

/**
 * The language server attaches these commands to the code lenses that generate the Spring AOT metadata (see
 * `VSCodeBuildCommandProvider`). Keep their arguments compatible with `maven.goal.custom` and `gradle.runBuild`.
 */
export function registerBuildCommands(context: ExtensionContext, buildProvider: Promise<BuildProvider>): void {
    context.subscriptions.push(
        commands.registerCommand('vscode-spring-boot.maven.goal', async (pom: string, goal: string, env: Record<string, string> = {}) =>
            (await buildProvider).run({ tool: MAVEN, buildFile: pom, command: goal, env })),
        commands.registerCommand('vscode-spring-boot.gradle.build', async (buildFile: string, command: string, env: Record<string, string> = {}) =>
            (await buildProvider).run({ tool: GRADLE, buildFile, command, env }))
    );
}

/** Runs the build as a task in the folder of the build file. */
async function runBuild(name: string, buildFile: string, executable: string, args: string[], env: Record<string, string>): Promise<void> {
    const options = { cwd: path.dirname(buildFile), env };
    // Without a shell the environment reaches the build as it is, shell startup files (SDKMAN, for example) cannot
    // change JAVA_HOME. On Windows the wrapper scripts and `mvn` are batch files, which need a shell.
    const execution = IS_WINDOWS
        ? new ShellExecution(executable, args, options)
        : new ProcessExecution(executable, args, options);
    const task = new Task({ type: IS_WINDOWS ? 'shell' : 'process' }, workspace.getWorkspaceFolder(Uri.file(buildFile)) ?? TaskScope.Workspace,
        name, 'Spring Boot', execution, []);
    task.presentationOptions = { reveal: TaskRevealKind.Always, panel: TaskPanelKind.Dedicated, clear: true };
    await tasks.executeTask(task);
}

/** The file in `dir` or the closest parent folder that has it, the lookup Maven for Java uses for `mvnw`. */
function findUp(dir: string, fileName: string): string | undefined {
    while (!fs.existsSync(path.join(dir, fileName))) {
        const parent = path.dirname(dir);
        if (parent === dir) {
            return undefined;
        }
        dir = parent;
    }
    return path.join(dir, fileName);
}
