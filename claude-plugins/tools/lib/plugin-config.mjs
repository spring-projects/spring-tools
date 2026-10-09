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

// Consistency rules between the plugin configuration (manifests, hooks, skills,
// agents) and the MCP tools implemented by the language server. The plugin is loaded by two
// clients: Claude Code reads `.claude-plugin/plugin.json`, `hooks/hooks.json` and `agents/`,
// GitHub Copilot CLI reads the Agent Plugins 1.0 manifests (`plugin.json`, `mcp.json`) and the
// `com.github.copilot/` directory. Both sets must stay in sync.

import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { basename, join } from 'node:path';

export const MCP_SOURCES_DIR = 'headless-services/spring-boot-language-server/src/main/java/org/springframework/ide/vscode/boot/mcp';
export const PLUGIN_DIR = 'claude-plugins/spring-tools';
export const MCP_SERVER_NAME = 'spring-tools-mcp';
export const PLUGIN_NAME = 'spring-tools';
export const HOOK_SERVER = `plugin:${PLUGIN_NAME}:${MCP_SERVER_NAME}`;
export const SCOPED_TOOL_PREFIX = `mcp__plugin_${PLUGIN_NAME}_${MCP_SERVER_NAME}__`;
export const COPILOT_DIR = 'com.github.copilot';
export const PLUGIN_SCHEMA = 'https://agent-plugins.org/schemas/1.0.0/plugin.schema.json';
export const MCP_SCHEMA = 'https://agent-plugins.org/schemas/1.0.0/mcp.schema.json';

/**
 * Reads the `@Tool` annotated methods of the language server's MCP components.
 * @returns {Map<string, string[]>} tool name -> declared parameter names
 */
export function collectMcpTools(root) {
    const tools = new Map();
    const dir = join(root, MCP_SOURCES_DIR);
    if (!existsSync(dir)) {
        return tools;
    }
    for (const entry of readdirSync(dir)) {
        const file = join(dir, entry);
        if (!entry.endsWith('.java') || statSync(file).isDirectory()) {
            continue;
        }
        const text = readFileSync(file, 'utf8');
        let index = 0;
        while ((index = text.indexOf('@Tool(', index)) !== -1) {
            const rest = text.slice(index);
            const method = rest.match(/\n\s*public\s+[\w<>\[\],.? ]+?\s+(\w+)\s*\(/);
            if (method) {
                const start = index + method.index + method[0].length;
                const params = splitTopLevel(text.slice(start, matchingParen(text, start)));
                tools.set(method[1], params.map((p) => p.trim().match(/(\w+)$/)[1]));
            }
            index += '@Tool('.length;
        }
    }
    return tools;
}

/** Index of the `)` closing the parenthesis opened just before `start`, ignoring parens inside string literals. */
function matchingParen(text, start) {
    let depth = 1;
    let inString = false;
    for (let i = start; i < text.length; i++) {
        const c = text[i];
        if (inString) {
            if (c === '\\') {
                i++;
            } else if (c === '"') {
                inString = false;
            }
        } else if (c === '"') {
            inString = true;
        } else if (c === '(') {
            depth++;
        } else if (c === ')' && --depth === 0) {
            return i;
        }
    }
    throw new Error('unbalanced parentheses in MCP tool signature');
}

/** Splits a parameter list on commas that are not nested in parentheses, brackets or string literals. */
function splitTopLevel(params) {
    const result = [];
    let depth = 0;
    let inString = false;
    let current = '';
    for (let i = 0; i < params.length; i++) {
        const c = params[i];
        if (inString) {
            if (c === '\\') {
                current += c + params[++i];
                continue;
            }
            if (c === '"') {
                inString = false;
            }
        } else if (c === '"') {
            inString = true;
        } else if (c === '(' || c === '<' || c === '[') {
            depth++;
        } else if (c === ')' || c === '>' || c === ']') {
            depth--;
        } else if (c === ',' && depth === 0) {
            result.push(current);
            current = '';
            continue;
        }
        current += c;
    }
    if (current.trim()) {
        result.push(current);
    }
    return result;
}

/** Minimal YAML front matter reader for SKILL.md files (scalars and `- item` lists). */
export function parseFrontMatter(text) {
    const match = text.match(/^---\r?\n([\s\S]*?)\r?\n---/);
    if (!match) {
        return null;
    }
    const data = {};
    const lines = match[1].split(/\r?\n/);
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        const kv = line.match(/^([A-Za-z][\w-]*):\s*(.*)$/);
        if (!kv) {
            continue;
        }
        const [, key, rawValue] = kv;
        let value = rawValue.trim();
        if (value === '') {
            const items = [];
            while (i + 1 < lines.length && /^\s+-\s+/.test(lines[i + 1])) {
                items.push(unquote(lines[++i].replace(/^\s+-\s+/, '')));
            }
            data[key] = items;
        } else if (value.startsWith('[') && value.endsWith(']')) {
            data[key] = value.slice(1, -1).split(',').map((v) => unquote(v.trim())).filter(Boolean);
        } else {
            data[key] = unquote(value);
        }
    }
    return data;
}

