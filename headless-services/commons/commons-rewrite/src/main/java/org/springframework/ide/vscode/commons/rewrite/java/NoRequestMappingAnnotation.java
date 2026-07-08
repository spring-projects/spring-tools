/*******************************************************************************
 * Copyright (c) 2023, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.rewrite.java;

import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Range;

public class NoRequestMappingAnnotation extends org.openrewrite.java.spring.NoRequestMappingAnnotation implements RangeScopedRecipe {

	private static final AnnotationMatcher REQUEST_MAPPING_ANNOTATION_MATCHER = new AnnotationMatcher(
			"@org.springframework.web.bind.annotation.RequestMapping");
	
	@Option(description = "Preferred request mapping method if not present", required = false)
	@Nullable
	private String preferredMapping;
	
	@Option(description = "Text range that contains the request definition", required = false)
	@Nullable
	private Range range;

	public String getPreferredMapping() {
		return preferredMapping;
	}

	public void setPreferredMapping(String preferredMapping) {
		this.preferredMapping = preferredMapping;
	}

	@Override
	public void setRange(Range range) {
		this.range = range;
	}

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {
		return new RangeScopedJavaIsoVisitor<ExecutionContext>(range) {
			
			@Override
			public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
				J.Annotation a = super.visitAnnotation(annotation, ctx);
				if (REQUEST_MAPPING_ANNOTATION_MATCHER.matches(a)
						&& getCursor().getParentOrThrow().getValue() instanceof J.MethodDeclaration) {
					Optional<J.Assignment> requestMethodArg = requestMethodArgument(a);
					Optional<String> requestType = requestMethodArg.map(this::requestMethodType).or(() -> Optional.ofNullable(preferredMapping));
					String resolvedRequestMappingAnnotationClassName = requestType.map(NoRequestMappingAnnotation::associatedRequestMapping)
							.orElse(null);
					if (resolvedRequestMappingAnnotationClassName == null) {
						// Without a method argument @RequestMapping matches all request methods, so we
						// can't safely convert
						return a;
					}

					maybeRemoveImport("org.springframework.web.bind.annotation.RequestMapping");
					maybeRemoveImport("org.springframework.web.bind.annotation.RequestMethod");
					requestType.ifPresent(requestMethod -> maybeRemoveImport(
							"org.springframework.web.bind.annotation.RequestMethod." + requestMethod));

					// Remove the argument
					if (requestMethodArg.isPresent() && resolvedRequestMappingAnnotationClassName != null) {
						if (a.getArguments() != null) {
							a = a.withArguments(ListUtils.map(a.getArguments(),
									arg -> requestMethodArg.get().equals(arg) ? null : arg));
						}
					}

					// Change the Annotation Type
					if (resolvedRequestMappingAnnotationClassName != null) {
						maybeAddImport(
								"org.springframework.web.bind.annotation." + resolvedRequestMappingAnnotationClassName);
						a = (J.Annotation) new ChangeType("org.springframework.web.bind.annotation.RequestMapping",
								"org.springframework.web.bind.annotation." + resolvedRequestMappingAnnotationClassName,
								false).getVisitor().visit(a, ctx, getCursor());
					}

					// if there is only one remaining argument now, and it is "path" or "value",
					// then we can drop the key name
					if (a != null && a.getArguments() != null && a.getArguments().size() == 1) {
						a = a.withArguments(ListUtils.map(a.getArguments(), arg -> {
							if (arg instanceof J.Assignment && ((J.Assignment) arg).getVariable() instanceof J.Identifier) {
								J.Identifier ident = (J.Identifier) ((J.Assignment) arg).getVariable();
								if ("path".equals(ident.getSimpleName()) || "value".equals(ident.getSimpleName())) {
									return ((J.Assignment) arg).getAssignment().withPrefix(Space.EMPTY);
								}
							}
							return arg;
						}));
					}
				}
				return a != null ? a : annotation;
			}

			private Optional<J.Assignment> requestMethodArgument(J.Annotation annotation) {
				return annotation.getArguments().stream()
						.filter(arg -> arg instanceof J.Assignment
								&& ((J.Assignment) arg).getVariable() instanceof J.Identifier
								&& "method".equals(((J.Identifier) ((J.Assignment) arg).getVariable()).getSimpleName()))
						.map(J.Assignment.class::cast).findFirst();
			}

			private String requestMethodType(J.Assignment assignment) {
				if (assignment.getAssignment() instanceof J.Identifier) {
					return ((J.Identifier) assignment.getAssignment()).getSimpleName();
				} else if (assignment.getAssignment() instanceof J.FieldAccess) {
					return ((J.FieldAccess) assignment.getAssignment()).getSimpleName();
				} else if (assignment.getAssignment() instanceof J.NewArray) {
					J.NewArray newArray = (J.NewArray) assignment.getAssignment();
					if (newArray.getInitializer() != null && newArray.getInitializer().size() > 0) {
						if (preferredMapping != null && !preferredMapping.isBlank()) {
							return newArray.getInitializer().stream().filter(J.FieldAccess.class::isInstance)
									.map(J.FieldAccess.class::cast).map(fa -> fa.getSimpleName())
									.filter(n -> preferredMapping.equals(n)).findFirst().orElse(null);
						} else if (newArray.getInitializer().size() == 1) {
							return ((J.FieldAccess) newArray.getInitializer().get(0)).getSimpleName();
						}
					}
				}
				return null;
			}

		};
	}

	@Nullable
	public static String associatedRequestMapping(String method) {
		switch (method) {
		case "POST":
		case "PUT":
		case "DELETE":
		case "PATCH":
		case "GET":
			return method.charAt(0) + method.toLowerCase().substring(1) + "Mapping";
		}
		// HEAD, OPTIONS, TRACE do not have associated RequestMapping variant
		return null;
	}
}
