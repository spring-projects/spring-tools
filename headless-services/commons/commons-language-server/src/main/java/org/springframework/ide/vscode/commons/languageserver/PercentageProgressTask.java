/*******************************************************************************
 * Copyright (c) 2023 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver;

import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressReport;

/**
 * Eclipse progress requires sending total work units number with the begin progress message. The number is any intger.
 * VScode and likely other LSP clients require the begin progress message to have 0 and progress is assumed to be between 0 and 100.
 * This class takes care of these differences.
 * 
 * @author aboyko
 *
 */
public class PercentageProgressTask extends AbstractProgressTask {
	
	private int total;
	private int current;
	
	private int currentPercentage;
	
	public PercentageProgressTask(String taskId, ProgressClient client, int total, String title) {
		super(taskId, client);
		this.total = total;
		this.current = 0;
		begin(title);
	}
	
	private void begin(String title) {
		WorkDoneProgressBegin progressBegin = new WorkDoneProgressBegin();
		progressBegin.setPercentage(0);
		progressBegin.setCancellable(false);
		progressBegin.setTitle(title);
		client.begin(taskId, progressBegin);
	}
	
	public int getTotal() {
		return total;
	}
	
	public int getCurrent() {
		return current;
	}
	
	public void setCurrent(int current) {
		if (current > total) {
			throw new IllegalArgumentException();
		}
		this.current = current;
		reportCurrent();
	}
	
	private void reportPercent(int percent) {
		if (percent > currentPercentage) {
			currentPercentage = percent;
			WorkDoneProgressReport r = new WorkDoneProgressReport();
			r.setPercentage(percent);
			client.report(taskId, r);
		}
	}
	
	private void reportCurrent() {
		reportPercent(current * 100 / total);
	}
	
	public void increment() {
		if (current >= total) {
			throw new IllegalStateException();
		}
		current++;
		reportCurrent();
	}

	
}