function unquote(value) {
    return value.replace(/^"(.*)"$/, '$1').replace(/^'(.*)'$/, '$1');
}

function hookEntries(hooksJson) {
    const result = [];
    for (const [event, groups] of Object.entries(hooksJson.hooks ?? {})) {
        for (const group of groups) {
            for (const hook of group.hooks ?? []) {
                result.push({ event, matcher: group.matcher, hook });
            }
        }
    }
    return result;
}

/** Validates hooks.json against the MCP tool inventory (and, given the repo root, the plugin files); returns error strings. */
export function checkHooks(hooksJson, tools, root) {
    const errors = [];
    const entries = hookEntries(hooksJson);
    if (entries.length === 0) {
        errors.push('hooks.json declares no hooks');
    }
    for (const { event, hook } of entries) {
        const where = `${event} hook '${hook.if ?? hook.tool ?? hook.command}'`;
        if (hook.if !== undefined && !/^(Bash|PowerShell|Edit|Read|WebFetch|Skill|Agent|mcp__[\w-]+)\(.+\)$/.test(hook.if)) {
            errors.push(`${where}: 'if' must be a single permission rule like Edit(*.java) or Bash(git *)`);
        }
        if (hook.type === 'command') {
            if (typeof hook.command !== 'string' || !hook.command) {
                errors.push(`${where}: command hook without a command`);
            } else if (hook.command.includes('${CLAUDE_PLUGIN_ROOT}') && !Array.isArray(hook.args)) {
                errors.push(`${where}: path placeholders belong in exec-form 'args', not in a shell-form command`);
            }
            for (const arg of hook.args ?? []) {
                const prefix = '${CLAUDE_PLUGIN_ROOT}/';
                if (root && arg.startsWith(prefix) && !existsSync(join(root, PLUGIN_DIR, arg.slice(prefix.length)))) {
                    errors.push(`${where}: '${arg}' does not exist in the plugin directory`);
                }
            }
            continue;
        }
        if (hook.type !== 'mcp_tool') {
            continue;
        }
        if (event === 'SessionStart' || event === 'Setup') {
            errors.push(`${where}: Claude Code skips mcp_tool hooks on ${event} at launch (no MCP client context yet); use a command hook`);
        }
        if (hook.server !== HOOK_SERVER) {
            errors.push(`${where}: server must be '${HOOK_SERVER}', found '${hook.server}'`);
        }
        if (!tools.has(hook.tool)) {
            errors.push(`${where}: unknown MCP tool '${hook.tool}' (known: ${[...tools.keys()].sort().join(', ')})`);
            continue;
        }
        const params = tools.get(hook.tool);
        for (const key of Object.keys(hook.input ?? {})) {
            if (!params.includes(key)) {
                errors.push(`${where}: input '${key}' is not a parameter of ${hook.tool}(${params.join(', ')})`);
            }
        }
        for (const param of params) {
            if (!(hook.input ?? {})[param]) {
                errors.push(`${where}: parameter '${param}' of ${hook.tool} is not provided`);
            }
        }
    }
    return errors;
}

