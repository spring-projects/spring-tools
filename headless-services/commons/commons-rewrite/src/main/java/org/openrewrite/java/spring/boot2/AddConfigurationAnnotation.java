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

import java.util.Comparator;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

public class AddConfigurationAnnotation extends Recipe {

    private static final String CONFIGURATION_PACKAGE = "org.springframework.context.annotation";
    private static final String CONFIGURATION_SIMPLE_NAME = "Configuration";
    private static final String FQN_CONFIGURATION = CONFIGURATION_PACKAGE + "." + CONFIGURATION_SIMPLE_NAME;


    @Override
    public String getDisplayName() {
        return "Add `@Configuration` annotation";
    }

    @Override
    public String getDescription() {
        return "Add `@Configuration` annotation";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                return addConfigurationAnnotation(c);
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
        };
    }

}
