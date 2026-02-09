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
package org.springframework.ide.vscode.commons.rewrite.java;

import java.util.Comparator;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;

public class AddValidatedAnnotation extends Recipe {

    private static final String VALIDATED_PACKAGE = "org.springframework.validation.annotation";
    private static final String VALIDATED_SIMPLE_NAME = "Validated";
    private static final String FQN_VALIDATED = VALIDATED_PACKAGE + "." + VALIDATED_SIMPLE_NAME;

    @Override
    public String getDisplayName() {
        return "Add `@Validated` annotation";
    }

    @Override
    public String getDescription() {
        return "Add `@Validated` annotation to a class.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
                return addValidatedAnnotation(c);
            }

            private J.ClassDeclaration addValidatedAnnotation(J.ClassDeclaration c) {
                maybeAddImport(FQN_VALIDATED);
                return JavaTemplate.builder("@" + VALIDATED_SIMPLE_NAME)
                    .imports(FQN_VALIDATED)
                    .javaParser(JavaParser.fromJavaVersion().dependsOn("package " + VALIDATED_PACKAGE +
                                                                       "; public @interface " + VALIDATED_SIMPLE_NAME + " {}"))
                    .build().apply(
                        getCursor(),
                        c.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName))
                    );
            }
        };
    }

}