/** Validates one SKILL.md against its directory, the MCP tools and the other skills. */
export function checkSkill(dirName, text, tools, skillNames) {
    const errors = [];
    const front = parseFrontMatter(text);
    if (!front) {
        return [`${dirName}: SKILL.md has no YAML front matter`];
    }
    if (front.name !== dirName) {
        errors.push(`${dirName}: front matter name '${front.name}' differs from directory name`);
    }
    if (!front.description) {
        errors.push(`${dirName}: missing description`);
    }
    const allowed = Array.isArray(front['allowed-tools']) ? front['allowed-tools'] : (front['allowed-tools'] ? String(front['allowed-tools']).split(',').map((s) => s.trim()) : []);
    for (const entry of allowed) {
        if (entry.startsWith('mcp__')) {
            if (!entry.startsWith(SCOPED_TOOL_PREFIX)) {
                errors.push(`${dirName}: MCP tool '${entry}' is not scoped with '${SCOPED_TOOL_PREFIX}'`);
            } else if (!tools.has(entry.slice(SCOPED_TOOL_PREFIX.length))) {
                errors.push(`${dirName}: unknown MCP tool '${entry.slice(SCOPED_TOOL_PREFIX.length)}'`);
            }
        }
        // Skill(plugin:name) only matches an argument-less call; Skill(plugin:name *) also covers arguments.
        const skillRef = entry.match(/^Skill\(([^:)\s]+):([^)\s]+)( \*)?\)$/);
        if (skillRef) {
            if (skillRef[1] !== PLUGIN_NAME) {
                errors.push(`${dirName}: skill reference '${entry}' uses plugin '${skillRef[1]}'`);
            } else if (!skillNames.includes(skillRef[2])) {
                errors.push(`${dirName}: skill reference '${entry}' points to a missing skill`);
            }
        } else if (entry.startsWith('Skill(')) {
            errors.push(`${dirName}: skill reference '${entry}' must look like Skill(${PLUGIN_NAME}:<skill>) or Skill(${PLUGIN_NAME}:<skill> *)`);
        }
    }
    for (const match of text.matchAll(/`((?:get|file|refresh|find|capture|clear)[A-Z]\w*)`/g)) {
        if (!tools.has(match[1])) {
            errors.push(`${dirName}: body mentions MCP tool '${match[1]}' which does not exist`);
        }
    }
    return errors;
}

/** MCP tool names referenced by a skill's allowed-tools (scoped entries only). */
export function skillToolNames(text) {
    const front = parseFrontMatter(text);
    const allowed = Array.isArray(front?.['allowed-tools']) ? front['allowed-tools'] : (front?.['allowed-tools'] ? String(front['allowed-tools']).split(',').map((s) => s.trim()) : []);
    return allowed.filter((e) => e.startsWith(SCOPED_TOOL_PREFIX)).map((e) => e.slice(SCOPED_TOOL_PREFIX.length));
}

/**
 * Validates one agents/<name>.md file (Claude Code sub-agent). Agents shipped in a plugin need a
 * name matching the file, a description, and may only grant MCP tools of the plugin's own server.
 */
export function checkAgent(fileName, text, tools) {
    const errors = [];
    const expectedName = basename(fileName).replace(/\.md$/, '');
    const front = parseFrontMatter(text);
    if (!front) {
        return [`${fileName}: agent file has no YAML front matter`];
    }
    if (front.name !== expectedName) {
        errors.push(`${fileName}: front matter name '${front.name}' differs from file name '${expectedName}'`);
    }
    if (!front.description || String(front.description).length < 20) {
        errors.push(`${fileName}: missing or too short description (Claude uses it to decide when to delegate)`);
    }
    for (const key of ['hooks', 'mcpServers', 'permissionMode']) {
        if (front[key] !== undefined) {
            errors.push(`${fileName}: '${key}' is ignored for agents shipped in a plugin`);
        }
    }
    const granted = Array.isArray(front.tools) ? front.tools : (front.tools ? String(front.tools).split(',').map((s) => s.trim()) : []);
    for (const entry of granted) {
        if (!entry.startsWith('mcp__')) {
            continue;
        }
        if (entry === `${SCOPED_TOOL_PREFIX}*` || entry === SCOPED_TOOL_PREFIX.slice(0, -2)) {
            continue;
        }
        if (!entry.startsWith(SCOPED_TOOL_PREFIX)) {
            errors.push(`${fileName}: MCP tool '${entry}' is not scoped with '${SCOPED_TOOL_PREFIX}'`);
        } else if (!tools.has(entry.slice(SCOPED_TOOL_PREFIX.length))) {
            errors.push(`${fileName}: unknown MCP tool '${entry.slice(SCOPED_TOOL_PREFIX.length)}'`);
        }
    }
    for (const match of text.matchAll(/`((?:get|file|refresh|find|capture|clear)[A-Z]\w*)`/g)) {
        if (!tools.has(match[1])) {
            errors.push(`${fileName}: body mentions MCP tool '${match[1]}' which does not exist`);
        }
    }
    return errors;
}

