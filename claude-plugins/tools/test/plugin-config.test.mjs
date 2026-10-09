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
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

import {
    checkEvals,
    HOOK_SERVER,
    PLUGIN_DIR,
    SCOPED_TOOL_PREFIX,
    checkHooks,
    checkManifest,
    checkSkill,
    collectMcpTools,
    parseFrontMatter,
} from '../lib/plugin-config.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..', '..');
const pluginDir = join(repoRoot, PLUGIN_DIR);
const tools = collectMcpTools(repoRoot);
const skillsDir = join(pluginDir, 'skills');
const skillNames = readdirSync(skillsDir);

test('the language server exposes the MCP tools the plugin relies on', () => {
    for (const name of ['getProjectList', 'getProjectDiagnostics', 'fileChanged', 'fileDeleted', 'refreshWorkspace', 'getSpringBootVersion']) {
        assert.ok(tools.has(name), `MCP tool ${name} not found in the language server sources`);
    }
    assert.deepEqual(tools.get('fileChanged'), ['filePath']);
    assert.deepEqual(tools.get('getProjectDiagnostics'), ['projectName']);
});

test('checkHooks accepts an existing plugin MCP tool for a valid hook', () => {
    const hooks = {
        hooks: {
            PostToolUse: [{
                matcher: 'Edit',
                hooks: [{
                    type: 'mcp_tool',
                    if: 'Edit(*.java)',
                    server: HOOK_SERVER,
                    tool: 'fileChanged',
                    input: { filePath: '${tool_input.file_path}' },
                }],
            }],
        },
    };
    assert.deepEqual(checkHooks(hooks, tools), []);
});

test('every skill has a SKILL.md whose name matches its directory and whose tools exist', () => {
    for (const dirName of skillNames) {
        const file = join(skillsDir, dirName, 'SKILL.md');
        assert.ok(existsSync(file), `${dirName} has no SKILL.md`);
        assert.deepEqual(checkSkill(dirName, readFileSync(file, 'utf8'), tools, skillNames), [], dirName);
    }
});

test('every MCP tool has positive behavioral eval coverage', () => {
    const agentNames = readdirSync(join(pluginDir, 'agents'))
        .filter((file) => file.endsWith('.md'))
        .map((file) => file.replace(/\.md$/, ''));
    assert.deepEqual(checkEvals(join(pluginDir, 'evals'), tools, skillNames, agentNames), []);
});

test('the quickfix skill reads explanations by diagnostic code and may look up the Spring Boot version', () => {
    const text = readFileSync(join(pluginDir, 'skills', 'quickfix', 'SKILL.md'), 'utf8');
    const front = parseFrontMatter(text);
    assert.deepEqual(front.arguments, ['code', 'file', 'range']);
    assert.ok(text.includes('${CLAUDE_PLUGIN_ROOT}/explanations/<CODE>.md'));
    assert.ok(text.includes('`../../explanations/<CODE>.md` relative to this skill directory'));
    assert.ok(!/\$ARGUMENTS\[/.test(text), 'quickfix should use the named placeholders only');
    assert.ok(front['allowed-tools'].includes(`${SCOPED_TOOL_PREFIX}getSpringBootVersion`));
});

test('the validate skill pre-approves quickfix invocations that carry arguments', () => {
    const front = parseFrontMatter(readFileSync(join(pluginDir, 'skills', 'validate', 'SKILL.md'), 'utf8'));
    assert.ok(front['allowed-tools'].includes('Skill(spring-tools:quickfix *)'));
    assert.ok(!front['allowed-tools'].includes('Skill(spring-tools:quickfix)'), 'the exact-match form only covers an argument-less call');
});

test('the create-spring-boot-project skill only grants the start.spring.io lookup and registers the new project', () => {
    const text = readFileSync(join(pluginDir, 'skills', 'create-spring-boot-project', 'SKILL.md'), 'utf8');
    const allowed = parseFrontMatter(text)['allowed-tools'];
    assert.ok(allowed.includes('Bash(curl -sS https://start.spring.io)'));
    assert.ok(!allowed.some((rule) => /^Bash\(curl -sS \*\)$/.test(rule)), 'no blanket curl grant');
    assert.ok(text.includes('curl -sS https://start.spring.io | grep'), 'the body must use exactly the granted lookup command');
    assert.ok(allowed.includes(`${SCOPED_TOOL_PREFIX}refreshWorkspace`));
    assert.match(text, /refreshWorkspace/);
});

test('plugin.json and the local marketplace are consistent with the plugin directory', () => {
    const pluginJson = JSON.parse(readFileSync(join(pluginDir, '.claude-plugin', 'plugin.json'), 'utf8'));
    const marketplace = JSON.parse(readFileSync(join(repoRoot, 'claude-plugins', '.claude-plugin', 'marketplace.json'), 'utf8'));
    assert.deepEqual(checkManifest(pluginJson, marketplace, repoRoot), []);
});

test('negative control: a misspelled MCP tool name in a hook is detected', () => {
    const hooks = JSON.parse(readFileSync(join(here, 'fixtures', 'bad-hooks.json'), 'utf8'));
    const errors = checkHooks(hooks, tools, repoRoot);
    assert.match(errors.join('\n'), /unknown MCP tool 'fileChangd'/);
    assert.match(errors.join('\n'), /server must be 'plugin:spring-tools:spring-tools-mcp'/);
    assert.match(errors.join('\n'), /input 'path' is not a parameter of fileChanged\(filePath\)/);
    assert.match(errors.join('\n'), /skips mcp_tool hooks on SessionStart/);
    assert.match(errors.join('\n'), /does not exist in the plugin directory/);
    assert.match(errors.join('\n'), /path placeholders belong in exec-form 'args'/);
});

test('negative control: a skill with an unscoped or unknown tool and a wrong name is detected', () => {
    const text = readFileSync(join(here, 'fixtures', 'bad-skill.md'), 'utf8');
    const errors = checkSkill('validate', text, tools, skillNames);
    assert.match(errors.join('\n'), /name 'validat' differs from directory name/);
    assert.match(errors.join('\n'), /MCP tool 'mcp__spring-tools-mcp__getProjectList' is not scoped/);
    assert.match(errors.join('\n'), /unknown MCP tool 'getDiagnostics'/);
    assert.match(errors.join('\n'), /skill reference 'Skill\(spring-tools:nope\)' points to a missing skill/);
    assert.match(errors.join('\n'), /skill reference 'Skill\(quickfix\)' must look like/);
    assert.match(errors.join('\n'), /body mentions MCP tool 'getDiagnostics' which does not exist/);
});
