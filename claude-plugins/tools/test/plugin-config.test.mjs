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
import { spawnSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, readdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

import { stagePlugin } from '../evals/run-with-claude.mjs';
import {
    checkCodexHookSurface,
    checkCodexMarketplace,
    checkEvals,
    CODEX_CATALOG_PATH,
    HOOK_SERVER,
    OPENCODE_MCP_TOOL_ACTION,
    PLUGIN_DIR,
    SCOPED_TOOL_PREFIX,
    checkHooks,
    checkManifest,
    checkOpenCodeAgent,
    checkOpenCodeConfig,
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
    assert.deepEqual(checkEvals(join(here, '..', 'evals'), tools, skillNames, agentNames), []);
});

test('mcp-tools-covered grades every MCP tool', () => {
    const gradersDir = join(here, '..', 'evals', 'mcp-tools-covered', 'graders');
    const gradedTools = new Set(readdirSync(gradersDir).map((file) =>
        parseFrontMatter(readFileSync(join(gradersDir, file), 'utf8')).tool));
    const missing = [...tools.keys()].filter((name) => !gradedTools.has(`${SCOPED_TOOL_PREFIX}${name}`));
    assert.deepEqual(missing, []);
});

test('eval staging combines the plugin and its external cases without changing the published plugin', () => {
    const tempDir = mkdtempSync(join(tmpdir(), 'spring-tools-eval-test-'));
    try {
        const stagedPlugin = join(tempDir, 'plugin');
        stagePlugin(stagedPlugin);
        assert.ok(existsSync(join(stagedPlugin, '.claude-plugin', 'plugin.json')));
        assert.ok(existsSync(join(stagedPlugin, 'skills', 'spring-review', 'SKILL.md')));
        assert.ok(existsSync(join(stagedPlugin, 'evals', 'validate-finds-spring-problems', 'prompt.md')));
        assert.ok(existsSync(join(stagedPlugin, 'evals', 'mocks', 'spring-tools-mcp', '_tools.json')));
        assert.ok(!existsSync(join(stagedPlugin, 'evals', 'results')));
        assert.ok(!existsSync(join(pluginDir, 'evals', 'validate-finds-spring-problems', 'prompt.md')));
    } finally {
        rmSync(tempDir, { recursive: true, force: true });
    }
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

test('the OpenCode adapter uses the V2 MCP and permission grammar', () => {
    const config = JSON.parse(readFileSync(join(pluginDir, 'opencode', 'opencode.jsonc'), 'utf8'));
    assert.deepEqual(checkOpenCodeConfig(config), []);
    const reviewer = readFileSync(join(pluginDir, 'opencode', 'agents', 'spring-reviewer.md'), 'utf8');
    assert.deepEqual(checkOpenCodeAgent('agents/spring-reviewer.md', reviewer), []);
    assert.equal(OPENCODE_MCP_TOOL_ACTION, 'spring-tools_*');
    assert.match(reviewer, /action: external_directory/);
});

test('negative control: deprecated OpenCode V1 grammar is detected', () => {
    const config = {
        mcp: {
            'spring-tools': { type: 'local', command: [], enabled: true, timeout: 120000 },
        },
    };
    const configErrors = checkOpenCodeConfig(config).join('\n');
    assert.match(configErrors, /V1 mcp\.spring-tools is deprecated/);
    assert.match(configErrors, /no mcp\.servers\.spring-tools entry/);

    const reviewer = `---
description: Reviews Spring Boot projects with Spring Tools diagnostics, bean wiring, endpoints and architecture. Read-only reviewer subagent.
mode: subagent
permission:
  read: allow
  "spring-tools_*": allow
---
Body
`;
    const agentErrors = checkOpenCodeAgent('agents/spring-reviewer.md', reviewer).join('\n');
    assert.match(agentErrors, /V1 'permission' map is deprecated/);
    assert.match(agentErrors, /no V2 'permissions' rule list/);
    assert.match(agentErrors, /must deny all actions/);
});

test('negative control: OpenCode reviewer permissions stay ordered and read-only', () => {
    const reviewer = readFileSync(join(pluginDir, 'opencode', 'agents', 'spring-reviewer.md'), 'utf8');
    const denyAll = '  - action: "*"\n    resource: "*"\n    effect: deny\n';
    const denyLast = reviewer.replace(denyAll, '').replace('---\nYou are a Spring Boot reviewer.', `${denyAll}---\nYou are a Spring Boot reviewer.`);
    assert.match(checkOpenCodeAgent('agents/spring-reviewer.md', denyLast).join('\n'), /first permission rule must deny all actions/);

    const editable = reviewer.replace('  - action: read\n', '  - action: edit\n    resource: "*"\n    effect: allow\n  - action: read\n');
    assert.match(checkOpenCodeAgent('agents/spring-reviewer.md', editable).join('\n'), /only read-only allow rules/);
});

test('the portable manifest explicitly disables Codex hook discovery', () => {
    const portable = JSON.parse(readFileSync(join(pluginDir, 'plugin.json'), 'utf8'));
    assert.deepEqual(checkCodexHookSurface(portable), []);
});

test('negative control: missing Codex hook override is detected', () => {
    const portable = JSON.parse(readFileSync(join(pluginDir, 'plugin.json'), 'utf8'));
    delete portable.extensions;
    assert.match(checkCodexHookSurface(portable).join('\n'), /extensions\.com\.openai must be defined/);

    portable.extensions = { 'com.openai': { hooks: './hooks/hooks.json' } };
    assert.match(checkCodexHookSurface(portable).join('\n'), /empty object so Codex does not load Claude-only hooks/);
});

test('the Codex catalog points at the published plugin directory', () => {
    const catalog = JSON.parse(readFileSync(join(repoRoot, CODEX_CATALOG_PATH), 'utf8'));
    assert.deepEqual(checkCodexMarketplace(catalog, repoRoot), []);
});

test('negative control: a broken Codex catalog is detected', () => {
    const catalog = JSON.parse(readFileSync(join(repoRoot, CODEX_CATALOG_PATH), 'utf8'));
    catalog.plugins[0].source.path = './somewhere-else';
    catalog.plugins[0].policy.authentication = 'NEVER';
    const errors = checkCodexMarketplace(catalog, repoRoot).join('\n');
    assert.match(errors, /source\.path must resolve to claude-plugins\/spring-tools/);
    assert.match(errors, /policy\.authentication must be ON_INSTALL or ON_USE/);

    delete catalog.interface;
    delete catalog.plugins[0].policy;
    delete catalog.plugins[0].category;
    const missingMetadata = checkCodexMarketplace(catalog, repoRoot).join('\n');
    assert.match(missingMetadata, /interface\.displayName is required/);
    assert.match(missingMetadata, /policy\.installation must be/);
    assert.match(missingMetadata, /category is required/);
});

test('generated reviewer adapters are current', () => {
    const result = spawnSync(process.execPath, [join(repoRoot, 'claude-plugins/tools/sync-agent-adapters.mjs'), '--check'], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
});
