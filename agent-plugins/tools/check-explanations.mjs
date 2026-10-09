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

// Verifies the Claude plugin explanation playbooks against the language server's
// diagnostic codes: coverage (code <-> file), structure, cross-references, the
// coverage table in research-notes and, on request, that every documentation
// link resolves.

import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

// Directories holding the `*ProblemType` enums whose constants are diagnostic codes. The
// version-validation enum is deliberately absent: its constants are preference keys only and
// its diagnostics all carry BOOT_VERSION_VALIDATION_CODE (see VERSION_VALIDATOR_FILE).
export const PROBLEM_TYPES_DIRS = [
    'headless-services/spring-boot-language-server/src/main/java/org/springframework/ide/vscode/boot/java',
    'headless-services/spring-boot-language-server/src/main/java/org/springframework/ide/vscode/boot/properties/reconcile',
    'headless-services/spring-boot-language-server/src/main/java/org/springframework/ide/vscode/boot/yaml/reconcile',
];
export const VERSION_VALIDATOR_FILE = 'headless-services/spring-boot-language-server/src/main/java/org/springframework/ide/vscode/boot/validation/generations/AbstractDiagnosticValidator.java';
// The application-YAML reconciler flattens `<<` merge keys with commons-yaml's NodeMergeSupport, which
// reports malformed merge values with the generic YamlSchemaProblems.SCHEMA_PROBLEM code instead of an
// ApplicationYamlProblemType constant. The code is only collected while that dependency exists.
export const YAML_AST_RECONCILER_FILE = 'headless-services/spring-boot-language-server/src/main/java/org/springframework/ide/vscode/boot/yaml/reconcile/ApplicationYamlASTReconciler.java';
export const YAML_SCHEMA_PROBLEMS_FILE = 'headless-services/commons/commons-yaml/src/main/java/org/springframework/ide/vscode/commons/yaml/reconcile/YamlSchemaProblems.java';
export const EXPLANATIONS_DIR = 'agent-plugins/spring-tools/explanations';
export const TODO_FILE = 'agent-plugins/research-notes/TODO_quickfixes.md';

const DOC_LINK = /https:\/\/(?:docs\.spring\.io|spring\.io|github\.com\/spring-projects)\//;
const URL_PATTERN = /https?:\/\/[^\s)>\]"'`]+/g;
const CROSS_REF = /`([A-Z][A-Z0-9_]{3,})`/g;
const LINK_HOSTS = new Set(['docs.spring.io', 'spring.io', 'enterprise.spring.io', 'github.com', 'docs.oracle.com', 'openjdk.org', 'yaml.org', 'raw.githubusercontent.com']);

function walk(dir, predicate, out = []) {
    if (!existsSync(dir)) {
        return out;
    }
    for (const entry of readdirSync(dir)) {
        const full = join(dir, entry);
        if (statSync(full).isDirectory()) {
            walk(full, predicate, out);
        } else if (predicate(entry)) {
            out.push(full);
        }
    }
    return out;
}

