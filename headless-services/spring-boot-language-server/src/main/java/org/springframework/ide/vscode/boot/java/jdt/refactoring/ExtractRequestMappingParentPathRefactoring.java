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

import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.requestmapping.WebEndpointIndexer;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;

/**
 * A JDT-based refactoring that extracts the common parent path shared by a controller's
 * method-level request mapping annotations (e.g. {@code @GetMapping}, {@code @PostMapping})
 * into a class-level {@code @RequestMapping}, stripping that shared prefix from each
 * method's own path.
 * <p>
 * If the controller already has a class-level {@code @RequestMapping} with a literal path,
 * the common prefix is merged into it (its path is updated to include the additional shared
 * segment) rather than inserting a second annotation; otherwise a new class-level
 * {@code @RequestMapping} is inserted.
 * <p>
 * Method-level mapping annotations are re-located by their declaring method's offset and
 * matched by annotation simple name only (not resolved bindings), mirroring the identification
 * already performed by the reconciler that produced this refactoring's {@link JdtFixDescriptor}.
 */
public class ExtractRequestMappingParentPathRefactoring implements JdtRefactoring {

	private static final Set<String> MAPPING_ANNOTATION_SIMPLE_NAMES = Set.of(
			"RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping");

	private final int classOffset;
	private final String methodPathPrefix;
	private final String classAnnotationPath;
	private final int[] methodOffsets;

	/**
	 * @param classOffset          start position of the controller's {@link TypeDeclaration}
	 * @param methodPathPrefix     the common parent path to strip from each method's own path,
	 *                             e.g. {@code "/bar"}
	 * @param classAnnotationPath  the path the class-level {@code @RequestMapping} should end up
	 *                             with, e.g. {@code "/foo/bar"} when merging into an existing
	 *                             {@code @RequestMapping("/foo")}, or just {@code methodPathPrefix}
	 *                             when there is no existing class-level mapping
	 * @param methodOffsets        start positions of the method declarations whose mapping
	 *                             annotation should have {@code methodPathPrefix} stripped from it
	 */
	public ExtractRequestMappingParentPathRefactoring(int classOffset, String methodPathPrefix, String classAnnotationPath, int... methodOffsets) {
		this.classOffset = classOffset;
		this.methodPathPrefix = methodPathPrefix;
		this.classAnnotationPath = classAnnotationPath;
		this.methodOffsets = methodOffsets;
	}

	@Override
	public void apply(ASTRewrite rewrite, CompilationUnit cu) {
		TypeDeclaration type = JdtRefactorUtils.findAncestorAtOffset(cu, classOffset, TypeDeclaration.class);
		if (type == null) {
			return;
		}

		AST ast = cu.getAST();
		boolean anyChanged = false;

		for (int offset : methodOffsets) {
			MethodDeclaration method = JdtRefactorUtils.findAncestorAtOffset(cu, offset, MethodDeclaration.class);
			if (method == null) {
				continue;
			}

			Annotation mapping = findMappingAnnotation(method);
			if (mapping != null && stripMethodPathPrefix(rewrite, ast, mapping)) {
				anyChanged = true;
			}
		}

		if (anyChanged) {
			setClassLevelRequestMapping(rewrite, ast, type);
			JdtRefactorUtils.addImport(rewrite, ast, cu,
					new ClassType(JdtRefactorUtils.extractPackageName(Annotations.SPRING_REQUEST_MAPPING),
							JdtRefactorUtils.extractSimpleName(Annotations.SPRING_REQUEST_MAPPING)));
		}
	}

	private boolean stripMethodPathPrefix(ASTRewrite rewrite, AST ast, Annotation mapping) {
		if (mapping instanceof SingleMemberAnnotation sma) {
			String suffix = suffixAfterMethodPathPrefix(sma.getValue());
			if (suffix == null) {
				return false;
			}
			if (suffix.isEmpty()) {
				rewrite.replace(mapping, JdtRefactorUtils.markerAnnotationLike(ast, mapping), null);
			} else {
				rewrite.replace(sma.getValue(), JdtRefactorUtils.newStringLiteral(ast, suffix), null);
			}
			return true;
		} else if (mapping instanceof NormalAnnotation na) {
			MemberValuePair pathPair = JdtRefactorUtils.findValueOrPathMemberValuePair(na);
			if (pathPair == null) {
				return false;
			}
			String suffix = suffixAfterMethodPathPrefix(pathPair.getValue());
			if (suffix == null) {
				return false;
			}
			if (suffix.isEmpty()) {
				if (na.values().size() == 1) {
					rewrite.replace(mapping, JdtRefactorUtils.markerAnnotationLike(ast, mapping), null);
				} else {
					rewrite.getListRewrite(na, NormalAnnotation.VALUES_PROPERTY).remove(pathPair, null);
				}
			} else {
				rewrite.replace(pathPair.getValue(), JdtRefactorUtils.newStringLiteral(ast, suffix), null);
			}
			return true;
		}
		return false;
	}

