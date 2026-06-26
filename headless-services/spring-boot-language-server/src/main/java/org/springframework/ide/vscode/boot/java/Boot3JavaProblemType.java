/*******************************************************************************
 * Copyright (c) 2020, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java;

import static org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemSeverity.ERROR;
import static org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemSeverity.WARNING;

import java.util.List;

import org.eclipse.lsp4j.DiagnosticTag;
import org.springframework.ide.vscode.boot.common.SpringProblemCategories;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemCategory;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemSeverity;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;

/**
 * This enum is supposed to represent *all* the different types of problems SpringBoot language server
 * may detect in Java code.
 */
public enum Boot3JavaProblemType implements ProblemType {
	
	JAVA_TYPE_NOT_SUPPORTED(ERROR, "Type no supported as of Spring Boot 3", "Type not supported as of Spring Boot 3"),
	FACTORIES_KEY_NOT_SUPPORTED(ERROR, "Spring factories key not supported", "Spring factories key not supported"),
	MODULITH_TYPE_REF_VIOLATION(ERROR, "Modulith restricted type reference", "Modulith restricted type reference"),
	MODULITH_APPLICATION_MODULE_LISTENER(WARNING,
			"Prefer @ApplicationModuleListener over @Async + @Transactional + @TransactionalEventListener",
			"Use @ApplicationModuleListener");
	
	private final ProblemSeverity defaultSeverity;
	private final String description;
	private String label;
	private final List<DiagnosticTag> tags;

	private Boot3JavaProblemType(ProblemSeverity defaultSeverity, String description, String label, List<DiagnosticTag> tags) {
		this.description = description;
		this.defaultSeverity = defaultSeverity;
		this.label = label;
		this.tags = tags;
	}

	private Boot3JavaProblemType(ProblemSeverity defaultSeverity, String description, String label) {
		this(defaultSeverity, description, label, null);
	}

	private Boot3JavaProblemType(ProblemSeverity defaultSeverity, String description) {
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
		return SpringProblemCategories.BOOT_3;
	}

	@Override
	public List<DiagnosticTag> getTags() {
		return tags;
	}
	
}
