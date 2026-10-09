#!/usr/bin/env node
/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/

// End-to-end smoke test for the Claude Code plugin: drives launcher.js over MCP stdio exactly
// the way Claude Code does, against a workspace that holds a copy of a Spring Boot test project.
// Needs the language server JAR in the plugin (claude-plugins/update-local-jars.sh) or
// SPRING_TOOLS_LS_JAR, and a Java 21+ runtime.
//
//   node claude-plugins/tools/smoke/mcp-smoke.mjs [--project-src <dir>] [--keep]
//
// Prints one PASS/FAIL line per check and ends with "SMOKE PASSED" only when every check passed.

import { execFileSync, spawn } from 'node:child_process';
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, realpathSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, dirname, join, resolve } from 'node:path';
import { createInterface } from 'node:readline';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..', '..');
const pluginDir = join(repoRoot, 'claude-plugins', 'spring-tools');
const JAR_NAME = 'spring-boot-language-server-standalone-exec.jar';
const { defaultInstallDir } = createRequire(import.meta.url)(join(pluginDir, 'install.js'));

const args = process.argv.slice(2);
const option = (name) => (args.includes(name) ? args[args.indexOf(name) + 1] : undefined);
const projectSrc = resolve(option('--project-src') ?? join(repoRoot, 'headless-services/spring-boot-language-server/src/test/resources/test-projects/sf7-validation'));
const keep = args.includes('--keep');

// Prefer the freshly built test artifact so CI always exercises the source it just compiled;
// otherwise reuse the normal versioned persistent install cache used by launcher.js.
const localBuildJar = join(pluginDir, 'language-server', JAR_NAME);
const jarPath = process.env.SPRING_TOOLS_LS_JAR || (existsSync(localBuildJar) ? localBuildJar : join(defaultInstallDir(pluginDir), JAR_NAME));
if (!existsSync(jarPath)) {
    console.error(`language server JAR not found at ${jarPath}; build the local test JAR with claude-plugins/update-local-jars.sh or set SPRING_TOOLS_LS_JAR`);
    process.exit(2);
}
if (!existsSync(join(projectSrc, 'pom.xml')) && !existsSync(join(projectSrc, 'build.gradle'))) {
    console.error(`--project-src ${projectSrc} is not a Maven/Gradle project`);
    process.exit(2);
}

// Workspace layout mirrors "a folder with several services": the test project is one subdirectory,
// and a second project appears later to exercise discovery after startup.
const work = mkdtempSync(join(tmpdir(), 'spring-tools-smoke-'));
const workspace = join(work, 'workspace');
const dataDir = join(work, 'data');
const projectDir = join(workspace, basename(projectSrc));
mkdirSync(dataDir, { recursive: true });
cpSync(projectSrc, projectDir, { recursive: true, filter: (src) => !/[\\/](target|build|\.git)$/.test(src) });

const results = [];
const check = (cond, label) => {
    results.push(Boolean(cond));
    console.log(`${cond ? 'PASS' : 'FAIL'} ${label}`);
};
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const childPids = (parentPid) => {
    try {
        return execFileSync('pgrep', ['-P', String(parentPid)], { encoding: 'utf8' })
            .trim()
            .split(/\s+/)
            .filter(Boolean)
            .map(Number);
    } catch (error) {
        if (error.status === 1) {
            return [];
        }
        throw error;
    }
};
const isProcessAlive = (pid) => {
    try {
        process.kill(pid, 0);
        return true;
    } catch (error) {
        if (error.code === 'ESRCH') {
            return false;
        }
        throw error;
    }
};

const child = spawn(process.execPath, [join(pluginDir, 'launcher.js')], {
    env: { ...process.env, CLAUDE_PROJECT_DIR: workspace, CLAUDE_PLUGIN_DATA: dataDir, CLAUDE_PLUGIN_ROOT: pluginDir },
    stdio: ['pipe', 'pipe', 'pipe'],
});
let stderr = '';
child.stderr.on('data', (d) => { stderr += d; });

const pending = new Map();
let nextId = 1;
const strayStdout = [];
createInterface({ input: child.stdout }).on('line', (line) => {
    if (!line.trim()) return;
    let message;
    try { message = JSON.parse(line); } catch { strayStdout.push(line); return; }
    if (message.id !== undefined && pending.has(message.id)) {
        pending.get(message.id)(message);
        pending.delete(message.id);
    }
});

