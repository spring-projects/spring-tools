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
package org.springframework.ide.vscode.boot.java.utils;

import java.util.Objects;

/**
 * Fully qualified name of a Java type referenced as a dependency from a source file.
 */
public record QualifiedTypeName(String name) {

	public QualifiedTypeName {
		Objects.requireNonNull(name);
	}

	public static QualifiedTypeName of(String qualifiedName) {
		return new QualifiedTypeName(qualifiedName);
	}
}
