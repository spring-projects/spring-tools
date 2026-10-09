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
// Starts the standalone Spring Boot language server as an MCP stdio server for Claude Code,
// GitHub Copilot CLI, Codex and OpenCode.
// Environment (all optional):
//   SPRING_TOOLS_JAVA        java executable to use (else $JAVA_HOME/bin/java, else java on PATH; first with Java 21+ wins)
//   SPRING_TOOLS_JAVA_OPTS   extra JVM options, e.g. "-Xmx2g" (appended after the defaults, so they override them)
//   SPRING_TOOLS_LS_JAR      path to a local language server JAR to run instead of the downloaded one
//   SPRING_TOOLS_LS_WATCH    "false" disables the built-in file watcher; use hooks or explicit change tools to update the index
//   SPRING_TOOLS_PROJECT_DIR workspace directory to index, overriding the auto-detection below
//   SPRING_TOOLS_DATA_DIR    persistent log/runtime directory (else use client data dir or ~/.spring-tools/data)
'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawn, spawnSync } = require('child_process');
const { ensureJar, JAR_NAME, defaultInstallDir } = require('./install.js');

const MIN_JAVA_MAJOR = 21;

// Shown to the client alongside the tool names when MCP tool search defers the full tool list.
const SERVER_INSTRUCTIONS = [
    'Spring Tools language server for the Spring Boot projects in this workspace.',
    'Start with getProjectList (use the projectName field, location is the project root directory; retry while the list is still empty right after startup, the project model is being resolved).',
    'getProjectDiagnostics reports Spring-specific problems with a code such as JAVA_PUBLIC_BEAN_METHOD; every code has a fix playbook. Use the quickfix skill when available, or read the matching explanations/<CODE>.md file, then validate the project with the validate skill or getProjectDiagnostics.',
    'Bean wiring: getBeanDetails, findBeansByType, getBeanUsageInfo. Endpoints: getRequestMappings, findRequestMappingsByMethod. Build facts: getSpringBootVersion, getJavaVersion, getResolvedProjectClasspath.',
    'Architecture: getLogicalStructure, getStereotypesList, findComponentsByStereotype and the logical-structure baseline tools. Spring versions and support: getReleases, getGenerations, getUpcomingReleases, getLatestReleaseInformation.',
    'For guidance, use the quickfix or validate Spring Tools skill when available.',
    'The server watches the workspace directory, so edits, new files and new projects are picked up within a second without any notification; call fileChanged/fileDeleted only when a change might have been missed and refreshWorkspace when the index looks stale (e.g. after a large git checkout).',
].join(' ');

/**
 * Server instructions with the absolute location of the fix playbooks appended. Clients that do not
 * substitute path placeholders in skill and agent files learn where the playbooks are this way.
 */
function serverInstructions(pluginRoot = __dirname) {
    return `${SERVER_INSTRUCTIONS} The fix playbooks are the <CODE>.md files in ${path.join(pluginRoot, 'explanations')}.`;
}

/** Major Java version from `java -version` output; null when unparseable. */
function parseJavaMajor(output) {
    const match = /version "(\d+)(?:\.(\d+))?/.exec(output || '');
    if (!match) {
        return null;
    }
    const major = Number(match[1]);
    return major === 1 ? Number(match[2]) : major;
}

/** Java executables to try, most specific first. */
function javaCandidates(env = process.env, platform = process.platform) {
    const exe = platform === 'win32' ? 'java.exe' : 'java';
    const candidates = [];
    if (env.SPRING_TOOLS_JAVA) {
        candidates.push({ java: env.SPRING_TOOLS_JAVA, source: 'SPRING_TOOLS_JAVA' });
    }
    if (env.JAVA_HOME) {
        candidates.push({ java: path.join(env.JAVA_HOME, 'bin', exe), source: 'JAVA_HOME' });
    }
    candidates.push({ java: 'java', source: 'PATH' });
    return candidates;
}

function probeJava(java) {
    const result = spawnSync(java, ['-version'], { encoding: 'utf8', timeout: 20000, windowsHide: true });
    if (result.error) {
        return { output: '', error: result.error.message };
    }
    return { output: `${result.stderr || ''}${result.stdout || ''}` };
}

/** First candidate that runs and reports Java >= MIN_JAVA_MAJOR, plus what was tried. */
function pickJava(candidates, probe = probeJava) {
    const tried = [];
    for (const candidate of candidates) {
        const probed = probe(candidate.java);
        const major = probed.error ? null : parseJavaMajor(probed.output);
        tried.push({ ...candidate, major, error: probed.error });
        if (major !== null && major >= MIN_JAVA_MAJOR) {
            return { ...candidate, major, tried };
        }
    }
    return { java: null, major: null, tried };
}

