/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.spring;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Replace method declaration @RequestMapping annotations with the associated variant
 * as defined by the request method type (GET, POST, PUT, PATCH, DELETE)
 * <p>
 * (HEAD, OPTIONS, TRACE) methods do not have associated RequestMapping variant and are not converted
 * <ul>
 * <li> @RequestMapping() changes to @GetMapping
 * <li> @RequestMapping(method = POST) changes to @PostMapping
 * <li> @RequestMapping(method = { HEAD, GET }) No change
 * </ul>
 */
public class NoRequestMappingAnnotation extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove `@RequestMapping` annotations";
    }

    @Override
    public String getDescription() {
        return "Replace method declaration `@RequestMapping` annotations with `@GetMapping`, `@PostMapping`, etc. when possible.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-S4488");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.springframework.web.bind.annotation.RequestMapping", false),
                new NoRequestMappingAnnotationVisitor());
    }

    private static class NoRequestMappingAnnotationVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final AnnotationMatcher REQUEST_MAPPING_ANNOTATION_MATCHER = new AnnotationMatcher("@org.springframework.web.bind.annotation.RequestMapping");

        @Override
        public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
            J.Annotation a = super.visitAnnotation(annotation, ctx);
            if (REQUEST_MAPPING_ANNOTATION_MATCHER.matches(a) && getCursor().getParentOrThrow().getValue() instanceof J.MethodDeclaration) {
                Optional<J.Assignment> requestMethodArg = requestMethodArgument(a);
                Optional<String> requestType = requestMethodArg.map(this::requestMethodType);
                String resolvedRequestMappingAnnotationClassName = requestType.map(this::associatedRequestMapping).orElse(null);
                if (resolvedRequestMappingAnnotationClassName == null) {
                    // Without a method argument @RequestMapping matches all request methods, so we can't safely convert
                    return a;
                }

                maybeRemoveImport("org.springframework.web.bind.annotation.RequestMapping");
                maybeRemoveImport("org.springframework.web.bind.annotation.RequestMethod");
                requestType.ifPresent(requestMethod -> maybeRemoveImport("org.springframework.web.bind.annotation.RequestMethod." + requestMethod));

                // Remove the argument
                if (methodArgumentHasSingleType(requestMethodArg.get())) {
                    if (a.getArguments() != null) {
                        a = a.withArguments(ListUtils.map(a.getArguments(), arg -> requestMethodArg.get().equals(arg) ? null : arg));
                    }
                }

                // Change the Annotation Type
                maybeAddImport("org.springframework.web.bind.annotation." + resolvedRequestMappingAnnotationClassName);
                a = (J.Annotation) new ChangeType("org.springframework.web.bind.annotation.RequestMapping",
                        "org.springframework.web.bind.annotation." + resolvedRequestMappingAnnotationClassName, false)
                        .getVisitor().visit(a, ctx, getCursor().getParentOrThrow());

                // if there is only one remaining argument now, and it is "path" or "value", then we can drop the key name
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
            if (annotation.getArguments() == null) {
                return Optional.empty();
            }
            return annotation.getArguments().stream()
                    .filter(arg -> arg instanceof J.Assignment &&
                            ((J.Assignment) arg).getVariable() instanceof J.Identifier &&
                            "method".equals(((J.Identifier) ((J.Assignment) arg).getVariable()).getSimpleName()))
                    .map(J.Assignment.class::cast)
                    .findFirst();
        }

        private boolean methodArgumentHasSingleType(J.Assignment assignment) {
            if (!(assignment.getAssignment() instanceof J.NewArray)) {
                return true;
            }
            J.NewArray newArray = (J.NewArray) assignment.getAssignment();
            return newArray.getInitializer() != null && newArray.getInitializer().size() == 1;
        }

        private @Nullable String requestMethodType(J.@Nullable Assignment assignment) {
            if(assignment == null) {
                return null;
            }
            if (assignment.getAssignment() instanceof J.Identifier) {
                return ((J.Identifier) assignment.getAssignment()).getSimpleName();
            } else if (assignment.getAssignment() instanceof J.FieldAccess) {
                return ((J.FieldAccess) assignment.getAssignment()).getSimpleName();
            } else if (methodArgumentHasSingleType(assignment)) {
                if(assignment.getAssignment() instanceof J.NewArray) {
                    J.NewArray newArray = (J.NewArray) assignment.getAssignment();
                    List<Expression> initializer = newArray.getInitializer();
                    if(initializer == null || initializer.size() != 1) {
                        return null;
                    }
                    Expression methodName = initializer.get(0);
                    if(methodName instanceof J.Identifier) {
                        return ((J.Identifier)methodName).getSimpleName();
                    } else if(methodName instanceof J.FieldAccess) {
                        return ((J.FieldAccess) methodName).getSimpleName();
                    }
                } else if(assignment.getAssignment() instanceof J.Identifier) {
                    return ((J.Identifier) assignment.getAssignment()).getSimpleName();
                }
            }
            return null;
        }

        private @Nullable String associatedRequestMapping(String method) {
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
}
