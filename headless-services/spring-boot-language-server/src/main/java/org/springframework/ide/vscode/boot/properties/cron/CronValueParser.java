/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.properties.cron;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.ide.vscode.boot.java.cron.CronReconciler;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.util.ValueParseException;
import org.springframework.ide.vscode.commons.util.ValueParser;

/**
 * Validates a CRON expression by delegating to {@link CronReconciler}. Only the first problem is reported
 * since a {@link ValueParser} signals a failure with a single exception.
 *
 * @author Alex Boyko
 */
public class CronValueParser implements ValueParser {

	private static final CronReconciler RECONCILER = new CronReconciler();

	@Override
	public Object parse(String str) throws Exception {
		// Blank value is ignored by Boot, i.e. same as the property not being set
		if (str.isBlank()) {
			return null;
		}
		List<ReconcileProblem> problems = new ArrayList<>();
		RECONCILER.reconcile(str, r -> r, new IProblemCollector() {
			@Override
			public void beginCollecting() {
			}

			@Override
			public void endCollecting() {
			}

			@Override
			public void accept(ReconcileProblem problem) {
				problems.add(problem);
			}
		});
		ReconcileProblem problem = problems.stream().min(Comparator.comparingInt(ReconcileProblem::getOffset)).orElse(null);
		if (problem != null) {
			// Always highlight something visible, a missing token is a zero length problem
			int start = Math.max(0, Math.min(problem.getOffset(), str.length() - 1));
			int end = Math.min(str.length(), Math.max(problem.getOffset() + problem.getLength(), start + 1));
			throw new ValueParseException(problem.getMessage(), start, end, str.substring(start, end));
		}
		return null;
	}

}
