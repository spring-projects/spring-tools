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
import { test } from 'node:test';

import { combineToolTraces, countToolMatches } from '../lib/eval-grading.mjs';

test('MCP calls from Copilot logs are not counted twice when the mock server traced them', () => {
    const trace = combineToolTraces(
        [
            { tool: 'spring-tools-mcp-getProjectList', input: '{"page":1}' },
            { tool: 'view', input: '{"file_path":"README.md"}' },
        ],
        [{ tool: 'getProjectList', input: '{"page":1}' }],
        new Set(['getProjectList']),
    );

    assert.equal(countToolMatches({
        tool: 'mcp__plugin_spring-tools_spring-tools-mcp__getProjectList',
    }, trace), 1);
    assert.equal(trace.filter((entry) => entry.tool === 'view').length, 1);
});
