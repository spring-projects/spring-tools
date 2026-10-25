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

// Exercises the plugin's JAR installer against a local HTTP server standing in for the CDN.

import assert from 'node:assert/strict';
import { execFile, spawnSync } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { createRequire } from 'node:module';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { after, afterEach, before, beforeEach, test } from 'node:test';

const require = createRequire(import.meta.url);
const here = dirname(fileURLToPath(import.meta.url));
const pluginRoot = join(here, '..', '..', 'spring-tools');
const installer = require(join(pluginRoot, 'install.js'));
const { JAR_NAME, ensureJar, downloadUrls, assertAllowedUrl, proxyFor, hostMatchesNoProxy, pluginVersion, defaultInstallDir, parseArgs, createTunnelAgent } = installer;

const version = pluginVersion(pluginRoot);
// Download-failure tests must not resolve the build fallback against this file's own checkout
// (findSourceRoot would find it and kick off a real, multi-minute Maven build); give them a
// pluginRoot whose parent directories hold no source checkout.
const isolatedRoot = mkdtempSync(join(tmpdir(), 'spring-tools-isolated-'));
const isolatedPluginRoot = join(isolatedRoot, 'claude-plugins', 'spring-tools');
mkdirSync(isolatedPluginRoot, { recursive: true });
writeFileSync(join(isolatedPluginRoot, 'plugin.json'), JSON.stringify({ version }));
const jarBytes = randomBytes(3 * 1024 * 1024 + 123);
const jarDigest = createHash('sha256').update(jarBytes).digest('hex');
const state = { mode: 'ok', requests: [] };
let server;
let base;
let loopbackUnavailable = false;
const quiet = () => {};

test('installer defaults to persistent client data and rejects a missing --dest value', () => {
    assert.equal(defaultInstallDir(pluginRoot, { PLUGIN_DATA: '/client/data' }, '/home/test'), join('/client/data', 'language-server', version));
    assert.equal(defaultInstallDir(pluginRoot, {}, '/home/test'), join('/home/test/.spring-tools/data', 'language-server', version));
    assert.throws(() => parseArgs(['--dest']), /requires a directory path/);
    assert.throws(() => parseArgs(['--dest', '--if-missing']), /requires a directory path/);
    assert.equal(parseArgs(['--dest', 'cache']).dest, join(process.cwd(), 'cache'));
});

test('plugin versions used in download paths cannot escape the plugin data directory', () => {
    const root = mkdtempSync(join(tmpdir(), 'spring-tools-version-'));
    try {
        mkdirSync(join(root, '.claude-plugin'));
        writeFileSync(join(root, '.claude-plugin', 'plugin.json'), JSON.stringify({ version: '../../outside' }));
        assert.throws(() => pluginVersion(root), /valid version/);
    } finally {
        rmSync(root, { recursive: true, force: true });
    }
});

before(async () => {
    server = createServer((req, res) => {
        state.requests.push(req.url);
        let { pathname } = new URL(req.url, 'http://127.0.0.1');
        if (state.mode === 'missing') {
            res.writeHead(404);
            res.end('not found');
            return;
        }
        const jarPath = `/spring-tools/release/language-server/spring-boot/${version}/${JAR_NAME}`;
        if (state.mode === 'redirect' && pathname.startsWith(jarPath)) {
            res.writeHead(302, { location: pathname.replace(jarPath, `/spring-tools/moved/${JAR_NAME}`) });
            res.end();
            return;
        }
        pathname = pathname.replace(`/spring-tools/moved/${JAR_NAME}`, jarPath);
        if (pathname === `${jarPath}.sha256`) {
            const digest = state.mode === 'bad-sum' ? 'f'.repeat(64) : jarDigest;
            res.writeHead(200, { 'content-type': 'text/plain' });
            res.end(state.mode === 'sha256sum-format' ? `${digest}  ${JAR_NAME}\n` : `${digest}\n`);
        } else if (pathname === jarPath) {
            res.writeHead(200, { 'content-type': 'application/java-archive', 'content-length': jarBytes.length });
            res.end(jarBytes);
        } else {
            res.writeHead(404);
            res.end('not found');
        }
    });
    await new Promise((resolve, reject) => {
        server.once('error', reject);
        server.listen(0, '127.0.0.1', resolve);
    }).catch((error) => {
        if (error.code !== 'EPERM') throw error;
        loopbackUnavailable = true;
    });
    if (loopbackUnavailable) return;
    base = `http://127.0.0.1:${server.address().port}/spring-tools`;
    process.env.SPRING_TOOLS_LS_DOWNLOAD_BASE = base;
    // This test file runs inside a real Spring Tools checkout, which the build fallback would
    // otherwise find and use to run a real Maven build; point it at a directory with no checkout
    // so download-failure tests see a clean failure instead of a multi-minute reactor build.
    process.env.SPRING_TOOLS_SOURCE_DIR = join(tmpdir(), 'spring-tools-no-source-checkout');
});

