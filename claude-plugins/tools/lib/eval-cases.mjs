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

import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

import { parseFrontMatter } from './plugin-config.mjs';

/** Read a CLI flag's following value, or undefined when the flag is absent. */
export function argValue(args, flag) {
    const index = args.indexOf(flag);
    return index === -1 ? undefined : args[index + 1];
}

/** List shared eval case names in deterministic order, optionally restricted to one case. */
export function listEvalCases(evalsDir, only) {
    return readdirSync(evalsDir)
        .filter((name) => existsSync(join(evalsDir, name, 'prompt.md')))
        .filter((name) => !only || name === only)
        .sort();
}

/** Load one shared prompt and all of its graders. */
export function readEvalCase(evalsDir, name) {
    const text = readFileSync(join(evalsDir, name, 'prompt.md'), 'utf8');
    const front = parseFrontMatter(text) ?? {};
    const prompt = text.replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n/, '').trim();
    const gradersDir = join(evalsDir, name, 'graders');
    const graders = readdirSync(gradersDir).filter((file) => file.endsWith('.md')).map((file) => {
        const graderText = readFileSync(join(gradersDir, file), 'utf8');
        return { file, ...parseFrontMatter(graderText), rubric: graderText.replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n/, '').trim() };
    });
    return { name, front, prompt, graders };
}

/** Read the authoritative JSONL trace written by the mock MCP server. */
export function readMockTrace(tracePath) {
    return readFileSync(tracePath, 'utf8').split('\n').filter(Boolean).map((line) => JSON.parse(line));
}

/** Keep the judge prompt identical across hosts; only the host invocation differs. */
export function judgePrompt(grader, answer) {
    return [
        'You are grading the answer of an AI assistant against a rubric. Do not use any tools.',
        'Reply with exactly one word: PASS or FAIL.',
        '', '## Rubric', grader.rubric,
        '', '## Answer', answer.slice(0, 12000),
    ].join('\n');
}
