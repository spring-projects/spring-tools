/*******************************************************************************
 * Copyright (c) 2019, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver;

import org.eclipse.lsp4j.WorkDoneProgressEnd;

/**
 * Base class for progress tasks that report progress to the LSP client.
 * 
 * <p>This handler can be used for long-running progress that requires message updates to the same task.</p>
 *
 */
public abstract class AbstractProgressTask {
	
	private static long progress_counter = 0; 
	
	protected final String taskId;
	protected final ProgressClient client;
	
	
	public AbstractProgressTask(String taskId, ProgressClient client) {
		this.taskId = taskId + "-" + (progress_counter++);
		this.client = client;
	}
	
	public void done() {
		WorkDoneProgressEnd endReport = new WorkDoneProgressEnd();
		this.client.end(taskId, endReport);
	}

}
