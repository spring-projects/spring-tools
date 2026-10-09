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

// Stdio MCP server that replays the recorded responses in evals/mocks instead of starting the
// language server. Claude Code mocks MCP servers itself during `claude plugin eval`; Copilot CLI
// has no such feature, so run-with-copilot.mjs points a copy of the plugin at this script.
//
//   EVAL_MOCK_DIRS   os.delimiter separated mock directories, searched in order
//   EVAL_TRACE       file the tool calls are appended to (one JSON object per line)
//   EVAL_INSTRUCTIONS  optional server instructions to report from `initialize`

import { appendFileSync, existsSync, readFileSync } from 'node:fs';
import { delimiter, join } from 'node:path';

const mockDirs = (process.env.EVAL_MOCK_DIRS ?? '').split(delimiter).filter(Boolean);
const tracePath = process.env.EVAL_TRACE;

/** First mock file for `name` in the configured directories, or null. */
function findMock(name) {
    // Tool names become file names below. Refuse arbitrary names so a malformed MCP request
    // cannot make this local replay server read files outside the selected mock directories.
    if (typeof name !== 'string' || !/^[A-Za-z][A-Za-z0-9]*$/.test(name)) {
        return null;
    }
    for (const dir of mockDirs) {
        const file = join(dir, `${name}.md`);
        if (existsSync(file)) {
            return readFileSync(file, 'utf8');
        }
    }
    return null;
}

/** Recorded mocks may carry a front matter block with the expected arguments; the body is the response. */
function mockBody(text) {
    return text.replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n/, '');
}

function send(message) {
    process.stdout.write(`${JSON.stringify(message)}\n`);
}

function trace(entry) {
    if (tracePath) {
        appendFileSync(tracePath, `${JSON.stringify(entry)}\n`);
    }
}

function handle(request) {
    const { id, method, params } = request;
    if (method === 'initialize') {
        return {
            protocolVersion: params?.protocolVersion ?? '2024-11-05',
            capabilities: { tools: { listChanged: false } },
            serverInfo: { name: 'spring-tools-mcp', version: '0.0.0-eval' },
            ...(process.env.EVAL_INSTRUCTIONS ? { instructions: process.env.EVAL_INSTRUCTIONS } : {}),
        };
    }
    if (method === 'ping') {
        return {};
    }
    if (method === 'tools/list') {
        for (const dir of mockDirs) {
            const file = join(dir, '_tools.json');
            if (existsSync(file)) {
                return JSON.parse(readFileSync(file, 'utf8'));
            }
        }
        throw new Error(`no recorded tools/list (_tools.json) in ${mockDirs.join(delimiter)}`);
    }
    if (method === 'tools/call') {
        const name = params?.name;
        trace({ tool: name, input: JSON.stringify(params?.arguments ?? {}) });
        if (typeof name !== 'string' || !/^[A-Za-z][A-Za-z0-9]*$/.test(name)) {
            return { isError: true, content: [{ type: 'text', text: 'invalid tool name' }] };
        }
        const mock = findMock(name);
        if (mock === null) {
            return { isError: true, content: [{ type: 'text', text: `no recorded response for ${name}` }] };
        }
        return { content: [{ type: 'text', text: mockBody(mock).trim() }] };
    }
    if (method?.startsWith('notifications/')) {
        return null;
    }
    const error = new Error(`unsupported method ${method}`);
    error.code = -32601;
    throw error;
}

let buffer = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (chunk) => {
    buffer += chunk;
    let newline;
    while ((newline = buffer.indexOf('\n')) !== -1) {
        const line = buffer.slice(0, newline).trim();
        buffer = buffer.slice(newline + 1);
        if (!line) {
            continue;
        }
        let request;
        try {
            request = JSON.parse(line);
        } catch {
            continue;
        }
        try {
            const result = handle(request);
            if (request.id !== undefined && result !== null) {
                send({ jsonrpc: '2.0', id: request.id, result });
            }
        } catch (error) {
            if (request.id !== undefined) {
                send({ jsonrpc: '2.0', id: request.id, error: { code: error.code ?? -32603, message: error.message } });
            }
        }
    }
});