function describeTried(tried) {
    return tried.map((t) => `${t.java} (${t.source}): ${t.error ? t.error : t.major === null ? 'no version reported' : `Java ${t.major}`}`).join('; ');
}

/** Splits a JVM options string on whitespace, honoring single/double quotes (also inside a token). */
function splitOpts(value) {
    const opts = [];
    let current = '';
    let quote = null;
    let inToken = false;
    for (const char of value || '') {
        if (quote) {
            if (char === quote) {
                quote = null;
            } else {
                current += char;
            }
        } else if (char === '"' || char === "'") {
            quote = char;
            inToken = true;
        } else if (/\s/.test(char)) {
            if (inToken) {
                opts.push(current);
            }
            current = '';
            inToken = false;
        } else {
            current += char;
            inToken = true;
        }
    }
    if (inToken) {
        opts.push(current);
    }
    return opts;
}

/** true unless SPRING_TOOLS_LS_WATCH is set to false/0/no/off (case-insensitive). */
function watchEnabled(value) {
    return !/^\s*(false|0|no|off)\s*$/i.test(value || '');
}

function resolveDataDir({ env = process.env, home = os.homedir() } = {}) {
    return path.resolve(env.SPRING_TOOLS_DATA_DIR || env.CLAUDE_PLUGIN_DATA || env.PLUGIN_DATA || path.join(home, '.spring-tools', 'data'));
}

function isDirectory(dir) {
    try {
        return fs.statSync(dir).isDirectory();
    } catch {
        return false;
    }
}

/** An absolute, existing directory that is not the plugin's own root (which is never a workspace). */
function usableProjectDir(dir, pluginRoot) {
    return !!dir && path.isAbsolute(dir) && path.resolve(dir) !== path.resolve(pluginRoot) && isDirectory(dir);
}

/**
 * GitHub Copilot CLI starts plugin MCP servers with the plugin root as working directory and passes
 * no workspace path, but it records the session's directory before starting them.
 */
function copilotWorkspaceDir(env, home) {
    const session = env.COPILOT_AGENT_SESSION_ID;
    // A session id is a single path segment; anything else would escape the session-state directory.
    if (!session || !/^[\w.-]+$/.test(session) || session === '.' || session === '..') {
        return null;
    }
    try {
        const state = fs.readFileSync(path.join(home, '.copilot', 'session-state', session, 'workspace.yaml'), 'utf8');
        return /^cwd:[ \t]*(.+?)[ \t]*$/m.exec(state)?.[1] ?? null;
    } catch {
        return null;
    }
}

/**
 * Workspace the language server should index. Claude Code exports CLAUDE_PROJECT_DIR; Copilot CLI
 * runs it in the plugin directory, so the workspace has to be recovered from session state. Other
 * plugin hosts may also change the MCP process cwd, so prefer the inherited PWD to that fallback.
 * @returns {{dir: string, source: string, detected: boolean}}
 */
function resolveProjectDir({ env = process.env, cwd = process.cwd(), pluginRoot = __dirname, home = os.homedir() } = {}) {
    const override = env.SPRING_TOOLS_PROJECT_DIR;
    if (override) {
        const dir = path.resolve(cwd, override);
        if (!isDirectory(dir)) {
            throw new Error(`SPRING_TOOLS_PROJECT_DIR is not an existing directory: ${dir}`);
        }
        return { dir, source: 'SPRING_TOOLS_PROJECT_DIR', detected: true };
    }
    const candidates = [
        { dir: env.CLAUDE_PROJECT_DIR, source: 'CLAUDE_PROJECT_DIR' },
        { dir: copilotWorkspaceDir(env, home), source: 'Copilot session state' },
        { dir: env.PWD, source: 'PWD' },
        { dir: cwd, source: 'working directory' },
    ];
    for (const candidate of candidates) {
        if (usableProjectDir(candidate.dir, pluginRoot)) {
            return { dir: path.resolve(candidate.dir), source: candidate.source, detected: true };
        }
    }
    return { dir: path.resolve(cwd), source: 'working directory', detected: false };
}

function buildJavaArgs({ jarPath, dataDir, projectDir, watch = true, extraOpts = [], instructions = serverInstructions() }) {
    return [
        '-Xmx1024m',
        '-Djdk.util.zip.disableZip64ExtraFieldValidation=true',
        '-Dspring.config.location=classpath:/application.properties',
        '-Dspring.profiles.active=file-logging',
        `-Dlogging.file.name=${path.join(dataDir, 'boot-ls.log')}`,
        '-Dlogging.level.root=INFO',
        '-Dspring.ai.mcp.server.stdio=true',
        `-Dspring.ai.mcp.server.instructions=${instructions}`,
        // Only the MCP tools are exposed over stdio; nothing connects to the LSP transport.
        '-Dlanguageserver.enabled=false',
        `-Dspring.boot.ls.project.dir=${projectDir}`,
        // The server follows on-disk changes under the project dir itself (java.nio WatchService).
        `-Dspring.boot.ls.project.watch=${watch ? 'true' : 'false'}`,
        ...extraOpts,
        '-jar',
        jarPath,
    ];
}

