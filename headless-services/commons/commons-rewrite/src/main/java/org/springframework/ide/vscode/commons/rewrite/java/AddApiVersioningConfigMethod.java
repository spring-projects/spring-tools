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

import java.nio.file.Path;
import java.nio.file.Paths;

import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite.Description;
import org.openrewrite.NlsRewrite.DisplayName;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.CompilationUnit;
import org.openrewrite.java.tree.TypeUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AddApiVersioningConfigMethod extends Recipe {

	private static final String WEB_MVC_CONFIGURER = "org.springframework.web.servlet.config.annotation.WebMvcConfigurer";
	private static final String WEB_FLUX_CONFIGURER = "org.springframework.web.reactive.config.WebFluxConfigurer";
	private static final String API_VERSION_CONFIGURER = "org.springframework.web.servlet.config.annotation.ApiVersionConfigurer";
	
	private static final MethodMatcher CONFIG_METHOD_MATCHER = new MethodMatcher(
			"* configureApiVersioning(org.springframework.web.servlet.config.annotation.ApiVersionConfigurer)");

	@Option(description = "Path to the Java file", example = "com/example/config/WebConfig.java")
	private String filePath;

	@JsonCreator
	public AddApiVersioningConfigMethod(@JsonProperty("filePath") String filePath) {
		this.filePath = filePath;
	}

	@Override
	public @DisplayName String getDisplayName() {
		return "Add configureApiVersioning method";
	}

	@Override
	public @Description String getDescription() {
		return "Adds configureApiVersioning method to WebMvcConfigurer or WebFluxConfigurer class if it doesn't exist.";
	}

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor() {
		final Path file = Paths.get(filePath);
		return new JavaIsoVisitor<ExecutionContext>() {
			
			@Override
			public CompilationUnit visitCompilationUnit(CompilationUnit cu, ExecutionContext p) {
				Path cuPath = cu.getSourcePath();
				// Only process if this is the target file
				if (cuPath == null || !cuPath.equals(file)) {
					return cu;
				} else {
					return super.visitCompilationUnit(cu, p);
				}
			}

			@Override
			public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
				J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
				
				// Check if class implements WebMvcConfigurer or WebFluxConfigurer
				if (!isWebConfigurer(c)) {
					return c;
				}
				
				// Check if configureApiVersioning method already exists
				if (hasConfigureApiVersioningMethod(c)) {
					return c;
				}
				
				// Add the configureApiVersioning method
				return addConfigureApiVersioningMethod(c, ctx);
			}
			
			private boolean isWebConfigurer(J.ClassDeclaration classDecl) {
				return TypeUtils.isAssignableTo(WEB_MVC_CONFIGURER, classDecl.getType())
						|| TypeUtils.isAssignableTo(WEB_FLUX_CONFIGURER, classDecl.getType());
			}
			
			private boolean hasConfigureApiVersioningMethod(J.ClassDeclaration classDecl) {
				return classDecl.getBody().getStatements().stream()
						.filter(J.MethodDeclaration.class::isInstance)
						.map(J.MethodDeclaration.class::cast)
						.anyMatch(method -> CONFIG_METHOD_MATCHER.matches(method.getMethodType()));
			}
			
			private J.ClassDeclaration addConfigureApiVersioningMethod(J.ClassDeclaration classDecl, ExecutionContext ctx) {
				JavaTemplate template = JavaTemplate.builder("""
					@Override
					public void configureApiVersioning(ApiVersionConfigurer configurer) {
					}
					""")
						.contextSensitive()
						.javaParser(JavaParser.fromJavaVersion()
								.dependsOn("""
									package org.springframework.web.servlet.config.annotation;
									public interface ApiVersionConfigurer {}
									""")
						)
						.imports(API_VERSION_CONFIGURER)
						.build();
				
				maybeAddImport(API_VERSION_CONFIGURER);
				
				return template.apply(
						getCursor(),
						classDecl.getBody().getCoordinates().lastStatement()
				);
			}
		};
	}
}