	/**
	 * Returns what remains of the given path expression's literal value after removing
	 * {@link #methodPathPrefix}, or {@code null} if the expression isn't a resolvable literal
	 * path prefixed by {@link #methodPathPrefix}.
	 */
	private String suffixAfterMethodPathPrefix(Expression pathExpression) {
		String raw = ASTUtils.getExpressionValueAsString(pathExpression);
		if (raw == null) {
			return null;
		}
		String normalized = WebEndpointIndexer.combinePath("", raw);
		if (!normalized.equals(methodPathPrefix) && !normalized.startsWith(methodPathPrefix + "/")) {
			return null;
		}
		return normalized.substring(methodPathPrefix.length());
	}

	/**
	 * Updates the existing class-level {@code @RequestMapping}'s path to {@link #classAnnotationPath},
	 * or inserts a new {@code @RequestMapping(classAnnotationPath)} if the class doesn't have one.
	 */
	private void setClassLevelRequestMapping(ASTRewrite rewrite, AST ast, TypeDeclaration type) {
		Annotation existing = findClassMappingAnnotation(type);
		if (existing == null) {
			SingleMemberAnnotation newAnnotation = ast.newSingleMemberAnnotation();
			newAnnotation.setTypeName(ast.newSimpleName(JdtRefactorUtils.extractSimpleName(Annotations.SPRING_REQUEST_MAPPING)));
			newAnnotation.setValue(JdtRefactorUtils.newStringLiteral(ast, classAnnotationPath));

			ListRewrite modifiersRewrite = rewrite.getListRewrite(type, TypeDeclaration.MODIFIERS2_PROPERTY);
			modifiersRewrite.insertFirst(newAnnotation, null);
		} else if (existing instanceof SingleMemberAnnotation sma) {
			rewrite.replace(sma.getValue(), JdtRefactorUtils.newStringLiteral(ast, classAnnotationPath), null);
		} else if (existing instanceof NormalAnnotation na) {
			MemberValuePair pathPair = JdtRefactorUtils.findValueOrPathMemberValuePair(na);
			if (pathPair != null) {
				rewrite.replace(pathPair.getValue(), JdtRefactorUtils.newStringLiteral(ast, classAnnotationPath), null);
			} else {
				MemberValuePair newPair = ast.newMemberValuePair();
				newPair.setName(ast.newSimpleName("value"));
				newPair.setValue(JdtRefactorUtils.newStringLiteral(ast, classAnnotationPath));
				rewrite.getListRewrite(na, NormalAnnotation.VALUES_PROPERTY).insertFirst(newPair, null);
			}
		} else if (existing instanceof MarkerAnnotation) {
			rewrite.replace(existing, singleMemberAnnotationLike(ast, existing, classAnnotationPath), null);
		}
	}

	private static Annotation findClassMappingAnnotation(TypeDeclaration type) {
		for (Object mod : type.modifiers()) {
			if (mod instanceof Annotation a) {
				String simpleName = JdtRefactorUtils.extractSimpleName(a.getTypeName().getFullyQualifiedName());
				if ("RequestMapping".equals(simpleName)) {
					return a;
				}
			}
		}
		return null;
	}

	private static SingleMemberAnnotation singleMemberAnnotationLike(AST ast, Annotation original, String value) {
		SingleMemberAnnotation sma = ast.newSingleMemberAnnotation();
		sma.setTypeName((Name) ASTNode.copySubtree(ast, original.getTypeName()));
		sma.setValue(JdtRefactorUtils.newStringLiteral(ast, value));
		return sma;
	}

	private static Annotation findMappingAnnotation(MethodDeclaration method) {
		for (Object mod : method.modifiers()) {
			if (mod instanceof Annotation a) {
				String simpleName = JdtRefactorUtils.extractSimpleName(a.getTypeName().getFullyQualifiedName());
				if (MAPPING_ANNOTATION_SIMPLE_NAMES.contains(simpleName)) {
					return a;
				}
			}
		}
		return null;
	}

}