async function resolveJar(log) {
    if (process.env.SPRING_TOOLS_LS_JAR) {
        const jarPath = path.resolve(process.env.SPRING_TOOLS_LS_JAR);
        if (!fs.existsSync(jarPath)) {
            throw new Error(`SPRING_TOOLS_LS_JAR points to a missing file: ${jarPath}`);
        }
        log(`Using language server JAR from SPRING_TOOLS_LS_JAR: ${jarPath}`);
        return jarPath;
    }
    const jarDest = defaultInstallDir(__dirname);
    const jarPath = path.join(jarDest, JAR_NAME);
    if (!fs.existsSync(jarPath)) {
        log(`${JAR_NAME} not found, downloading it (first start of this plugin version). If the client reports the MCP server as failed before the download finishes, reconnect it after the download completes.`);
    }
    return (await ensureJar({ pluginRoot: __dirname, dest: jarDest, ifMissing: true, log })).jarPath;
}

async function start() {
    const log = (line) => console.error(`[spring-tools] ${line}`);
    // The plugin root changes on every plugin update, so runtime state such as the log file goes to
    // the persistent data dir both clients export (PLUGIN_DATA is the Agent Plugins spelling).
    const project = resolveProjectDir();
    const dataDir = resolveDataDir();
    if (project.detected) {
        log(`Indexing ${project.dir} (from ${project.source})`);
    } else {
        throw new Error(`Could not determine the workspace directory; refusing to index the plugin directory ${project.dir}. Set SPRING_TOOLS_PROJECT_DIR in the MCP server environment to the workspace root. For Codex, configure the server env explicitly; exporting a variable in the parent shell may not forward it.`);
    }

    const picked = pickJava(javaCandidates());
    if (!picked.java) {
        throw new Error(`No Java ${MIN_JAVA_MAJOR}+ runtime found. Tried: ${describeTried(picked.tried)}. Install a JDK 21 or newer and put it on PATH, or point JAVA_HOME or SPRING_TOOLS_JAVA at it.`);
    }
    log(`Using Java ${picked.major} from ${picked.java} (${picked.source})`);

    const jarPath = await resolveJar(log);
    fs.mkdirSync(dataDir, { recursive: true });
    const watch = watchEnabled(process.env.SPRING_TOOLS_LS_WATCH);
    if (!watch) {
        log('File watcher disabled by SPRING_TOOLS_LS_WATCH; the index only follows the plugin hooks and explicit fileChanged/refreshWorkspace calls.');
    }
    const javaArgs = buildJavaArgs({ jarPath, dataDir, projectDir: project.dir, watch, extraOpts: splitOpts(process.env.SPRING_TOOLS_JAVA_OPTS) });
    const child = spawn(picked.java, javaArgs, { stdio: 'inherit', windowsHide: true });

    // The client terminates this launcher when the session ends; forward that to the JVM so it
    // does not linger as an orphan. The server shuts down gracefully within about a second.
    let shutdownSignal = null;
    let forceKillTimer;
    for (const signal of ['SIGINT', 'SIGTERM', 'SIGQUIT']) {
        process.once(signal, () => {
            shutdownSignal = signal;
            if (!child.killed) {
                child.kill('SIGTERM');
            }
            // Give the language server time to flush logs and release workspace resources.
            forceKillTimer = setTimeout(() => child.kill('SIGKILL'), 5000);
            forceKillTimer.unref();
        });
    }

    child.on('error', (error) => {
        log(`Failed to start the Java process: ${error.message}`);
        process.exit(1);
    });
    child.on('close', (code, signal) => {
        clearTimeout(forceKillTimer);
        if (shutdownSignal) {
            process.exit(0);
        } else if (code !== 0) {
            log(`Language server exited with ${signal ? `signal ${signal}` : `code ${code}`}; see ${path.join(dataDir, 'boot-ls.log')}`);
        }
        process.exit(code ?? 1);
    });
}

if (require.main === module) {
    start().catch((error) => {
        console.error(`[spring-tools] ${error.message}`);
        process.exit(1);
    });
}

module.exports = { MIN_JAVA_MAJOR, SERVER_INSTRUCTIONS, serverInstructions, parseJavaMajor, javaCandidates, pickJava, splitOpts, watchEnabled, buildJavaArgs, resolveProjectDir, resolveDataDir };