/**
 * Every MCP tool the language server exposes must be reachable through a documented path: an
 * allowed-tools entry of some skill or an mcp_tool hook. Returns the tool names that are not.
 */
export function uncoveredTools(tools, skillTexts, hooksJson) {
    const covered = new Set();
    for (const text of skillTexts) {
        for (const name of skillToolNames(text)) {
            covered.add(name);
        }
    }
    for (const { hook } of hookEntries(hooksJson ?? {})) {
        if (hook.type === 'mcp_tool' && hook.tool) {
            covered.add(hook.tool);
        }
    }
    return [...tools.keys()].filter((name) => !covered.has(name)).sort();
}

/**
 * Consistency of the behavioral eval suite (claude plugin eval) with the rest of the plugin: every
 * mock answers a real tool, every case has a prompt and at least one grader, graders reference
 * only real tools/skills/agents, and every skill and agent has at least one case that checks it.
 * Returns error strings.
 */
export function checkEvals(evalsDir, tools, skillNames, agentNames) {
    const errors = [];
    if (!existsSync(evalsDir)) {
        return [`evals directory ${evalsDir} is missing`];
    }
    const mockDirs = [join(evalsDir, 'mocks', MCP_SERVER_NAME)];
    const cases = readdirSync(evalsDir).filter((d) => !['mocks', 'results'].includes(d) && statSync(join(evalsDir, d)).isDirectory());
    if (cases.length === 0) {
        errors.push('evals: no cases');
    }
    const coveredSkills = new Set();
    const coveredAgents = new Set();
    for (const name of cases) {
        const caseDir = join(evalsDir, name);
        const promptFile = join(caseDir, 'prompt.md');
        if (!existsSync(promptFile)) {
            errors.push(`evals/${name}: prompt.md is missing`);
            continue;
        }
        const prompt = readFileSync(promptFile, 'utf8');
        if (!prompt.replace(/^---[\s\S]*?\n---/, '').trim()) {
            errors.push(`evals/${name}: prompt body is empty`);
        }
        const gradersDir = join(caseDir, 'graders');
        const graders = existsSync(gradersDir) ? readdirSync(gradersDir).filter((f) => f.endsWith('.md')) : [];
        if (graders.length === 0) {
            errors.push(`evals/${name}: no graders`);
        }
        for (const grader of graders) {
            const graderText = readFileSync(join(gradersDir, grader), 'utf8');
            const data = parseFrontMatter(graderText);
            if (!data?.type) {
                errors.push(`evals/${name}/graders/${grader}: no type in front matter`);
                continue;
            }
            const graderWhere = `evals/${name}/graders/${grader}`;
            if (!['tool_used', 'regex', 'llm'].includes(data.type)) {
                errors.push(`${graderWhere}: unsupported grader type '${data.type}'`);
                continue;
            }
            const weight = Number(data.weight ?? 1);
            if (!Number.isFinite(weight) || weight <= 0) {
                errors.push(`${graderWhere}: weight must be a positive number`);
            }
            if (data.type === 'regex') {
                if (typeof data.pattern !== 'string' || !data.pattern) {
                    errors.push(`${graderWhere}: regex grader needs a non-empty pattern`);
                } else {
                    try { new RegExp(data.pattern, data.flags ?? ''); } catch (error) {
                        errors.push(`${graderWhere}: invalid regex (${error.message})`);
                    }
                }
            }
            if (data.type === 'llm' && !graderText.replace(/^---[\s\S]*?\r?\n---\r?\n/, '').trim()) {
                errors.push(`${graderWhere}: llm grader rubric is empty`);
            }
            if (data.type !== 'tool_used') {
                continue;
            }
            const tool = data.tool ?? '';
            if (!tool) {
                errors.push(`${graderWhere}: tool_used grader needs a tool`);
            }
            if (tool.startsWith(SCOPED_TOOL_PREFIX) && !tools.has(tool.slice(SCOPED_TOOL_PREFIX.length))) {
                errors.push(`${graderWhere}: MCP tool '${tool}' does not exist`);
            }
            if (data.input_match !== undefined) {
                try { new RegExp(data.input_match); } catch (error) {
                    errors.push(`${graderWhere}: invalid input_match regex (${error.message})`);
                }
            }
            const min = data.min === undefined ? 1 : Number(data.min);
            const max = data.max === undefined ? Number.POSITIVE_INFINITY : Number(data.max);
            if (!Number.isFinite(min) || min < 0 || Number.isNaN(max) || max < min) {
                errors.push(`${graderWhere}: min/max must define non-negative occurrence bounds with max >= min`);
            }
            if (tool === 'Skill' && data.input_match) {
                const skill = data.input_match.match(/\)\?([\w-]+)"/)?.[1];
                if (skill && !skillNames.includes(skill)) {
                    errors.push(`evals/${name}/graders/${grader}: skill '${skill}' does not exist`);
                }
                if (skill && data.max !== '0') {
                    coveredSkills.add(skill);
                }
            }
            if (tool === 'Agent' && data.input_match) {
                if (!agentNames.includes(data.input_match)) {
                    errors.push(`evals/${name}/graders/${grader}: agent '${data.input_match}' does not exist`);
                }
                coveredAgents.add(data.input_match);
            }
        }
        const caseMocks = join(caseDir, 'mocks', MCP_SERVER_NAME);
        if (existsSync(caseMocks)) {
            mockDirs.push(caseMocks);
        }
    }
    for (const dir of mockDirs) {
        if (!existsSync(dir)) {
            errors.push(`evals: mock directory ${dir} is missing`);
            continue;
        }
        for (const file of readdirSync(dir).filter((f) => f.endsWith('.md'))) {
            const tool = basename(file, '.md');
            if (!tools.has(tool)) {
                errors.push(`evals: mock ${file} answers unknown tool '${tool}'`);
            }
        }
    }
    const suiteMocks = mockDirs[0];
    if (existsSync(suiteMocks)) {
        const mocked = new Set(readdirSync(suiteMocks).filter((f) => f.endsWith('.md')).map((f) => basename(f, '.md')));
        const unmocked = [...tools.keys()].filter((t) => !mocked.has(t)).sort();
        if (unmocked.length) {
            errors.push(`evals: tools without a suite-wide mock (re-run claude-plugins/tools/evals/record-mocks.mjs): ${unmocked.join(', ')}`);
        }
        const toolsJson = join(suiteMocks, '_tools.json');
        if (!existsSync(toolsJson)) {
            errors.push('evals: mocks/_tools.json (saved tools/list) is missing');
        } else {
            const listed = new Set((JSON.parse(readFileSync(toolsJson, 'utf8')).tools ?? []).map((t) => t.name));
            const stale = [...tools.keys()].filter((t) => !listed.has(t)).concat([...listed].filter((t) => !tools.has(t))).sort();
            if (stale.length) {
                errors.push(`evals: mocks/_tools.json differs from the language server's tools (re-record): ${stale.join(', ')}`);
            }
        }
    }
    for (const skill of skillNames.filter((s) => !coveredSkills.has(s))) {
        errors.push(`evals: skill '${skill}' has no case asserting it fires`);
    }
    for (const agent of agentNames.filter((a) => !coveredAgents.has(a))) {
        errors.push(`evals: agent '${agent}' has no case asserting it is used`);
    }
    return errors;
}

