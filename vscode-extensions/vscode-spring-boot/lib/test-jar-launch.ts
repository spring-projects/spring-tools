import { CancellationToken, DebugConfiguration, DebugConfigurationProvider, DebugConfigurationProviderTriggerKind, DebugSession, Disposable, WorkspaceFolder, commands, debug, window } from "vscode";
import { TEST_RUNNER_MAIN_CLASSES, hookListenerToBooleanPreference } from "./debug-config-provider";
import path from "path";
import { tmpdir } from "os";
import { randomUUID } from "crypto";
import * as fs from "fs";

const ENV_TESTJAR_ARTIFACT_PREFIX = "TESTJARS_ARTIFACT_";

interface ExecutableBootProject {
    name: string;
    uri: string;
    mainClass: string;
    classpath: string[];
    gav: string;
}

class TestJarDebugConfigProvider implements DebugConfigurationProvider {

    async resolveDebugConfigurationWithSubstitutedVariables(folder: WorkspaceFolder | undefined, debugConfiguration: DebugConfiguration, _token?: CancellationToken): Promise<DebugConfiguration> {
        // TestJar launch support
        if (TEST_RUNNER_MAIN_CLASSES.includes(debugConfiguration.mainClass) && isTestJarsOnClasspath(debugConfiguration)) {
            const projects = await commands.executeCommand("sts/spring-boot/executableBootProjects") as ExecutableBootProject[];
            let env = debugConfiguration.env;
            if (!env) {
                env = {};
                debugConfiguration.env = env;
            }
            const projectsWithErrors: ExecutableBootProject[] = [];
            // Create all project classparth data files and add env vars for workspace projects
            await Promise.all(projects.filter(p => p.gav).map(async p => {
                const envName = this.createEnvVarName(p);
                if (!env[envName]) {
                    try {
                        env[envName] = await this.createFile(p);
                    } catch (_error) {
                        projectsWithErrors.push(p);
                    }
                }
            }));
            if (projectsWithErrors.length > 0) {
                const projectStr = projectsWithErrors.map(p => `'${p.name}'`);
                window.showWarningMessage(`TestJar Support: Could not provide data for workspace projects: ${projectStr}`);
            }
        }
        return debugConfiguration;
    }

    private createEnvVarName(project: ExecutableBootProject) {
        return `${ENV_TESTJAR_ARTIFACT_PREFIX}${project.gav.replace(/:/g, "_")}`;
    }

    private async createFile(project: ExecutableBootProject) {
        const filePath = path.join(tmpdir(), `${project.gav.replace(/:/g, "_")}-${randomUUID()}`);
        await fs.writeFile(filePath, `# the main class to invoke\nmain=${project.mainClass}\n# the classpath to use delimited by the OS specific delimiters\nclasspath=${project.classpath.join(path.delimiter)}`, function(err) {
            if(err) {
                throw Error();
            }
        }); 
        return filePath;
    }

}

export function startTestJarSupport(): Disposable {
    return hookListenerToBooleanPreference(
        'boot-java-vscode-only.test-jars',
         () => Disposable.from(
            debug.onDidTerminateDebugSession(cleanupDebugSession),
            debug.registerDebugConfigurationProvider('java', new TestJarDebugConfigProvider(), DebugConfigurationProviderTriggerKind.Initial),
            new Disposable(() => cleanupDebugSession(debug.activeDebugSession)) // If VSCode is shutdown then clean active debug session if it satisfies conditions
        )
    );
}

async function cleanupDebugSession(session: DebugSession) {
    // Handle termination of a Boot app with TestJars on the classpath
    if (session.type === 'java' && TEST_RUNNER_MAIN_CLASSES.includes(session.configuration.mainClass) && isTestJarsOnClasspath(session.configuration) && session.configuration.env) {
        await Promise.all(Object.keys(session.configuration.env).filter(k => k.startsWith(ENV_TESTJAR_ARTIFACT_PREFIX)).map(k => fs.rm(session.configuration.env[k], () => {})));
    }
}

function isTestJarsOnClasspath(debugConfiguration: DebugConfiguration): boolean {
    if (Array.isArray(debugConfiguration.classPaths)) {
        return !!debugConfiguration.classPaths.find(isTestJarFile);
    }
    return false;

}

function isTestJarFile(f: string): boolean {
    const fileName = path.basename(f || "");
    return /^spring-boot-testjars-\d+\.\d+\.\d+(.*)?.jar$/.test(fileName);
}



