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

import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { chmodSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '..', '..', '..');
const runner = (name) => join(root, 'claude-plugins/tools/evals', name);

function fakeHost(name, body) {
    const dir = mkdtempSync(join(tmpdir(), `spring-tools-${name}-eval-`));
    const file = join(dir, name);
    writeFileSync(file, `#!/bin/sh\n${body}\n`);
    chmodSync(file, 0o755);
    return { dir, file };
}

function runEval({ host, runnerName, caseName, env = {} }) {
    return spawnSync(process.execPath, [runner(runnerName), '--case', caseName], {
        cwd: root,
        encoding: 'utf8',
        timeout: 60000,
        env: { ...process.env, ...env },
    });
}

function outputOf(result) {
    return `${result.stdout ?? ''}\n${result.stderr ?? ''}`;
}

function assertRunnerPassed(result) {
    if (result.error) {
        throw result.error;
    }
    assert.equal(result.status, 0, outputOf(result));
    assert.match(outputOf(result), /1\/1 cases passed/);
}

const codexReviewStub = `
if [ "$1" = "--version" ]; then
  echo 'codex 1.0.0-eval'
  exit 0
fi
if [ -n "$CODEX_CONFIG_CAPTURE" ] && grep -q 'mock-mcp-server' "$CODEX_HOME/config.toml"; then
  cp "$CODEX_HOME/config.toml" "$CODEX_CONFIG_CAPTURE"
fi
out=""
prev=""
for arg in "$@"; do
  if [ "$prev" = "--output-last-message" ]; then out="$arg"; fi
  prev="$arg"
done
case "$*" in
  *"You are grading the answer"*)
    if [ -n "$out" ]; then printf 'PASS\\n' > "$out"; fi
    printf '%s\\n' '{"type":"item.completed","item":{"id":"j1","type":"agent_message","text":"PASS"}}'
    ;;
  *)
    if [ -n "$out" ]; then printf 'Remove the redundant @Autowired constructor.\\n' > "$out"; fi
    printf '%s\\n' '{"type":"item.completed","item":{"id":"i1","type":"agent_message","text":"Remove the redundant @Autowired constructor."}}'
    ;;
esac
exit 0
`;

const codexEmptyStub = `
if [ "$1" = "--version" ]; then
  echo 'codex 1.0.0-eval'
  exit 0
fi
out=""
prev=""
for arg in "$@"; do
  if [ "$prev" = "--output-last-message" ]; then out="$arg"; fi
  prev="$arg"
done
if [ -n "$out" ]; then printf 'done\\n' > "$out"; fi
printf '%s\\n' '{"type":"item.completed","item":{"id":"i1","type":"agent_message","text":"done"}}'
exit 0
`;

const codexCollabStub = `
if [ "$1" = "--version" ]; then
  echo 'codex 1.0.0-eval'
  exit 0
fi
out=""
prev=""
for arg in "$@"; do
  if [ "$prev" = "--output-last-message" ]; then out="$arg"; fi
  prev="$arg"
done
case "$*" in
  *"You are grading the answer"*)
    if [ -n "$out" ]; then printf 'PASS\\n' > "$out"; fi
    printf '%s\\n' '{"type":"item.completed","item":{"id":"j1","type":"agent_message","text":"PASS"}}'
    ;;
  *)
    if [ -n "$out" ]; then printf 'Remove @Autowired; Spring Boot 4.0.8 is available.\\n' > "$out"; fi
    printf '%s\\n' \
      '{"type":"thread.started","thread":{"id":"thr_review","agentRole":"spring-reviewer"}}' \
      '{"type":"item.completed","item":{"id":"c1","type":"collab_tool_call","tool":"spawnAgent","receiverThreadIds":["thr_review"],"prompt":"Review the project"}}' \
      '{"type":"item.completed","item":{"id":"c2","type":"collab_tool_call","tool":"wait","receiverThreadIds":["thr_review"]}}' \
      '{"type":"item.completed","item":{"id":"i1","type":"agent_message","text":"Remove @Autowired; Spring Boot 4.0.8 is available."}}'
    ;;
esac
exit 0
`;

const opencodeStub = `
if [ "$1" = "--version" ]; then
  echo 'opencode 2.0.0-eval'
  exit 0
fi
if [ -n "$OPENCODE_CONFIG" ] && grep -q 'mock-mcp-server' "$OPENCODE_CONFIG"; then
  cp "$OPENCODE_CONFIG" "$OPENCODE_CONFIG_CAPTURE"
fi
case "$*" in
  *"You are grading the answer"*)
    printf '%s\\n' '{"type":"text","part":{"text":"PASS"}}'
    ;;
  *)
    printf '%s\\n' \
      '{"type":"text","part":{"text":"Remove the redundant @Autowired constructor."}}' \
      '{"type":"tool_use","part":{"tool":"skill","state":{"input":{"id":"spring-review"}},"callID":"c1","id":"p1","sessionID":"ses_eval","messageID":"msg_eval","type":"tool"}}'
    ;;
esac
exit 0
`;

