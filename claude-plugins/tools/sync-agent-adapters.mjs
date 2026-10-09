#!/usr/bin/env node
/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

// Regenerates host-specific reviewer wrappers from the portable spring-review skill.
// Run with --write after changing skills/spring-review/SKILL.md.

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const plugin = join(here, '..', 'spring-tools');
const write = process.argv.includes('--write');

function splitFrontMatter(text, file) {
    const match = text.match(/^(---\r?\n[\s\S]*?\r?\n---\r?\n)([\s\S]*)$/);
    if (!match) throw new Error(`${file} has no YAML front matter`);
    return { frontMatter: match[1], body: match[2].trim() };
}

const skill = splitFrontMatter(readFileSync(join(plugin, 'skills/spring-review/SKILL.md'), 'utf8'), 'spring-review/SKILL.md');
const claudeFile = join(plugin, 'agents/spring-reviewer.md');
const copilotFile = join(plugin, 'com.github.copilot/agents/spring-reviewer.agent.md');
const openCodeFile = join(plugin, 'opencode/agents/spring-reviewer.md');
const claude = splitFrontMatter(readFileSync(claudeFile, 'utf8'), 'agents/spring-reviewer.md');
const copilot = splitFrontMatter(readFileSync(copilotFile, 'utf8'), 'Copilot spring-reviewer.agent.md');

const openCodeFrontMatter = `---
description: Reviews Spring Boot projects with Spring Tools diagnostics, bean wiring, endpoints and architecture. Read-only reviewer subagent.
mode: subagent
permission:
  read: allow
  glob: allow
  grep: allow
  edit: deny
  bash: deny
  "spring-tools_*": allow
---
`;

const outputs = [
    [claudeFile, claude.frontMatter, skill.body],
    [copilotFile, copilot.frontMatter, skill.body],
    [openCodeFile, openCodeFrontMatter, skill.body],
];
const mismatches = [];
for (const [file, frontMatter, body] of outputs) {
    const expected = `${frontMatter}${body}\n`;
    let actual;
    try { actual = readFileSync(file, 'utf8'); } catch { actual = ''; }
    if (actual !== expected) {
        if (write) {
            writeFileSync(file, expected);
        } else {
            mismatches.push(file.slice(plugin.length + 1));
        }
    }
}
if (mismatches.length) {
    console.error(`reviewer adapters drifted from skills/spring-review/SKILL.md: ${mismatches.join(', ')}`);
    process.exit(1);
}
console.log(write ? 'reviewer adapters generated' : 'reviewer adapters match shared spring-review skill');
