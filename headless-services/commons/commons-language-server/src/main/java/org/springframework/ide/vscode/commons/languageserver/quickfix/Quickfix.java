/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver.quickfix;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.ide.vscode.commons.util.text.DocumentUtil;

import com.google.common.collect.ImmutableList;

public class Quickfix<T> {

	public static class QuickfixData<T> {
		
		public final QuickfixType type;
		public final T params;
		public final String title;
		public final boolean preferred;

		public QuickfixData(QuickfixType type, T params, String title, boolean preferred) {
			super();
			this.type = type;
			this.params = params;
			this.title = title;
			this.preferred = preferred;
		}
		
		public QuickfixData(QuickfixType type, T params, String title) {
			this(type, params, title, false);
		}

	}

	private final String CODE_ACTION_CMD_ID;

	private final Range range;
	private final Either<String, MarkupContent> diagMsg;
	private final QuickfixData<T> data;

	public Quickfix(String CODE_ACTION_CMD_ID, Diagnostic diag, QuickfixData<T> data) {
		super();
		this.CODE_ACTION_CMD_ID = CODE_ACTION_CMD_ID;
		this.range = diag.getRange();
		this.diagMsg = diag.getMessage();
		this.data = data;
	}

	public Range getRange() {
		return range;
	}

	public CodeAction getCodeAction(CodeActionContext context) {
		CodeAction ca = new CodeAction();
		ca.setKind(CodeActionKind.QuickFix);
		ca.setTitle(data.title);
		ca.setDiagnostics(appliesToDiagnostics(context));
		ca.setCommand(new Command(
				data.title,
				CODE_ACTION_CMD_ID,
				ImmutableList.of(data.type.getId(), data.params)
		));
		return ca;
	}

	public boolean appliesTo(Range range, CodeActionContext context) {
		return appliesToContext(context) && DocumentUtil.containsRange(this.range, range);
	}

	private boolean appliesToContext(CodeActionContext context) {
		if (context!=null) {
			return context.getDiagnostics().stream().anyMatch(diag ->
				this.diagMsg == null || this.diagMsg.equals(diag.getMessage())
			);
		}
		return true;
	}
	
	private List<Diagnostic> appliesToDiagnostics(CodeActionContext context) {
		return context.getDiagnostics().stream()
				.filter(diag -> this.diagMsg == null || this.diagMsg.equals(diag.getMessage()))
				.collect(Collectors.toList());
	}
}
