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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

/**
 * A JDT-based refactoring that replaces a class's {@code @Controller} and
 * {@code @ResponseBody} annotations (both together) with
 * {@code @RestController}.
 * <p>
 * {@code @ResponseBody} never has any attributes, and {@code @Controller}'s only
 * attribute ({@code value}, the bean name) has a direct equivalent of the same
 * name on {@code @RestController}, so any bean-name value is carried over as-is.
 * <p>
 * Pass one or more type offsets to convert. When used with a single offset this
 * corresponds to a node-scoped quickfix; with multiple offsets (all occurrences in a file)
 * this corresponds to a file-scoped "fix all" quickfix.
 */
public class RestControllerRefactoring implements JdtRefactoring {

	private static final String CONTROLLER_FQN = "org.springframework.stereotype.Controller";
	private static final String RESPONSE_BODY_FQN = "org.springframework.web.bind.annotation.ResponseBody";
	private static final String REST_CONTROLLER_FQN = "org.springframework.web.bind.annotation.RestController";

	private final int[] typeOffsets;

	/**
	 * @param typeOffsets start positions of the type declarations to convert
	 */
	public RestControllerRefactoring(int... typeOffsets) {
		this.typeOffsets = typeOffsets;
	}

	@Override
	public void apply(ASTRewrite rewrite, CompilationUnit cu) {
		boolean anyConverted = false;
		Set<ASTNode> exactRangeNodes = new HashSet<>();
		for (int offset : typeOffsets) {
			TypeDeclaration type = JdtRefactorUtils.findAncestorAtOffset(cu, offset, TypeDeclaration.class);
			if (type != null && convertType(rewrite, cu, type, exactRangeNodes)) {
				anyConverted = true;
			}
		}

		if (anyConverted) {
			// the two merged annotations are direct children of a rewritten list, so JDT's
			// default target source range would extend onto any unclaimed leading comment
			// (e.g. a line comment sitting between the Javadoc and the annotations) and delete
			// it along with the annotation it is replacing/removing
			rewrite.setTargetSourceRangeComputer(JdtRefactorUtils.exactRangeSourceComputer(exactRangeNodes));

			AST ast = cu.getAST();
			JdtRefactorUtils.addImport(rewrite, ast, cu,
					new ClassType(JdtRefactorUtils.extractPackageName(REST_CONTROLLER_FQN),
							JdtRefactorUtils.extractSimpleName(REST_CONTROLLER_FQN)));
			JdtRefactorUtils.removeImports(cu, rewrite, CONTROLLER_FQN, RESPONSE_BODY_FQN);
		}
	}

	private boolean convertType(ASTRewrite rewrite, CompilationUnit cu, TypeDeclaration type,
			Set<ASTNode> exactRangeNodes) {
		AST ast = cu.getAST();

		Annotation controllerAnnotation = JdtRefactorUtils.findAnnotationByName(type, CONTROLLER_FQN);
		Annotation responseBodyAnnotation = JdtRefactorUtils.findAnnotationByName(type, RESPONSE_BODY_FQN);

		if (controllerAnnotation == null || responseBodyAnnotation == null) {
			return false;
		}

		Annotation newAnnotation = JdtRefactorUtils.copyAnnotationWithNewTypeName(ast, controllerAnnotation,
				JdtRefactorUtils.extractSimpleName(REST_CONTROLLER_FQN));

		List<Annotation> merged = List.of(controllerAnnotation, responseBodyAnnotation);
		JdtRefactorUtils.replaceWithMergedAnnotation(rewrite, type, TypeDeclaration.MODIFIERS2_PROPERTY, merged,
				newAnnotation, exactRangeNodes);

		return true;
	}

}
