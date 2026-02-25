/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.rewrite.java;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite.Description;
import org.openrewrite.NlsRewrite.DisplayName;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AddOrUpdateAnnotationAttribute;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Annotation;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.J.VariableDeclarations;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AddAnnotationOverMethod extends Recipe {
	
	public record Attribute(String name, String value) {}

	@Override
	public @DisplayName String getDisplayName() {
		return "Add annotation over method";
	}

	@Override
	public @Description String getDescription() {
		return "Add annotation over method.";
	}
	
	@Option(description = "Method pattern", example = "com.example.Person setAge(int)")
	private String method;
	
	@Option(description = "Annotation type")
	private String annotationType;

	@Nullable
	@Option(description = "Annotation attributes", required = false)
	private List<Attribute> attributes;

	@JsonCreator
	public AddAnnotationOverMethod(
			@JsonProperty("method")  String method,
			@JsonProperty("annotationType") String annotationType,
			@JsonProperty("attributes") @Nullable List<Attribute> attributes) {
		this.method = method;
		this.annotationType = annotationType;
		this.attributes = attributes;
	}

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {
		final MethodMatcher matcher = new MethodMatcher(method);
		return new JavaIsoVisitor<>() {
			@Override
			public MethodDeclaration visitMethodDeclaration(MethodDeclaration method, ExecutionContext ctx) {
				MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
				if (matcher.matches(m.getMethodType()) || matchesParameterTypeNames(m)) {
					boolean changed = false;
					Optional<Annotation> optAnnotation = m.getLeadingAnnotations().stream().filter(a -> TypeUtils.isOfClassType(a.getType(), annotationType)).findFirst();
					if (optAnnotation.isEmpty()) {
						List<J.Annotation> annotations = new ArrayList<>(m.getLeadingAnnotations());
						JavaType.ShallowClass at = JavaType.ShallowClass.build(annotationType);
						J.Annotation annotation = new J.Annotation(
								Tree.randomId(),
								Space.EMPTY,
								Markers.EMPTY,
								new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, List.of(), at.getClassName(), at, null),
								null);
						annotations.add(annotation);
						m = m.withLeadingAnnotations(annotations);
						maybeAddImport(annotationType);
						changed = true;
					}
					if (attributes != null) {
						for (Attribute attr : attributes) {
							MethodDeclaration newM = (MethodDeclaration) new AddOrUpdateAnnotationAttribute(annotationType, attr.name(),
									attr.value(), (String) null, null, null).getVisitor().visit(m, ctx, getCursor().getParent());
							if (newM != m) {
								m = newM;
								updateCursor(m);
								changed = true;
							}
						}
					}
					if (changed) {
						m = autoFormat(m, m.getName(), ctx, getCursor().getParent());
					}
				}
				return m;
			}

			private boolean matchesParameterTypeNames(MethodDeclaration md) {
				String currentMethod = "%s %s(%s)".formatted(md.getMethodType().getDeclaringType().getFullyQualifiedName(),
						md.getName().getSimpleName(), md.getParameters().stream()
								.map(s -> {
									if (s instanceof VariableDeclarations vd) {
										return getStr(vd.getTypeExpression());
									}
									return s.print(getCursor());
								}).collect(Collectors.joining(",")));
				return method.equals(currentMethod);
			}
			
			private String getStr(NameTree tt) {
				if (tt instanceof J.Identifier id) {
					return id.getSimpleName();
				} else if (tt instanceof J.FieldAccess fa) {
					return getStr(fa.getName());
				} else if (tt instanceof J.ArrayType at) {
					return getStr(at.getElementType());
				} else if (tt instanceof J.ParameterizedType pt) {
					return getStr(pt.getClazz());
				} else {
					return tt.print(getCursor());
				}
			}
		};
	}

}
