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

import { MCP_SERVER_NAME, SCOPED_TOOL_PREFIX } from './plugin-config.mjs';

const TOOL_ALIASES = { Read: ['view'], Skill: ['skill'], Glob: ['glob'], Grep: ['grep'], Bash: ['bash'], Agent: ['agent'] };

/** Copilot selects plugin-provided agents by the file-derived id, without a plugin namespace. */
export function copilotAgentId(inputMatch) {
    return typeof inputMatch === 'string' && /^[\w-]+$/.test(inputMatch) ? inputMatch : undefined;
}

/** Count trace entries matching a grader's tool name and optional JSON-input pattern. */
export function countToolMatches(grader, trace) {
    const wanted = grader.tool.startsWith(SCOPED_TOOL_PREFIX)
        ? [`${MCP_SERVER_NAME}-${grader.tool.slice(SCOPED_TOOL_PREFIX.length)}`]
        : (TOOL_ALIASES[grader.tool] ?? [grader.tool]);
    const pattern = grader.input_match ? new RegExp(grader.input_match) : null;
    return trace.filter((entry) => wanted.includes(entry.tool) && (!pattern || pattern.test(entry.input))).length;
}

/** Apply the eval grader's inclusive min/max occurrence bounds (default: at least one). */
export function toolGradePasses(grader, trace) {
    const count = countToolMatches(grader, trace);
    const min = grader.min === undefined ? 1 : Number(grader.min);
    const max = grader.max === undefined ? Number.POSITIVE_INFINITY : Number(grader.max);
    return Number.isFinite(min) && min >= 0 && !Number.isNaN(max) && max >= min && count >= min && count <= max;
}

/** Prefer the mock server's authoritative MCP trace over duplicate MCP entries in Copilot's logs. */
export function combineToolTraces(logEntries, mockEntries, mcpToolNames) {
    const isMcpToolLogEntry = (tool) => {
        const candidates = [
            tool,
            tool.startsWith(`${MCP_SERVER_NAME}-`) ? tool.slice(MCP_SERVER_NAME.length + 1) : null,
            tool.split('__').at(-1),
        ];
        return candidates.some((candidate) => mcpToolNames.has(candidate));
    };
    return [
        ...logEntries.filter((entry) => !isMcpToolLogEntry(entry.tool)),
        ...mockEntries.map((entry) => ({ ...entry, tool: `${MCP_SERVER_NAME}-${entry.tool}` })),
    ];
}
