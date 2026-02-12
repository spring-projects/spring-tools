/*******************************************************************************
 * Copyright (c) 2016, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.commons.languageserver;

import java.util.function.Supplier;

import org.springframework.ide.vscode.commons.protocol.STS4LanguageClient;

/**
 * Service for managing progress tasks reported to the LSP client.
 * 
 * <p>This is the primary API for working with progress indicators. Users should create
 * progress tasks using the factory methods and interact with the returned task objects.
 * The tasks directly communicate with the underlying {@link ProgressClient}.</p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * ProgressService progress = ProgressService.create(() -&gt; lspClient);
 * IndefiniteProgressTask task = progress.createIndefiniteProgressTask(
 *     "indexing", "Indexing Project", "Starting...");
 * task.progressEvent("Processing files...");
 * task.done();
 * </pre>
 */
public final class ProgressService {

	/**
	 * No-op progress service that discards all progress events.
	 * Useful for testing or when progress reporting is disabled.
	 */
	public static final ProgressService NO_PROGRESS = new ProgressService(ProgressClient.NO_PROGRESS);
	
	private final ProgressClient client;
	
	/**
	 * Factory method to create a ProgressService with a lazily-supplied LSP client.
	 * The supplier may return null before the client is connected; in that case
	 * progress notifications are silently discarded.
	 * 
	 * @param clientSupplier supplier for the LSP client
	 * @return a new ProgressService instance
	 * @throws IllegalArgumentException if clientSupplier is null
	 */
	public static ProgressService create(Supplier<STS4LanguageClient> clientSupplier) {
		if (clientSupplier == null) {
			throw new IllegalArgumentException("STS4LanguageClient supplier cannot be null");
		}
		return new ProgressService(new LspProgressClient(clientSupplier));
	}
	
	/**
	 * Package-private constructor for creating a ProgressService with a ProgressClient.
	 * External code should use the factory method {@link #create(STS4LanguageClient)}.
	 * 
	 * @param client the client to use for sending progress notifications
	 * @throws IllegalArgumentException if client is null
	 */
	ProgressService(ProgressClient client) {
		if (client == null) {
			throw new IllegalArgumentException("ProgressClient cannot be null");
		}
		this.client = client;
	}
	
	/**
	 * Creates an indefinite progress task that shows a spinner with messages.
	 * Useful for tasks where the total amount of work is unknown.
	 * 
	 * @param taskId unique identifier for the progress task
	 * @param title the title of the progress indicator
	 * @param message the initial message to display
	 * @return a new IndefiniteProgressTask
	 */
	public IndefiniteProgressTask createIndefiniteProgressTask(String taskId, String title, String message) {
		return new IndefiniteProgressTask(taskId, client, title, message);
	}
	
	/**
	 * Creates a percentage-based progress task that shows a progress bar.
	 * Useful for tasks where the total amount of work is known.
	 * 
	 * @param taskId unique identifier for the progress task
	 * @param totalWork the total number of work units
	 * @param title the title of the progress indicator
	 * @return a new PercentageProgressTask
	 */
	public PercentageProgressTask createPercentageProgressTask(String taskId, int totalWork, String title) {
		return new PercentageProgressTask(taskId, client, totalWork, title);
	}

}