/** Validates plugin.json and the local marketplace; returns error strings. */
export function checkManifest(pluginJson, marketplaceJson, root) {
    const errors = [];
    if (pluginJson.name !== PLUGIN_NAME) {
        errors.push(`plugin.json name must be '${PLUGIN_NAME}'`);
    }
    if (!/^\d+\.\d+\.\d+(?:-[\w.]+)?$/.test(pluginJson.version ?? '')) {
        errors.push(`plugin.json version '${pluginJson.version}' is not X.Y.Z[-qualifier]`);
    }
    const server = pluginJson.mcpServers?.[MCP_SERVER_NAME];
    if (!server) {
        errors.push(`plugin.json must declare mcpServers.${MCP_SERVER_NAME}`);
    } else {
        if (server.command !== 'node') {
            errors.push(`mcpServers.${MCP_SERVER_NAME}.command must be 'node'`);
        }
        const launcher = (server.args ?? []).find((a) => a.endsWith('/launcher.js'));
        if (!launcher || !launcher.startsWith('${CLAUDE_PLUGIN_ROOT}/')) {
            errors.push(`mcpServers.${MCP_SERVER_NAME}.args must launch \${CLAUDE_PLUGIN_ROOT}/launcher.js`);
        } else if (!existsSync(join(root, PLUGIN_DIR, 'launcher.js'))) {
            errors.push('launcher.js is missing from the plugin directory');
        }
    }
    const entry = (marketplaceJson.plugins ?? []).find((p) => p.name === PLUGIN_NAME);
    if (!entry) {
        errors.push(`marketplace.json has no plugin named '${PLUGIN_NAME}'`);
    } else if (typeof entry.source !== 'string' || !existsSync(join(root, 'claude-plugins', entry.source, '.claude-plugin', 'plugin.json'))) {
        errors.push(`marketplace.json source '${entry.source}' does not point at a plugin directory`);
    }
    return errors;
}

