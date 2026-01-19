/*******************************************************************************
 * Copyright (c) 2022, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.validation.generations.preferences;

import static org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemSeverity.IGNORE;
import static org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemSeverity.INFO;
import static org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemSeverity.WARNING;

import java.util.List;

import org.eclipse.lsp4j.DiagnosticTag;
import org.springframework.ide.vscode.boot.common.SpringProblemCategories;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemCategory;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemSeverity;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;

public enum VersionValidationProblemType implements ProblemType {

	SUPPORTED_OSS_VERSION(IGNORE, "Supported OSS Boot Version", "Supported OSS Boot Version"),
	UNSUPPORTED_OSS_VERSION(WARNING, "Unsupported OSS Version", "Unsupported OSS Version"),

	UNSUPPORTED_COMMERCIAL_VERSION(WARNING, "Unsupported Commercial Version", "Unsupported Commercial Version"),
	SUPPORTED_COMMERCIAL_VERSION(IGNORE, "Supported Commercial Version", "Supported Commercial Version"),
		
	UPDATE_LATEST_MAJOR_VERSION(IGNORE, "Update to Latest Major Version", "Update to Latest Major Version"),
	UPDATE_LATEST_MINOR_VERSION(INFO, "Update to Latest Minor Version", "Update to Latest Minor Version"),
	UPDATE_LATEST_PATCH_VERSION(WARNING, "Update to Latest Patch Version", "Update to Latest Patch Version"),
	
	SPRING_CLOUD_INCOMPATIBLE_BOOT_VERSION(WARNING, "Incompatible Spring Boot Version for Spring Cloud", "Incompatible Spring Boot Version for Spring Cloud");

	private final ProblemSeverity defaultSeverity;
	private final String description;
	private String label;
	private final List<DiagnosticTag> tags;

	private VersionValidationProblemType(ProblemSeverity defaultSeverity, String description, String label, List<DiagnosticTag> tags) {
		this.description = description;
		this.defaultSeverity = defaultSeverity;
		this.label = label;
		this.tags = tags;
	}

	private VersionValidationProblemType(ProblemSeverity defaultSeverity, String description, String label) {
		this(defaultSeverity, description, label, null);
	}

	@Override
	public ProblemSeverity getDefaultSeverity() {
		return defaultSeverity;
	}

	public String getLabel() {
		if (label==null) {
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
		return SpringProblemCategories.VERSION_VALIDATION;
	}

	@Override
	public List<DiagnosticTag> getTags() {
		return tags;
	}

}
