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

import { spawnSync } from 'node:child_process';
import { chmodSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { delimiter, dirname, join, resolve } from 'node:path';
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

const codexStub = `
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

const opencodeStub = `
if [ "$1" = "--version" ]; then
  echo 'opencode 1.0.0-eval'
  exit 0
fi
printf '%s\\n' '{"type":"text","part":{"text":"done"}}' '{"type":"tool_use","part":{"tool":"skill","state":{"input":{"id":"spring-review"}},"callID":"c1","id":"p1","sessionID":"ses_eval","messageID":"msg_eval","type":"tool"}}'
exit 0
`;

test('Codex runner grades a skill case from its JSONL event stream', () => {
    const host = fakeHost('codex', codexStub);
    try {
        const result = spawnSync(process.execPath, [runner('run-with-codex.mjs'), '--case', 'spring-review-skill'], {
            cwd: root,
            encoding: 'utf8',
            timeout: 60000,
            env: { ...process.env, CODEX_CLI: host.file },
        });
        assertRunnerPassed(result);
    } finally {
        rmSync(host.dir, { recursive: true, force: true });
    }
});

test('OpenCode runner grades a skill case from its JSON event stream', () => {
    const host = fakeHost('opencode', opencodeStub);
    try {
        const result = spawnSync(process.execPath, [runner('run-with-opencode.mjs'), '--case', 'spring-review-skill'], {
            cwd: root,
            encoding: 'utf8',
            timeout: 60000,
            env: { ...process.env, PATH: `${host.dir}${delimiter}${process.env.PATH ?? ''}` },
        });
        assertRunnerPassed(result);
    } finally {
        rmSync(host.dir, { recursive: true, force: true });
    }
});

function assertRunnerPassed(result) {
    const output = `${result.stdout ?? ''}\n${result.stderr ?? ''}`;
    if (result.error) {
        throw result.error;
    }
    if (result.status !== 0 || !output.includes('1/1 cases passed')) {
        throw new Error(`eval runner failed with status ${result.status}:\n${output}`);
    }
}
