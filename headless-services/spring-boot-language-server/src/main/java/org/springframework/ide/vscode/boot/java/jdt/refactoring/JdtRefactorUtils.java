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
package org.springframework.ide.vscode.boot.java.jdt.refactoring;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

/**
 * Shared utility methods for JDT-based refactorings.
 *
 * @author Alex Boyko
 */
public final class JdtRefactorUtils {

	private JdtRefactorUtils() {
		// utility class
	}

	/**
	 * Add an import for the given {@link ClassType} to the compilation unit, unless
	 * the import is unnecessary.
	 * <p>
	 * An import is considered unnecessary when any of the following is true:
	 * <ul>
	 *   <li>The type is in the {@code java.lang} package</li>
	 *   <li>The type is in the default (unnamed) package</li>
	 *   <li>The type is in the same package as the compilation unit</li>
	 *   <li>An exact import for the type already exists</li>
	 *   <li>A wildcard (on-demand) import already covers the type's package</li>
	 * </ul>
	 * <p>
	 * When an import is added, it is inserted in lexicographic sorted order among
	 * the existing imports.
	 *
	 * @param rewrite   the {@link ASTRewrite} to record the change
	 * @param ast       the AST factory
	 * @param cu        the compilation unit
	 * @param className the class name to import
	 */
	public static void addImport(ASTRewrite rewrite, AST ast, CompilationUnit cu, ClassType className) {
		String packageName = className.getPackageName();

		// Don't add import for java.lang types
		if ("java.lang".equals(packageName)) {
			return;
		}

		// Don't add import for default package types
		if (packageName.isEmpty()) {
			return;
		}

		// Don't add import if type is in the same package
		if (cu.getPackage() != null) {
			String cuPackage = cu.getPackage().getName().getFullyQualifiedName();
			if (cuPackage.equals(packageName)) {
				return;
			}
		}

		String fullyQualifiedName = className.getFullyQualifiedName();

		// Check if import already exists (exact or wildcard)
		for (Object importObj : cu.imports()) {
			ImportDeclaration imp = (ImportDeclaration) importObj;
			if (imp.isOnDemand()) {
				// Wildcard import like "import java.util.*"
				if (imp.getName().getFullyQualifiedName().equals(packageName)) {
					return;
				}
			} else if (imp.getName().getFullyQualifiedName().equals(fullyQualifiedName)) {
				return;
			}
		}

		ImportDeclaration importDecl = ast.newImportDeclaration();
		importDecl.setName(ast.newName(fullyQualifiedName));

		ListRewrite importsRewrite = rewrite.getListRewrite(cu, CompilationUnit.IMPORTS_PROPERTY);

		// Insert in sorted order
		ImportDeclaration insertBefore = null;
		for (Object importObj : cu.imports()) {
			ImportDeclaration existing = (ImportDeclaration) importObj;
			if (existing.getName().getFullyQualifiedName().compareTo(fullyQualifiedName) > 0) {
				insertBefore = existing;
				break;
			}
		}

		if (insertBefore != null) {
			importsRewrite.insertBefore(importDecl, insertBefore, null);
		} else {
			importsRewrite.insertLast(importDecl, null);
		}
	}

}
