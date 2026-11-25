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

// Runs the plugin's external eval suite (claude-plugins/tools/evals) with OpenCode, using the
// same shared prompts, mocks and graders as the Claude and Copilot runners:
//
//   node claude-plugins/tools/evals/run-with-opencode.mjs [--case <name>] [--model <model>] [--keep]
//
// Each case gets a throwaway workspace with the sf7-validation fixture, the shared skills under
// .agents/skills, the reviewer adapter under .opencode/agents, and a custom OpenCode config whose
// MCP server replays the recorded mocks. No language server and no network are needed. OpenCode's
// JSON event stream supplies tool calls; the mock server supplies the authoritative MCP trace.

import { spawnSync } from 'node:child_process';
import { appendFileSync, cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { delimiter, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { MCP_SERVER_NAME, OPENCODE_MCP_TOOL_ACTION, PLUGIN_DIR, PLUGIN_NAME } from '../lib/plugin-config.mjs';
import { combineToolTraces, normalizeOpenCodeMcpTool, toolGradePasses } from '../lib/eval-grading.mjs';
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
const judgeModel = argValue(args, '--judge-model');
const keep = args.includes('--keep');
const opencodeCli = process.env.OPENCODE_CLI || 'opencode';

function stageWorkspace(caseRoot) {
    const workspace = join(caseRoot, 'workspace');
    mkdirSync(workspace, { recursive: true });
    cpSync(fixture, join(workspace, 'sf7-validation'), { recursive: true, filter: (src) => !/[/\\](target|build)([/\\]|$)/.test(src) });
    cpSync(join(pluginDir, 'skills'), join(workspace, '.agents', 'skills'), { recursive: true });
    mkdirSync(join(workspace, '.opencode', 'agents'), { recursive: true });
    cpSync(join(pluginDir, 'opencode', 'agents', 'spring-reviewer.md'), join(workspace, '.opencode', 'agents', 'spring-reviewer.md'));
    return workspace;
}

/** Translate shared allowed_tools into OpenCode's ordered, deny-by-default permission rules. */
function evalPermissions(allowedTools) {
    const allowed = new Set(allowedTools ?? []);
    const permissions = [{ action: '*', resource: '*', effect: 'deny' }];
    const allow = (action, resource = '*') => permissions.push({ action, resource, effect: 'allow' });
    for (const [tool, action] of [['Read', 'read'], ['Glob', 'glob'], ['Grep', 'grep'], ['Skill', 'skill']]) {
        if (allowed.has(tool)) {
            allow(action);
        }
    }
    if (allowed.has('Agent')) {
        allow('subagent', 'spring-reviewer');
    }
    allow(OPENCODE_MCP_TOOL_ACTION);
    if (allowed.has('Read')) {
        // The shared playbooks live in the plugin, outside the throwaway workspace.
        allow('external_directory', `${pluginDir}/*`);
    }
    return permissions;
}

/** Replacement for the plugin's OpenCode MCP fragment: same server name, mocked launcher. */
function stageConfig(caseRoot, tracePath, mockDirs, allowedTools) {
    const configPath = join(caseRoot, 'opencode.json');
    const config = JSON.parse(readFileSync(join(pluginDir, 'opencode', 'opencode.jsonc'), 'utf8'));
    config.permissions = evalPermissions(allowedTools);
    config.mcp.servers[PLUGIN_NAME] = {
        type: 'local',
        command: ['node', join(here, 'mock-mcp-server.mjs')],
        environment: {
            EVAL_MOCK_DIRS: mockDirs.join(delimiter),
            EVAL_TRACE: tracePath,
        },
        timeout: { startup: 120000 },
        // Native tool names are needed so the trace maps to the portable graders.
        codemode: false,
    };
    writeFileSync(configPath, `${JSON.stringify(config, null, 2)}\n`);
    return configPath;
}

function stageJudgeConfig(caseRoot) {
    const configPath = join(caseRoot, 'opencode-judge.json');
    const config = { permissions: [{ action: '*', resource: '*', effect: 'deny' }] };
    writeFileSync(configPath, `${JSON.stringify(config, null, 2)}\n`);
    return configPath;
}

function inputText(input) {
    return typeof input === 'string' ? input : JSON.stringify(input ?? {});
}

/** Map OpenCode's native tool names to the names the portable graders understand. */
function traceEntry(part) {
    const input = part.state?.input;
    if (part.tool === 'skill') {
        return { tool: 'skill', input: JSON.stringify({ skill: input?.id ?? input?.name ?? input?.skill ?? '' }) };
    }
    if (part.tool === 'subagent' || part.tool === 'task') {
        return { tool: 'agent', input: JSON.stringify({ ...input, agent: input?.subagent_type ?? input?.agent ?? input?.description ?? '' }) };
    }
    if (part.tool === 'read') {
        return { tool: 'view', input: inputText(input) };
    }
    return { tool: normalizeOpenCodeMcpTool(part.tool, mcpToolNames), input: inputText(input) };
}

function parseOpenCodeEvents(stdout) {
    const trace = [];
    const messages = [];
    for (const line of stdout.split('\n')) {
        if (!line.trim()) {
            continue;
        }
        let event;
        try {
            event = JSON.parse(line);
        } catch {
            continue;
        }
        if (event.type === 'text' && event.part?.text) {
            messages.push(event.part.text);
        } else if (event.type === 'tool_use' && event.part) {
            trace.push(traceEntry(event.part));
        }
    }
    return { trace, answer: messages.join('\n') };
}

function runOpenCode({ prompt, workspace, configPath, timeout, judge = false }) {
    const opencodeArgs = ['--standalone', 'run', '--format', 'json'];
    if (!judge) {
        opencodeArgs.push('--auto');
    }
    const selectedModel = judge ? judgeModel : model;
    if (selectedModel) {
        opencodeArgs.push('--model', selectedModel);
    }
    opencodeArgs.push(prompt);
    const result = spawnSync(opencodeCli, opencodeArgs, {
        cwd: workspace,
        encoding: 'utf8',
        timeout,
        maxBuffer: 20 * 1024 * 1024,
        env: {
            ...process.env,
            OPENCODE_CONFIG: configPath,
            OPENCODE_DISABLE_TELEMETRY: '1',
        },
    });
    const events = parseOpenCodeEvents(result.stdout ?? '');
    return { ...events, status: result.status, stderr: result.stderr ?? '', error: result.error };
}

function llmVerdict(grader, answer, workspace, judgeConfigPath) {
    const prompt = judgePrompt(grader, answer);
    const result = runOpenCode({ prompt, workspace, configPath: judgeConfigPath, timeout: 300000, judge: true });
    const verdict = result.answer.trim().toUpperCase();
    return { pass: result.status === 0 && verdict === 'PASS', raw: verdict.slice(0, 200) };
}

function gradeCase(testCase, answer, trace, workspace, judgeConfigPath) {
    return testCase.graders.map((grader) => {
        const weight = Number(grader.weight ?? 1);
        if (grader.type === 'tool_used') {
            return { file: grader.file, weight, pass: toolGradePasses(grader, trace, 'opencode'), detail: grader.tool };
        }
        if (grader.type === 'regex') {
            return { file: grader.file, weight, pass: new RegExp(grader.pattern, grader.flags ?? '').test(answer), detail: grader.pattern };
        }
        if (grader.type === 'llm') {
            const verdict = llmVerdict(grader, answer, workspace, judgeConfigPath);
            return { file: grader.file, weight, pass: verdict.pass, detail: verdict.raw };
        }
        return { file: grader.file, weight, pass: false, detail: `unsupported grader type '${grader.type}'` };
    });
}

const caseNames = listEvalCases(evalsDir, only);
if (caseNames.length === 0) {
    console.error(only ? `no eval case named '${only}'` : 'no eval cases found');
    process.exit(2);
}
if (spawnSync(opencodeCli, ['--version'], { encoding: 'utf8' }).status !== 0) {
    console.error('OpenCode is not available on the PATH');
    process.exit(2);
}

const root = mkdtempSync(join(tmpdir(), 'spring-tools-opencode-evals-'));
const summary = [];
let failed = 0;
try {
    for (const name of caseNames) {
        const testCase = readEvalCase(evalsDir, name);
        const caseRoot = join(root, name);
        const workspace = stageWorkspace(caseRoot);
        const tracePath = join(caseRoot, 'mcp-trace.jsonl');
        writeFileSync(tracePath, '');
        const caseMocks = join(evalsDir, name, 'mocks', MCP_SERVER_NAME);
        const mockDirs = [existsSync(caseMocks) ? caseMocks : null, join(evalsDir, 'mocks', MCP_SERVER_NAME)].filter(Boolean);
        const configPath = stageConfig(caseRoot, tracePath, mockDirs, testCase.front.allowed_tools);
        const judgeConfigPath = stageJudgeConfig(caseRoot);

        const timeout = Number(testCase.front.timeout_seconds ?? 600) * 1000;
        const result = runOpenCode({ prompt: testCase.prompt, workspace, configPath, timeout });
        const mockTrace = readMockTrace(tracePath);
        const trace = combineToolTraces(result.trace, mockTrace, mcpToolNames);
        writeFileSync(join(caseRoot, 'answer.txt'), result.answer);

        const results = result.status === 0 && !result.error
            ? gradeCase(testCase, result.answer, trace, workspace, judgeConfigPath)
            : [{ file: 'run', weight: 1, pass: false, detail: `opencode exited with ${result.status ?? result.error?.message}: ${result.stderr.trim().slice(-300)}` }];
        const total = results.reduce((sum, entry) => sum + entry.weight, 0);
        const scored = results.filter((entry) => entry.pass).reduce((sum, entry) => sum + entry.weight, 0);
        const pass = results.every((entry) => entry.pass);
        failed += pass ? 0 : 1;
        summary.push({ name, pass, scored, total });
        console.log(`${pass ? 'PASS' : 'FAIL'} ${name} (${scored}/${total})`);
        for (const entry of results.filter((result) => !result.pass)) {
            console.log(`     - ${entry.file}: ${entry.detail}`);
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
    const rows = summary.map((entry) => `| ${entry.name} | ${entry.pass ? 'pass' : 'fail'} | ${entry.scored}/${entry.total} |`).join('\n');
    appendFileSync(process.env.GITHUB_STEP_SUMMARY, `\n### OpenCode evals\n\n| case | result | score |\n| --- | --- | --- |\n${rows}\n`);
}
console.log(`\n${summary.length - failed}/${summary.length} cases passed`);
process.exit(failed === 0 ? 0 : 1);
