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

import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressReport;

/**
 * Low-level interface responsible for sending progress notifications to the LSP client.
 * Implementations handle the actual protocol-level communication.
 */
public interface ProgressClient {

	/**
	 * No-op implementation that discards all progress events.
	 * Useful for testing or when progress reporting is disabled.
	 */
	public static final ProgressClient NO_PROGRESS = new ProgressClient() {

		@Override
		public void begin(String taskId, WorkDoneProgressBegin report) {
		}

		@Override
		public void report(String taskId, WorkDoneProgressReport report) {
		}

		@Override
		public void end(String taskId, WorkDoneProgressEnd report) {
		}
		
	};
	
	/**
	 * Sends a begin progress notification to the LSP client.
	 * 
	 * @param taskId unique identifier for the progress task
	 * @param report the initial progress report containing title, message, and optional percentage
	 */
	void begin(String taskId, WorkDoneProgressBegin report);
	
	/**
	 * Sends a progress update notification to the LSP client.
	 * Each event updates the message shown to the user, replacing the previous one.
	 * Multiple progress indicators may be shown simultaneously if they have different taskIds.
	 *
	 * @param taskId unique identifier for the progress task
	 * @param report the progress update containing message and optional percentage
	 */
	void report(String taskId, WorkDoneProgressReport report);
	
	/**
	 * Sends an end progress notification to the LSP client.
	 * 
	 * @param taskId unique identifier for the progress task
	 * @param report the final progress report containing an optional message
	 */
	void end(String taskId, WorkDoneProgressEnd report);

}