function request(method, params, timeoutMs = 120000) {
    const id = nextId++;
    return new Promise((resolvePromise, reject) => {
        const timer = setTimeout(() => reject(new Error(`timeout waiting for ${method} (id ${id})`)), timeoutMs);
        pending.set(id, (message) => { clearTimeout(timer); resolvePromise(message); });
        child.stdin.write(`${JSON.stringify({ jsonrpc: '2.0', id, method, params })}\n`);
    });
}
const notify = (method, params) => child.stdin.write(`${JSON.stringify({ jsonrpc: '2.0', method, params })}\n`);
const call = async (name, toolArgs = {}) => {
    const response = await request('tools/call', { name, arguments: toolArgs });
    return { response, text: (response.result?.content ?? []).map((c) => c.text ?? JSON.stringify(c)).join('\n') };
};
const projectNames = (text) => [...text.replace(/\\"/g, '"').matchAll(/"projectName"\s*:\s*"([^"]+)"/g)].map((m) => m[1]);
const codesOf = (text) => [...new Set([...text.matchAll(/"code"\s*:\s*"([A-Za-z_0-9]+)"/g)].map((m) => m[1]))];
function javaFilesUnder(directory) {
    if (!existsSync(directory)) return [];
    return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
        const path = join(directory, entry.name);
        return entry.isDirectory() ? javaFilesUnder(path) : (entry.isFile() && entry.name.endsWith('.java') ? [path] : []);
    });
}

async function pollUntil(fn, predicate, attempts, intervalMs) {
    let value;
    for (let i = 0; i < attempts; i++) {
        value = await fn();
        if (predicate(value)) return value;
        await sleep(intervalMs);
    }
    return value;
}

