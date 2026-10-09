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

// Checks the plugin configuration against the language server's MCP tools:
//   node agent-plugins/tools/check-plugin-config.mjs [--cross-host]
// Fails when a hook, skill or agent references an unknown tool, when a skill/agent is malformed,
// or when an MCP tool is unreachable through skills/hooks or lacks positive behavioral eval coverage.
// --cross-host also validates the Codex catalog, the Copilot and OpenCode adapters, and the portable manifests.

import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
    COPILOT_DIR,
    PLUGIN_DIR,
    checkAgent,
    checkAgentPluginManifest,
    checkCodexHookSurface,
    checkCodexMarketplace,
    checkCopilotAgent,
    CODEX_CATALOG_PATH,
    checkCopilotHooks,
    checkEvals,
    checkHooks,
    checkManifest,
    checkOpenCodeAgent,
    checkOpenCodeConfig,
    checkSkill,
    collectMcpTools,
    uncoveredTools,
} from './lib/plugin-config.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..');
const pluginDir = join(repoRoot, PLUGIN_DIR);
const crossHost = process.argv.includes('--cross-host');

const errors = [];
const tools = collectMcpTools(repoRoot);
if (tools.size === 0) {
    errors.push('no @Tool methods found in the language server sources');
}

const hooks = JSON.parse(readFileSync(join(pluginDir, 'hooks', 'hooks.json'), 'utf8'));
errors.push(...checkHooks(hooks, tools, repoRoot));

const skillsDir = join(pluginDir, 'skills');
const skillNames = readdirSync(skillsDir).filter((d) => existsSync(join(skillsDir, d, 'SKILL.md')));
const skillTexts = [];
for (const name of readdirSync(skillsDir)) {
    const file = join(skillsDir, name, 'SKILL.md');
    if (!existsSync(file)) {
        errors.push(`${name}: skill directory without SKILL.md`);
        continue;
    }
    const text = readFileSync(file, 'utf8');
    skillTexts.push(text);
    errors.push(...checkSkill(name, text, tools, skillNames));
}

const agentsDir = join(pluginDir, 'agents');
const agentFiles = existsSync(agentsDir) ? readdirSync(agentsDir).filter((f) => f.endsWith('.md')) : [];
for (const file of agentFiles) {
    errors.push(...checkAgent(file, readFileSync(join(agentsDir, file), 'utf8'), tools));
}

const sharedReviewSkill = readFileSync(join(skillsDir, 'spring-review', 'SKILL.md'), 'utf8');
const reviewBody = (text) => text.replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n/, '').trim();
const claudeReviewer = join(agentsDir, 'spring-reviewer.md');
if (!existsSync(claudeReviewer)) {
    errors.push('Claude reviewer: adapter is missing');
} else if (reviewBody(readFileSync(claudeReviewer, 'utf8')) !== reviewBody(sharedReviewSkill)) {
    errors.push('Claude reviewer: instructions differ from skills/spring-review/SKILL.md; run sync-agent-adapters.mjs --write');
}

if (crossHost) {
    const copilotAgentsDir = join(pluginDir, COPILOT_DIR, 'agents');
    const copilotAgentFiles = existsSync(copilotAgentsDir) ? readdirSync(copilotAgentsDir).filter((f) => f.endsWith('.agent.md')) : [];
    for (const file of agentFiles) {
        if (!copilotAgentFiles.includes(`${file.replace(/\.md$/, '')}.agent.md`)) {
            errors.push(`${COPILOT_DIR}/agents: no copy of agents/${file}, Copilot would fall back to the Claude front matter`);
        }
    }
    for (const file of copilotAgentFiles) {
        const claudeFile = join(agentsDir, file.replace(/\.agent\.md$/, '.md'));
        errors.push(...checkCopilotAgent(file, readFileSync(join(copilotAgentsDir, file), 'utf8'), existsSync(claudeFile) ? readFileSync(claudeFile, 'utf8') : undefined));
    }

    for (const [label, file] of [
        ['Copilot reviewer', join(copilotAgentsDir, 'spring-reviewer.agent.md')],
        ['OpenCode reviewer', join(pluginDir, 'opencode', 'agents', 'spring-reviewer.md')],
    ]) {
        if (!existsSync(file)) {
            errors.push(`${label}: adapter is missing`);
        } else if (reviewBody(readFileSync(file, 'utf8')) !== reviewBody(sharedReviewSkill)) {
            errors.push(`${label}: instructions differ from skills/spring-review/SKILL.md; run sync-agent-adapters.mjs --write`);
        }
    }

    const openCodeConfig = JSON.parse(readFileSync(join(pluginDir, 'opencode', 'opencode.jsonc'), 'utf8'));
    errors.push(...checkOpenCodeConfig(openCodeConfig));
    errors.push(...checkOpenCodeAgent('agents/spring-reviewer.md', readFileSync(join(pluginDir, 'opencode', 'agents', 'spring-reviewer.md'), 'utf8')));

    const copilotHooks = JSON.parse(readFileSync(join(pluginDir, COPILOT_DIR, 'hooks', 'hooks.json'), 'utf8'));
    errors.push(...checkCopilotHooks(copilotHooks, repoRoot));

    const codexCatalogPath = join(repoRoot, CODEX_CATALOG_PATH);
    if (!existsSync(codexCatalogPath)) {
        errors.push(`${CODEX_CATALOG_PATH}: Codex marketplace catalog is missing`);
    } else {
        errors.push(...checkCodexMarketplace(JSON.parse(readFileSync(codexCatalogPath, 'utf8')), repoRoot));
    }
}

const pluginJson = JSON.parse(readFileSync(join(pluginDir, '.claude-plugin', 'plugin.json'), 'utf8'));
const marketplace = JSON.parse(readFileSync(join(repoRoot, 'agent-plugins', '.claude-plugin', 'marketplace.json'), 'utf8'));
errors.push(...checkManifest(pluginJson, marketplace, repoRoot));
if (crossHost) {
    const portablePluginJson = JSON.parse(readFileSync(join(pluginDir, 'plugin.json'), 'utf8'));
    errors.push(...checkAgentPluginManifest(
        portablePluginJson,
        JSON.parse(readFileSync(join(pluginDir, 'mcp.json'), 'utf8')),
        pluginJson,
        repoRoot,
    ));
    errors.push(...checkCodexHookSurface(portablePluginJson));
}

const uncovered = uncoveredTools(tools, skillTexts, hooks);
if (uncovered.length) {
    errors.push(`MCP tools reachable through no skill and no hook: ${uncovered.join(', ')}`);
}

const evalsDir = join(here, 'evals');
errors.push(...checkEvals(evalsDir, tools, skillNames, agentFiles.map((f) => f.replace(/\.md$/, ''))));
const evalCases = existsSync(evalsDir) ? readdirSync(evalsDir).filter((d) => existsSync(join(evalsDir, d, 'prompt.md'))).length : 0;

if (errors.length) {
    for (const error of errors) {
        console.error(`ERROR ${error}`);
    }
    console.error(`plugin config FAILED: ${errors.length} problem(s)`);
    process.exit(1);
}
const agentSummary = crossHost ? `${agentFiles.length} agent(s) with Copilot copies` : `${agentFiles.length} agent(s)`;
console.log(`plugin config OK (${tools.size} tools, ${skillNames.length} skills, ${agentSummary}, ${evalCases} eval case(s), every tool covered by a skill or hook and behavioral eval)`);
