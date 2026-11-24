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

// Runs the plugin's external eval suite (claude-plugins/tools/evals) with Codex CLI, the same
// way run-with-copilot.mjs runs it with GitHub Copilot CLI:
//
//   node claude-plugins/tools/evals/run-with-codex.mjs [--case <name>] [--model <model>] [--keep]
//
// Each case gets a throwaway workspace, a throwaway CODEX_HOME with the shared skills and the
// recorded MCP mocks, and a staged spring-reviewer custom agent. No language server is started.
// Codex's JSONL event stream supplies tool calls; the mock server supplies the authoritative MCP
// trace. Codex has no callable Skill tool, so positive skill cases explicitly activate the skill
// with a `$skill-name` marker before applying the shared graders.

import { spawnSync } from 'node:child_process';
import { appendFileSync, cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { delimiter, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { MCP_SERVER_NAME, PLUGIN_DIR, parseFrontMatter } from '../lib/plugin-config.mjs';
import { combineToolTraces, toolGradePasses } from '../lib/eval-grading.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..', '..');
const pluginDir = join(repoRoot, PLUGIN_DIR);
const evalsDir = here;
const fixture = join(repoRoot, 'headless-services/spring-boot-language-server/src/test/resources/test-projects/sf7-validation');
const mockTools = JSON.parse(readFileSync(join(evalsDir, 'mocks', MCP_SERVER_NAME, '_tools.json'), 'utf8')).tools;
const mcpToolNames = new Set(mockTools.map(({ name }) => name));

const args = process.argv.slice(2);
const only = valueOf('--case');
const model = valueOf('--model');
const judgeModel = valueOf('--judge-model');
const keep = args.includes('--keep');
const codexCli = process.env.CODEX_CLI || 'codex';

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

function tomlString(value) {
    return JSON.stringify(value);
}

/** Codex loads personal skills and agents from CODEX_HOME, so the eval never touches the user's config. */
function stageCodexHome(caseRoot, workspace, tracePath, mockDirs) {
    const home = join(caseRoot, 'codex-home');
    mkdirSync(join(home, 'skills'), { recursive: true });
    cpSync(join(pluginDir, 'skills'), join(home, 'skills'), { recursive: true });
    stageReviewerAgent(workspace);
    writeFileSync(join(home, 'config.toml'), [
        `[mcp_servers.${MCP_SERVER_NAME}]`,
        `command = ${tomlString('node')}`,
        `args = [${tomlString(join(here, 'mock-mcp-server.mjs'))}]`,
        `[mcp_servers.${MCP_SERVER_NAME}.env]`,
        `EVAL_MOCK_DIRS = ${tomlString(mockDirs.join(delimiter))}`,
        `EVAL_TRACE = ${tomlString(tracePath)}`,
        '',
    ].join('\n'));
    return home;
}

/** Codex custom agents are TOML files; reuse the same instructions as the shared review skill. */
function stageReviewerAgent(workspace) {
    const file = join(pluginDir, 'skills', 'spring-review', 'SKILL.md');
    const text = readFileSync(file, 'utf8');
    const front = parseFrontMatter(text) ?? {};
    const instructions = text.replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n/, '').trim();
    const agentsDir = join(workspace, '.codex', 'agents');
    mkdirSync(agentsDir, { recursive: true });
    writeFileSync(join(agentsDir, 'spring-reviewer.toml'), [
        'name = "spring-reviewer"',
        `description = ${tomlString(front.description ?? 'Read-only Spring Tools review of the current project.')}`,
        `developer_instructions = ${tomlString(instructions)}`,
        '',
    ].join('\n'));
}

function stageJudgeHome(caseRoot) {
    const home = join(caseRoot, 'codex-judge-home');
    mkdirSync(home, { recursive: true });
    writeFileSync(join(home, 'config.toml'), '');
    return home;
}

/** Codex activates skills with a `$name` marker instead of a callable Skill tool. */
function explicitSkillNames(testCase) {
    const names = new Set();
    for (const grader of testCase.graders) {
        if (grader.type !== 'tool_used' || grader.tool !== 'Skill') {
            continue;
        }
        const match = grader.input_match?.match(/"skill"\s*:\s*"(?:[\w-]+:)?([\w-]+)"/);
        if (match && (grader.min === undefined || Number(grader.min) > 0)) {
            names.add(match[1]);
        }
    }
    return [...names];
}

function codexEnv(home) {
    const env = { ...process.env, CODEX_HOME: home };
    if (process.env.CODEX_EVAL_API_KEY) {
        env.CODEX_API_KEY = process.env.CODEX_EVAL_API_KEY;
    }
    return env;
}

function parseCodexEvents(stdout) {
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
        if (event.type !== 'item.completed' || !event.item) {
            continue;
        }
        const item = event.item;
        if (item.type === 'agent_message') {
            messages.push(item.text ?? '');
        } else if (item.type === 'mcp_tool_call') {
            trace.push({ tool: normalizeMcpTool(item.tool), input: JSON.stringify(item.arguments ?? {}) });
        } else if (item.type === 'collab_tool_call') {
            trace.push({ tool: 'agent', input: JSON.stringify({ agent: item.tool, prompt: item.prompt ?? '' }) });
        } else if (item.type === 'command_execution') {
            trace.push({ tool: 'view', input: item.command ?? '' });
        }
    }
    return { trace, answer: messages.join('\n') };
}

