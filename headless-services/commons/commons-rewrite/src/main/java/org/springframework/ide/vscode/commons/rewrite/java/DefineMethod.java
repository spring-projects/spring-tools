/*******************************************************************************
 * Copyright (c) 2022, 2023 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.rewrite.java;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import com.fasterxml.jackson.annotation.JsonCreator;

public class DefineMethod extends Recipe {

    private static final String MESSAGE = "methodPresent";

    private String targetFqName;

    private String signature;

    private String template;

    private List<String> typeStubs = Collections.emptyList();

    private List<String> imports = Collections.emptyList();

    private List<String> classpath = Collections.emptyList();

    public DefineMethod() {
    }

    @JsonCreator
    public DefineMethod(String targetFqName, String signature, String template, List<String> imports, List<String> typeStubs, List<String> classpath) {
        this.targetFqName = targetFqName;
        this.signature = signature;
        this.template = template;
        this.imports = imports;
        this.typeStubs = typeStubs;
        this.classpath = classpath;
    }

    @Override
    public String getDisplayName() {
        return "Define a bean in type";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            private MethodMatcher matcher = new MethodMatcher(targetFqName + ' ' + signature);

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                JavaType.Method type = method.getMethodType();
                if (type != null && matcher.matches(type)) {
                    getCursor().dropParentUntil(J.ClassDeclaration.class::isInstance).putMessage(MESSAGE, true);
                }
                return method;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                J.ClassDeclaration c = super.visitClassDeclaration(classDecl, executionContext);
                Boolean methodPresent = getCursor().pollMessage(MESSAGE);
                if (template != null && methodPresent == null) {
                    JavaType.FullyQualified type = c.getType();
                    if (type != null && targetFqName.equals(type.getFullyQualifiedName())) {
                        JavaTemplate t = JavaTemplate.builder(template)
                        		.contextSensitive()
                                .javaParser(JavaParser
                                        .fromJavaVersion()
                                        .dependsOn(typeStubs.toArray(new String[typeStubs.size()]))
                                        .classpath(classpath.stream().map(s -> Paths.get(s)).collect(Collectors.toList())))

                                .imports(imports.toArray(new String[imports.size()])).build();
                        c = t.apply(getCursor(), classDecl.getBody().getCoordinates().addMethodDeclaration((m, n) -> 1));
                        for (String fq : imports) {
                            maybeAddImport(fq);
                        }
                    }
                }
                return c;
            }
        };
    }

	@Override
	public String getDescription() {
		return "Defines a bean declaration method in a type";
	}

}
