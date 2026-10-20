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
package org.springframework.ide.vscode.boot.yaml.reconcile;

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
public enum ApplicationYamlProblemType implements ProblemType {

	YAML_SYNTAX_ERROR("Error parsing the input using snakeyaml"),
	YAML_UNKNOWN_PROPERTY(WARNING, "Property-key not found in the configuration metadata on the project's classpath"),
	YAML_VALUE_TYPE_MISMATCH("Expecting a value of a certain type, but value doesn't parse as such"),
	YAML_EXPECT_SCALAR("Expecting a 'scalar' value but found something more complex."),
	YAML_EXPECT_TYPE_FOUND_SEQUENCE("Found a 'sequence' node where a non 'list-like' type is expected"),
	YAML_EXPECT_TYPE_FOUND_MAPPING("Found a 'mapping' node where a type that can't be treated as a 'property map' is expected"),
	YAML_EXPECT_MAPPING("Expecting a 'mapping' node but found something else"),
	YAML_EXPECT_BEAN_PROPERTY_NAME("Expecting a 'bean property' name but found something more complex"),
	YAML_INVALID_BEAN_PROPERTY("Accessing a named property in a type that doesn't provide a property accessor with that name"),
	YAML_DEPRECATED_ERROR(ERROR, "Property is marked as Deprecated(Error)", null, List.of(DiagnosticTag.Deprecated)),
	YAML_DEPRECATED_WARNING(WARNING, "Property is marked as Deprecated(Warning)", null, List.of(DiagnosticTag.Deprecated)),
	YAML_DUPLICATE_KEY("A mapping node contains multiple entries for the same key"),
	YAML_SHOULD_ESCAPE(WARNING, "This key contains special characters and should probably be escaped by surrounding it with '[]'"),
	YAML_PLACEHOLDER_SYNTAX("Syntax error in a ${...} placeholder"),
	YAML_UNRESOLVED_PLACEHOLDER(IGNORE, "Placeholder has no default value and its key is not defined in configuration metadata or in the project's own configuration files, so the value must be provided at runtime");

	private final ProblemSeverity defaultSeverity;
	private final String description;
	private String label;
	private final List<DiagnosticTag> tags;

	private ApplicationYamlProblemType(ProblemSeverity defaultSeverity, String description, String label, List<DiagnosticTag> tags) {
		this.description = description;
		this.defaultSeverity = defaultSeverity;
		this.label = label;
		this.tags = tags;
	}

	private ApplicationYamlProblemType(ProblemSeverity defaultSeverity, String description, String label) {
		this(defaultSeverity, description, label, null);
	}

	private ApplicationYamlProblemType(ProblemSeverity defaultSeverity, String description) {
		this(defaultSeverity, description, null);
	}

	private ApplicationYamlProblemType(String description) {
		this(ERROR, description);
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
		return SpringProblemCategories.YAML;
	}

	@Override
	public List<DiagnosticTag> getTags() {
		return tags;
	}

}