after(() => {
    if (server?.listening) server.close();
    delete process.env.SPRING_TOOLS_LS_DOWNLOAD_BASE;
    delete process.env.SPRING_TOOLS_SOURCE_DIR;
});

let dest;
beforeEach(() => {
    state.mode = 'ok';
    state.requests = [];
    dest = mkdtempSync(join(tmpdir(), 'spring-tools-install-'));
});

afterEach(() => {
    if (dest) {
        rmSync(dest, { recursive: true, force: true });
        dest = undefined;
    }
});

const leftovers = () => readdirSync(dest).filter((n) => n !== JAR_NAME);
const skipWithoutLoopback = (t) => {
    if (!loopbackUnavailable) return false;
    t.skip('sandbox denies loopback server binding');
    return true;
};

test('downloads the JAR for the plugin version, verifies it and installs it atomically', async (t) => {
    if (skipWithoutLoopback(t)) return;
    const result = await ensureJar({ pluginRoot, dest, log: quiet });
    assert.equal(result.status, 'downloaded');
    assert.equal(result.jarPath, join(dest, JAR_NAME));
    assert.ok(readFileSync(result.jarPath).equals(jarBytes), 'installed bytes must match the download');
    assert.deepEqual(leftovers(), [], 'no .part or .lock files may remain');
    assert.deepEqual(state.requests.map((u) => u.endsWith('.sha256')), [true, false], 'checksum first, then the JAR');
});

test('--if-missing is a no-op when the JAR is already there', async () => {
    writeFileSync(join(dest, JAR_NAME), 'existing');
    const result = await ensureJar({ pluginRoot, dest, ifMissing: true, log: quiet });
    assert.equal(result.status, 'present');
    assert.equal(readFileSync(join(dest, JAR_NAME), 'utf8'), 'existing');
    assert.deepEqual(state.requests, [], 'must not contact the server');
});

test('negative control: a checksum mismatch leaves no JAR and no partial file behind', async (t) => {
    if (skipWithoutLoopback(t)) return;
    state.mode = 'bad-sum';
    await assert.rejects(ensureJar({ pluginRoot: isolatedPluginRoot, dest, log: quiet }), /Checksum mismatch/);
    assert.ok(!existsSync(join(dest, JAR_NAME)), 'a JAR that failed verification must not be installed');
    assert.deepEqual(leftovers(), []);
});

test('accepts the sha256sum "digest  filename" checksum format', async (t) => {
    if (skipWithoutLoopback(t)) return;
    state.mode = 'sha256sum-format';
    await ensureJar({ pluginRoot: isolatedPluginRoot, dest, log: quiet });
    assert.ok(readFileSync(join(dest, JAR_NAME)).equals(jarBytes));
});

test('follows redirects that stay on an allowed host', async (t) => {
    if (skipWithoutLoopback(t)) return;
    state.mode = 'redirect';
    await ensureJar({ pluginRoot, dest, log: quiet });
    assert.ok(readFileSync(join(dest, JAR_NAME)).equals(jarBytes));
    assert.ok(state.requests.some((u) => u.includes('/moved/')), 'the moved locations must have been fetched');
});

test('concurrent installers serialize on the lock and the second one reuses the first download', async (t) => {
    if (skipWithoutLoopback(t)) return;
    const [a, b] = await Promise.all([
        ensureJar({ pluginRoot, dest, ifMissing: true, log: quiet }),
        ensureJar({ pluginRoot, dest, ifMissing: true, log: quiet }),
    ]);
    assert.deepEqual([a.status, b.status].sort(), ['downloaded', 'present']);
    assert.equal(state.requests.filter((u) => !u.endsWith('.sha256')).length, 1, 'the JAR must be downloaded once');
    assert.deepEqual(leftovers(), []);
});

