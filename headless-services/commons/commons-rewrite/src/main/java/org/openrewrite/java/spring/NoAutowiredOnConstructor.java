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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveAnnotationVisitor;
import org.openrewrite.java.search.FindAnnotations;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

public class NoAutowiredOnConstructor extends Recipe {
    private static final AnnotationMatcher AUTOWIRED_ANNOTATION_MATCHER =
            new AnnotationMatcher("@org.springframework.beans.factory.annotation.Autowired(true)");

    @Override
    public String getDisplayName() {
        return "Remove the `@Autowired` annotation on inferred constructor";
    }

    @Override
    public String getDescription() {
        return "Spring can infer an autowired constructor when there is a single constructor on the bean. " +
                "This recipe removes unneeded `@Autowired` annotations on constructors.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>("org.springframework.beans.factory.annotation.Autowired", false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                int constructorCount = 0;
                for (Statement s : cd.getBody().getStatements()) {
                    if (isConstructor(s)) {
                        constructorCount++;
                        if (constructorCount > 1) {
                            return cd;
                        }
                    }
                }

                // Lombok can also provide a constructor, so keep `@Autowired` on constructors if found
                if (!FindAnnotations.find(cd, "@lombok.*Constructor").isEmpty()) {
                    return cd;
                }

                // `@ConfigurationProperties` classes usually use field injection, so keep `@Autowired` on constructors
                if (!FindAnnotations.find(cd, "@org.springframework.boot.context.properties.ConfigurationProperties").isEmpty()) {
                    return cd;
                }

                return cd.withBody(cd.getBody().withStatements(
                        ListUtils.map(cd.getBody().getStatements(), s -> {
                            if (!isConstructor(s)) {
                                return s;
                            }
                            maybeRemoveImport("org.springframework.beans.factory.annotation.Autowired");
                            return (Statement) new RemoveAnnotationVisitor(AUTOWIRED_ANNOTATION_MATCHER).visit(s, ctx, getCursor());
                        })
                ));
            }
        });
    }

    private static boolean isConstructor(Statement s) {
        return s instanceof J.MethodDeclaration && ((J.MethodDeclaration) s).isConstructor();
    }
}
