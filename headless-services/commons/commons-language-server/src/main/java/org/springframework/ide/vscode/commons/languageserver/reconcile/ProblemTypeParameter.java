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
package org.springframework.ide.vscode.commons.languageserver.reconcile;

import java.util.List;

/**
 * Optional configuration for a {@link ProblemType}, surfaced in client settings and
 * {@code problem-types.json} metadata.
 */
public final class ProblemTypeParameter {

	public enum ValueType {
		INTEGER,
		STRING,
		BOOLEAN
	}

	private final String key;
	private final String label;
	private final String description;
	private final ValueType type;
	private final String defaultValue;

	public ProblemTypeParameter(String key, String label, String description, ValueType type, String defaultValue) {
		this.key = key;
		this.label = label;
		this.description = description;
		this.type = type;
		this.defaultValue = defaultValue;
	}

	public String getKey() {
		return key;
	}

	public String getLabel() {
		return label;
	}

	public String getDescription() {
		return description;
	}

	public ValueType getType() {
		return type;
	}

	public String getDefaultValue() {
		return defaultValue;
	}

	public static List<ProblemTypeParameter> none() {
		return List.of();
	}
}
