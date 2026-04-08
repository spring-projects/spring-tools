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
package org.springframework.ide.vscode.boot.java;

import static org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemSeverity.WARNING;

import java.util.List;

import org.eclipse.lsp4j.DiagnosticTag;
import org.springframework.ide.vscode.boot.common.SpringProblemCategories;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemCategory;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemSeverity;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemTypeParameter;

public enum SpringAiProblemType implements ProblemType {

	SPRING_AI_TOOL_MISSING_DESCRIPTION(WARNING,
			"Spring AI tool/prompt/resource is missing a description. Descriptions are critical for the LLM to correctly select and invoke the tool.",
			"Missing @Tool description",
			ProblemTypeParameter.none()),

	SPRING_AI_TOOL_DESCRIPTION_TOO_SHORT(WARNING,
			"Spring AI tool/prompt/resource description is too short. A meaningful description helps the LLM decide when and how to invoke the tool.",
			"@Tool description too short",
			List.of(new ProblemTypeParameter(
					"minimum-length",
					"Minimum description length",
					"Minimum required character length for Spring AI tool descriptions (@Tool, @McpTool, @McpPrompt, @McpResource).",
					ProblemTypeParameter.ValueType.INTEGER,
					"30")));

	/** Default for {@code minimum-length} on {@link #SPRING_AI_TOOL_DESCRIPTION_TOO_SHORT} (keep in sync with parameter default). */
	public static final int DEFAULT_TOOL_DESCRIPTION_MIN_LENGTH = 30;

	private final ProblemSeverity defaultSeverity;
	private final String description;
	private final String label;
	private final List<DiagnosticTag> tags;
	private final List<ProblemTypeParameter> parameters;

	private SpringAiProblemType(ProblemSeverity defaultSeverity, String description, String label, List<DiagnosticTag> tags,
			List<ProblemTypeParameter> parameters) {
		this.description = description;
		this.defaultSeverity = defaultSeverity;
		this.label = label;
		this.tags = tags;
		this.parameters = parameters;
	}

	private SpringAiProblemType(ProblemSeverity defaultSeverity, String description, String label,
			List<ProblemTypeParameter> parameters) {
		this(defaultSeverity, description, label, null, parameters);
	}

	@Override
	public ProblemSeverity getDefaultSeverity() {
		return defaultSeverity;
	}

	@Override
	public String getLabel() {
		return label;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public String getCode() {
		return name();
	}

	@Override
	public ProblemCategory getCategory() {
		return SpringProblemCategories.SPRING_AI;
	}

	@Override
	public List<DiagnosticTag> getTags() {
		return tags;
	}

	@Override
	public List<ProblemTypeParameter> getParameters() {
		return parameters;
	}

}
