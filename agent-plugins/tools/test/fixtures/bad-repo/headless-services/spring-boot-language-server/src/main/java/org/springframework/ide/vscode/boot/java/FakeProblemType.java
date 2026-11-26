/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java;

// Test fixture for agent-plugins/tools: a minimal problem-type enum with three codes.
public enum FakeProblemType implements ProblemType {

	GOOD_CODE(WARNING, "A code with a valid playbook", "Good code"),
	MISSING_CODE(WARNING, "A code without a playbook", "Missing code"),
	REF_TARGET(INFO, "A code with a malformed playbook", "Ref target");

	public static final int NOT_A_CODE = 30;

	private final String description;

	private FakeProblemType(Object severity, String description, String label) {
		this.description = description;
	}

	@Override
	public String getCode() {
		return name();
	}
}
