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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * Quick fix on a {@link QueryProblemType#SQL_SYNTAX} diagnostic that sets the
 * current package's entry in the {@code sql-dialect-overrides} map: offers
 * every applicable dialect plus "auto" when the classpath is ambiguous, or
 * just "auto" when an existing override mismatches an unambiguous classpath.
 * Since the whole map is one setting value, applying the fix means reading
 * it, updating this package's entry, and sending the whole map back via the
 * generic {@value #SET_CONFIGURATION_COMMAND_ID} command.
 */
public class SqlDialectQuickFixProvider implements JavaCodeActionHandler {

	/** Generic "set a configuration value" client command: (key, value). */
	public static final String SET_CONFIGURATION_COMMAND_ID = "boot-ls.client.set-configuration";

	private static final String SQL_DIALECT_OVERRIDES_SETTING_KEY = SpringProblemCategories
			.problemParameterSettingKey(SpringProblemCategories.DATA_QUERY, "sql-dialect-overrides");

	private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");

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

		String packageName = packageName(doc);
		List<SqlType> applicable = sqlDialectResolver.applicableDialects(project);
		SqlType override = sqlDialectResolver.getOverride(packageName);

		List<SqlType> candidates;
		if (applicable.size() > 1) {
			// Ambiguous classpath: every applicable dialect, plus auto.
			candidates = new ArrayList<>(applicable);
			candidates.add(SqlType.AUTO);
		} else if (override != SqlType.AUTO && !applicable.contains(override)) {
			// Unambiguous classpath, but the override doesn't match it: only offer reverting to auto.
			candidates = List.of(SqlType.AUTO);
		} else {
			return List.of();
		}

		List<Either<Command, CodeAction>> fixes = new ArrayList<>();
		for (SqlType option : candidates) {
			if (option == override) {
				continue;
			}
			String title = "Set SQL dialect for package '" + packageName + "' to " + option.getLabel();
			fixes.add(Either.forRight(createCodeAction(syntaxErrors, title, packageName, option)));
		}
		return fixes;
	}

	private static String packageName(TextDocument doc) {
		Matcher m = PACKAGE_DECLARATION.matcher(doc.get());
		return m.find() ? m.group(1) : "";
	}

	/** Skips writing an entry that would just repeat what's already inherited. */
	private void applyOverride(Map<String, SqlType> overrides, String packageName, SqlType dialect) {
		if (dialect == inheritedOverride(packageName)) {
			overrides.remove(packageName);
		} else {
			overrides.put(packageName, dialect);
		}
	}

	/** What {@code packageName} would resolve to if it had no entry of its own. */
	private SqlType inheritedOverride(String packageName) {
		int dot = packageName.lastIndexOf('.');
		return dot < 0 ? SqlType.AUTO : sqlDialectResolver.getOverride(packageName.substring(0, dot));
	}

	private CodeAction createCodeAction(List<Diagnostic> diagnostics, String title, String packageName, SqlType dialect) {
		Map<String, SqlType> overrides = new LinkedHashMap<>(sqlDialectResolver.getOverrides());
		applyOverride(overrides, packageName, dialect);

		Map<String, String> settingValues = new LinkedHashMap<>();
		overrides.forEach((pkg, type) -> settingValues.put(pkg, type.getSettingValue()));

		Command cmd = new Command();
		cmd.setTitle(title);
		cmd.setCommand(SET_CONFIGURATION_COMMAND_ID);
		cmd.setArguments(List.of(SQL_DIALECT_OVERRIDES_SETTING_KEY, settingValues));

		CodeAction ca = new CodeAction();
		ca.setTitle(title);
		ca.setKind(CodeActionKind.QuickFix);
		ca.setDiagnostics(diagnostics);
		ca.setCommand(cmd);
		return ca;
	}

}
