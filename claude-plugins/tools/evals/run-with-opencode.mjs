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
import { appendFileSync, cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { delimiter, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { MCP_SERVER_NAME, OPENCODE_MCP_TOOL_PREFIX, PLUGIN_DIR, parseFrontMatter } from '../lib/plugin-config.mjs';
import { combineToolTraces, toolGradePasses } from '../lib/eval-grading.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..', '..');
const pluginDir = join(repoRoot, PLUGIN_DIR);
const evalsDir = here;
const fixture = join(repoRoot, 'headless-services/spring-boot-language-server/src/test/resources/test-projects/sf7-validation');
const mcpToolNames = new Set(JSON.parse(readFileSync(join(evalsDir, 'mocks', MCP_SERVER_NAME, '_tools.json'), 'utf8')).tools.map(({ name }) => name));

const args = process.argv.slice(2);
const only = valueOf('--case');
const model = valueOf('--model');
const judgeModel = valueOf('--judge-model');
const keep = args.includes('--keep');

function valueOf(flag) {
    const index = args.indexOf(flag);
    return index === -1 ? undefined : args[index + 1];
}

function readCase(name) {
    const text = readFileSync(join(evalsDir, name, 'prompt.md'), 'utf8');
    const front = parseFrontMatter(text) ?? {};
    const prompt = text.replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n/, '').trim();
    const graders = readGraders(join(evalsDir, name, 'graders'));
    return { name, front, prompt, graders };
}

function readGraders(gradersDir) {
    return readdirSync(gradersDir).filter((file) => file.endsWith('.md')).map((file) => {
        const text = readFileSync(join(gradersDir, file), 'utf8');
        return { file, ...parseFrontMatter(text), rubric: text.replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n/, '').trim() };
    });
}

function stageWorkspace(caseRoot) {
    const workspace = join(caseRoot, 'workspace');
    mkdirSync(workspace, { recursive: true });
    cpSync(fixture, join(workspace, 'sf7-validation'), { recursive: true, filter: (src) => !/[/\\](target|build)([/\\]|$)/.test(src) });
    cpSync(join(pluginDir, 'skills'), join(workspace, '.agents', 'skills'), { recursive: true });
    mkdirSync(join(workspace, '.opencode', 'agents'), { recursive: true });
    cpSync(join(pluginDir, 'opencode', 'agents', 'spring-reviewer.md'), join(workspace, '.opencode', 'agents', 'spring-reviewer.md'));
    return workspace;
}

/** Replacement for the plugin's OpenCode MCP fragment: same server name, mocked launcher. */
function stageConfig(caseRoot, tracePath, mockDirs) {
    const configPath = join(caseRoot, 'opencode.json');
    const config = JSON.parse(readFileSync(join(pluginDir, 'opencode', 'opencode.jsonc'), 'utf8'));
    config.mcp.servers[MCP_SERVER_NAME] = {
        type: 'local',
        command: ['node', join(here, 'mock-mcp-server.mjs')],
        environment: {
            EVAL_MOCK_DIRS: mockDirs.join(delimiter),
            EVAL_TRACE: tracePath,
        },
        timeout: { startup: 120000 },
    };
    writeFileSync(configPath, `${JSON.stringify(config, null, 2)}\n`);
    return configPath;
}

function stageJudgeConfig(caseRoot) {
    const configPath = join(caseRoot, 'opencode-judge.json');
    writeFileSync(configPath, '{}\n');
    return configPath;
}

function inputText(input) {
    return typeof input === 'string' ? input : JSON.stringify(input ?? {});
}

function normalizeMcpTool(tool) {
    if (mcpToolNames.has(tool) || typeof tool !== 'string' || !tool.startsWith(OPENCODE_MCP_TOOL_PREFIX)) {
        return tool;
    }
    const suffix = tool.slice(OPENCODE_MCP_TOOL_PREFIX.length);
    return mcpToolNames.has(suffix) ? suffix : tool;
}

/** Map OpenCode's native tool names to the names the portable graders understand. */
function traceEntry(part) {
    const input = part.state?.input;
    if (part.tool === 'skill') {
        return { tool: 'skill', input: JSON.stringify({ skill: input?.name ?? input?.skill ?? '' }) };
    }
    if (part.tool === 'subagent' || part.tool === 'task') {
        return { tool: 'agent', input: JSON.stringify({ ...input, agent: input?.subagent_type ?? input?.agent ?? input?.description ?? '' }) };
    }
    if (part.tool === 'read') {
        return { tool: 'view', input: inputText(input) };
    }
    return { tool: normalizeMcpTool(part.tool), input: inputText(input) };
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
    const result = spawnSync('opencode', opencodeArgs, {
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
    const prompt = [
        'You are grading the answer of an AI assistant against a rubric. Do not use any tools.',
        'Reply with exactly one word: PASS or FAIL.',
        '', '## Rubric', grader.rubric,
        '', '## Answer', answer.slice(0, 12000),
    ].join('\n');
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

const caseNames = readdirSync(evalsDir)
    .filter((name) => existsSync(join(evalsDir, name, 'prompt.md')))
    .filter((name) => !only || name === only)
    .sort();
if (caseNames.length === 0) {
    console.error(only ? `no eval case named '${only}'` : 'no eval cases found');
    process.exit(2);
}
if (spawnSync('opencode', ['--version'], { encoding: 'utf8' }).status !== 0) {
    console.error('OpenCode is not available on the PATH');
    process.exit(2);
}

const root = mkdtempSync(join(tmpdir(), 'spring-tools-opencode-evals-'));
const summary = [];
let failed = 0;
try {
    for (const name of caseNames) {
        const testCase = readCase(name);
        const caseRoot = join(root, name);
        const workspace = stageWorkspace(caseRoot);
        const tracePath = join(caseRoot, 'mcp-trace.jsonl');
        writeFileSync(tracePath, '');
        const caseMocks = join(evalsDir, name, 'mocks', MCP_SERVER_NAME);
        const mockDirs = [existsSync(caseMocks) ? caseMocks : null, join(evalsDir, 'mocks', MCP_SERVER_NAME)].filter(Boolean);
        const configPath = stageConfig(caseRoot, tracePath, mockDirs);
        const judgeConfigPath = stageJudgeConfig(caseRoot);

        const timeout = Number(testCase.front.timeout_seconds ?? 600) * 1000;
        const result = runOpenCode({ prompt: testCase.prompt, workspace, configPath, timeout });
        const mockTrace = readFileSync(tracePath, 'utf8').split('\n').filter(Boolean).map((line) => JSON.parse(line));
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
