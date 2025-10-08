/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java;

import static org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemSeverity.WARNING;

import java.util.List;

import org.eclipse.lsp4j.DiagnosticTag;
import org.springframework.ide.vscode.boot.common.SpringProblemCategories;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemCategory;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemSeverity;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;

public enum Boot4JavaProblemType implements ProblemType {
	
	REGISTRAR_BEAN_INVALID_ANNOTATION(WARNING, "Bean Registrar cannot be registered as a bean via `@Component` annotations", "Invalid annotation over bean registrar"),
	REGISTRAR_BEAN_DECLARATION(WARNING, "Bean Registrar should be added to a configuration bean via `@Import`", "Not added to configuration via `@Import`"),
	API_VERSIONING_NOT_CONFIGURED(WARNING, "API Versioning used but not configured anywhere", "API Versioning not configured anywhere"),
	API_VERSION_SYNTAX_ERROR(WARNING, "API version cannot be parsed into a standard semantic version", "API version cannot be parsed into a standard semantic version"),
	API_VERSIONING_VIA_PATH_SEGMENT_CONFIGURED_IN_COMBINATION(WARNING, "API versioninig path segment strategy should not be mixed with other strategies", "API versioninig path segment strategy should not be mixed with other strategies"),
	API_VERSIONING_STRATEGY_CONFIGURATION_DUPLICATED(WARNING, "API versioninig strategy is configured multiple times with the same strategy", "API versioninig strategy is configured multiple times with the same strategy");
	
	private final ProblemSeverity defaultSeverity;
	private final String description;
	private String label;
	private final List<DiagnosticTag> tags;

	private Boot4JavaProblemType(ProblemSeverity defaultSeverity, String description, String label, List<DiagnosticTag> tags) {
		this.description = description;
		this.defaultSeverity = defaultSeverity;
		this.label = label;
		this.tags = tags;
	}

	private Boot4JavaProblemType(ProblemSeverity defaultSeverity, String description, String label) {
		this(defaultSeverity, description, label, null);
	}

	private Boot4JavaProblemType(ProblemSeverity defaultSeverity, String description) {
		this(defaultSeverity, description, null);
	}

	@Override
	public ProblemSeverity getDefaultSeverity() {
		return defaultSeverity;
	}

	public String getLabel() {
		if (label == null) {
			label = createDefaultLabel();
		}
		return label;
	}

	@Override
	public String getDescription() {
		return description;
	}

	private String createDefaultLabel() {
		String label = this.toString().substring(5).toLowerCase().replace('_', ' ');
		return Character.toUpperCase(label.charAt(0)) + label.substring(1);
	}

	@Override
	public String getCode() {
		return name();
	}

	@Override
	public ProblemCategory getCategory() {
		return SpringProblemCategories.BOOT_4;
	}

	@Override
	public List<DiagnosticTag> getTags() {
		return tags;
	}
	
}