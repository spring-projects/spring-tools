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
import org.openrewrite.java.search.FindMethods;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.CompilationUnit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AddApiVersioningConfigurationCall extends Recipe {

	private static final String API_VERSION_CONFIGURER = "org.springframework.web.servlet.config.annotation.ApiVersionConfigurer";
	private static final String MEDIA_TYPE = "org.springframework.http.MediaType";
	
	private static final MethodMatcher CONFIG_METHOD_MATCHER = new MethodMatcher(
			"* configureApiVersioning(org.springframework.web.servlet.config.annotation.ApiVersionConfigurer)");
	
	public static final String API_VERSION_CONFIGURER_STUB = """
		package org.springframework.web.servlet.config.annotation;
		public class ApiVersionConfigurer {
			public ApiVersionConfigurer useRequestHeader(String headerName) { return this; }
			public ApiVersionConfigurer useQueryParam(String paramName) { return this; }
			public ApiVersionConfigurer usePathSegment(int index) { return this; }
			public ApiVersionConfigurer useMediaTypeParameter(org.springframework.http.MediaType mediaType, String paramName) { return this; }
		}
		""";
	
	public static final String MEDIA_TYPE_STUB = """
		package org.springframework.http;
		public class MediaType {
			public static final MediaType ALL = new MediaType();
		}
		""";

	public enum ConfigType {
		HEADER,
		PATH,
		QUERY,
		MEDIA_TYPE
	}

	@Option(description = "Path to the Java file", example = "com/example/config/WebConfig.java")
	private String filePath;

	@Option(description = "Configuration type: HEADER, PATH, QUERY, or MEDIA_TYPE")
	private ConfigType configType;

	@Option(description = "Configuration value: header name, query parameter name, path segment index, or media type parameter name")
	private String value;

	@JsonCreator
	public AddApiVersioningConfigurationCall(
			@JsonProperty("filePath") String filePath,
			@JsonProperty("configType") ConfigType configType,
			@JsonProperty("value") String value) {
		this.filePath = filePath;
		this.configType = configType;
		this.value = value;
	}

	@Override
	public @DisplayName String getDisplayName() {
		return "Add API versioning configuration";
	}

	@Override
	public @Description String getDescription() {
		return "Adds API versioning configuration (useRequestHeader, useQueryParam, usePathSegment, or useMediaTypeParameter) to configureApiVersioning method.";
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
			public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
				J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
				
				// Check if this is the configureApiVersioning method
				if (!CONFIG_METHOD_MATCHER.matches(m.getMethodType())) {
					return m;
				}
				
				// Check if the appropriate useXXX() call already exists
				if (hasConfigurationCall(m)) {
					return m;
				}
				
				// Add the configuration call
				return addConfigurationCall(m, ctx);
			}
			
			private boolean hasConfigurationCall(J.MethodDeclaration method) {
				String matcherPattern = switch (configType) {
					case HEADER -> "%s useRequestHeader(..)".formatted(API_VERSION_CONFIGURER);
					case QUERY -> "%s useQueryParam(..)".formatted(API_VERSION_CONFIGURER);
					case PATH -> "%s usePathSegment(..)".formatted(API_VERSION_CONFIGURER);
					case MEDIA_TYPE -> "%s useMediaTypeParameter(..)".formatted(API_VERSION_CONFIGURER);
				};
				
				return !FindMethods.find(method, matcherPattern).isEmpty();
			}
			
			private J.MethodDeclaration addConfigurationCall(J.MethodDeclaration method, ExecutionContext ctx) {
				// Extract the parameter name from the method declaration
				J.Identifier parameterName = null; // fallback
				if (!method.getParameters().isEmpty() && method.getParameters().get(0) instanceof J.VariableDeclarations) {
					J.VariableDeclarations varDecls = (J.VariableDeclarations) method.getParameters().get(0);
					if (!varDecls.getVariables().isEmpty()) {
						parameterName = varDecls.getVariables().get(0).getName();
					}
				}
				
				String templateCode = switch (configType) {
					case HEADER -> "#{any(%s)}.useRequestHeader(\"%s\");".formatted(API_VERSION_CONFIGURER, value);
					case QUERY -> "#{any(%s)}.useQueryParam(\"%s\");".formatted(API_VERSION_CONFIGURER, value);
					case PATH -> "#{any(%s)}.usePathSegment(%d);".formatted(API_VERSION_CONFIGURER, Integer.valueOf(value));
					case MEDIA_TYPE -> "#{any(%s)}.useMediaTypeParameter(MediaType.ALL, \"%s\");".formatted(API_VERSION_CONFIGURER, value);
				};
				
				JavaTemplate template = JavaTemplate.builder(templateCode)
						.contextSensitive()
						.javaParser(JavaParser.fromJavaVersion()
								.dependsOn(API_VERSION_CONFIGURER_STUB, MEDIA_TYPE_STUB)
						)
						.imports(API_VERSION_CONFIGURER, MEDIA_TYPE)
						.build();
				
				if (configType == ConfigType.MEDIA_TYPE) {
					maybeAddImport(MEDIA_TYPE);
				}
				
				return template.apply(
						getCursor(),
						method.getBody().getCoordinates().lastStatement(),
						parameterName
				);
			}
		};
	}
}