test('stale partial downloads from an interrupted run are removed', async (t) => {
    if (skipWithoutLoopback(t)) return;
    writeFileSync(join(dest, `${JAR_NAME}.4242.part`), 'truncated');
    await ensureJar({ pluginRoot, dest, log: quiet });
    assert.deepEqual(leftovers(), []);
});

test('falls back to building the standalone JAR from an available source checkout', async (t) => {
    if (skipWithoutLoopback(t)) return;
    state.mode = 'missing';
    const root = mkdtempSync(join(tmpdir(), 'spring-tools-source-'));
    const localPlugin = join(root, 'claude-plugins', 'spring-tools');
    const target = join(root, 'headless-services', 'spring-boot-language-server-standalone', 'target');
    mkdirSync(localPlugin, { recursive: true });
    mkdirSync(target, { recursive: true });
    writeFileSync(join(localPlugin, 'plugin.json'), JSON.stringify({ version }));
    writeFileSync(join(root, 'headless-services', 'pom.xml'), '<project/>');
    writeFileSync(join(root, 'headless-services', 'mvnw'), `#!/bin/sh\nprintf "Maven build output\\n"\nmkdir -p spring-boot-language-server-standalone/target\nprintf built > spring-boot-language-server-standalone/target/test-standalone-exec.jar\n`);
    chmodSync(join(root, 'headless-services', 'mvnw'), 0o755);
    try {
        const script = `require(${JSON.stringify(join(pluginRoot, 'install.js'))}).ensureJar({
            pluginRoot: ${JSON.stringify(localPlugin)}, dest: ${JSON.stringify(dest)}
        }).then(result => process.stdout.write(JSON.stringify(result))).catch(error => {
            console.error(error); process.exitCode = 1;
        });`;
        const output = await new Promise((resolve, reject) => {
            execFile(process.execPath, ['-e', script], {
                env: { ...process.env, SPRING_TOOLS_LS_DOWNLOAD_BASE: base, SPRING_TOOLS_SOURCE_DIR: root },
            }, (error, stdout, stderr) => error ? reject(error) : resolve({ stdout, stderr }));
        });
        const result = JSON.parse(output.stdout);
        assert.equal(result.status, 'built');
        assert.match(output.stderr, /Maven build output/);
        assert.equal(readFileSync(result.jarPath, 'utf8'), 'built');
        assert.deepEqual(leftovers(), []);
    } finally {
        rmSync(root, { recursive: true, force: true });
    }
});

test('the CLI reports a download on stdout for the SessionStart hook and stays silent when nothing was needed', async (t) => {
    if (skipWithoutLoopback(t)) return;
    const probe = spawnSync(process.execPath, ['-e', ''], { encoding: 'utf8' });
    if (probe.error?.code === 'EPERM') {
        t.skip('sandbox denies child process creation');
        return;
    }
    const env = { ...process.env, SPRING_TOOLS_LS_DOWNLOAD_BASE: base };
    delete env.SPRING_TOOLS_LS_JAR;
    // Must stay asynchronous: the mock server lives in this process and has to answer the child.
    const run = (args) => new Promise((resolve) => {
        execFile(process.execPath, [join(pluginRoot, 'install.js'), ...args], { env, encoding: 'utf8' }, (error, stdout, stderr) => {
            resolve({ status: error ? error.code : 0, stdout, stderr });
        });
    });
    const first = await run(['--if-missing', '--dest', dest]);
    assert.equal(first.status, 0, first.stderr);
    assert.match(first.stdout, /downloaded its language server/);
    assert.match(first.stderr, /Installed Spring Boot Language Server/);
    const second = await run(['--if-missing', '--dest', dest]);
    assert.equal(second.status, 0);
    assert.equal(second.stdout, '');
    const unknown = await run(['--bogus']);
    assert.equal(unknown.status, 1);
    assert.match(unknown.stderr, /unknown argument/);
});

test('download URLs follow the CDN layout for releases and snapshots', () => {
    assert.equal(downloadUrls('2.5.0', 'https://cdn.spring.io/spring-tools').jarUrl,
        `https://cdn.spring.io/spring-tools/release/language-server/spring-boot/2.5.0/${JAR_NAME}`);
    assert.equal(downloadUrls('2.5.0-SNAPSHOT', 'https://cdn.spring.io/spring-tools/').jarUrl,
        `https://cdn.spring.io/spring-tools/snapshot/language-server/spring-boot/${JAR_NAME}`);
    assert.equal(downloadUrls('2.5.0').sha256Url, `${downloadUrls('2.5.0').jarUrl}.sha256`);
});

