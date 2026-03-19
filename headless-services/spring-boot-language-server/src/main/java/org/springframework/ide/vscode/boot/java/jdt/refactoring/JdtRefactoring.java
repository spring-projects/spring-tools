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

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

/**
 * A JDT-based refactoring that records AST modifications into an {@link ASTRewrite}.
 * <p>
 * Implementations must be concrete, named classes (not lambdas or anonymous classes)
 * because they are serialized/deserialized over the LSP protocol using
 * {@link org.springframework.ide.vscode.commons.RuntimeTypeAdapterFactory}.
 * All instance state must be Gson-serializable (records, primitives, collections
 * of serializable types).
 * <p>
 * Multiple {@code JdtRefactoring} instances may share a single {@link ASTRewrite}
 * when applied to the same {@link CompilationUnit}.
 */
public interface JdtRefactoring {

	/**
	 * Record all AST modifications into the given rewrite.
	 *
	 * @param rewrite the shared {@link ASTRewrite} to record changes into
	 * @param cu      the parsed {@link CompilationUnit} (must be the same instance
	 *                that the rewrite was created from)
	 */
	void apply(ASTRewrite rewrite, CompilationUnit cu);

}
