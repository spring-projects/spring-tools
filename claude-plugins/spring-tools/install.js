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
// Downloads the Spring Boot language server JAR matching the plugin version and verifies its
// SHA-256. Used in-process by launcher.js and as a CLI by the SessionStart hook:
//   node install.js [--if-missing] [--dest <dir>]
// Only stderr is used for logging so the launcher's stdout stays a clean MCP channel.
'use strict';

const fs = require('fs');
const path = require('path');
const http = require('http');
const https = require('https');
const net = require('net');
const tls = require('tls');
const crypto = require('crypto');
const os = require('os');
const { spawnSync } = require('child_process');
const { Transform } = require('stream');
const { pipeline } = require('stream/promises');

const JAR_NAME = 'spring-boot-language-server-standalone-exec.jar';
const DEFAULT_DOWNLOAD_BASE = 'https://cdn.spring.io/spring-tools';
const MAX_REDIRECTS = 5;
const SOCKET_IDLE_TIMEOUT_MS = 60000;
const LOCK_STALE_MS = 30 * 60 * 1000;
const LOCK_WAIT_MAX_MS = 20 * 60 * 1000;
const LOCK_POLL_MS = 500;
const LOOPBACK_HOSTS = new Set(['localhost', '127.0.0.1', '::1', '[::1]']);

// The Agent Plugins manifest at the plugin root is the one both clients can read; the Claude Code
// manifest in .claude-plugin/ is the fallback for older layouts.
function pluginVersion(pluginRoot) {
    const manifestPath = [path.join(pluginRoot, 'plugin.json'), path.join(pluginRoot, '.claude-plugin', 'plugin.json')]
        .find((candidate) => fs.existsSync(candidate));
    if (!manifestPath) {
        throw new Error(`no plugin.json found in ${pluginRoot}`);
    }
    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
    if (typeof manifest.version !== 'string' || !/^[0-9A-Za-z][0-9A-Za-z.+-]*$/.test(manifest.version)) {
        throw new Error('plugin.json has no valid version');
    }
    return manifest.version;
}

function defaultInstallDir(pluginRoot = __dirname, env = process.env, home = os.homedir()) {
    const dataDir = env.SPRING_TOOLS_DATA_DIR || env.CLAUDE_PLUGIN_DATA || env.PLUGIN_DATA || path.join(home, '.spring-tools', 'data');
    // Keep artifacts from different plugin releases isolated; an old JAR must never satisfy
    // --if-missing after the plugin version changes.
    return path.resolve(dataDir, 'language-server', pluginVersion(pluginRoot));
}

/** Release versions live under release/<version>/, anything with a qualifier under snapshot/. */
function downloadUrls(version, base = process.env.SPRING_TOOLS_LS_DOWNLOAD_BASE || DEFAULT_DOWNLOAD_BASE) {
    const root = base.replace(/\/+$/, '');
    const jarUrl = version.includes('-')
        ? `${root}/snapshot/language-server/spring-boot/${JAR_NAME}`
        : `${root}/release/language-server/spring-boot/${version}/${JAR_NAME}`;
    return { jarUrl, sha256Url: `${jarUrl}.sha256` };
}

/** Hosts a download may come from: the CDN, plus a configured mirror. Plain HTTP only for loopback (tests). */
function assertAllowedUrl(url, base = process.env.SPRING_TOOLS_LS_DOWNLOAD_BASE || DEFAULT_DOWNLOAD_BASE) {
    let parsed;
    try {
        parsed = new URL(url);
    } catch {
        throw new Error(`Invalid URL: ${url}`);
    }
    const allowedHosts = new Set([new URL(DEFAULT_DOWNLOAD_BASE).hostname, new URL(base).hostname]);
    if (!allowedHosts.has(parsed.hostname)) {
        throw new Error(`Refusing to fetch from untrusted host: ${url}`);
    }
    if (parsed.protocol !== 'https:' && !(parsed.protocol === 'http:' && LOOPBACK_HOSTS.has(parsed.hostname))) {
        throw new Error(`Refusing insecure download URL: ${url}`);
    }
    return parsed;
}

