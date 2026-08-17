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
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;

/**
 * A JDT-based refactoring that moves a path-like value off a {@code @Controller}
 * or {@code @RestController} annotation and onto a class-level {@code @RequestMapping}
 * (that attribute names the Spring bean, not a route, so a path there is almost
 * always accidental).
 * <p>
 * Self-scans the given {@link CompilationUnit} for every offending class rather
 * than being anchored to a single offset, so the same instance can be used both
 * for a single-file "fix all in file" quick fix and, in principle, for a wider
 * multi-file scope.
 */
public class MovePathToRequestMappingRefactoring implements JdtRefactoring {

	private static final Set<String> CONTROLLER_ANNOTATION_SIMPLE_NAMES = Set.of(
			JdtRefactorUtils.extractSimpleName(Annotations.CONTROLLER),
			JdtRefactorUtils.extractSimpleName(Annotations.REST_CONTROLLER));

	@Override
	public void apply(ASTRewrite rewrite, CompilationUnit cu) {
		AST ast = cu.getAST();
		cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(TypeDeclaration node) {
				processType(rewrite, ast, cu, node);
				return true;
			}
		});
	}

	private void processType(ASTRewrite rewrite, AST ast, CompilationUnit cu, TypeDeclaration type) {
		Annotation controllerAnnotation = findControllerAnnotation(type);
		if (controllerAnnotation != null) {
			String path = extractPathValue(controllerAnnotation);
			if (path != null) {
				removeValueFromAnnotation(rewrite, ast, controllerAnnotation);

				Annotation requestMapping = findAnnotationBySimpleName(type, "RequestMapping");
				if (requestMapping != null) {
					updateRequestMappingValue(rewrite, ast, requestMapping, path);
				} else {
					insertNewRequestMapping(rewrite, ast, cu, type, path);
				}
			}
		}
	}

	private static Annotation findControllerAnnotation(TypeDeclaration type) {
		for (Object mod : type.modifiers()) {
			if (mod instanceof Annotation a
					&& CONTROLLER_ANNOTATION_SIMPLE_NAMES.contains(JdtRefactorUtils.extractSimpleName(a.getTypeName().getFullyQualifiedName()))) {
				return a;
			}
		}
		return null;
	}

	private static Annotation findAnnotationBySimpleName(TypeDeclaration type, String simpleName) {
		for (Object mod : type.modifiers()) {
			if (mod instanceof Annotation a && simpleName.equals(a.getTypeName().getFullyQualifiedName())) {
				return a;
			}
		}
		return null;
	}

	/**
	 * Extracts the path value from either the bare-literal shorthand
	 * ({@code @Controller("/path")}) or the keyword form
	 * ({@code @Controller(value = "/path")}), returning {@code null} unless the
	 * value looks like a path (contains a {@code /}).
	 */
	private static String extractPathValue(Annotation annotation) {
		Expression value = null;
		if (annotation instanceof SingleMemberAnnotation sma) {
			value = sma.getValue();
		} else if (annotation instanceof NormalAnnotation na) {
			value = findValuePairValue(na);
		}

		if (value != null) {
			String stringValue = ASTUtils.getExpressionValueAsString(value, t -> {});
			if (stringValue != null && stringValue.contains("/")) {
				return stringValue;
			}
		}
		return null;
	}

	private static Expression findValuePairValue(NormalAnnotation na) {
		MemberValuePair pair = findValuePair(na);
		return pair == null ? null : pair.getValue();
	}

	private static MemberValuePair findValuePair(NormalAnnotation na) {
		for (Object o : na.values()) {
			MemberValuePair pair = (MemberValuePair) o;
			if ("value".equals(pair.getName().getIdentifier())) {
				return pair;
			}
		}
		return null;
	}

	private static void removeValueFromAnnotation(ASTRewrite rewrite, AST ast, Annotation annotation) {
		if (annotation instanceof SingleMemberAnnotation) {
			rewrite.replace(annotation, JdtRefactorUtils.markerAnnotationLike(ast, annotation), null);
		} else if (annotation instanceof NormalAnnotation na) {
			if (na.values().size() == 1) {
				rewrite.replace(annotation, JdtRefactorUtils.markerAnnotationLike(ast, annotation), null);
			} else {
				MemberValuePair pair = findValuePair(na);
				if (pair != null) {
					rewrite.getListRewrite(na, NormalAnnotation.VALUES_PROPERTY).remove(pair, null);
				}
			}
		}
	}

	private static void updateRequestMappingValue(ASTRewrite rewrite, AST ast, Annotation requestMapping, String path) {
		if (requestMapping instanceof MarkerAnnotation marker) {
			SingleMemberAnnotation replacement = ast.newSingleMemberAnnotation();
			replacement.setTypeName((Name) ASTNode.copySubtree(ast, marker.getTypeName()));
			replacement.setValue(JdtRefactorUtils.newStringLiteral(ast, path));
			rewrite.replace(marker, replacement, null);
		} else if (requestMapping instanceof SingleMemberAnnotation sma) {
			rewrite.replace(sma.getValue(), JdtRefactorUtils.newStringLiteral(ast, path), null);
		} else if (requestMapping instanceof NormalAnnotation na) {
			MemberValuePair pair = JdtRefactorUtils.findValueOrPathMemberValuePair(na);
			if (pair != null) {
				rewrite.replace(pair.getValue(), JdtRefactorUtils.newStringLiteral(ast, path), null);
			} else {
				MemberValuePair newPair = ast.newMemberValuePair();
				newPair.setName(ast.newSimpleName("value"));
				newPair.setValue(JdtRefactorUtils.newStringLiteral(ast, path));
				rewrite.getListRewrite(na, NormalAnnotation.VALUES_PROPERTY).insertFirst(newPair, null);
			}
		}
	}

	private static void insertNewRequestMapping(ASTRewrite rewrite, AST ast, CompilationUnit cu, TypeDeclaration type, String path) {
		SingleMemberAnnotation newAnnotation = ast.newSingleMemberAnnotation();
		newAnnotation.setTypeName(ast.newSimpleName(JdtRefactorUtils.extractSimpleName(Annotations.SPRING_REQUEST_MAPPING)));
		newAnnotation.setValue(JdtRefactorUtils.newStringLiteral(ast, path));

		Annotation lastAnnotation = lastAnnotationOf(type);
		ListRewrite modifiersRewrite = rewrite.getListRewrite(type, TypeDeclaration.MODIFIERS2_PROPERTY);
		if (lastAnnotation != null) {
			modifiersRewrite.insertAfter(newAnnotation, lastAnnotation, null);
		} else {
			modifiersRewrite.insertFirst(newAnnotation, null);
		}

		JdtRefactorUtils.addImport(rewrite, ast, cu, new ClassType(
				JdtRefactorUtils.extractPackageName(Annotations.SPRING_REQUEST_MAPPING),
				JdtRefactorUtils.extractSimpleName(Annotations.SPRING_REQUEST_MAPPING)));
	}

	private static Annotation lastAnnotationOf(TypeDeclaration type) {
		Annotation last = null;
		for (Object mod : type.modifiers()) {
			if (mod instanceof Annotation a) {
				last = a;
			}
		}
		return last;
	}

}