function normalizeMcpTool(tool) {
    if (mcpToolNames.has(tool)) {
        return tool;
    }
    const suffix = typeof tool === 'string' ? tool.split('__').at(-1) : tool;
    return mcpToolNames.has(suffix) ? suffix : tool;
}

function runCodex({ prompt, workspace, home, answerPath, timeout, judge = false }) {
    const codexArgs = [
        'exec', '--json', '--ephemeral', '--skip-git-repo-check',
        '-C', workspace, '--sandbox', 'read-only',
        '--output-last-message', answerPath,
    ];
    if (judge && judgeModel) {
        codexArgs.push('--model', judgeModel);
    } else if (!judge && model) {
        codexArgs.push('--model', model);
    }
    codexArgs.push(prompt);
    const result = spawnSync(codexCli, codexArgs, {
        cwd: workspace,
        encoding: 'utf8',
        timeout,
        maxBuffer: 20 * 1024 * 1024,
        env: codexEnv(home),
    });
    const events = parseCodexEvents(result.stdout ?? '');
    const answer = existsSync(answerPath) ? readFileSync(answerPath, 'utf8') : events.answer;
    return { answer, trace: events.trace, status: result.status, stderr: result.stderr ?? '', error: result.error };
}

function llmVerdict(grader, answer, workspace, judgeHome) {
    const prompt = [
        'You are grading the answer of an AI assistant against a rubric. Do not use any tools.',
        'Reply with exactly one word: PASS or FAIL.',
        '', '## Rubric', grader.rubric,
        '', '## Answer', answer.slice(0, 12000),
    ].join('\n');
    const verdictPath = join(judgeHome, 'verdict.txt');
    const result = runCodex({ prompt, workspace, home: judgeHome, answerPath: verdictPath, timeout: 300000, judge: true });
    const verdict = result.answer.trim().toUpperCase();
    return { pass: result.status === 0 && verdict === 'PASS', raw: verdict.slice(0, 200) };
}

function gradeCase(testCase, answer, trace, workspace, judgeHome) {
    return testCase.graders.map((grader) => {
        const weight = Number(grader.weight ?? 1);
        if (grader.type === 'tool_used') {
            return { file: grader.file, weight, pass: toolGradePasses(grader, trace, 'codex'), detail: grader.tool };
        }
        if (grader.type === 'regex') {
            return { file: grader.file, weight, pass: new RegExp(grader.pattern, grader.flags ?? '').test(answer), detail: grader.pattern };
        }
        if (grader.type === 'llm') {
            const verdict = llmVerdict(grader, answer, workspace, judgeHome);
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
if (spawnSync(codexCli, ['--version'], { encoding: 'utf8' }).status !== 0) {
    console.error('Codex CLI is not available on the PATH');
    process.exit(2);
}

const root = mkdtempSync(join(tmpdir(), 'spring-tools-codex-evals-'));
const summary = [];
let failed = 0;
try {
    for (const name of caseNames) {
        const testCase = readCase(name);
        const caseRoot = join(root, name);
        const workspace = join(caseRoot, 'workspace');
        mkdirSync(workspace, { recursive: true });
        cpSync(fixture, join(workspace, 'sf7-validation'), { recursive: true, filter: (src) => !/[/\\](target|build)([/\\]|$)/.test(src) });

        const tracePath = join(caseRoot, 'mcp-trace.jsonl');
        writeFileSync(tracePath, '');
        const caseMocks = join(evalsDir, name, 'mocks', MCP_SERVER_NAME);
        const mockDirs = [existsSync(caseMocks) ? caseMocks : null, join(evalsDir, 'mocks', MCP_SERVER_NAME)].filter(Boolean);
        const home = stageCodexHome(caseRoot, workspace, tracePath, mockDirs);
        const judgeHome = stageJudgeHome(caseRoot);

        const skillNames = explicitSkillNames(testCase);
        const explicitPrompt = skillNames.length
            ? `${skillNames.map((skill) => `$${skill}`).join(' ')} ${testCase.prompt}`
            : testCase.prompt;
        const answerPath = join(caseRoot, 'answer.txt');
        const timeout = Number(testCase.front.timeout_seconds ?? 600) * 1000;
        const result = runCodex({ prompt: explicitPrompt, workspace, home, answerPath, timeout });
        const mockTrace = readFileSync(tracePath, 'utf8').split('\n').filter(Boolean).map((line) => JSON.parse(line));
        const trace = [
            ...combineToolTraces(result.trace, mockTrace, mcpToolNames),
            ...skillNames.map((skill) => ({ tool: 'skill', input: JSON.stringify({ skill }) })),
        ];
        writeFileSync(answerPath, result.answer);

        const results = result.status === 0 && !result.error
            ? gradeCase(testCase, result.answer, trace, workspace, judgeHome)
            : [{ file: 'run', weight: 1, pass: false, detail: `codex exited with ${result.status ?? result.error?.message}: ${result.stderr.trim().slice(-300)}` }];
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
    appendFileSync(process.env.GITHUB_STEP_SUMMARY, `\n### Codex evals\n\n| case | result | score |\n| --- | --- | --- |\n${rows}\n`);
}
console.log(`\n${summary.length - failed}/${summary.length} cases passed`);
process.exit(failed === 0 ? 0 : 1);
