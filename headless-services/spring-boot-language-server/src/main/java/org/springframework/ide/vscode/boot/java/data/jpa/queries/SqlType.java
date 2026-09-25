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

	MYSQL("MySQL", "mysql"),
	POSTGRESQL("PostgreSQL", "postgresql");

	private final String label;
	private final String settingValue;

	SqlType(String label, String settingValue) {
		this.label = label;
		this.settingValue = settingValue;
	}

	/**
	 * Human-readable name, e.g. for diagnostic messages and quick fix titles.
	 */
	public String getLabel() {
		return label;
	}

	/**
	 * The value this dialect is represented by in the
	 * {@code spring-boot.ls.problem-parameters.data-query.sql-dialect} setting.
	 */
	public String getSettingValue() {
		return settingValue;
	}

}
