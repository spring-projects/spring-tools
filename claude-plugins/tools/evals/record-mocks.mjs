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

// Records the MCP mocks for the plugin's behavioral eval suite (claude plugin eval) from the real
// language server: starts launcher.js over MCP stdio against a copy of the sf7-validation test
// project, calls every tool the eval cases may use and writes the answers as
// spring-tools/evals/mocks/spring-tools-mcp/<tool>.md plus the saved tools/list as _tools.json.
// Workspace paths are rewritten to /workspace so the recordings are stable across machines.
// Needs the language server JAR in the plugin (claude-plugins/update-local-jars.sh) or
// SPRING_TOOLS_LS_JAR, a Java 21+ runtime and network access (spring.io, Maven Central).
//
//   node claude-plugins/tools/evals/record-mocks.mjs [--project-src <dir>]

import { spawn } from 'node:child_process';
import { cpSync, existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir, homedir } from 'node:os';
import { basename, dirname, join, resolve } from 'node:path';
import { createInterface } from 'node:readline';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..', '..');
const pluginDir = join(repoRoot, 'claude-plugins', 'spring-tools');
const { defaultInstallDir } = createRequire(import.meta.url)(join(pluginDir, 'install.js'));
const mocksDir = join(pluginDir, 'evals', 'mocks', 'spring-tools-mcp');
// Case-level override: the fixture only yields version diagnostics by itself, so the validate case
// gets a recording taken after a redundant @Autowired constructor was added to the application class.
const validateCaseMocksDir = join(pluginDir, 'evals', 'validate-finds-spring-problems', 'mocks', 'spring-tools-mcp');
const JAR_NAME = 'spring-boot-language-server-standalone-exec.jar';

const args = process.argv.slice(2);
const option = (name) => (args.includes(name) ? args[args.indexOf(name) + 1] : undefined);
const projectSrc = resolve(option('--project-src') ?? join(repoRoot, 'headless-services/spring-boot-language-server/src/test/resources/test-projects/sf7-validation'));

// Prefer a fresh test build; otherwise read the same versioned cache as launcher.js.
const localBuildJar = join(pluginDir, 'language-server', JAR_NAME);
const jarPath = process.env.SPRING_TOOLS_LS_JAR || (existsSync(localBuildJar) ? localBuildJar : join(defaultInstallDir(pluginDir), JAR_NAME));
if (!existsSync(jarPath)) {
    console.error(`language server JAR not found at ${jarPath}; build the local test JAR with claude-plugins/update-local-jars.sh or set SPRING_TOOLS_LS_JAR`);
    process.exit(2);
}

const stagingRoot = mkdtempSync(join(pluginDir, 'evals', '.record-mocks-'));
const stagedMocksDir = join(stagingRoot, 'suite');
const stagedValidateMocksDir = join(stagingRoot, 'validate');
mkdirSync(stagedMocksDir, { recursive: true });
mkdirSync(stagedValidateMocksDir, { recursive: true });

const work = mkdtempSync(join(tmpdir(), 'spring-tools-eval-record-'));
const workspace = join(work, 'workspace');
const dataDir = join(work, 'data');
const projectName = basename(projectSrc);
mkdirSync(dataDir, { recursive: true });
cpSync(projectSrc, join(workspace, projectName), { recursive: true, filter: (src) => !/[\\/](target|build|\.git)$/.test(src) });

const child = spawn(process.execPath, [join(pluginDir, 'launcher.js')], {
    env: { ...process.env, CLAUDE_PROJECT_DIR: workspace, CLAUDE_PLUGIN_DATA: dataDir, CLAUDE_PLUGIN_ROOT: pluginDir },
    stdio: ['pipe', 'pipe', 'inherit'],
});
const childExited = new Promise((resolvePromise) => child.once('exit', resolvePromise));
const pending = new Map();
let nextId = 1;
createInterface({ input: child.stdout }).on('line', (line) => {
    let message;
    try { message = JSON.parse(line); } catch { return; }
    if (message.id !== undefined && pending.has(message.id)) {
        pending.get(message.id)(message);
        pending.delete(message.id);
    }
});
const request = (method, params, timeoutMs = 120000) => new Promise((resolvePromise, reject) => {
    const id = nextId++;
    const timer = setTimeout(() => reject(new Error(`timeout waiting for ${method}`)), timeoutMs);
    pending.set(id, (message) => { clearTimeout(timer); resolvePromise(message); });
    child.stdin.write(`${JSON.stringify({ jsonrpc: '2.0', id, method, params })}\n`);
});
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const call = async (name, toolArgs = {}) => {
    const response = await request('tools/call', { name, arguments: toolArgs });
    if (response.error) throw new Error(`${name}: ${JSON.stringify(response.error)}`);
    return (response.result?.content ?? []).map((c) => c.text ?? JSON.stringify(c)).join('\n');
};
async function pollUntil(fn, predicate, attempts, intervalMs) {
    let value;
    for (let i = 0; i < attempts; i++) {
        value = await fn();
        if (predicate(value)) return value;
        await sleep(intervalMs);
    }
    return value;
}