// The Agent Plugins 1.0 plugin schema is closed: anything else (for example displayName) is rejected.
const AGENT_PLUGIN_FIELDS = ['$schema', 'name', 'version', 'description', 'author', 'homepage', 'repository', 'license', 'keywords', 'extensions'];

/**
 * Validates the Agent Plugins 1.0 manifests that GitHub Copilot CLI reads (plugin.json and
 * mcp.json in the plugin root) and keeps their shared fields in sync with the Claude Code manifest.
 */
export function checkAgentPluginManifest(pluginJson, mcpJson, claudeJson, root) {
    const errors = [];
    if (pluginJson.$schema !== PLUGIN_SCHEMA) {
        errors.push(`plugin.json $schema must be '${PLUGIN_SCHEMA}'`);
    }
    for (const key of Object.keys(pluginJson)) {
        if (!AGENT_PLUGIN_FIELDS.includes(key)) {
            errors.push(`plugin.json: '${key}' is not allowed by the Agent Plugins schema (allowed: ${AGENT_PLUGIN_FIELDS.join(', ')})`);
        }
    }
    if (pluginJson.name !== PLUGIN_NAME) {
        errors.push(`plugin.json name must be '${PLUGIN_NAME}'`);
    }
    if (typeof pluginJson.author !== 'object' || !pluginJson.author?.name) {
        errors.push('plugin.json author must be an object with a name');
    }
    for (const key of ['name', 'version', 'description']) {
        if (pluginJson[key] !== claudeJson[key]) {
            errors.push(`plugin.json ${key} differs from .claude-plugin/plugin.json ('${pluginJson[key]}' vs '${claudeJson[key]}')`);
        }
    }

    if (mcpJson.$schema !== MCP_SCHEMA) {
        errors.push(`mcp.json $schema must be '${MCP_SCHEMA}'`);
    }
    for (const key of Object.keys(mcpJson)) {
        if (key !== '$schema' && key !== 'mcpServers') {
            errors.push(`mcp.json: unexpected key '${key}'`);
        }
    }
    const server = mcpJson.mcpServers?.[MCP_SERVER_NAME];
    if (!server) {
        errors.push(`mcp.json must declare mcpServers.${MCP_SERVER_NAME}`);
        return errors;
    }
    if (server.type !== 'stdio') {
        errors.push(`mcp.json ${MCP_SERVER_NAME}.type must be 'stdio'`);
    }
    if (server.command !== 'node') {
        errors.push(`mcp.json ${MCP_SERVER_NAME}.command must be 'node' (the command is a single token and is not placeholder-expanded)`);
    }
    const launcher = (server.args ?? []).find((a) => a.endsWith('/launcher.js'));
    if (!launcher || !launcher.startsWith('${PLUGIN_ROOT}/')) {
        errors.push(`mcp.json ${MCP_SERVER_NAME}.args must launch \${PLUGIN_ROOT}/launcher.js (only \${PLUGIN_ROOT} and \${PLUGIN_DATA} are expanded)`);
    } else if (root && !existsSync(join(root, PLUGIN_DIR, 'launcher.js'))) {
        errors.push('launcher.js is missing from the plugin directory');
    }
    for (const key of Object.keys(server.env ?? {})) {
        if (key === 'PLUGIN_ROOT' || key === 'PLUGIN_DATA') {
            errors.push(`mcp.json ${MCP_SERVER_NAME}.env must not redefine '${key}' (the client provides it)`);
        }
    }
    return errors;
}

