import * as fs from 'fs';
import * as path from 'path';
import {
    commands, extensions, ExtensionContext, ProcessExecution, ShellExecution, Task, TaskPanelKind, TaskRevealKind, tasks,
    TaskScope, Uri, window, workspace
} from 'vscode';

const MAVEN = 'maven';
const GRADLE = 'gradle';
const buildExtensions = {
    [MAVEN]: { id: 'vscjava.vscode-maven', name: 'Maven for Java', command: 'maven.goal.custom' },
    [GRADLE]: { id: 'vscjava.vscode-gradle', name: 'Gradle for Java', command: 'gradle.runBuild' }
};
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
    async run(request) {
        const { id, name } = buildExtensions[request.tool];
        if (!extensions.getExtension(id)) {
            const install = `Install ${name}`;
            if (await window.showWarningMessage(`${name} is required to run this build.`, install) !== install) {
                return;
            }
            await commands.executeCommand('workbench.extensions.installExtension', id);
            const extension = extensions.getExtension(id);
            if (!extension) {
                throw new Error(`${name} is unavailable after installation.`);
            }
            await extension.activate();
        }
        await runExtensionBuild(request);
    }
};

async function runExtensionBuild({ tool, buildFile, command, env }: BuildRequest): Promise<void> {
    await commands.executeCommand(buildExtensions[tool].command, buildFile, command, env);
}

export const intellijBuildProvider: BuildProvider = {
    run(request) {
        const { tool, buildFile, command, env } = request;
        if (extensions.getExtension(buildExtensions[tool].id)) {
            return runExtensionBuild(request);
        }
        // Use a CLI task until we integrate IntelliJ's native build API.
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

/**
 * The language server attaches these commands to the code lenses that generate the Spring AOT metadata (see
 * `ClientCommandBuildCommandProvider`). Keep their arguments compatible with `maven.goal.custom` and `gradle.runBuild`.
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
