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
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

/**
 * A JDT-based refactoring that adds a {@code @Query} annotation (JPA, MongoDB, or JDBC)
 * over a Spring Data repository method, or adds missing attributes to an existing one.
 * <p>
 * The method is located by its declaring class FQN, name, and parameter type names.
 * If the annotation is already present, only the attributes that are not yet set are added.
 * <p>
 * Attributes are supplied as name/value pairs where the value is already formatted
 * as a Java source literal (quoted string or text block).
 */
public class AddQueryAnnotationRefactoring implements JdtRefactoring {

	/**
	 * @param name  attribute name (e.g. {@code "value"}, {@code "nativeQuery"})
	 * @param value attribute value as it should appear in source
	 *              (e.g. {@code "\"SELECT ...\""}  or {@code "\"\"\"\nSELECT...\n\"\"\""})
	 */
	public record Attribute(String name, String value) {}

	private final String annotationFqn;
	private final String declaringClassFqn;
	private final String methodName;
	private final List<String> parameterTypeNames;
	private final List<Attribute> attributes;

	public AddQueryAnnotationRefactoring(
			String annotationFqn,
			String declaringClassFqn,
			String methodName,
			List<String> parameterTypeNames,
			List<Attribute> attributes) {
		this.annotationFqn = annotationFqn;
		this.declaringClassFqn = declaringClassFqn;
		this.methodName = methodName;
		this.parameterTypeNames = parameterTypeNames != null ? List.copyOf(parameterTypeNames) : List.of();
		this.attributes = attributes != null ? List.copyOf(attributes) : List.of();
	}

	@Override
	public void apply(ASTRewrite rewrite, CompilationUnit cu) {
		AST ast = cu.getAST();
		MethodDeclaration target = findMethod(cu);
		if (target == null) {
			return;
		}

		Annotation existing = findAnnotation(target);
		if (existing == null) {
			Annotation annotation = buildAnnotation(ast, attributes);
			ListRewrite modifiersRewrite = rewrite.getListRewrite(target, MethodDeclaration.MODIFIERS2_PROPERTY);
			modifiersRewrite.insertFirst(annotation, null);
		} else if (existing instanceof NormalAnnotation normalAnnotation) {
			addMissingAttributes(rewrite, ast, normalAnnotation);
		} else if (existing instanceof SingleMemberAnnotation singleMember) {
			replaceSingleMemberWithNormal(rewrite, ast, singleMember);
		} else if (existing instanceof MarkerAnnotation marker && !attributes.isEmpty()) {
			rewrite.replace(marker, buildAnnotation(ast, attributes), null);
		}

		JdtRefactorUtils.addImport(rewrite, ast, cu,
				new ClassType(extractPackageName(annotationFqn), extractSimpleName(annotationFqn)));
	}

	private void addMissingAttributes(ASTRewrite rewrite, AST ast, NormalAnnotation annotation) {
		Set<String> existingNames = new HashSet<>();
		for (Object val : annotation.values()) {
			if (val instanceof MemberValuePair mvp) {
				existingNames.add(mvp.getName().getIdentifier());
			}
		}

		ListRewrite valuesRewrite = rewrite.getListRewrite(annotation, NormalAnnotation.VALUES_PROPERTY);
		for (Attribute attr : attributes) {
			if (!existingNames.contains(attr.name())) {
				valuesRewrite.insertLast(buildMemberValuePair(ast, attr), null);
			}
		}
	}

	private void replaceSingleMemberWithNormal(ASTRewrite rewrite, AST ast, SingleMemberAnnotation singleMember) {
		Expression existingValue = singleMember.getValue();

		NormalAnnotation replacement = ast.newNormalAnnotation();
		replacement.setTypeName(ast.newSimpleName(extractSimpleName(annotationFqn)));

		@SuppressWarnings("unchecked")
		List<MemberValuePair> values = replacement.values();

		MemberValuePair valuePair = ast.newMemberValuePair();
		valuePair.setName(ast.newSimpleName("value"));
		valuePair.setValue((Expression) ASTNode.copySubtree(ast, existingValue));
		values.add(valuePair);

		Set<String> existingNames = Set.of("value");
		for (Attribute attr : attributes) {
			if (!existingNames.contains(attr.name())) {
				values.add(buildMemberValuePair(ast, attr));
			}
		}

		rewrite.replace(singleMember, replacement, null);
	}