const copilotStub = `
if [ "$1" = "--version" ]; then
  echo 'copilot 1.0.0-eval'
  exit 0
fi
printf '%s\\n' "$@" >> "$COPILOT_ARGS_CAPTURE"
logs=""
prev=""
for arg in "$@"; do
  if [ "$prev" = "--log-dir" ]; then logs="$arg"; fi
  prev="$arg"
done
if [ -n "$logs" ]; then
  mkdir -p "$logs"
  printf '%s\\n' '{"name": "skill", "arguments": "{\\"skill\\":\\"spring-review\\"}"}' > "$logs/copilot.log"
fi
case "$*" in
  *"You are grading the answer"*) printf 'PASS\\n' ;;
  *) printf 'Remove the redundant @Autowired constructor.\\n' ;;
esac
exit 0
`;

const claudeStub = `
if [ "$1" = "plugin" ] && [ "$2" = "eval" ]; then
  printf '%s\\n' "$@" > "$CLAUDE_ARGS_CAPTURE"
  test -f "$3/evals/spring-review-skill/prompt.md" || exit 9
  exit 0
fi
exit 2
`;

test('Claude runner stages shared cases and forwards options to the current CLI', () => {
    const host = fakeHost('claude', claudeStub);
    try {
        const argsPath = join(host.dir, 'args.txt');
        const result = runEval({
            host,
            runnerName: 'run-with-claude.mjs',
            caseName: 'spring-review-skill',
            env: {
                CLAUDE_CLI: host.file,
                CLAUDE_ARGS_CAPTURE: argsPath,
                CLAUDE_EVAL_RESULTS_DIR: join(host.dir, 'results'),
            },
        });
        assert.equal(result.status, 0, outputOf(result));
        const args = readFileSync(argsPath, 'utf8');
        assert.match(args, /plugin\neval/);
        assert.match(args, /--case\nspring-review-skill/);
    } finally {
        rmSync(host.dir, { recursive: true, force: true });
    }
});

test('Copilot runner limits available tools and grades its debug-log trace', () => {
    const host = fakeHost('copilot', copilotStub);
    try {
        const argsPath = join(host.dir, 'args.txt');
        const result = runEval({
            host,
            runnerName: 'run-with-copilot.mjs',
            caseName: 'spring-review-skill',
            env: {
                COPILOT_CLI: host.file,
                COPILOT_ARGS_CAPTURE: argsPath,
            },
        });
        assertRunnerPassed(result);
        assert.match(readFileSync(argsPath, 'utf8'), /--available-tools=view,glob,grep,skill,spring-tools-mcp\/\*/);
    } finally {
        rmSync(host.dir, { recursive: true, force: true });
    }
});

test('Codex runner uses answer evidence instead of synthesizing skill activation', () => {
    const host = fakeHost('codex', codexReviewStub);
    try {
        const configCapture = join(host.dir, 'config.toml');
        const result = runEval({
            host,
            runnerName: 'run-with-codex.mjs',
            caseName: 'spring-review-skill',
            env: { CODEX_CLI: host.file, CODEX_CONFIG_CAPTURE: configCapture },
        });
        assertRunnerPassed(result);
        const config = readFileSync(configCapture, 'utf8');
        assert.match(config, /approval_policy = "never"/);
        assert.match(config, /default_tools_approval_mode = "approve"/);
    } finally {
        rmSync(host.dir, { recursive: true, force: true });
    }
});

test('Codex runner fails a skill-only prompt when observable answer evidence is absent', () => {
    const host = fakeHost('codex', codexEmptyStub);
    try {
        const result = runEval({
            host,
            runnerName: 'run-with-codex.mjs',
            caseName: 'spring-review-skill',
            env: { CODEX_CLI: host.file },
        });
        assert.equal(result.status, 1, outputOf(result));
        assert.match(outputOf(result), /0\/1 cases passed/);
    } finally {
        rmSync(host.dir, { recursive: true, force: true });
    }
});

test('Codex runner maps only spawnAgent to the reviewer role from thread metadata', () => {
    const host = fakeHost('codex', codexCollabStub);
    try {
        const result = runEval({
            host,
            runnerName: 'run-with-codex.mjs',
            caseName: 'reviewer-agent-reviews-project',
            env: { CODEX_CLI: host.file },
        });
        assertRunnerPassed(result);
    } finally {
        rmSync(host.dir, { recursive: true, force: true });
    }
});

test('OpenCode runner stages deny-by-default permissions and grades its JSON event stream', () => {
    const host = fakeHost('opencode', opencodeStub);
    try {
        const configCapture = join(host.dir, 'opencode-config.json');
        const result = runEval({
            host,
            runnerName: 'run-with-opencode.mjs',
            caseName: 'spring-review-skill',
            env: {
                OPENCODE_CLI: host.file,
                OPENCODE_CONFIG_CAPTURE: configCapture,
            },
        });
        assertRunnerPassed(result);
        const config = JSON.parse(readFileSync(configCapture, 'utf8'));
        assert.equal(config.mcp.servers['spring-tools'].codemode, false);
        assert.deepEqual(config.permissions[0], { action: '*', resource: '*', effect: 'deny' });
        assert.ok(config.permissions.some((rule) => rule.action === 'spring-tools_*' && rule.effect === 'allow'));
        assert.ok(config.permissions.some((rule) => rule.action === 'external_directory' && rule.resource.endsWith('/claude-plugins/spring-tools/*')));
        assert.ok(!config.permissions.some((rule) => ['shell', 'edit', 'webfetch', 'websearch'].includes(rule.action) && rule.effect === 'allow'));
    } finally {
        rmSync(host.dir, { recursive: true, force: true });
    }
});
