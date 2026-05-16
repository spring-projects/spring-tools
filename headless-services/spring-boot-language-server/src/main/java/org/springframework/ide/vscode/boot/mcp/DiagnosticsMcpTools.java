/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.mcp;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.MarkupContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.java.reconcilers.CachedDiagnostic;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.stereotype.Component;

/**
 * MCP tools for accessing project diagnostics produced by the Spring Tools language server.
 * Diagnostics are read on demand from the index cache — no extra in-memory state is kept.
 *
 * @author Martin Lippert
 */
@Component
public class DiagnosticsMcpTools {

	private static final Logger logger = LoggerFactory.getLogger(DiagnosticsMcpTools.class);

	private final JavaProjectFinder projectFinder;
	private final SpringSymbolIndex symbolIndex;

	public DiagnosticsMcpTools(JavaProjectFinder projectFinder, SpringSymbolIndex symbolIndex) {
		this.projectFinder = projectFinder;
		this.symbolIndex = symbolIndex;
	}

	/**
	 * A single diagnostic issue reported for a source file in the project.
	 *
	 * @param uri         document URI of the affected source file
	 * @param startLine   0-based start line
	 * @param startColumn 0-based start character offset
	 * @param endLine     0-based end line
	 * @param endColumn   0-based end character offset
	 * @param severity    "error", "warning", "info", "hint", or "unspecified"
	 * @param message     human-readable diagnostic message
	 * @param code        optional diagnostic code (may be null)
	 * @param source      optional tool that produced the diagnostic (may be null)
	 */
	public static record ProjectDiagnostic(
			String uri,
			int startLine,
			int startColumn,
			int endLine,
			int endColumn,
			String severity,
			String message,
			String code,
			String source
	) {}

	@Tool(description = """
			Returns all current Spring Tools diagnostics or problems or validations (errors, warnings, infos, hints) for a specific project.
			Diagnostics are produced by the Spring Tools language server during indexing and validation.
			Each entry identifies the source file, the exact source location, the severity, and the message.
			Only diagnostics known to Spring Tools are included; general Java compiler errors are not.
			Use getProjectList to obtain valid project names.
			""")
	public List<ProjectDiagnostic> getProjectDiagnostics(
			@ToolParam(description = "IDE project name from getProjectList().projectName (case-insensitive match)") String projectName)
			throws Exception {

		logger.info("get diagnostics for project: {}", projectName);

		symbolIndex.waitOperation().get(10, TimeUnit.SECONDS);

		IJavaProject project = getProject(projectName);

		List<CachedDiagnostic> cached = symbolIndex.getJavaIndexer()
				.getCacheHelper()
				.getAllCachedDiagnostics(project);

		List<ProjectDiagnostic> result = cached.stream()
				.map(cd -> new ProjectDiagnostic(
						cd.getDocURI(),
						cd.getDiagnostic().getRange().getStart().getLine(),
						cd.getDiagnostic().getRange().getStart().getCharacter(),
						cd.getDiagnostic().getRange().getEnd().getLine(),
						cd.getDiagnostic().getRange().getEnd().getCharacter(),
						severityToString(cd.getDiagnostic().getSeverity()),
						extractMessage(cd),
						extractCode(cd),
						cd.getDiagnostic().getSource()
				))
				.toList();

		logger.info("found {} diagnostics for project: {}", result.size(), projectName);
		return result;
	}

	private String severityToString(DiagnosticSeverity severity) {
		if (severity == null) {
			return "unspecified";
		}
		return switch (severity) {
			case Error -> "error";
			case Warning -> "warning";
			case Information -> "info";
			case Hint -> "hint";
		};
	}

	private String extractMessage(CachedDiagnostic cd) {
		var message = cd.getDiagnostic().getMessage();
		if (message == null) {
			return null;
		}
		if (message.isLeft()) {
			return message.getLeft();
		}
		MarkupContent markup = message.getRight();
		return markup != null ? markup.getValue() : null;
	}

	private String extractCode(CachedDiagnostic cd) {
		var code = cd.getDiagnostic().getCode();
		if (code == null) {
			return null;
		}
		if (code.isLeft()) {
			return code.getLeft();
		}
		if (code.isRight()) {
			return String.valueOf(code.getRight());
		}
		return null;
	}

	private IJavaProject getProject(String projectName) throws Exception {
		Optional<? extends IJavaProject> found = projectFinder.all().stream()
				.filter(project -> project.getElementName().equalsIgnoreCase(projectName))
				.findFirst();

		if (found.isEmpty()) {
			throw new Exception("project with name " + projectName + " not found");
		}
		return found.get();
	}

}
