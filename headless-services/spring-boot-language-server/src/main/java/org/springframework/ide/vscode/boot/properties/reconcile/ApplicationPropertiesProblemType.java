/*******************************************************************************
 * Copyright (c) 2015, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.properties.reconcile;

import static org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemSeverity.ERROR;
import static org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemSeverity.IGNORE;
import static org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemSeverity.WARNING;

import java.util.List;

import org.eclipse.lsp4j.DiagnosticTag;
import org.springframework.ide.vscode.boot.common.SpringProblemCategories;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemCategory;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemSeverity;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;

/**
 * @author Kris De Volder
 */
public enum ApplicationPropertiesProblemType implements ProblemType {
	
	PROP_INVALID_BEAN_NAVIGATION("Accessing a 'bean property' in a type that doesn't have properties (e.g. like String or Integer)"),
	PROP_INVALID_INDEXED_NAVIGATION("Accessing a property using [] in a type that doesn't support that"),
	PROP_EXPECTED_DOT_OR_LBRACK("Unexpected character found where a '.' or '[' was expected"),
	PROP_NO_MATCHING_RBRACK("Found a '[' but no matching ']'"),
	PROP_NON_INTEGER_IN_BRACKETS("Use of [..] navigation with non-integer value"),
	PROP_VALUE_TYPE_MISMATCH("Expecting a value of a certain type, but value doesn't parse as such"),
	PROP_INVALID_BEAN_PROPERTY("Accessing a named property in a type that doesn't provide a property accessor with that name"),
	PROP_UNKNOWN_PROPERTY(WARNING, "Property-key not found in any configuration metadata on the project's classpath"),
	PROP_DEPRECATED(WARNING, "Property is marked as Deprecated", null, List.of(DiagnosticTag.Deprecated)),
	PROP_DUPLICATE_KEY("Multiple assignments to the same property value"),
	PROP_SYNTAX_ERROR("Syntax Error"),
	PROP_PLACEHOLDER_SYNTAX(WARNING, "Syntax error in a ${...} placeholder"),
	PROP_UNRESOLVED_PLACEHOLDER(IGNORE, "Placeholder has no default value and its key could not be found in configuration metadata or in the project's own configuration files, so make sure it is defined at runtime");

	private final ProblemSeverity defaultSeverity;
	private final String description;
	private String label;
	private final List<DiagnosticTag> tags;

	private ApplicationPropertiesProblemType(ProblemSeverity defaultSeverity, String description, String label, List<DiagnosticTag> tags) { 
		this.description = description;
		this.defaultSeverity = defaultSeverity;
		this.label = label;
		this.tags = tags;
	}

	private ApplicationPropertiesProblemType(ProblemSeverity defaultSeverity, String description, String label) {
		this(defaultSeverity, description, label, null);
	}

	private ApplicationPropertiesProblemType(ProblemSeverity defaultSeverity, String description) {
		this(defaultSeverity, description, null);
	}

	private ApplicationPropertiesProblemType(String description) {
		this(ERROR, description);
	}

	public ProblemSeverity getDefaultSeverity() {
		return defaultSeverity;
	}

	public String getLabel() {
		if (label == null) {
			label = createDefaultLabel();
		}
		return label;
	}

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
		return SpringProblemCategories.PROPERTIES;
	}

	@Override
	public List<DiagnosticTag> getTags() {
		return tags;
	}

}