/** Diagnostic codes the language server can report for Java sources and configuration files, sorted. */
export function collectCodes(root) {
    const codes = new Set();
    const files = PROBLEM_TYPES_DIRS.flatMap((dir) => walk(join(root, dir), (n) => n.endsWith('ProblemType.java')));
    for (const file of files) {
        const text = readFileSync(file, 'utf8');
        if (!/enum\s+\w+\s+implements\s+ProblemType/.test(text)) {
            continue;
        }
        for (const match of text.matchAll(/^\s+([A-Z][A-Z0-9_]+)\(/gm)) {
            codes.add(match[1]);
        }
    }
    const validator = join(root, VERSION_VALIDATOR_FILE);
    if (existsSync(validator)) {
        const match = readFileSync(validator, 'utf8').match(/BOOT_VERSION_VALIDATION_CODE\s*=\s*"([A-Z0-9_]+)"/);
        if (match) {
            codes.add(match[1]);
        }
    }
    const yamlReconciler = join(root, YAML_AST_RECONCILER_FILE);
    const yamlSchemaProblems = join(root, YAML_SCHEMA_PROBLEMS_FILE);
    if (existsSync(yamlReconciler) && existsSync(yamlSchemaProblems)
        && /\bNodeMergeSupport\b/.test(readFileSync(yamlReconciler, 'utf8'))) {
        const match = readFileSync(yamlSchemaProblems, 'utf8').match(/SCHEMA_PROBLEM\s*=\s*problemType\(\s*"([A-Za-z0-9_]+)"/);
        if (match) {
            codes.add(match[1]);
        }
    }
    return [...codes].sort();
}

/** Playbook codes present on disk (file names without .md), sorted. */
export function collectPlaybooks(root) {
    const dir = join(root, EXPLANATIONS_DIR);
    if (!existsSync(dir)) {
        return [];
    }
    return readdirSync(dir).filter((n) => n.endsWith('.md')).map((n) => n.slice(0, -3)).sort();
}

/** Structural rules for one playbook; returns human readable error strings. */
export function checkPlaybook(code, text, knownCodes, playbooks) {
    const errors = [];
    const lines = text.split(/\r?\n/);
    const firstLine = lines.find((l) => l.trim().length > 0) ?? '';
    if (firstLine.trim() !== '## Explanations') {
        errors.push(`${code}: must start with '## Explanations' (found '${firstLine.trim().slice(0, 40)}')`);
    }
    if (!lines.some((l) => l.trim() === '## Fixes')) {
        errors.push(`${code}: missing '## Fixes' section`);
    }
    const fences = lines.filter((l) => l.trimStart().startsWith('```')).length;
    if (fences % 2 !== 0) {
        errors.push(`${code}: unbalanced code fences (${fences})`);
    }
    if (!DOC_LINK.test(text)) {
        errors.push(`${code}: no link to docs.spring.io, spring.io or github.com/spring-projects`);
    }
    const prose = text.replace(/```[\s\S]*?```/g, '').replace(/`[^`\n]*`/g, '');
    if (/(^|\s)(TODO|FIXME)\s*:|[[(<](TODO|TBD|FIXME)[\])>]|\bTBD\b/m.test(prose)) {
        errors.push(`${code}: contains placeholder text (TODO:/TBD/FIXME:) outside code spans`);
    }
    for (const match of text.matchAll(CROSS_REF)) {
        const ref = match[1];
        if (ref !== code && knownCodes.has(ref) && !playbooks.has(ref)) {
            errors.push(`${code}: references code ${ref} which has no playbook`);
        }
    }
    return errors;
}

/** Parses the coverage table: code -> true (has playbook mark) / false. */
export function parseTodoTable(text) {
    const result = new Map();
    for (const line of text.split(/\r?\n/)) {
        const match = line.match(/^\|\s*`([A-Za-z][A-Za-z0-9_]+)`\s*\|.*\|\s*(✅|❌)\s*\|\s*$/);
        if (match) {
            result.set(match[1], match[2] === '✅');
        }
    }
    return result;
}

function duplicateTodoRows(text) {
    const seen = new Set();
    const duplicates = new Set();
    for (const line of text.split(/\r?\n/)) {
        const match = line.match(/^\|\s*`([A-Za-z][A-Za-z0-9_]+)`\s*\|.*\|\s*(✅|❌)\s*\|\s*$/);
        if (!match) continue;
        if (seen.has(match[1])) duplicates.add(match[1]);
        seen.add(match[1]);
    }
    return [...duplicates].sort();
}

export function extractUrls(text) {
    const urls = new Set();
    // URLs inside examples are code, not references readers should follow.
    const prose = text.replace(/^```[\s\S]*?^```\s*/gm, '').replace(/`[^`]*`/g, '');
    for (const match of prose.matchAll(URL_PATTERN)) {
        let url = match[0].replace(/[).,;:]+$/, '');
        url = url.split('#')[0];
        urls.add(url);
    }
    return [...urls];
}

/** Only check links to the documentation hosts maintained by Spring and its project repos. */
export function allowedDocumentationUrl(value) {
    try {
        const url = new URL(value);
        const host = url.hostname.toLowerCase();
        if (url.protocol !== 'https:' || !LINK_HOSTS.has(host)) return false;
        if (host === 'github.com') return url.pathname.startsWith('/spring-projects/');
        if (host === 'raw.githubusercontent.com') return url.pathname.startsWith('/spring-projects/spring-security/');
        if (host === 'openjdk.org') return url.pathname.startsWith('/jeps/');
        if (host === 'yaml.org') return url.pathname.startsWith('/spec/') || url.pathname.startsWith('/type/');
        if (host === 'docs.oracle.com') return url.pathname.startsWith('/en/java/javase/');
        if (host === 'enterprise.spring.io') return url.pathname === '/';
        return true;
    } catch {
        return false;
    }
}

async function fetchStatus(url) {
    try {
        let target = url;
        for (let redirects = 0; redirects <= 5; redirects++) {
            const response = await fetch(target, {
                method: 'GET',
                redirect: 'manual',
                headers: { 'user-agent': 'Mozilla/5.0 (spring-tools explanations check)' },
                signal: AbortSignal.timeout(30000),
            });
            if (![301, 302, 303, 307, 308].includes(response.status)) {
                await response.body?.cancel().catch(() => undefined);
                return response.status;
            }
            const location = response.headers.get('location');
            await response.body?.cancel().catch(() => undefined);
            if (!location || redirects === 5) return response.status;
            const next = new URL(location, target).href;
            if (!allowedDocumentationUrl(next)) {
                return `blocked redirect  ${next}`;
            }
            target = next;
        }
        return 'too many redirects';
    } catch (error) {
        return `ERR ${error.name ?? ''} ${error.message ?? ''}`.trim();
    }
}

export async function checkLinks(urls, concurrency = 4) {
    const failures = [];
    const queue = [];
    for (const url of urls) {
        if (allowedDocumentationUrl(url)) queue.push(url);
        else failures.push(`blocked URL (outside approved documentation hosts)  ${url}`);
    }
    async function worker() {
        while (queue.length) {
            const url = queue.shift();
            const status = await fetchStatus(url);
            if (status !== 200) {
                failures.push(`${status}  ${url}`);
            }
        }
    }
    await Promise.all(Array.from({ length: Math.min(concurrency, urls.length || 1) }, worker));
    return failures.sort();
}

/**
 * Runs all offline checks.
 * @param {{root: string, codes?: string[], requireAll?: boolean, todo?: boolean}} options
 */
export function runChecks(options) {
    const root = resolve(options.root);
    const errors = [];
    const warnings = [];
    const knownCodes = new Set(collectCodes(root));
    if (knownCodes.size === 0) {
        errors.push(`no problem-type codes found under ${PROBLEM_TYPES_DIRS.join(', ')}`);
    }
    const playbooks = new Set(collectPlaybooks(root));

    for (const orphan of [...playbooks].filter((p) => !knownCodes.has(p))) {
        errors.push(`orphan playbook without a matching problem-type code: ${orphan}.md`);
    }

    const selected = options.codes?.length ? [...new Set(options.codes)] : [...playbooks];
    for (const code of selected) {
        if (!/^(?:[A-Z][A-Z0-9_]+|YamlSchemaProblem)$/.test(code)) {
            errors.push(`invalid diagnostic code '${code}'`);
            continue;
        }
        const file = join(root, EXPLANATIONS_DIR, `${code}.md`);
        if (!existsSync(file)) {
            errors.push(`missing playbook: ${EXPLANATIONS_DIR}/${code}.md`);
            continue;
        }
        errors.push(...checkPlaybook(code, readFileSync(file, 'utf8'), knownCodes, playbooks));
    }

    const missing = [...knownCodes].filter((c) => !playbooks.has(c));
    if (missing.length) {
        const message = `${missing.length} code(s) without playbook: ${missing.join(', ')}`;
        (options.requireAll ? errors : warnings).push(message);
    }

    if (options.todo) {
        const todoFile = join(root, TODO_FILE);
        if (!existsSync(todoFile)) {
            errors.push(`missing coverage table ${TODO_FILE}`);
        } else {
            const todoText = readFileSync(todoFile, 'utf8');
            const table = parseTodoTable(todoText);
            for (const code of duplicateTodoRows(todoText)) {
                errors.push(`${TODO_FILE}: duplicate row for code ${code}`);
            }
            for (const code of knownCodes) {
                if (!table.has(code)) {
                    errors.push(`${TODO_FILE}: no row for code ${code}`);
                } else if (table.get(code) !== playbooks.has(code)) {
                    errors.push(`${TODO_FILE}: playbook mark for ${code} is ${table.get(code) ? '✅' : '❌'} but file ${playbooks.has(code) ? 'exists' : 'is missing'}`);
                }
            }
            for (const code of table.keys()) {
                if (!knownCodes.has(code)) {
                    errors.push(`${TODO_FILE}: row for unknown code ${code}`);
                }
            }
        }
    }

    const urls = new Set();
    for (const code of selected) {
        const file = join(root, EXPLANATIONS_DIR, `${code}.md`);
        if (existsSync(file)) {
            extractUrls(readFileSync(file, 'utf8')).forEach((u) => urls.add(u));
        }
    }

    return { errors, warnings, codes: knownCodes.size, playbooks: playbooks.size, selected: selected.length, urls: [...urls].sort() };
}

function parseArgs(argv) {
    const options = { root: undefined, codes: [], requireAll: false, todo: false, links: false, help: false };
    for (let i = 0; i < argv.length; i++) {
        const arg = argv[i];
        if (arg === '--root') options.root = argv[++i];
        else if (arg === '--codes') options.codes = (argv[++i] ?? '').split(',').map((c) => c.trim()).filter(Boolean);
        else if (arg === '--require-all') options.requireAll = true;
        else if (arg === '--todo') options.todo = true;
        else if (arg === '--links') options.links = true;
        else if (arg === '--help' || arg === '-h') options.help = true;
        else throw new Error(`unknown argument: ${arg}`);
    }
    return options;
}

const USAGE = `usage: node check-explanations.mjs [--root DIR] [--codes A,B] [--require-all] [--todo] [--links]

  --root DIR      repository root (default: two levels above this script)
  --codes A,B     restrict structure and link checks to these codes (files must exist)
  --require-all   fail when a problem-type code has no playbook
  --todo          verify ${TODO_FILE} lists every code with the right playbook mark
  --links         fetch every documentation URL of the selected playbooks (needs network)`;

async function main() {
    const options = parseArgs(process.argv.slice(2));
    if (options.help) {
        console.log(USAGE);
        return 0;
    }
    const root = options.root ?? join(dirname(fileURLToPath(import.meta.url)), '..', '..');
    const result = runChecks({ ...options, root });
    for (const warning of result.warnings) console.log(`WARN  ${warning}`);
    for (const error of result.errors) console.log(`ERROR ${error}`);
    if (result.errors.length) {
        console.log(`explanations check FAILED with ${result.errors.length} error(s)`);
        return 1;
    }
    console.log(`explanations check passed (${result.codes} codes, ${result.playbooks} playbooks, ${result.selected} checked)`);
    if (options.links) {
        const failures = await checkLinks(result.urls);
        for (const failure of failures) console.log(`ERROR link ${failure}`);
        if (failures.length) {
            console.log(`links check FAILED with ${failures.length} unreachable URL(s)`);
            return 1;
        }
        console.log(`links check passed (${result.urls.length} urls)`);
    }
    return 0;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
    main().then((code) => process.exit(code), (error) => {
        console.error(error.message);
        process.exit(2);
    });
}
