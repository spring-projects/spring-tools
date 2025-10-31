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
package org.openrewrite.java.spring.framework;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.ChangeMethodAccessLevelVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

public class BeanMethodsNotPublic extends Recipe {
    private static final String BEAN = "org.springframework.context.annotation.Bean";
    private static final AnnotationMatcher BEAN_ANNOTATION_MATCHER = new AnnotationMatcher("@" + BEAN);

    @Override
    public String getDisplayName() {
        return "Remove `public` from `@Bean` methods";
    }

    @Override
    public String getDescription() {
        return "Remove public modifier from `@Bean` methods. They no longer have to be public visibility to be usable by Spring.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(BEAN, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                if (service(AnnotationService.class).matches(getCursor(), BEAN_ANNOTATION_MATCHER) &&
                    !TypeUtils.isOverride(method.getMethodType())) {
                    // remove public modifier and copy any associated comments to the method
                    doAfterVisit(new ChangeMethodAccessLevelVisitor<>(new MethodMatcher(method), null));
                }
                return super.visitMethodDeclaration(method, ctx);
            }
        });
    }
}
