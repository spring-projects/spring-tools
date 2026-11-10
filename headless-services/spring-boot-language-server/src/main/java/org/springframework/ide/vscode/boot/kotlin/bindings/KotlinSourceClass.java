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
package org.springframework.ide.vscode.boot.kotlin.bindings;

import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax;

/**
 * A class that is declared in a Kotlin source file of the project.
 *
 * @param binaryName the JVM name of the class: package, then the names of the enclosing classes
 *        separated by '$', then the name
 * @param file the file the class is declared in
 * @param outer the class this class is nested in, null for top-level classes
 *
 * @author Martin Lippert
 */
public record KotlinSourceClass(String binaryName, KotlinSyntax.File file, KotlinSyntax.ClassDeclaration declaration, KotlinSourceClass outer) {

	public static KotlinSourceClass topLevel(KotlinSyntax.File file, KotlinSyntax.ClassDeclaration declaration) {
		String packageName = file.packageName();
		return new KotlinSourceClass(packageName.isEmpty() ? declaration.name() : packageName + "." + declaration.name(), file, declaration, null);
	}

	public KotlinSourceClass nested(KotlinSyntax.ClassDeclaration nestedDeclaration) {
		return new KotlinSourceClass(binaryName + "$" + nestedDeclaration.name(), file, nestedDeclaration, this);
	}

}
