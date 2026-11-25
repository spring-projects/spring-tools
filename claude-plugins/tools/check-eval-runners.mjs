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

// Guards against four-host eval coverage drifting after a merge:
//   node claude-plugins/tools/check-eval-runners.mjs [--runners] [--docs] [--workflow]
//
// --runners checks that every host has an executable runner that uses the shared cases, mocks and
// graders; --docs checks that the README shows all four commands; --workflow checks that each host
// has an opt-in, time-boxed job with a credential check.

import { spawnSync } from 'node:child_process';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const EVAL_HOSTS = [
    {
        name: 'Claude',
        runner: 'run-with-claude.mjs',
        command: 'node claude-plugins/tools/evals/run-with-claude.mjs',
        markers: ['stagePlugin', "plugin', 'eval", 'resultsDir'],
        workflow: {
            job: 'plugin-evals',
            input: 'run-evals',
            command: 'run-with-claude.mjs',
            secret: 'ANTHROPIC_API_KEY',
            extra: ['--max-cost-usd'],
        },
    },
    {
        name: 'GitHub Copilot CLI',
        runner: 'run-with-copilot.mjs',
        command: 'node claude-plugins/tools/evals/run-with-copilot.mjs',
        markers: ['mock-mcp-server.mjs', 'combineToolTraces', 'toolGradePasses', 'logTrace', '--case'],
        workflow: {
            job: 'copilot-plugin-evals',
            input: 'run-copilot-evals',
            command: 'run-with-copilot.mjs',
            secret: 'GITHUB_TOKEN',
            extra: ['copilot-requests: write'],
        },
    },
    {
        name: 'Codex',
        runner: 'run-with-codex.mjs',
        command: 'node claude-plugins/tools/evals/run-with-codex.mjs',
        markers: ['mock-mcp-server.mjs', 'combineToolTraces', 'toolGradePasses', 'codexArgs', 'mcp_tool_call', 'CODEX_HOME', '$skill'],
        workflow: {
            job: 'codex-plugin-evals',
            input: 'run-codex-evals',
            command: 'run-with-codex.mjs',
            secret: 'secrets.CODEX_API_KEY',
            extra: ['CODEX_EVAL_API_KEY'],
        },
    },
    {
        name: 'OpenCode',
        runner: 'run-with-opencode.mjs',
        command: 'node claude-plugins/tools/evals/run-with-opencode.mjs',
        markers: ['mock-mcp-server.mjs', 'combineToolTraces', 'toolGradePasses', 'opencodeArgs', 'tool_use', 'OPENCODE_CONFIG', 'OPENCODE_MCP_TOOL_ACTION', 'normalizeOpenCodeMcpTool'],
        workflow: {
            job: 'opencode-plugin-evals',
            input: 'run-opencode-evals',
            command: 'run-with-opencode.mjs',
            secret: 'secrets.ANTHROPIC_API_KEY',
            extra: ['opencode-eval-model', '@opencode/cli@2', 'anthropic/*'],
        },
    },
];

const README = 'claude-plugins/spring-tools/README.md';
const WORKFLOW = '.github/workflows/claude-plugin-check.yml';

/** Every host runner must still be wired to the shared cases, mocks and grading library. */
export function checkEvalRunnerCoverage(root) {
    const errors = [];
    const evalsDir = join(root, 'claude-plugins', 'tools', 'evals');
    const sharedMarkers = ['readEvalCase', 'listEvalCases'];
    const cases = existsSync(evalsDir)
        ? readdirSync(evalsDir).filter((name) => existsSync(join(evalsDir, name, 'prompt.md')))
        : [];
    if (cases.length === 0) {
        errors.push('no shared eval cases found');
    }

    for (const host of EVAL_HOSTS) {
        const file = join(evalsDir, host.runner);
        if (!existsSync(file)) {
            errors.push(`${host.name}: eval runner ${host.runner} is missing`);
            continue;
        }
        const text = readFileSync(file, 'utf8');
        if (!text.startsWith('#!/usr/bin/env node')) {
            errors.push(`${host.runner}: missing executable shebang`);
        }
        for (const marker of host.markers) {
            if (!text.includes(marker)) {
                errors.push(`${host.runner}: no reference to '${marker}'`);
            }
        }
        if (host.runner !== 'run-with-claude.mjs') {
            for (const marker of sharedMarkers) {
                if (!text.includes(marker)) {
                    errors.push(`${host.runner}: does not use the shared ${marker}`);
                }
            }
        }
    }

    if (!existsSync(join(evalsDir, 'mock-mcp-server.mjs'))) {
        errors.push('shared mock-mcp-server.mjs is missing');
    }
    if (!existsSync(join(root, 'claude-plugins', 'tools', 'lib', 'eval-cases.mjs'))) {
        errors.push('shared eval-cases.mjs is missing');
    }
    const grading = join(root, 'claude-plugins', 'tools', 'lib', 'eval-grading.mjs');
    if (!existsSync(grading)) {
        errors.push('shared eval-grading.mjs is missing');
    } else {
        const text = readFileSync(grading, 'utf8');
        for (const host of ['codex', 'opencode']) {
            if (!new RegExp(`\\b${host}\\s*:`).test(text)) {
                errors.push(`eval-grading.mjs: no ${host} tool aliases`);
            }
        }
    }
    return errors;
}

