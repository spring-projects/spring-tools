/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.java.spring.boot2;

import org.openrewrite.*;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Comparator;

public class AddConfigurationAnnotationIfBeansPresent extends Recipe {

    private static final String FQN_BEAN = "org.springframework.context.annotation.Bean";
    private static final String CONFIGURATION_PACKAGE = "org.springframework.context.annotation";
    private static final String CONFIGURATION_SIMPLE_NAME = "Configuration";
    private static final String FQN_CONFIGURATION = CONFIGURATION_PACKAGE + "." + CONFIGURATION_SIMPLE_NAME;
    private static final AnnotationMatcher BEAN_ANNOTATION_MATCHER = new AnnotationMatcher("@" + FQN_BEAN, true);
    private static final AnnotationMatcher CONFIGURATION_ANNOTATION_MATCHER = new AnnotationMatcher("@" + FQN_CONFIGURATION, true);


    @Override
    public String getDisplayName() {
        return "Add missing `@Configuration` annotation";
    }

    @Override
    public String getDescription() {
        return "Class having `@Bean` annotation over any methods but missing `@Configuration` annotation over the declaring class would have `@Configuration` annotation added.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(FQN_BEAN, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                if (isApplicableClass(c, getCursor())) {
                    c = addConfigurationAnnotation(c);
                }
                return c;
            }

            private J.ClassDeclaration addConfigurationAnnotation(J.ClassDeclaration c) {
                maybeAddImport(FQN_CONFIGURATION);
                return JavaTemplate.builder("@" + CONFIGURATION_SIMPLE_NAME)
                    .imports(FQN_CONFIGURATION)
                    .javaParser(JavaParser.fromJavaVersion().dependsOn("package " + CONFIGURATION_PACKAGE +
                                                                       "; public @interface " + CONFIGURATION_SIMPLE_NAME + " {}"))
                    .build().apply(
                        getCursor(),
                        c.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName))
                    );
            }
        });
    }

    public static boolean isApplicableClass(J.ClassDeclaration classDecl, Cursor cursor) {
        if (classDecl.getKind() != J.ClassDeclaration.Kind.Type.Class) {
            return false;
        }

        boolean isStatic = false;
        for (J.Modifier m : classDecl.getModifiers()) {
            if (m.getType() == J.Modifier.Type.Abstract) {
                return false;
            } else if (m.getType() == J.Modifier.Type.Static) {
                isStatic = true;
            }
        }

        if (!isStatic) {
            // no static keyword? check if it is top level class in the CU
            Object enclosing = cursor.dropParentUntil(it -> it instanceof J.ClassDeclaration || it == Cursor.ROOT_VALUE).getValue();
            if (enclosing instanceof J.ClassDeclaration) {
                return false;
            }
        }

        // check if '@Configuration' is already over the class
        for (J.Annotation a : classDecl.getLeadingAnnotations()) {
            JavaType.FullyQualified aType = TypeUtils.asFullyQualified(a.getType());
            if (aType != null && CONFIGURATION_ANNOTATION_MATCHER.matchesAnnotationOrMetaAnnotation(aType)) {
                // Found '@Configuration' annotation
                return false;
            }
        }
        // No '@Configuration' present. Check if any methods have '@Bean' annotation
        for (Statement s : classDecl.getBody().getStatements()) {
            if (s instanceof J.MethodDeclaration) {
                if (isBeanMethod((J.MethodDeclaration) s)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isBeanMethod(J.MethodDeclaration methodDecl) {
        for (J.Modifier m : methodDecl.getModifiers()) {
            if (m.getType() == J.Modifier.Type.Abstract || m.getType() == J.Modifier.Type.Static) {
                return false;
            }
        }
        for (J.Annotation a : methodDecl.getLeadingAnnotations()) {
            JavaType.FullyQualified aType = TypeUtils.asFullyQualified(a.getType());
            if (aType != null && BEAN_ANNOTATION_MATCHER.matchesAnnotationOrMetaAnnotation(aType)) {
                return true;
            }
        }
        return false;
    }
}
