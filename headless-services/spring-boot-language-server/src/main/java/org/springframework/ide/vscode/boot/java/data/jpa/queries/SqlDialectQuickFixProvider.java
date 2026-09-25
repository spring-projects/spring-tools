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
package org.springframework.ide.vscode.boot.java.data.jpa.queries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionCapabilities;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.ide.vscode.boot.common.SpringProblemCategories;
import org.springframework.ide.vscode.boot.java.handlers.JavaCodeActionHandler;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.util.text.IRegion;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * Offers a quick fix on a {@link QueryProblemType#SQL_SYNTAX} diagnostic to
 * switch the global {@code spring-boot.ls.problem-parameters.data-query.sql-dialect} setting, in
 * exactly the two cases where a syntax error is plausibly a dialect problem
 * rather than a real mistake in the query:
 * <ul>
 * <li>the classpath is genuinely ambiguous (more than one recognized JDBC
 * driver present) - offer every applicable dialect plus "auto", minus
 * whichever is currently selected;</li>
 * <li>the classpath is unambiguous but the current override doesn't match
 * it - offer switching back to "auto".</li>
 * </ul>
 * An unambiguous syntax error with no override is presumably a real mistake
 * in the query, so nothing is offered. Each fix is a plain client-side
 * {@link Command} (not a {@code WorkspaceEdit}) that invokes the generic
 * {@value #SET_CONFIGURATION_COMMAND_ID} command with the setting's full
 * dotted name and the new value - both VSCode (a registered command in the
 * extension) and Eclipse (a handler in the {@code boot.ls} plugin) implement
 * it client-side, always against the global/workspace scope.
 */
public class SqlDialectQuickFixProvider implements JavaCodeActionHandler {

	/**
	 * Generic "set a configuration value" command, reusable by any future
	 * quick fix - not specific to the SQL dialect setting. Takes the setting's
	 * full dotted name and the new value as its two arguments.
	 */
	public static final String SET_CONFIGURATION_COMMAND_ID = "boot-ls.client.set-configuration";

	private static final String SQL_DIALECT_SETTING_KEY = SpringProblemCategories
			.problemParameterSettingKey(SpringProblemCategories.DATA_QUERY, "sql-dialect");

	private final SqlDialectResolver sqlDialectResolver;

	public SqlDialectQuickFixProvider(SqlDialectResolver sqlDialectResolver) {
		this.sqlDialectResolver = sqlDialectResolver;
	}

	@Override
	public List<Either<Command, CodeAction>> handle(IJavaProject project, CancelChecker cancelToken,
			CodeActionCapabilities capabilities, CodeActionContext context, TextDocument doc, IRegion region) {
		if (context == null || context.getDiagnostics() == null) {
			return List.of();
		}

		List<Diagnostic> syntaxErrors = context.getDiagnostics().stream()
				.filter(d -> d.getCode() != null && d.getCode().isLeft()
						&& QueryProblemType.SQL_SYNTAX.getCode().equals(d.getCode().getLeft()))
				.toList();
		if (syntaxErrors.isEmpty()) {
			return List.of();
		}

		List<SqlType> applicable = sqlDialectResolver.applicableDialects(project);
		Optional<SqlType> override = sqlDialectResolver.getOverride();
		String selectedSettingValue = override.map(SqlType::getSettingValue)
				.orElse(SqlDialectResolver.AUTO_SETTING_VALUE);

		List<SqlDialectResolver.DialectOption> candidates;
		if (applicable.size() > 1) {
			// Ambiguous classpath: every applicable dialect, plus auto.
			candidates = new ArrayList<>();
			for (SqlType type : applicable) {
				candidates.add(new SqlDialectResolver.DialectOption(type.getLabel(), type.getSettingValue()));
			}
			candidates.add(new SqlDialectResolver.DialectOption("Auto", SqlDialectResolver.AUTO_SETTING_VALUE));
		} else if (override.isPresent() && !applicable.contains(override.get())) {
			// Unambiguous classpath, but the override doesn't match it: only offer reverting to auto.
			candidates = List.of(new SqlDialectResolver.DialectOption("Auto", SqlDialectResolver.AUTO_SETTING_VALUE));
		} else {
			return List.of();
		}

		List<Either<Command, CodeAction>> fixes = new ArrayList<>();
		for (SqlDialectResolver.DialectOption option : candidates) {
			if (option.settingValue().equals(selectedSettingValue)) {
				continue;
			}
			String title = "Set SQL dialect to " + option.label();
			fixes.add(Either.forRight(createCodeAction(syntaxErrors, title, option.settingValue())));
		}
		return fixes;
	}

	private CodeAction createCodeAction(List<Diagnostic> diagnostics, String title, String dialectSettingValue) {
		Command cmd = new Command();
		cmd.setTitle(title);
		cmd.setCommand(SET_CONFIGURATION_COMMAND_ID);
		cmd.setArguments(List.of(SQL_DIALECT_SETTING_KEY, dialectSettingValue));

		CodeAction ca = new CodeAction();
		ca.setTitle(title);
		ca.setKind(CodeActionKind.QuickFix);
		ca.setDiagnostics(diagnostics);
		ca.setCommand(cmd);
		return ca;
	}

}
