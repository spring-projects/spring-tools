/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.commons.languageserver;

import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.protocol.STS4LanguageClient;

/**
 * Package-private implementation of {@link ProgressClient} that communicates with an LSP client
 * using the Language Server Protocol progress notifications.
 * 
 * <p>This class manages the lifecycle of progress tokens, ensuring proper
 * creation and cleanup of progress indicators.</p>
 * 
 * <p>This class is package-private as it's an internal implementation detail.
 * Users should only interact with {@link ProgressService} which creates instances of this class.</p>
 */
class LspProgressClient implements ProgressClient {
	
	private static final Logger log = LoggerFactory.getLogger(LspProgressClient.class);
	
	private final STS4LanguageClient client;
	private final ConcurrentHashMap<String, Boolean> activeTaskIDs = new ConcurrentHashMap<>();
	
	/**
	 * Package-private constructor. Creates a new LspProgressClient with the specified LSP client.
	 * 
	 * @param client the LSP client to use for sending notifications
	 * @throws IllegalArgumentException if client is null
	 */
	LspProgressClient(STS4LanguageClient client) {
		if (client == null) {
			throw new IllegalArgumentException("STS4LanguageClient cannot be null");
		}
		this.client = client;
	}
	
	@Override
	public void begin(String taskId, WorkDoneProgressBegin report) {
		if (client == null) {
			return;
		}
		
		boolean isNew = activeTaskIDs.put(taskId, true) == null;
		if (!isNew) {
			log.error("Progress for task id '{}' already exists", taskId);
		}
		
		// First create the progress token
		WorkDoneProgressCreateParams params = new WorkDoneProgressCreateParams();
		params.setToken(taskId);
		client.createProgress(params).thenAcceptAsync((p) -> {
			// Then send the begin notification
			ProgressParams progressParams = new ProgressParams();
			progressParams.setToken(taskId);
			progressParams.setValue(Either.forLeft(report));
			client.notifyProgress(progressParams);
		});
	}
	
	@Override
	public void report(String taskId, WorkDoneProgressReport report) {
		if (client == null) {
			return;
		}
		
		if (!activeTaskIDs.containsKey(taskId)) {
			log.error("Progress for task id '{}' does NOT exist!", taskId);
			return;
		}
		
		ProgressParams progressParams = new ProgressParams();
		progressParams.setToken(taskId);
		progressParams.setValue(Either.forLeft(report));
		client.notifyProgress(progressParams);
	}
	
	@Override
	public void end(String taskId, WorkDoneProgressEnd report) {
		if (client == null || activeTaskIDs.remove(taskId) == null) {
			return;
		}
		
		ProgressParams progressParams = new ProgressParams();
		progressParams.setToken(taskId);
		progressParams.setValue(Either.forLeft(report));
		client.notifyProgress(progressParams);
	}

}