// Copilot hook events are lowerCamelCase; the PascalCase spellings are the Claude Code ones.
const COPILOT_HOOK_EVENTS = ['sessionStart', 'userPromptSubmit', 'preToolUse', 'postToolUse', 'sessionEnd'];

/**
 * Validates com.github.copilot/hooks/hooks.json. Copilot only understands command hooks - a Claude
 * `mcp_tool` hook or a PascalCase event name makes it reject the whole file at startup.
 */
export function checkCopilotHooks(hooksJson, root) {
    const errors = [];
    if (hooksJson.version !== 1) {
        errors.push(`${COPILOT_DIR}/hooks/hooks.json: version must be 1`);
    }
    const events = Object.entries(hooksJson.hooks ?? {});
    if (events.length === 0) {
        errors.push(`${COPILOT_DIR}/hooks/hooks.json declares no hooks`);
    }
    for (const [event, entries] of events) {
        if (!COPILOT_HOOK_EVENTS.includes(event)) {
            errors.push(`${COPILOT_DIR}/hooks/hooks.json: unknown event '${event}' (expected one of ${COPILOT_HOOK_EVENTS.join(', ')})`);
            continue;
        }
        if (!Array.isArray(entries)) {
            errors.push(`${COPILOT_DIR}/hooks/hooks.json: '${event}' must be an array of hooks`);
            continue;
        }
        for (const hook of entries) {
            const where = `${COPILOT_DIR}/hooks/hooks.json ${event}`;
            if (hook.type !== 'command') {
                errors.push(`${where}: type '${hook.type}' is not supported, Copilot only runs 'command' hooks`);
                continue;
            }
            if (typeof hook.bash !== 'string' || !hook.bash) {
                errors.push(`${where}: command hook without a 'bash' command`);
            }
            if (typeof hook.powershell !== 'string' || !hook.powershell) {
                errors.push(`${where}: command hook without a 'powershell' command, it would not run on Windows`);
            }
            for (const command of [hook.bash, hook.powershell]) {
                for (const match of String(command ?? '').matchAll(/\$(?:env:)?PLUGIN_ROOT\/([\w./-]+)/g)) {
                    if (root && !existsSync(join(root, PLUGIN_DIR, match[1]))) {
                        errors.push(`${where}: '${match[1]}' does not exist in the plugin directory`);
                    }
                }
            }
        }
    }
    return errors;
}

/**
 * Validates the Copilot copy of an agent against the Claude Code original: same instructions, and
 * front matter restricted to supported fields. Copilot custom agents support a `tools` allowlist.
 */
export function checkCopilotAgent(fileName, copilotText, claudeText) {
    const errors = [];
    const expectedName = basename(fileName).replace(/\.agent\.md$/, '');
    const front = parseFrontMatter(copilotText);
    if (!front) {
        return [`${COPILOT_DIR}/agents/${fileName}: agent file has no YAML front matter`];
    }
    const where = `${COPILOT_DIR}/agents/${fileName}`;
    if (front.name !== expectedName) {
        errors.push(`${where}: front matter name '${front.name}' differs from file name '${expectedName}'`);
    }
    for (const key of Object.keys(front)) {
        if (!['name', 'description', 'tools'].includes(key)) {
            errors.push(`${where}: '${key}' is not supported by Copilot custom agents`);
        }
    }
    if (front.tools !== undefined
        && !(typeof front.tools === 'string' || (Array.isArray(front.tools) && front.tools.every((tool) => typeof tool === 'string')))) {
        errors.push(`${where}: 'tools' must be a string or a list of strings`);
    }
    if (claudeText === undefined) {
        errors.push(`${where}: no matching agents/${expectedName}.md for Claude Code`);
        return errors;
    }
    const claudeFront = parseFrontMatter(claudeText);
    if (front.description !== claudeFront?.description) {
        errors.push(`${where}: description differs from agents/${expectedName}.md`);
    }
    if (agentBody(copilotText) !== agentBody(claudeText)) {
        errors.push(`${where}: instructions differ from agents/${expectedName}.md, the two copies must stay identical`);
    }
    return errors;
}

/** The instructions of an agent file, i.e. everything below the front matter. */
function agentBody(text) {
    return text.replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n/, '').trim();
}