const started = Date.now();
try {
    const init = await request('initialize', { protocolVersion: '2024-11-05', capabilities: {}, clientInfo: { name: 'smoke', version: '0' } });
    check(init.result?.serverInfo?.name, `initialize answered by ${JSON.stringify(init.result?.serverInfo)} after ${Date.now() - started} ms`);
    check(typeof init.result?.instructions === 'string' && init.result.instructions.includes('getProjectList'), 'server instructions (used by Claude Code tool search) are advertised');
    notify('notifications/initialized', {});

    const tools = await request('tools/list', {});
    const names = (tools.result?.tools ?? []).map((t) => t.name).sort();
    console.log(`tools (${names.length}): ${names.join(', ')}`);
    for (const name of ['getProjectList', 'getProjectDiagnostics', 'getSpringBootVersion', 'fileChanged', 'fileDeleted', 'refreshWorkspace']) {
        check(names.includes(name), `tool '${name}' advertised`);
    }
    check(names.length >= 26, `the full tool inventory is advertised (${names.length} tools)`);
    const undocumented = (tools.result?.tools ?? []).filter((t) => !t.description || t.description.trim().length < 20).map((t) => t.name);
    check(undocumented.length === 0, `every tool carries a description${undocumented.length ? ` (missing: ${undocumented.join(', ')})` : ''}`);
    const fileChanged = tools.result.tools.find((t) => t.name === 'fileChanged');
    check(fileChanged && Object.keys(fileChanged.inputSchema?.properties ?? {}).join() === 'filePath', "fileChanged takes exactly {filePath} (what hooks.json sends)");

    const prompts = await request('prompts/list', {});
    const promptNames = (prompts.result?.prompts ?? []).map((p) => p.name);
    check(promptNames.length > 0, `MCP prompts advertised (${promptNames.join(', ') || 'none'})`);

    const discovery = Date.now();
    const list = await pollUntil(() => call('getProjectList'), (r) => projectNames(r.text).length > 0, 120, 3000);
    const projectName = projectNames(list.text)[0];
    console.log(`getProjectList after ${Date.now() - discovery} ms: ${list.text.slice(0, 200)}`);
    check(projectName === basename(projectSrc), `project '${projectName}' discovered under CLAUDE_PROJECT_DIR`);
    const location = list.text.replace(/\\"/g, '"').match(/"location"\s*:\s*"([^"]+)"/)?.[1];
    check(location && realpathSync(location) === realpathSync(projectDir), `getProjectList reports the project location (${location})`);

    const bootVersion = await call('getSpringBootVersion', { projectName });
    check(/"major"\s*:\s*\d+/.test(bootVersion.text), `getSpringBootVersion -> ${bootVersion.text.slice(0, 80)}`);
    const javaVersion = await call('getJavaVersion', { projectName });
    check(/\d/.test(javaVersion.text) && !javaVersion.response.result?.isError, `getJavaVersion -> ${javaVersion.text.slice(0, 40)}`);
    const classpath = await call('getResolvedProjectClasspath', { projectName });
    check(/spring-boot-[\w.-]*\.jar/.test(classpath.text), 'getResolvedProjectClasspath lists the Spring Boot JARs');

    const baseline = await pollUntil(() => call('getProjectDiagnostics', { projectName }), (r) => codesOf(r.text).length > 0, 40, 3000);
    console.log(`baseline diagnostic codes: ${codesOf(baseline.text).join(', ') || '(none)'}`);
    check(codesOf(baseline.text).length > 0, 'getProjectDiagnostics reports Spring diagnostics for the sample project');

    // The analysis tools behind the beans/endpoints/architecture skills, against the indexed fixture.
    const mappings = await pollUntil(() => call('getRequestMappings', { projectName }), (r) => r.text.includes('/greeting'), 20, 2000);
    check(mappings.text.includes('/greeting'), 'getRequestMappings lists the fixture endpoint /greeting');
    const gets = await call('findRequestMappingsByMethod', { projectName, httpMethod: 'GET' });
    check(gets.text.includes('/greeting'), 'findRequestMappingsByMethod GET finds the same endpoint');
    const stereotypes = await call('getListOfComponentsAndTheirStereotypes', { projectName });
    check(/Controller/i.test(stereotypes.text), 'getListOfComponentsAndTheirStereotypes classifies the controller');
    const structure = await call('getLogicalStructure', { projectName });
    check(!structure.response.error && !structure.response.result?.isError && structure.text.length > 20, `getLogicalStructure returns a tree (${structure.text.length} chars)`);
    const beans = await call('getBeanDetails', { projectName });
    check(/TestController|Config/.test(beans.text), 'getBeanDetails lists the fixture beans');

    // Same input shape as the PostToolUse Edit|Write hook: edit a Java file, then fileChanged {filePath}.
    const javaFiles = javaFilesUnder(join(projectDir, 'src/main/java'));
    const target = javaFiles.find((f) => /class \w+ \{/.test(readFileSync(f, 'utf8')));
    const original = readFileSync(target, 'utf8');
    writeFileSync(target, original.replace(/class (\w+) \{/, 'class $1 {\n\t@org.springframework.beans.factory.annotation.Autowired\n\tpublic $1() {}\n'));
    const changed = await call('fileChanged', { filePath: target });
    check(!changed.response.error && !changed.response.result?.isError, `fileChanged accepted the hook payload (${changed.text.slice(0, 60)})`);
    const edited = Date.now();
    const afterEdit = await pollUntil(() => call('getProjectDiagnostics', { projectName }), (r) => r.text.includes('JAVA_AUTOWIRED_CONSTRUCTOR'), 20, 2000);
    check(afterEdit.text.includes('JAVA_AUTOWIRED_CONSTRUCTOR'), `JAVA_AUTOWIRED_CONSTRUCTOR reported ${Date.now() - edited} ms after fileChanged on ${basename(target)}`);
    writeFileSync(target, original);

    const refreshed = await call('refreshWorkspace');
    check(!refreshed.response.error && !refreshed.response.result?.isError, `refreshWorkspace accepted {} (${refreshed.text.slice(0, 60)})`);
    const reverted = await pollUntil(() => call('getProjectDiagnostics', { projectName }), (r) => codesOf(r.text).join() === codesOf(baseline.text).join(), 20, 2000);
    check(codesOf(reverted.text).join() === codesOf(baseline.text).join(), 'diagnostics back to baseline after reverting the file and refreshWorkspace');

    // The server watches the workspace itself: an edit made without any hook or tool call (an
    // external editor, a code generator) must reach the index on its own.
    const watched = Date.now();
    writeFileSync(target, original.replace(/class (\w+) \{/, 'class $1 {\n\t@org.springframework.beans.factory.annotation.Autowired\n\tpublic $1() {}\n'));
    const afterWatch = await pollUntil(() => call('getProjectDiagnostics', { projectName }), (r) => r.text.includes('JAVA_AUTOWIRED_CONSTRUCTOR'), 20, 1000);
    check(afterWatch.text.includes('JAVA_AUTOWIRED_CONSTRUCTOR'), `file watcher: external edit indexed ${Date.now() - watched} ms after writing ${basename(target)} without fileChanged`);
    writeFileSync(target, original);
    const afterRevert = await pollUntil(() => call('getProjectDiagnostics', { projectName }), (r) => codesOf(r.text).join() === codesOf(baseline.text).join(), 20, 1000);
    check(codesOf(afterRevert.text).join() === codesOf(baseline.text).join(), 'file watcher: reverting the file on disk restores the baseline without any notification');
    const external = join(dirname(target), 'WatchedExtra.java');
    writeFileSync(external, `package ${original.match(/package ([\w.]+);/)[1]};\n\n@org.springframework.web.bind.annotation.RestController\npublic class WatchedExtra {\n\t@org.springframework.web.bind.annotation.GetMapping("/watched")\n\tpublic String watched() { return "ok"; }\n}\n`);
    const created = Date.now();
    const withExtra = await pollUntil(() => call('getRequestMappings', { projectName }), (r) => r.text.includes('/watched'), 20, 1000);
    check(withExtra.text.includes('/watched'), `file watcher: new controller indexed ${Date.now() - created} ms after creation`);
    rmSync(external);
    const withoutExtra = await pollUntil(() => call('getRequestMappings', { projectName }), (r) => !r.text.includes('/watched'), 20, 1000);
    check(!withoutExtra.text.includes('/watched'), 'file watcher: deleting the file removes its endpoint without fileDeleted');

    // A project created after startup (create-spring-boot-project skill, git checkout) must be
    // discovered by the watcher alone; refreshWorkspace stays available as the manual fallback.
    const generated = join(workspace, 'generated-app');
    mkdirSync(join(generated, 'src/main/java/com/example'), { recursive: true });
    writeFileSync(join(generated, 'pom.xml'), `<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>generated-app</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <properties><maven.compiler.release>21</maven.compiler.release></properties>
</project>
`);
    writeFileSync(join(generated, 'src/main/java/com/example/GeneratedApp.java'), 'package com.example;\n\npublic class GeneratedApp {\n}\n');
    const rediscovered = Date.now();
    const after = await pollUntil(() => call('getProjectList'), (r) => projectNames(r.text).includes('generated-app'), 30, 1000);
    check(projectNames(after.text).includes('generated-app'), `file watcher: project created after startup discovered ${Date.now() - rediscovered} ms later without refreshWorkspace (${projectNames(after.text).join(', ')})`);
    check(projectNames(after.text).includes(projectName), 'the original project is still listed after the discovery');
    const refreshedAgain = await call('refreshWorkspace');
    const afterRefresh = await pollUntil(() => call('getProjectList'), (r) => projectNames(r.text).includes('generated-app') && projectNames(r.text).includes(projectName), 20, 1000);
    check(!refreshedAgain.response.error && projectNames(afterRefresh.text).length === 2, 'refreshWorkspace keeps both projects (manual fallback still works)');

    check(existsSync(join(dataDir, 'boot-ls.log')), `log written to CLAUDE_PLUGIN_DATA (${join(dataDir, 'boot-ls.log')})`);
    check(strayStdout.length === 0, `stdout carried only JSON-RPC (${strayStdout.length} stray line(s))`);
    if (strayStdout.length) console.log(`stray stdout: ${strayStdout.slice(0, 5).join(' | ')}`);
    check(/\[spring-tools\] Using Java (\d+) from/.test(stderr), 'launcher reported the selected Java runtime on stderr');
    const serverLog = readFileSync(join(dataDir, 'boot-ls.log'), 'utf8');
    check(/file watcher started for .* directories, \d+ ms quiet period/.test(serverLog), 'server log confirms the file watcher started for the workspace');
} catch (error) {
    check(false, error.message);
} finally {
    const killed = Date.now();
    const serverPids = process.platform === 'win32' ? [] : childPids(child.pid);
    child.kill('SIGTERM');
    if (process.platform !== 'win32') {
        // The JVM must not outlive the launcher; graceful shutdown takes about a second.
        let alive = serverPids.filter(isProcessAlive);
        for (let i = 0; i < 30; i++) {
            alive = alive.filter(isProcessAlive);
            if (alive.length === 0) break;
            await sleep(1000);
        }
        check(alive.length === 0, `no orphaned JVM after SIGTERM to the launcher (${Date.now() - killed} ms${alive.length ? `, survivors: ${alive.join(', ')}` : ''})`);
        for (const pid of alive) {
            process.kill(pid, 'SIGKILL');
        }
    }
    const failed = results.filter((r) => !r).length;
    if (failed) {
        console.log(`--- launcher stderr (tail) ---\n${stderr.slice(-3000)}`);
        console.log(`SMOKE FAILED: ${failed} of ${results.length} checks failed (workspace kept at ${work})`);
        process.exitCode = 1;
    } else {
        console.log(`SMOKE PASSED: ${results.length} checks in ${Math.round((Date.now() - started) / 1000)} s`);
        if (!keep) rmSync(work, { recursive: true, force: true });
    }
}
