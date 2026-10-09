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
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

import {
    EXPLANATIONS_DIR,
    checkPlaybook,
    collectCodes,
    collectPlaybooks,
    allowedDocumentationUrl,
    extractUrls,
    parseTodoTable,
    runChecks,
} from '../check-explanations.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..', '..', '..');
const fixtureRoot = join(here, 'fixtures', 'bad-repo');

test('collects the diagnostic codes from every ProblemType enum, the version validation constant and the inherited YAML merge-key code', () => {
    const codes = collectCodes(repoRoot);
    for (const expected of ['JAVA_PUBLIC_BEAN_METHOD', 'MODULITH_APPLICATION_MODULE_LISTENER', 'API_VERSIONING_NOT_CONFIGURED',
        'JAVA_CONCRETE_BEAN_TYPE', 'SPRING_AI_TOOL_MISSING_DESCRIPTION', 'JAVA_SPEL_EXPRESSION_SYNTAX', 'CRON_SYNTAX', 'CRON_FIELD',
        'JPQL_SYNTAX', 'HQL_SYNTAX', 'SQL_SYNTAX', 'BOOT_VERSION_VALIDATION_CODE',
        'PROP_UNKNOWN_PROPERTY', 'PROP_SYNTAX_ERROR', 'YAML_UNKNOWN_PROPERTY', 'YAML_SHOULD_ESCAPE', 'YamlSchemaProblem']) {
        assert.ok(codes.includes(expected), `missing code ${expected}`);
    }
    assert.ok(codes.length >= 60, `only ${codes.length} codes found`);
    assert.ok(!codes.includes('DEFAULT_TOOL_DESCRIPTION_MIN_LENGTH'), 'constants must not be mistaken for codes');
    assert.ok(!codes.includes('UNSUPPORTED_OSS_VERSION'), 'version-validation preference keys are not diagnostic codes');
    assert.ok(!codes.includes('YamlSyntaxProblem'), 'only the commons-yaml code the Boot YAML reconciler can emit is collected');
});

test('every playbook on disk is structurally valid and belongs to a known code', () => {
    const result = runChecks({ root: repoRoot });
    assert.deepEqual(result.errors, []);
    assert.ok(result.playbooks >= 15);
    assert.ok(result.urls.length > 0, 'playbooks must link to documentation');
});

test('file names of playbooks are exactly the codes (case sensitive, no spaces)', () => {
    for (const code of collectPlaybooks(repoRoot)) {
        assert.match(code, /^(?:[A-Z][A-Z0-9_]+|YamlSchemaProblem)$/, `${EXPLANATIONS_DIR}/${code}.md`);
    }
});

test('negative control: the checker fails on the fixture with an orphan, a malformed playbook, an unresolved cross-reference and a stale coverage table', () => {
    const result = runChecks({ root: fixtureRoot, requireAll: true, todo: true });
    const joined = result.errors.join('\n');
    assert.match(joined, /orphan playbook .*ORPHAN_CODE\.md/);
    assert.match(joined, /REF_TARGET: missing '## Fixes' section/);
    assert.match(joined, /GOOD_CODE: references code MISSING_CODE which has no playbook/);
    assert.match(joined, /1 code\(s\) without playbook: MISSING_CODE/);
    assert.match(joined, /playbook mark for MISSING_CODE is ✅ but file is missing/);
    assert.match(joined, /no row for code REF_TARGET/);
    assert.match(joined, /row for unknown code STALE_CODE/);
});

test('negative control: a valid fixture playbook passes while the same text without a doc link fails', () => {
    const known = new Set(['GOOD_CODE', 'OTHER']);
    const files = new Set(['GOOD_CODE', 'OTHER']);
    const good = readFileSync(join(fixtureRoot, EXPLANATIONS_DIR, 'GOOD_CODE.md'), 'utf8').replace('`MISSING_CODE`', '`OTHER`');
    assert.deepEqual(checkPlaybook('GOOD_CODE', good, known, files), []);
    const noLink = good.replace(/https:\/\/docs\.spring\.io\S+/g, 'https://example.com/');
    assert.match(checkPlaybook('GOOD_CODE', noLink, known, files).join('\n'), /no link to docs\.spring\.io/);
    const unbalanced = good + '\n```java\n';
    assert.match(checkPlaybook('GOOD_CODE', unbalanced, known, files).join('\n'), /unbalanced code fences/);
    const descriptive = good.replace('It refers to', 'The quick fix inserts a TODO comment. It refers to');
    assert.deepEqual(checkPlaybook('GOOD_CODE', descriptive, known, files), [], 'describing an inserted TODO comment is fine');
    const placeholder = good.replace('It refers to', 'TODO: describe the fix. It refers to');
    assert.match(checkPlaybook('GOOD_CODE', placeholder, known, files).join('\n'), /placeholder text/);
});

test('coverage table parser reads code rows and playbook marks', () => {
    const table = parseTodoTable('| `A_CODE` | desc | fix | ✅ |\n| `B_CODE` | desc | fix | ❌ |\n| not a code | x | y | ✅ |');
    assert.deepEqual([...table.entries()], [['A_CODE', true], ['B_CODE', false]]);
});

test('url extraction strips anchors and trailing punctuation and de-duplicates', () => {
    const urls = extractUrls('see https://docs.spring.io/a.html#x, https://docs.spring.io/a.html) and (https://spring.io/b).');
    assert.deepEqual(urls, ['https://docs.spring.io/a.html', 'https://spring.io/b']);
    assert.deepEqual(extractUrls('Example: `https://example.com`\n```java\nString url = "https://example.com";\n```'), []);
});

test('documentation link checks reject non-documentation hosts and unsafe URL schemes', () => {
    assert.equal(allowedDocumentationUrl('https://docs.spring.io/spring-boot/reference/'), true);
    assert.equal(allowedDocumentationUrl('https://github.com/spring-projects/spring-boot'), true);
    assert.equal(allowedDocumentationUrl('https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Properties.html'), true);
    assert.equal(allowedDocumentationUrl('https://openjdk.org/jeps/486'), true);
    assert.equal(allowedDocumentationUrl('https://yaml.org/spec/1.2.2/'), true);
    assert.equal(allowedDocumentationUrl('https://raw.githubusercontent.com/spring-projects/spring-security/5.8.x/docs/example.adoc'), true);
    assert.equal(allowedDocumentationUrl('https://github.com/attacker/repo'), false);
    assert.equal(allowedDocumentationUrl('https://raw.githubusercontent.com/attacker/repo/main/file'), false);
    assert.equal(allowedDocumentationUrl('https://openjdk.org/'), false);
    assert.equal(allowedDocumentationUrl('https://127.0.0.1/admin'), false);
    assert.equal(allowedDocumentationUrl('http://spring.io/'), false);
    assert.equal(allowedDocumentationUrl('javascript:alert(1)'), false);
});

test('selected diagnostic codes cannot escape the explanations directory', () => {
    const result = runChecks({ root: fixtureRoot, codes: ['../../TODO_quickfixes'] });
    assert.ok(result.errors.some((error) => /invalid diagnostic code/.test(error)));
    assert.ok(!result.errors.some((error) => /missing playbook/.test(error)));
});