test('negative control: downloads are refused from untrusted hosts and over plain HTTP to non-loopback hosts', () => {
    assert.throws(() => assertAllowedUrl('https://evil.example.com/x.jar', 'https://cdn.spring.io/spring-tools'), /untrusted host/);
    assert.throws(() => assertAllowedUrl('http://cdn.spring.io/x.jar', 'https://cdn.spring.io/spring-tools'), /insecure/);
    assert.throws(() => assertAllowedUrl('http://mirror.example.com/x.jar', 'http://mirror.example.com/spring-tools'), /insecure/);
    assert.equal(assertAllowedUrl('https://cdn.spring.io/spring-tools/x.jar', 'https://mirror.example.com/spring-tools').hostname, 'cdn.spring.io');
    assert.equal(assertAllowedUrl('https://mirror.example.com/spring-tools/x.jar', 'https://mirror.example.com/spring-tools').hostname, 'mirror.example.com');
    assert.equal(assertAllowedUrl('http://127.0.0.1:9/x.jar', 'http://127.0.0.1:9/spring-tools').hostname, '127.0.0.1');
});

test('proxy selection follows HTTPS_PROXY, ALL_PROXY and NO_PROXY like curl', () => {
    const target = 'https://cdn.spring.io/spring-tools/x.jar';
    assert.equal(proxyFor(target, {}), null);
    assert.equal(proxyFor(target, { HTTPS_PROXY: 'http://proxy.example.com:3128' }).href, 'http://proxy.example.com:3128/');
    assert.equal(proxyFor(target, { https_proxy: 'proxy.example.com:3128' }).port, '3128', 'a bare host:port is an http proxy');
    assert.equal(proxyFor(target, { ALL_PROXY: 'http://user:pw@proxy.example.com:8080' }).username, 'user');
    assert.equal(proxyFor(target, { HTTP_PROXY: 'http://proxy.example.com:3128' }), null, 'HTTP_PROXY does not apply to https targets');
    assert.equal(proxyFor(target, { HTTPS_PROXY: 'http://proxy.example.com:3128', NO_PROXY: 'localhost,.spring.io' }), null);
    assert.equal(proxyFor(target, { HTTPS_PROXY: 'http://proxy.example.com:3128', no_proxy: '*' }), null);
    assert.equal(proxyFor('http://127.0.0.1:8080/x', { HTTP_PROXY: 'http://proxy.example.com:3128' }), null, 'loopback never goes through a proxy');
    assert.throws(() => proxyFor(target, { HTTPS_PROXY: 'socks5://proxy.example.com:1080' }), /only http:\/\/ proxies/);
    assert.equal(hostMatchesNoProxy('cdn.spring.io', 'example.com, spring.io:443'), true);
    assert.equal(hostMatchesNoProxy('cdn.spring.io', 'notspring.io'), false);
});

test('HTTPS requests reuse the established CONNECT tunnel', () => {
    const socket = {};
    const agent = createTunnelAgent(socket);
    assert.strictEqual(agent.createConnection({}), socket);
    agent.destroy();
});

test('recovers immediately when the installer that owns the lock has exited', async (t) => {
    if (skipWithoutLoopback(t)) return;
    const exited = spawnSync(process.execPath, ['-e', 'process.stdout.write(String(process.pid))'], { encoding: 'utf8' });
    assert.equal(exited.status, 0, exited.stderr);
    const deadPid = Number(exited.stdout);
    assert.ok(Number.isSafeInteger(deadPid) && deadPid > 0);

    const lockPath = join(dest, `${JAR_NAME}.lock`);
    writeFileSync(lockPath, `${deadPid} ${new Date().toISOString()}\n`);
    const result = await ensureJar({ pluginRoot, dest, log: quiet });

    assert.equal(result.status, 'downloaded');
    assert.ok(readFileSync(result.jarPath).equals(jarBytes));
    assert.deepEqual(leftovers(), []);
});

after(() => {
    rmSync(isolatedRoot, { recursive: true, force: true });
});
