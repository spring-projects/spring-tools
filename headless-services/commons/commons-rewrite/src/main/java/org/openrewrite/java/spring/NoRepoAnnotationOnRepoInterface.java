/*
 * Copyright 2022 the original author or authors.
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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

public class NoRepoAnnotationOnRepoInterface extends Recipe {

    private static final String INTERFACE_REPOSITORY = "org.springframework.data.repository.Repository";
    private static final String ANNOTATION_REPOSITORY = "org.springframework.stereotype.Repository";

    @Override
    public String getDisplayName() {
        return "Remove unnecessary `@Repository` annotation from Spring Data `Repository` sub-interface";
    }

    @Override
    public String getDescription() {
        return "Removes superfluous `@Repository` annotation from Spring Data `Repository` sub-interfaces.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(ANNOTATION_REPOSITORY, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                if (c.getKind() == J.ClassDeclaration.Kind.Type.Interface) {
                    boolean hasRepoAnnotation = c.getLeadingAnnotations().stream().anyMatch(annotation -> {
                        if (annotation.getArguments() == null || annotation.getArguments().isEmpty() ||
                                annotation.getArguments().get(0) instanceof J.Empty) {
                            JavaType.FullyQualified type = TypeUtils.asFullyQualified(annotation.getType());
                            return type != null && ANNOTATION_REPOSITORY.equals(type.getFullyQualifiedName());
                        }
                        return false;
                    });
                    if (hasRepoAnnotation && TypeUtils.isAssignableTo(INTERFACE_REPOSITORY, c.getType())) {
                        maybeRemoveImport(ANNOTATION_REPOSITORY);
                        return (J.ClassDeclaration) new RemoveAnnotationVisitor(new AnnotationMatcher("@" + ANNOTATION_REPOSITORY))
                                .visit(c, ctx, getCursor().getParentOrThrow());
                    }
                }
                return c;
            }
        });
    }
}