/** Parse all runners so a broken host adapter fails the check without needing that host installed. */
export function checkEvalRunnerSyntax(root) {
    const errors = [];
    for (const host of EVAL_HOSTS) {
        const file = join(root, 'claude-plugins', 'tools', 'evals', host.runner);
        if (!existsSync(file)) {
            continue;
        }
        const result = spawnSync(process.execPath, ['--check', file], { encoding: 'utf8' });
        if (result.status !== 0) {
            errors.push(`${host.runner}: ${result.stderr.trim().split('\n').at(-1) ?? 'syntax check failed'}`);
        }
    }
    return errors;
}

/** The README must show all four commands and the cost/auth consequences of running them. */
export function checkEvalRunnerDocs(readme) {
    const errors = [];
    if (!readme.includes('### Behavioral evals')) {
        errors.push('README: no Behavioral evals section');
    }
    for (const host of EVAL_HOSTS) {
        if (!readme.includes(host.command)) {
            errors.push(`README: no command for ${host.name}`);
        }
    }
    for (const marker of ['CLAUDE_CLI', 'COPILOT_CLI', 'CODEX_CLI', 'OPENCODE_CLI', 'CODEX_EVAL_API_KEY', 'OpenCode 2', 'check-eval-runners.mjs', '$skill', 'mcp-smoke', 'no skill-activation event', 'model call']) {
        if (!readme.includes(marker)) {
            errors.push(`README: no '${marker}' guidance`);
        }
    }
    return errors;
}

/** Isolate one top-level YAML job block without pulling in a YAML parser. */
function jobSection(workflow, job) {
    const lines = workflow.split(/\r?\n/);
    const start = lines.findIndex((line) => line === `  ${job}:`);
    if (start === -1) {
        return '';
    }
    const end = lines.findIndex((line, index) => index > start && /^  [A-Za-z][\w-]*:\s*$/.test(line));
    return lines.slice(start, end === -1 ? undefined : end).join('\n');
}

/** Every eval job must be opt-in, time-boxed, credential-checked and visible to CI validation. */
export function checkEvalRunnerWorkflow(workflow) {
    const errors = [];
    const dispatchStart = workflow.indexOf('  workflow_dispatch:');
    const dispatchEnd = workflow.indexOf('\npermissions:', dispatchStart);
    if (dispatchStart === -1 || dispatchEnd === -1) {
        return ['workflow: no workflow_dispatch inputs section'];
    }
    const dispatch = workflow.slice(dispatchStart, dispatchEnd);
    for (const host of EVAL_HOSTS) {
        const { job, input, command, secret, extra } = host.workflow;
        if (!dispatch.includes(`${input}:`)) {
            errors.push(`workflow: no ${input} input for ${host.name} evals`);
        }
        const section = jobSection(workflow, job);
        if (!section) {
            errors.push(`workflow: no ${job} job for ${host.name} evals`);
            continue;
        }
        const required = [
            `github.event_name == 'workflow_dispatch' && inputs.${input}`,
            'timeout-minutes:',
            command,
            secret,
            ...extra,
        ];
        for (const marker of required) {
            if (!section.includes(marker)) {
                errors.push(`workflow ${job}: no '${marker}'`);
            }
        }
    }
    if (!workflow.includes('check-eval-runners.mjs --runners --docs --workflow')) {
        errors.push('workflow: CI does not run the four-host coverage check');
    }
    return errors;
}

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..');
const args = process.argv.slice(2);
const requested = ['--runners', '--docs', '--workflow'].filter((flag) => args.includes(flag));
const modes = requested.length ? requested : ['--runners', '--docs', '--workflow'];
const results = {
    '--runners': () => [...checkEvalRunnerCoverage(repoRoot), ...checkEvalRunnerSyntax(repoRoot)],
    '--docs': () => checkEvalRunnerDocs(readFileSync(join(repoRoot, README), 'utf8')),
    '--workflow': () => checkEvalRunnerWorkflow(readFileSync(join(repoRoot, WORKFLOW), 'utf8')),
};
const labels = {
    '--runners': 'eval runner coverage passed',
    '--docs': 'eval runner documentation passed',
    '--workflow': 'eval runner workflow passed',
};

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    let failed = false;
    for (const mode of modes) {
        const errors = results[mode]();
        for (const error of errors) {
            console.error(`ERROR ${error}`);
        }
        if (errors.length) {
            failed = true;
        } else {
            console.log(labels[mode]);
        }
    }
    process.exit(failed ? 1 : 0);
}
