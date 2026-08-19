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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

/**
 * A JDT-based refactoring that replaces one or more {@code @Bean} methods' declared
 * return type with the concrete type actually returned, per the diagnostics already
 * computed by {@code PreciseBeanTypeReconciler}.
 * <p>
 * A single {@link Fix} backs the single-method quick fix; passing every mismatch found
 * in a file backs the "fix all in file" quick fix (mirroring
 * {@link RemoveAnnotationRefactoring}'s single-vs-batch {@code offsets} pattern, generalized
 * to carry a type replacement rather than just a location).
 */
public class PreciseBeanTypeRefactoring implements JdtRefactoring {

	/**
	 * A single {@code @Bean} method's return type replacement.
	 *
	 * @param methodOffset             start position of the {@code @Bean} method's declaration
	 * @param newReturnType            type string (parseable by {@link JavaType#parse(String)})
	 *                                 of the concrete type to use as the new return type
	 * @param oldReturnTypeErasureFqn  fully qualified erasure name of the currently declared
	 *                                 return type, whose import should be dropped if it becomes
	 *                                 unused, or {@code null}/empty if there is nothing to remove
	 */
	public record Fix(int methodOffset, String newReturnType, String oldReturnTypeErasureFqn) {
	}

	private final List<Fix> fixes;

	public PreciseBeanTypeRefactoring(Fix... fixes) {
		this(List.of(fixes));
	}

	public PreciseBeanTypeRefactoring(List<Fix> fixes) {
		this.fixes = fixes;
	}

	@Override
	public void apply(ASTRewrite rewrite, CompilationUnit cu) {
		AST ast = cu.getAST();
		Set<String> fqnsToRemove = new LinkedHashSet<>();

		for (Fix fix : fixes) {
			MethodDeclaration method = findMethodAtOffset(cu, fix.methodOffset());
			if (method == null || method.getReturnType2() == null) {
				continue;
			}

			JavaType type = JavaType.parse(fix.newReturnType());
			rewrite.replace(method.getReturnType2(), type.toType(ast), null);

			for (ClassType cn : type.getAllClassNames()) {
				JdtRefactorUtils.addImport(rewrite, ast, cu, cn);
			}

			String oldFqn = fix.oldReturnTypeErasureFqn();
			if (oldFqn != null && !oldFqn.isEmpty()) {
				fqnsToRemove.add(oldFqn);
			}
		}

		if (!fqnsToRemove.isEmpty()) {
			JdtRefactorUtils.removeImports(cu, rewrite, fqnsToRemove.toArray(new String[0]));
		}
	}

	private static MethodDeclaration findMethodAtOffset(CompilationUnit cu, int offset) {
		ASTNode node = NodeFinder.perform(cu, offset, 0);
		while (node != null && !(node instanceof MethodDeclaration)) {
			node = node.getParent();
		}
		return (MethodDeclaration) node;
	}

}