function hostMatchesNoProxy(hostname, noProxy) {
    if (!noProxy) {
        return false;
    }
    return noProxy.split(',').map((e) => e.trim().toLowerCase()).filter(Boolean).some((entry) => {
        if (entry === '*') {
            return true;
        }
        const pattern = entry.replace(/^\./, '').replace(/:\d+$/, '');
        return hostname === pattern || hostname.endsWith(`.${pattern}`);
    });
}

/** Proxy URL for a target, following curl's HTTPS_PROXY/HTTP_PROXY/ALL_PROXY and NO_PROXY conventions. */
function proxyFor(targetUrl, env = process.env) {
    const target = new URL(targetUrl);
    const hostname = target.hostname.toLowerCase();
    if (LOOPBACK_HOSTS.has(hostname) || hostMatchesNoProxy(hostname, env.NO_PROXY || env.no_proxy)) {
        return null;
    }
    const candidates = target.protocol === 'https:'
        ? [env.HTTPS_PROXY, env.https_proxy, env.ALL_PROXY, env.all_proxy]
        : [env.HTTP_PROXY, env.http_proxy, env.ALL_PROXY, env.all_proxy];
    const proxy = candidates.find((value) => value && value.trim());
    if (!proxy) {
        return null;
    }
    const parsed = new URL(/^[a-z][a-z0-9+.-]*:\/\//i.test(proxy) ? proxy : `http://${proxy}`);
    if (parsed.protocol !== 'http:') {
        throw new Error(`Unsupported proxy protocol ${parsed.protocol} (only http:// proxies are supported)`);
    }
    return parsed;
}

function proxyAuthHeader(proxy) {
    if (!proxy.username && !proxy.password) {
        return null;
    }
    const credentials = `${decodeURIComponent(proxy.username)}:${decodeURIComponent(proxy.password)}`;
    return `Basic ${Buffer.from(credentials).toString('base64')}`;
}

/** Opens a CONNECT tunnel through an HTTP proxy and wraps it in TLS for the target host. */
function connectThroughProxy(proxy, target) {
    return new Promise((resolve, reject) => {
        const port = target.port || 443;
        const socket = net.connect({ host: proxy.hostname, port: Number(proxy.port) || 80 });
        socket.setTimeout(SOCKET_IDLE_TIMEOUT_MS, () => {
            socket.destroy(new Error(`Timed out connecting to proxy ${proxy.host}`));
        });
        socket.once('error', reject);
        socket.once('connect', () => {
            const auth = proxyAuthHeader(proxy);
            socket.write(`CONNECT ${target.hostname}:${port} HTTP/1.1\r\nHost: ${target.hostname}:${port}\r\n`
                + (auth ? `Proxy-Authorization: ${auth}\r\n` : '') + 'Connection: keep-alive\r\n\r\n');
        });
        let response = '';
        const onData = (chunk) => {
            response += chunk.toString('latin1');
            const end = response.indexOf('\r\n\r\n');
            if (end === -1) {
                return;
            }
            socket.removeListener('data', onData);
            const statusLine = response.slice(0, response.indexOf('\r\n'));
            if (!/^HTTP\/1\.[01] 200\b/.test(statusLine)) {
                socket.destroy();
                reject(new Error(`Proxy ${proxy.host} refused CONNECT to ${target.hostname}:${port}: ${statusLine}`));
                return;
            }
            const leftover = response.slice(end + 4);
            if (leftover.length) {
                socket.destroy();
                reject(new Error(`Proxy ${proxy.host} sent unexpected data after the CONNECT response`));
                return;
            }
            socket.pause();
            const secure = tls.connect({ socket, servername: target.hostname });
            secure.once('secureConnect', () => resolve(secure));
            secure.once('error', reject);
        };
        socket.on('data', onData);
    });
}

function urlToOptions(target) {
    return {
        protocol: target.protocol,
        hostname: target.hostname,
        port: target.port || undefined,
        path: `${target.pathname}${target.search}`,
        method: 'GET',
    };
}

function createTunnelAgent(socket) {
    const agent = new https.Agent({ keepAlive: false });
    agent.createConnection = () => socket;
    return agent;
}

/** GET with redirects, host allow-list and proxy support; resolves with a 200 response stream. */
async function get(url, redirectCount = 0) {
    const target = assertAllowedUrl(url);
    const proxy = proxyFor(url);
    const headers = { 'user-agent': 'spring-tools-claude-plugin' };
    let request;
    if (proxy && target.protocol === 'https:') {
        const socket = await connectThroughProxy(proxy, target);
        request = https.request({ ...urlToOptions(target), headers, agent: createTunnelAgent(socket) });
    } else if (proxy) {
        const auth = proxyAuthHeader(proxy);
        request = http.request({
            host: proxy.hostname, port: Number(proxy.port) || 80, method: 'GET', path: target.href,
            headers: { ...headers, host: target.host, ...(auth ? { 'proxy-authorization': auth } : {}) },
        });
    } else {
        request = (target.protocol === 'https:' ? https : http).request({ ...urlToOptions(target), headers });
    }
    const response = await new Promise((resolve, reject) => {
        request.setTimeout(SOCKET_IDLE_TIMEOUT_MS, () => request.destroy(new Error(`Timed out fetching ${url}`)));
        request.once('response', resolve);
        request.once('error', reject);
        request.end();
    });
    if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location) {
        response.resume();
        if (redirectCount >= MAX_REDIRECTS) {
            throw new Error(`Too many redirects while fetching ${url}`);
        }
        return get(new URL(response.headers.location, url).toString(), redirectCount + 1);
    }
    if (response.statusCode !== 200) {
        response.resume();
        throw new Error(`Failed to fetch ${url}: HTTP ${response.statusCode}`);
    }
    return response;
}

