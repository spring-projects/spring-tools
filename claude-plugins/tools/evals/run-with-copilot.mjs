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

// Runs the plugin's external eval suite (claude-plugins/tools/evals) with GitHub Copilot CLI, the
// same way `claude plugin eval` runs it with Claude Code:
//
//   node claude-plugins/tools/evals/run-with-copilot.mjs [--case <name>] [--model <model>] [--keep]
//
// Each case gets a throwaway workspace with the sf7-validation fixture and a throwaway copy of the
// plugin whose MCP server is mock-mcp-server.mjs, so no language server and no network are needed.
// The trace is read back from the Copilot debug log (tool calls) and the mock server (MCP calls),
// then the recorded graders are applied: `tool_used` and `regex` locally, `llm` through one extra
// Copilot call that answers PASS or FAIL.

import { spawnSync } from 'node:child_process';
import { appendFileSync, cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { delimiter, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { PLUGIN_DIR, MCP_SERVER_NAME } from '../lib/plugin-config.mjs';
import { combineToolTraces, copilotAgentId, toolGradePasses } from '../lib/eval-grading.mjs';
import { argValue, judgePrompt, listEvalCases, readEvalCase, readMockTrace } from '../lib/eval-cases.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..', '..');
const pluginDir = join(repoRoot, PLUGIN_DIR);
const evalsDir = here;
const fixture = join(repoRoot, 'headless-services/spring-boot-language-server/src/test/resources/test-projects/sf7-validation');
const mcpToolNames = new Set(JSON.parse(readFileSync(join(evalsDir, 'mocks', MCP_SERVER_NAME, '_tools.json'), 'utf8')).tools.map(({ name }) => name));

const args = process.argv.slice(2);
const only = argValue(args, '--case');
const model = argValue(args, '--model');
const keep = args.includes('--keep');
const copilotCli = process.env.COPILOT_CLI || 'copilot';

/** Copy of the plugin whose MCP server replays the recorded mocks of this case. */
function stagePlugin(root, caseName, tracePath) {
    const staged = join(root, 'plugin');
    cpSync(pluginDir, staged, { recursive: true, filter: (src) => !src.includes(`${join(PLUGIN_DIR, 'language-server')}`) });
    const caseMocks = join(evalsDir, caseName, 'mocks', MCP_SERVER_NAME);
    const mockDirs = [existsSync(caseMocks) ? caseMocks : null, join(evalsDir, 'mocks', MCP_SERVER_NAME)].filter(Boolean);
    const mcp = JSON.parse(readFileSync(join(staged, 'mcp.json'), 'utf8'));
    mcp.mcpServers[MCP_SERVER_NAME] = {
        type: 'stdio',
        command: 'node',
        args: [join(here, 'mock-mcp-server.mjs')],
        env: { EVAL_MOCK_DIRS: mockDirs.join(delimiter), EVAL_TRACE: tracePath },
    };
    writeFileSync(join(staged, 'mcp.json'), `${JSON.stringify(mcp, null, 2)}\n`);
    // The session hook only downloads the language server, which the mocked run never starts.
    rmSync(join(staged, 'com.github.copilot', 'hooks'), { recursive: true, force: true });
    return staged;
}

const COPILOT_TOOL_NAMES = { Read: 'view', Glob: 'glob', Grep: 'grep', Skill: 'skill' };

/** Limit the model to the shared case's tools plus the mocked Spring Tools MCP server. */
function availableTools(allowedTools) {
    const tools = (allowedTools ?? []).map((tool) => COPILOT_TOOL_NAMES[tool]).filter(Boolean);
    return [...tools, `${MCP_SERVER_NAME}/*`].join(',');
}

/** Runs one Copilot prompt and returns { answer, trace }. */
function runCopilot({ prompt, plugin, workspace, logs, agent, allowedTools, timeout }) {
    const copilotArgs = [
        '--plugin-dir', plugin,
        '--allow-all-tools', '--allow-all-mcp-server-instructions',
        '--no-ask-user', '--no-custom-instructions', '--disable-builtin-mcps',
        `--available-tools=${availableTools(allowedTools)}`,
        '--log-level', 'debug', '--log-dir', logs,
        '-p', prompt, '--silent',
    ];
    if (agent) {
        copilotArgs.push('--agent', agent);
    }
    if (model) {
        copilotArgs.push('--model', model);
    }
    const result = spawnSync(copilotCli, copilotArgs, { cwd: workspace, encoding: 'utf8', timeout, env: { ...process.env, COPILOT_DISABLE_TELEMETRY: '1' } });
    return { answer: result.stdout ?? '', stderr: result.stderr ?? '', status: result.status };
}

/** Tool calls Copilot made, read back from its debug log. */
function logTrace(logs) {
    const entries = [];
    for (const file of existsSync(logs) ? readdirSync(logs) : []) {
        const text = readFileSync(join(logs, file), 'utf8');
        for (const match of text.matchAll(/"name":\s*"([\w.-]+)",\s*"arguments":\s*"((?:[^"\\]|\\.)*)"/g)) {
            entries.push({ tool: match[1], input: match[2].replace(/\\"/g, '"').replace(/\\\\/g, '\\') });
        }
    }
    return entries;
}

/** A second, cheap Copilot call that judges the answer against the rubric of an `llm` grader. */
function llmVerdict(grader, answer, workspace) {
    const prompt = judgePrompt(grader, answer);
    // Prompt mode requires an explicit permission mode. Keep the judge read-only even then.
    const result = spawnSync(copilotCli, ['--allow-all-tools', '--available-tools=view', '--no-ask-user', '--no-custom-instructions', '--disable-builtin-mcps', '-p', prompt, '--silent'], { cwd: workspace, encoding: 'utf8', timeout: 300000 });
    const verdict = (result.stdout ?? '').trim().toUpperCase();
    return { pass: result.status === 0 && verdict === 'PASS', raw: verdict.slice(0, 200) };
}

function gradeCase(testCase, answer, trace, workspace) {
    const results = [];
    for (const grader of testCase.graders) {
        const weight = Number(grader.weight ?? 1);
        if (grader.type === 'tool_used') {
            results.push({ file: grader.file, weight, pass: toolGradePasses(grader, trace), detail: grader.tool });
        } else if (grader.type === 'regex') {
            const flags = grader.flags ?? '';
            results.push({ file: grader.file, weight, pass: new RegExp(grader.pattern, flags).test(answer), detail: grader.pattern });
        } else if (grader.type === 'llm') {
            const verdict = llmVerdict(grader, answer, workspace);
            results.push({ file: grader.file, weight, pass: verdict.pass, detail: verdict.raw });
        } else {
            results.push({ file: grader.file, weight, pass: false, detail: `unsupported grader type '${grader.type}'` });
        }
    }
    return results;
}

const caseNames = listEvalCases(evalsDir, only);
if (caseNames.length === 0) {
    console.error(only ? `no eval case named '${only}'` : 'no eval cases found');
    process.exit(2);
}
if (spawnSync(copilotCli, ['--version'], { encoding: 'utf8' }).status !== 0) {
    console.error('GitHub Copilot CLI is not available on the PATH');
    process.exit(2);
}

const root = mkdtempSync(join(tmpdir(), 'spring-tools-evals-'));
const summary = [];
let failed = 0;
try {
    for (const name of caseNames) {
        const testCase = readEvalCase(evalsDir, name);
        const caseRoot = join(root, name);
        const workspace = join(caseRoot, 'workspace');
        const logs = join(caseRoot, 'logs');
        mkdirSync(workspace, { recursive: true });
        mkdirSync(logs, { recursive: true });
        cpSync(fixture, join(workspace, 'sf7-validation'), { recursive: true, filter: (src) => !/[/\\](target|build)([/\\]|$)/.test(src) });
        const tracePath = join(caseRoot, 'mcp-trace.jsonl');
        writeFileSync(tracePath, '');
        const plugin = stagePlugin(caseRoot, name, tracePath);

        // Copilot selects a custom agent through --agent instead of a delegation tool.
        const agentGrader = testCase.graders.find((g) => g.type === 'tool_used' && g.tool === 'Agent');
        // Copilot custom-agent IDs are derived from the agent filename, not plugin-qualified.
        const agent = agentGrader ? copilotAgentId(agentGrader.input_match) : undefined;

        const timeout = Number(testCase.front.timeout_seconds ?? 600) * 1000;
        const { answer, stderr, status } = runCopilot({ prompt: testCase.prompt, plugin, workspace, logs, agent, allowedTools: testCase.front.allowed_tools, timeout });
        const mockTrace = readMockTrace(tracePath);
        const trace = [
            ...combineToolTraces(logTrace(logs), mockTrace, mcpToolNames),
            ...(agent ? [{ tool: 'agent', input: JSON.stringify({ agent }) }] : []),
        ];
        writeFileSync(join(caseRoot, 'answer.txt'), answer);
        const results = status === 0
            ? gradeCase(testCase, answer, trace, workspace)
            : [{ file: 'run', weight: 1, pass: false, detail: `copilot exited with ${status}: ${stderr.trim().slice(-300)}` }];

        const total = results.reduce((sum, r) => sum + r.weight, 0);
        const scored = results.filter((r) => r.pass).reduce((sum, r) => sum + r.weight, 0);
        const pass = results.every((r) => r.pass);
        failed += pass ? 0 : 1;
        summary.push({ name, pass, scored, total, results, dir: caseRoot });
        console.log(`${pass ? 'PASS' : 'FAIL'} ${name} (${scored}/${total})`);
        for (const result of results.filter((r) => !r.pass)) {
            console.log(`     - ${result.file}: ${result.detail}`);
        }
    }
} finally {
    if (!keep) {
        rmSync(root, { recursive: true, force: true });
    } else {
        console.log(`\nartifacts: ${root}`);
    }
}

if (process.env.GITHUB_STEP_SUMMARY) {
    const rows = summary.map((s) => `| ${s.name} | ${s.pass ? 'pass' : 'fail'} | ${s.scored}/${s.total} |`).join('\n');
    appendFileSync(process.env.GITHUB_STEP_SUMMARY, `\n### Copilot CLI evals\n\n| case | result | score |\n| --- | --- | --- |\n${rows}\n`);
}
console.log(`\n${summary.length - failed}/${summary.length} cases passed`);
process.exit(failed === 0 ? 0 : 1);
