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
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

import {
    EVAL_HOSTS,
    checkEvalRunnerCoverage,
    checkEvalRunnerDocs,
    checkEvalRunnerWorkflow,
} from '../check-eval-runners.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '..', '..', '..');
const readme = readFileSync(join(root, 'agent-plugins/spring-tools/README.md'), 'utf8');
const workflow = readFileSync(join(root, '.github/workflows/agent-plugin-check.yml'), 'utf8');

function stagingRoot() {
    const dir = mkdtempSync(join(tmpdir(), 'spring-tools-eval-check-'));
    const evalsDir = join(dir, 'agent-plugins/tools/evals');
    const libDir = join(dir, 'agent-plugins/tools/lib');
    mkdirSync(join(evalsDir, 'one-case/graders'), { recursive: true });
    mkdirSync(libDir, { recursive: true });
    writeFileSync(join(evalsDir, 'one-case/prompt.md'), 'Use the skill.\n');
    writeFileSync(join(evalsDir, 'mock-mcp-server.mjs'), '');
    cpSync(join(root, 'agent-plugins/tools/lib/eval-grading.mjs'), join(libDir, 'eval-grading.mjs'));
    cpSync(join(root, 'agent-plugins/tools/lib/eval-cases.mjs'), join(libDir, 'eval-cases.mjs'));
    for (const host of EVAL_HOSTS) {
        cpSync(join(root, 'agent-plugins/tools/evals', host.runner), join(evalsDir, host.runner));
    }
    return dir;
}

test('all four hosts have runner, documentation and guarded workflow coverage', () => {
    assert.deepEqual(checkEvalRunnerCoverage(root), []);
    assert.deepEqual(checkEvalRunnerDocs(readme), []);
    assert.deepEqual(checkEvalRunnerWorkflow(workflow), []);
});

test('coverage check catches a removed host runner', () => {
    const dir = stagingRoot();
    try {
        const codex = join(dir, 'agent-plugins/tools/evals/run-with-codex.mjs');
        assert.ok(existsSync(codex));
        rmSync(codex);
        const errors = checkEvalRunnerCoverage(dir);
        assert.ok(errors.some((error) => error.includes('Codex: eval runner')), errors.join('\n'));
    } finally {
        rmSync(dir, { recursive: true, force: true });
    }
});

test('coverage check catches host normalization drift', () => {
    const dir = stagingRoot();
    try {
        const grading = join(dir, 'agent-plugins/tools/lib/eval-grading.mjs');
        const text = readFileSync(grading, 'utf8').replace(/^\s*codex:\s*.*$/m, '');
        writeFileSync(grading, text);
        const errors = checkEvalRunnerCoverage(dir);
        assert.ok(errors.some((error) => error.includes('no codex tool aliases')), errors.join('\n'));
    } finally {
        rmSync(dir, { recursive: true, force: true });
    }
});

test('documentation check catches a missing host command', () => {
    const errors = checkEvalRunnerDocs(readme.replaceAll('node agent-plugins/tools/evals/run-with-opencode.mjs', ''));
    assert.ok(errors.some((error) => error.includes('no command for OpenCode')), errors.join('\n'));
});

test('workflow check catches an ungated eval job', () => {
    const errors = checkEvalRunnerWorkflow(workflow.replace(
        "github.event_name == 'workflow_dispatch' && inputs.run-codex-evals",
        'false',
    ));
    assert.ok(errors.some((error) => error.includes("workflow codex-plugin-evals: no 'github.event_name")), errors.join('\n'));
});