async function fetchText(url) {
    const response = await get(url);
    let data = '';
    for await (const chunk of response) {
        data += chunk;
        if (data.length > 4096) {
            throw new Error(`Unexpectedly large checksum file at ${url}`);
        }
    }
    return data.trim();
}

/** Streams the URL into dest while hashing; resolves with the hex SHA-256 of what was written. */
async function downloadFile(url, dest, log) {
    const response = await get(url);
    const total = Number(response.headers['content-length']) || 0;
    const hash = crypto.createHash('sha256');
    let received = 0;
    let nextReport = 0;
    const meter = new Transform({
        transform(chunk, encoding, callback) {
            hash.update(chunk);
            received += chunk.length;
            if (total && received >= nextReport) {
                log(`  ${Math.floor((received / total) * 100)}% (${Math.round(received / 1048576)} of ${Math.round(total / 1048576)} MB)`);
                nextReport += Math.max(total / 10, 1);
            }
            callback(null, chunk);
        },
    });
    await pipeline(response, meter, fs.createWriteStream(dest, { flags: 'wx' }));
    if (total && received !== total) {
        throw new Error(`Download of ${url} ended after ${received} of ${total} bytes`);
    }
    return hash.digest('hex');
}

function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

/** Creates <jar>.lock exclusively; waits while another installer holds it, breaks stale locks. */
async function acquireLock(lockPath) {
    const started = Date.now();
    for (;;) {
        try {
            fs.writeFileSync(lockPath, `${process.pid} ${new Date().toISOString()}\n`, { flag: 'wx' });
            return () => { try { fs.unlinkSync(lockPath); } catch { /* already gone */ } };
        } catch (error) {
            if (error.code !== 'EEXIST') {
                throw error;
            }
        }
        try {
            const lock = fs.statSync(lockPath);
            const ownerPid = Number(fs.readFileSync(lockPath, 'utf8').trim().split(/\s+/, 1)[0]);
            const hasOwnerPid = Number.isSafeInteger(ownerPid) && ownerPid > 0;
            let ownerAlive = false;
            if (hasOwnerPid) {
                try {
                    process.kill(ownerPid, 0);
                    ownerAlive = true;
                } catch (ownerError) {
                    if (ownerError.code === 'EPERM') {
                        ownerAlive = true;
                    } else if (ownerError.code !== 'ESRCH') {
                        throw ownerError;
                    }
                }
            }
            if ((hasOwnerPid && !ownerAlive) || Date.now() - lock.mtimeMs > LOCK_STALE_MS) {
                fs.unlinkSync(lockPath);
                continue;
            }
        } catch (lockError) {
            if (lockError.code !== 'ENOENT') {
                throw lockError;
            }
        }
        if (Date.now() - started > LOCK_WAIT_MAX_MS) {
            throw new Error(`Gave up waiting for another installer holding ${lockPath}`);
        }
        await sleep(LOCK_POLL_MS);
    }
}

