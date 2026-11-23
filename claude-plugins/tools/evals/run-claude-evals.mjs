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

import { spawnSync } from 'node:child_process';
import { cpSync, mkdirSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const pluginDir = resolve(here, '..', '..', 'spring-tools');

export function stagePlugin(destination) {
    cpSync(pluginDir, destination, {
        recursive: true,
        filter: (source) => source === pluginDir
            || (source !== join(pluginDir, 'evals') && !['language-server', 'node_modules'].includes(basename(source))),
    });
    cpSync(here, join(destination, 'evals'), {
        recursive: true,
        filter: (source) => source !== join(here, 'results'),
    });
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const temporaryDir = mkdtempSync(join(tmpdir(), 'spring-tools-evals-'));
    try {
        const stagedPlugin = join(temporaryDir, 'spring-tools');
        const resultsDir = join(here, 'results');
        stagePlugin(stagedPlugin);
        mkdirSync(resultsDir, { recursive: true });
        const result = spawnSync(process.env.CLAUDE_CLI || 'claude', [
            'plugin', 'eval', stagedPlugin,
            '--output-dir', resultsDir,
            '--report', join(resultsDir, 'report.html'),
            ...process.argv.slice(2),
        ], { stdio: 'inherit' });
        if (result.error) {
            throw result.error;
        }
        if (result.signal) {
            console.error(`Claude eval terminated by ${result.signal}`);
        }
        process.exitCode = result.status ?? 1;
    } finally {
        rmSync(temporaryDir, { recursive: true, force: true });
    }
}
