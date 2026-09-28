/*******************************************************************************
 * Copyright (c) 2024, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data.jpa.queries;

public enum SqlType {

	/** No override - infer from the classpath. Also the fallback for an unrecognized entry. */
	AUTO("Auto", "auto"),
	MYSQL("MySQL", "mysql"),
	POSTGRESQL("PostgreSQL", "postgresql");

	private final String label;
	private final String settingValue;

	SqlType(String label, String settingValue) {
		this.label = label;
		this.settingValue = settingValue;
	}

	/** Human-readable name, e.g. for diagnostic messages and quick fix titles. */
	public String getLabel() {
		return label;
	}

	/** The value this dialect is represented by in {@code sql-dialect-overrides}. */
	public String getSettingValue() {
		return settingValue;
	}

	/**
	 * Case-insensitive lookup by {@link #getSettingValue()}; anything
	 * unrecognized (including {@code null}) is treated as {@link #AUTO}.
	 */
	public static SqlType fromSettingValue(String settingValue) {
		for (SqlType type : values()) {
			if (type.settingValue.equalsIgnoreCase(settingValue)) {
				return type;
			}
		}
		return AUTO;
	}

}