function removeStalePartials(dir) {
    for (const entry of fs.readdirSync(dir)) {
        if (entry.startsWith(JAR_NAME) && entry.endsWith('.part')) {
            try { fs.unlinkSync(path.join(dir, entry)); } catch { /* best effort */ }
        }
    }
}

function findSourceRoot(pluginRoot, env = process.env) {
    const candidates = [env.SPRING_TOOLS_SOURCE_DIR, path.resolve(pluginRoot, '..', '..')].filter(Boolean);
    return candidates.map((candidate) => path.resolve(candidate)).find((root) =>
        fs.existsSync(path.join(root, 'headless-services', 'pom.xml'))
        && (fs.existsSync(path.join(root, 'headless-services', 'mvnw')) || fs.existsSync(path.join(root, 'headless-services', 'mvnw.cmd'))));
}

function buildJar(pluginRoot, dest, log) {
    const sourceRoot = findSourceRoot(pluginRoot);
    if (!sourceRoot) {
        throw new Error('No Spring Tools source checkout found for a local build. Set SPRING_TOOLS_SOURCE_DIR to the repository root, or install the JAR manually and set SPRING_TOOLS_LS_JAR.');
    }
    const servicesDir = path.join(sourceRoot, 'headless-services');
    const wrapper = path.join(servicesDir, process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw');
    const args = ['-pl', 'spring-boot-language-server-standalone', '-am', '-DskipTests', 'package'];
    log(`Building Spring Boot Language Server from ${sourceRoot}`);
    // Maven must not read MCP requests or write build output to the MCP stdout transport.
    const result = spawnSync(wrapper, args, { cwd: servicesDir, stdio: ['ignore', 2, 2], shell: process.platform === 'win32' });
    if (result.error || result.status !== 0) {
        throw new Error(`Local language server build failed${result.error ? `: ${result.error.message}` : ` (exit code ${result.status})`}`);
    }
    const targetDir = path.join(sourceRoot, 'headless-services', 'spring-boot-language-server-standalone', 'target');
    const builtJar = fs.readdirSync(targetDir).filter((name) => name.endsWith('-standalone-exec.jar')).sort().at(-1);
    if (!builtJar) throw new Error(`Maven build completed but no standalone JAR was found in ${targetDir}`);
    const temporary = path.join(dest, `${JAR_NAME}.${process.pid}.part`);
    try {
        fs.copyFileSync(path.join(targetDir, builtJar), temporary, fs.constants.COPYFILE_EXCL);
        fs.renameSync(temporary, path.join(dest, JAR_NAME));
    } finally {
        try { fs.unlinkSync(temporary); } catch { /* already renamed or absent */ }
    }
    log(`Built Spring Boot Language Server and installed it to ${path.join(dest, JAR_NAME)}`);
}

/**
 * Makes sure <dest>/<JAR_NAME> exists for the given plugin version.
 * @returns {Promise<{status: 'present'|'downloaded', jarPath: string}>}
 */
async function ensureJar({ pluginRoot = __dirname, dest = defaultInstallDir(pluginRoot), ifMissing = false, log = (line) => console.error(line) } = {}) {
    const jarPath = path.join(dest, JAR_NAME);
    if (ifMissing && fs.existsSync(jarPath)) {
        return { status: 'present', jarPath };
    }
    const version = pluginVersion(pluginRoot);
    fs.mkdirSync(dest, { recursive: true });
    const release = await acquireLock(`${jarPath}.lock`);
    try {
        if (ifMissing && fs.existsSync(jarPath)) {
            return { status: 'present', jarPath };
        }
        removeStalePartials(dest);
        try {
            const { jarUrl, sha256Url } = downloadUrls(version);
            log(`Downloading Spring Boot Language Server ${version} from ${jarUrl}`);
            const expected = (await fetchText(sha256Url)).split(/\s+/)[0].toLowerCase();
            if (!/^[0-9a-f]{64}$/.test(expected)) {
                throw new Error(`Checksum file ${sha256Url} does not contain a SHA-256 digest`);
            }
            const partPath = `${jarPath}.${process.pid}.part`;
            try {
                const actual = await downloadFile(jarUrl, partPath, log);
                if (actual !== expected) throw new Error(`Checksum mismatch for ${JAR_NAME}: expected ${expected}, got ${actual}`);
                fs.renameSync(partPath, jarPath);
            } finally {
                try { fs.unlinkSync(partPath); } catch { /* renamed away or never created */ }
            }
            log(`Installed Spring Boot Language Server ${version} to ${jarPath}`);
            return { status: 'downloaded', jarPath };
        } catch (downloadError) {
            log(`Language server download failed: ${downloadError.message}`);
            try {
                buildJar(pluginRoot, dest, log);
                return { status: 'built', jarPath };
            } catch (buildError) {
                throw new Error(`Could not download or build the Spring Tools language server. Download error: ${downloadError.message}. Build error: ${buildError.message}`);
            }
        }
    } finally {
        release();
    }
}

function parseArgs(argv) {
    const options = { ifMissing: false, dest: undefined, help: false };
    for (let i = 0; i < argv.length; i++) {
        const arg = argv[i];
        if (arg === '--if-missing') options.ifMissing = true;
        else if (arg === '--dest') {
            if (!argv[i + 1] || argv[i + 1].startsWith('--')) {
                throw new Error('--dest requires a directory path');
            }
            options.dest = path.resolve(argv[++i]);
        }
        else if (arg === '--help' || arg === '-h') options.help = true;
        else throw new Error(`unknown argument: ${arg}`);
    }
    return options;
}

async function main() {
    const options = parseArgs(process.argv.slice(2));
    if (options.help) {
        console.error('usage: node install.js [--if-missing] [--dest <dir>]');
        return 0;
    }
    if (process.env.SPRING_TOOLS_LS_JAR) {
        console.error(`SPRING_TOOLS_LS_JAR is set (${process.env.SPRING_TOOLS_LS_JAR}); nothing to download.`);
        return 0;
    }
    const result = await ensureJar({ ifMissing: options.ifMissing, dest: options.dest });
    if (result.status === 'downloaded') {
        // stdout of a SessionStart hook is added to Claude's context.
        console.log(`Spring Tools downloaded its language server to ${result.jarPath}. If the Spring Tools MCP server is listed as failed, reconnect it or restart the agent client.`);
    }
    return 0;
}

if (require.main === module) {
    main().then((code) => process.exit(code), (error) => {
        console.error(`Spring Tools language server installation failed: ${error.message}`);
        process.exit(1);
    });
}

module.exports = { JAR_NAME, DEFAULT_DOWNLOAD_BASE, downloadUrls, assertAllowedUrl, proxyFor, hostMatchesNoProxy, ensureJar, pluginVersion, defaultInstallDir, parseArgs, findSourceRoot, createTunnelAgent };
