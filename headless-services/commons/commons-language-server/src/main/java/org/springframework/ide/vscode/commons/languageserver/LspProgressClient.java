/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom, Inc.
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
import java.util.function.Supplier;

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
 * <p>The client is obtained lazily via a {@link Supplier}, allowing this class to be
 * instantiated before the LSP client is connected. If the client is not yet available
 * (supplier returns null), an {@link IllegalStateException} is thrown.</p>
 * 
 * <p>This class is package-private as it's an internal implementation detail.
 * Users should only interact with {@link ProgressService} which creates instances of this class.</p>
 */
class LspProgressClient implements ProgressClient {
	
	private static final Logger log = LoggerFactory.getLogger(LspProgressClient.class);
	
	private final Supplier<STS4LanguageClient> clientSupplier;
	private final ConcurrentHashMap<String, Boolean> activeTaskIDs = new ConcurrentHashMap<>();
	
	/**
	 * Package-private constructor. Creates a new LspProgressClient with the specified client supplier.
	 * 
	 * @param clientSupplier supplier for the LSP client; may return null if client is not yet connected
	 * @throws IllegalArgumentException if clientSupplier is null
	 */
	LspProgressClient(Supplier<STS4LanguageClient> clientSupplier) {
		if (clientSupplier == null) {
			throw new IllegalArgumentException("STS4LanguageClient supplier cannot be null");
		}
		this.clientSupplier = clientSupplier;
	}
	
	private STS4LanguageClient requireClient() {
		STS4LanguageClient client = clientSupplier.get();
		if (client == null) {
			throw new IllegalStateException("Language client is not connected. Progress notifications require an active client connection.");
		}
		return client;
	}
	
	@Override
	public void begin(String taskId, WorkDoneProgressBegin report) {
		STS4LanguageClient client = requireClient();
		
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
		STS4LanguageClient client = requireClient();
		
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
		STS4LanguageClient client = requireClient();
		if (activeTaskIDs.remove(taskId) == null) {
			return;
		}
		
		ProgressParams progressParams = new ProgressParams();
		progressParams.setToken(taskId);
		progressParams.setValue(Either.forLeft(report));
		client.notifyProgress(progressParams);
	}

}