	private MethodDeclaration findMethod(CompilationUnit cu) {
		MethodDeclaration[] result = new MethodDeclaration[1];
		cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				if (matches(node)) {
					result[0] = node;
				}
				return result[0] == null;
			}
		});
		return result[0];
	}

	private boolean matches(MethodDeclaration node) {
		if (!node.getName().getIdentifier().equals(methodName)) {
			return false;
		}

		IMethodBinding binding = node.resolveBinding();
		if (binding != null && binding.getDeclaringClass() != null) {
			String fqn = binding.getDeclaringClass().getQualifiedName();
			if (!fqn.equals(declaringClassFqn)) {
				return false;
			}
		}

		@SuppressWarnings("unchecked")
		List<SingleVariableDeclaration> params = node.parameters();
		if (params.size() != parameterTypeNames.size()) {
			return false;
		}

		if (binding != null) {
			ITypeBinding[] paramTypes = binding.getParameterTypes();
			for (int i = 0; i < paramTypes.length; i++) {
				if (!paramTypes[i].getName().equals(parameterTypeNames.get(i))) {
					return false;
				}
			}
		}

		return true;
	}

	private Annotation findAnnotation(MethodDeclaration node) {
		String simpleName = extractSimpleName(annotationFqn);
		for (Object mod : node.modifiers()) {
			if (mod instanceof Annotation a) {
				String name = a.getTypeName().getFullyQualifiedName();
				if (name.equals(annotationFqn) || name.equals(simpleName)) {
					return a;
				}
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private Annotation buildAnnotation(AST ast, List<Attribute> attrs) {
		String simpleName = extractSimpleName(annotationFqn);

		if (attrs.size() == 1 && "value".equals(attrs.get(0).name())) {
			SingleMemberAnnotation sma = ast.newSingleMemberAnnotation();
			sma.setTypeName(ast.newSimpleName(simpleName));
			sma.setValue(buildValue(ast, attrs.get(0)));
			return sma;
		}

		NormalAnnotation annotation = ast.newNormalAnnotation();
		annotation.setTypeName(ast.newSimpleName(simpleName));
		for (Attribute attr : attrs) {
			annotation.values().add(buildMemberValuePair(ast, attr));
		}
		return annotation;
	}

	private MemberValuePair buildMemberValuePair(AST ast, Attribute attr) {
		MemberValuePair pair = ast.newMemberValuePair();
		pair.setName(ast.newSimpleName(attr.name()));
		pair.setValue(buildValue(ast, attr));
		return pair;
	}

	private Expression buildValue(AST ast, Attribute attr) {
		if (attr.value().startsWith("\"\"\"")) {
			TextBlock tb = ast.newTextBlock();
			tb.setEscapedValue(attr.value());
			return tb;
		}
		String rawValue = attr.value();
		if (rawValue.startsWith("\"") && rawValue.endsWith("\"")) {
			StringLiteral sl = ast.newStringLiteral();
			sl.setEscapedValue(rawValue);
			return sl;
		}
		StringLiteral sl = ast.newStringLiteral();
		sl.setLiteralValue(rawValue);
		return sl;
	}

	private static String extractSimpleName(String fqn) {
		int lastDot = fqn.lastIndexOf('.');
		return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
	}

	private static String extractPackageName(String fqn) {
		int lastDot = fqn.lastIndexOf('.');
		return lastDot >= 0 ? fqn.substring(0, lastDot) : "";
	}

}
