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

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.springframework.ide.vscode.boot.java.Annotations;

/**
 * A JDT-based refactoring that replaces a {@code @Scope("request"/"session"/"application")}
 * annotation with the equivalent, more specific {@code @RequestScope}, {@code @SessionScope},
 * or {@code @ApplicationScope} annotation.
 * <p>
 * {@code @RequestScope}/{@code @SessionScope}/{@code @ApplicationScope} default their
 * {@code proxyMode} attribute to {@code ScopedProxyMode.TARGET_CLASS}, whereas plain
 * {@code @Scope} defaults it to {@code ScopedProxyMode.DEFAULT} (effectively no proxy) - so
 * swapping the annotation is only a no-op replacement when the original {@code proxyMode}
 * already matches the new default. To stay behavior-preserving in every other case, a non-null
 * {@code proxyModeConstant} (e.g. {@code "DEFAULT"}, {@code "NO"}, {@code "INTERFACES"}) is
 * carried over explicitly onto the new annotation, e.g. {@code @RequestScope(proxyMode =
 * ScopedProxyMode.DEFAULT)}. Passing {@code null} instead produces the bare form (e.g.
 * {@code @RequestScope}), letting its {@code TARGET_CLASS} default take effect - which is only
 * behavior-preserving when the original annotation's effective proxy mode was already
 * {@code TARGET_CLASS}.
 */
public class ReplaceScopeAnnotationRefactoring implements JdtRefactoring {

	private final int declarationOffset;
	private final String targetAnnotationFqn;
	private final String proxyModeConstant;

	/**
	 * @param declarationOffset   start position of the class or method declaration carrying the
	 *                            {@code @Scope} annotation to replace
	 * @param targetAnnotationFqn fully qualified name of the replacement annotation, e.g.
	 *                            {@link Annotations#SPRING_REQUEST_SCOPE}
	 * @param proxyModeConstant   simple name of the {@code ScopedProxyMode} enum constant to set
	 *                            explicitly on the new annotation (e.g. {@code "DEFAULT"}), or
	 *                            {@code null} to omit the {@code proxyMode} attribute entirely
	 */
	public ReplaceScopeAnnotationRefactoring(int declarationOffset, String targetAnnotationFqn, String proxyModeConstant) {
		this.declarationOffset = declarationOffset;
		this.targetAnnotationFqn = targetAnnotationFqn;
		this.proxyModeConstant = proxyModeConstant;
	}

	@Override
	public void apply(ASTRewrite rewrite, CompilationUnit cu) {
		BodyDeclaration declaration = JdtRefactorUtils.findAncestorAtOffset(cu, declarationOffset, BodyDeclaration.class);
		if (declaration == null) {
			return;
		}

		Annotation original = JdtRefactorUtils.findAnnotationByName(declaration, Annotations.SCOPE);
		if (original == null) {
			return;
		}

		AST ast = cu.getAST();
		String targetSimpleName = JdtRefactorUtils.extractSimpleName(targetAnnotationFqn);

		Annotation replacement = proxyModeConstant == null
				? bareAnnotation(ast, targetSimpleName)
				: annotationWithProxyMode(ast, targetSimpleName, proxyModeConstant);

		rewrite.replace(original, replacement, null);

		JdtRefactorUtils.addImport(rewrite, ast, cu,
				new ClassType(JdtRefactorUtils.extractPackageName(targetAnnotationFqn), targetSimpleName));
		if (proxyModeConstant != null) {
			JdtRefactorUtils.addImport(rewrite, ast, cu,
					new ClassType(JdtRefactorUtils.extractPackageName(Annotations.SCOPED_PROXY_MODE),
							JdtRefactorUtils.extractSimpleName(Annotations.SCOPED_PROXY_MODE)));
		}
		JdtRefactorUtils.removeImports(cu, rewrite, Annotations.SCOPE);
	}

	private static MarkerAnnotation bareAnnotation(AST ast, String simpleName) {
		MarkerAnnotation marker = ast.newMarkerAnnotation();
		marker.setTypeName(ast.newSimpleName(simpleName));
		return marker;
	}

	@SuppressWarnings("unchecked")
	private static NormalAnnotation annotationWithProxyMode(AST ast, String simpleName, String proxyModeConstant) {
		NormalAnnotation annotation = ast.newNormalAnnotation();
		annotation.setTypeName(ast.newSimpleName(simpleName));

		MemberValuePair pair = ast.newMemberValuePair();
		pair.setName(ast.newSimpleName("proxyMode"));
		pair.setValue(ast.newQualifiedName(
				ast.newSimpleName(JdtRefactorUtils.extractSimpleName(Annotations.SCOPED_PROXY_MODE)),
				ast.newSimpleName(proxyModeConstant)));

		List<MemberValuePair> values = annotation.values();
		values.add(pair);
		return annotation;
	}

}