const stable = (text) => text
    .replaceAll(`file://${workspace}`, 'file:///workspace')
    .replaceAll(workspace, '/workspace')
    .replaceAll(pluginDir, '/plugin')
    .replaceAll(`${dataDir}/language-server`, '/plugin/language-server')
    .replaceAll(homedir(), '~');
const pretty = (text) => {
    try { return JSON.stringify(JSON.parse(text), null, 2); } catch { return text; }
};
const yaml = (value) => (Array.isArray(value) ? `[${value.join(', ')}]` : value);
function writeMock(tool, body, expect = {}, dir = stagedMocksDir) {
    const front = Object.keys(expect).length
        ? `---\nexpect:\n${Object.entries(expect).map(([k, v]) => `  ${k}: ${yaml(v)}`).join('\n')}\n---\n`
        : '';
    writeFileSync(join(dir, `${tool}.md`), `${front}${pretty(stable(body)).trim()}\n`);
    console.log(`recorded ${tool} (${body.length} chars)${dir === mocksDir ? '' : ` -> ${dir}`}`);
}
const exact = { projectName };

try {
    await request('initialize', { protocolVersion: '2024-11-05', capabilities: {}, clientInfo: { name: 'record-mocks', version: '0' } });
    child.stdin.write(`${JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized', params: {} })}\n`);

    const tools = await request('tools/list', {});
    writeFileSync(join(stagedMocksDir, '_tools.json'), `${JSON.stringify({ tools: tools.result.tools }, null, 2)}\n`);

    const projectList = await pollUntil(() => call('getProjectList'), (t) => t.includes(`"${projectName}"`), 120, 3000);
    if (!projectList.includes(`"${projectName}"`)) throw new Error(`project ${projectName} was not discovered`);
    writeMock('getProjectList', projectList);

    const diagnostics = await pollUntil(() => call('getProjectDiagnostics', exact), (t) => /"code"/.test(t), 40, 3000);
    writeMock('getProjectDiagnostics', diagnostics, exact);
    writeMock('getSpringBootVersion', await call('getSpringBootVersion', exact), exact);
    writeMock('getJavaVersion', await call('getJavaVersion', exact), exact);
    writeMock('getResolvedProjectClasspath', await call('getResolvedProjectClasspath', exact), exact);

    const mappings = await pollUntil(() => call('getRequestMappings', exact), (t) => t.includes('/greeting'), 20, 2000);
    writeMock('getRequestMappings', mappings, exact);
    writeMock('findRequestMappingsByMethod', await call('findRequestMappingsByMethod', { ...exact, httpMethod: 'GET' }), { ...exact, httpMethod: 'string' });

    const beans = await call('getBeanDetails', exact);
    writeMock('getBeanDetails', beans, exact);
    const beanName = beans.match(/"name"\s*:\s*"([^"]+)"/)?.[1];
    const beanType = beans.match(/"type"\s*:\s*"([^"]+)"/)?.[1];
    if (!beanName || !beanType) throw new Error('no bean in getBeanDetails output');
    writeMock('getBeanUsageInfo', await call('getBeanUsageInfo', { ...exact, beanName }), { ...exact, beanName: 'string' });
    writeMock('findBeansByType', await call('findBeansByType', { ...exact, typeName: beanType }), { ...exact, typeName: 'string' });

    const stereotypes = await call('getStereotypesList', exact);
    writeMock('getStereotypesList', stereotypes, exact);
    writeMock('getListOfComponentsAndTheirStereotypes', await call('getListOfComponentsAndTheirStereotypes', exact), exact);
    const stereotypeName = stereotypes.match(/"name"\s*:\s*"([^"]+)"/)?.[1] ?? 'Controller';
    writeMock('findComponentsByStereotype', await call('findComponentsByStereotype', { ...exact, stereotypeName }), { ...exact, stereotypeName: 'string' });
    writeMock('getLogicalStructure', await call('getLogicalStructure', exact), exact);
    writeMock('getStructureDependencies', await call('getStructureDependencies', exact), exact);
    writeMock('captureLogicalStructureBaseline', await call('captureLogicalStructureBaseline', exact), exact);
    writeMock('getLogicalStructureChanges', await call('getLogicalStructureChanges', exact), exact);
    writeMock('getLogicalStructureBaselineHistory', await call('getLogicalStructureBaselineHistory', exact), exact);
    writeMock('clearLogicalStructureBaseline', await call('clearLogicalStructureBaseline', exact), exact);

    writeMock('getLatestReleaseInformation', await call('getLatestReleaseInformation', { projectName: 'spring-boot' }), { projectName: ['spring-boot', 'spring-framework', 'spring-security', 'spring-data-jpa', 'spring-cloud'] });
    writeMock('getLatestBootVersionsFromMavenRepo', await call('getLatestBootVersionsFromMavenRepo', exact), exact);
    writeMock('getReleases', await call('getReleases', { project: 'spring-boot' }), { project: 'string' });
    writeMock('getGenerations', await call('getGenerations', { project: 'spring-boot' }), { project: 'string' });
    writeMock('getUpcomingReleases', await call('getUpcomingReleases'));

    writeMock('refreshWorkspace', (await call('refreshWorkspace')) || 'OK');
    writeMock('fileChanged', (await call('fileChanged', { filePath: `${workspace}/${projectName}/pom.xml` })) || 'OK', { filePath: 'string' });
    writeMock('fileDeleted', (await call('fileDeleted', { filePath: `${workspace}/${projectName}/Missing.java` })) || 'OK', { filePath: 'string' });

    const application = join(workspace, projectName, 'src/main/java/com/example/demo/Sf7ValidationApplication.java');
    const original = readFileSync(application, 'utf8');
    writeFileSync(application, original.replace(/class (\w+) \{/, 'class $1 {\n\t@org.springframework.beans.factory.annotation.Autowired\n\tpublic $1() {}\n'));
    await call('fileChanged', { filePath: application });
    const withJavaDiagnostic = await pollUntil(() => call('getProjectDiagnostics', exact), (t) => t.includes('JAVA_AUTOWIRED_CONSTRUCTOR'), 30, 1000);
    if (!withJavaDiagnostic.includes('JAVA_AUTOWIRED_CONSTRUCTOR')) throw new Error('JAVA_AUTOWIRED_CONSTRUCTOR did not appear after the edit');
    writeMock('getProjectDiagnostics', withJavaDiagnostic, exact, stagedValidateMocksDir);
    writeFileSync(application, original);

    const replaceDirectory = (staged, target) => {
        mkdirSync(dirname(target), { recursive: true });
        const backup = `${target}.backup-${process.pid}`;
        rmSync(backup, { recursive: true, force: true });
        if (existsSync(target)) renameSync(target, backup);
        try {
            renameSync(staged, target);
            rmSync(backup, { recursive: true, force: true });
        } catch (error) {
            if (existsSync(backup) && !existsSync(target)) renameSync(backup, target);
            throw error;
        }
    };
    replaceDirectory(stagedMocksDir, mocksDir);
    replaceDirectory(stagedValidateMocksDir, validateCaseMocksDir);

    console.log(`${readdirSync(mocksDir).length} files written to ${mocksDir}`);
} finally {
    child.kill('SIGTERM');
    const exited = await new Promise((resolvePromise) => {
        const timer = setTimeout(() => resolvePromise(false), 10000);
        childExited.then(() => {
            clearTimeout(timer);
            resolvePromise(true);
        });
    });
    if (!exited) {
        child.kill('SIGKILL');
        await childExited;
    }
    rmSync(work, { recursive: true, force: true });
    rmSync(stagingRoot, { recursive: true, force: true });
}
