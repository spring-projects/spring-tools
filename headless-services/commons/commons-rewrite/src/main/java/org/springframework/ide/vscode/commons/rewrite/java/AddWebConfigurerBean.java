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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite.Description;
import org.openrewrite.NlsRewrite.DisplayName;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AddWebConfigurerBean extends ScanningRecipe<Set<Path>> {

	@Option(description = "Absolute path to the Java file", example = "/src/main/java/com/example/config/WebConfig.java")
	private String filePath;

	@Option(description = "Package name for the class", example = "com.example.config")
	private String packageName;

	@Option(description = "Whether to use WebFlux (true) or WebMvc (false)")
	private boolean isFlux;

	@JsonCreator
	public AddWebConfigurerBean(
			@JsonProperty("filePath") String filePath,
			@JsonProperty("packageName") String packageName,
			@JsonProperty("isFlux") boolean isFlux) {
		this.filePath = filePath;
		this.packageName = packageName;
		this.isFlux = isFlux;
	}

	@Override
	public @DisplayName String getDisplayName() {
		return "Add Web Configurer Bean";
	}

	@Override
	public @Description String getDescription() {
		return "Adds a @Configuration class that implements WebMvcConfigurer or WebFluxConfigurer if the file doesn't exist.";
	}

	@Override
	public Set<Path> getInitialValue(ExecutionContext ctx) {
		return new HashSet<>();
	}

	@Override
	public TreeVisitor<?, ExecutionContext> getScanner(Set<Path> acc) {
		return new TreeVisitor<Tree, ExecutionContext>() {
			@Override
			public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
				if (tree instanceof SourceFile) {
					SourceFile sourceFile = (SourceFile) tree;
					Path sourcePath = sourceFile.getSourcePath();
					if (sourcePath != null) {
						acc.add(sourcePath);
					}
				}
				return tree;
			}
		};
	}

	@Override
	public TreeVisitor<?, ExecutionContext> getVisitor(Set<Path> acc) {
		// No visitor needed - we only generate files if they don't exist
		return TreeVisitor.noop();
	}

	@Override
	public Collection<? extends SourceFile> generate(Set<Path> acc, ExecutionContext ctx) {
		// Extract the relative source path from the absolute filePath
		Path path = Paths.get(filePath);

		// Check if file already exists
		if (acc.contains(path)) {
			return Collections.emptyList();
		}

		// Extract class name from file path
		String className = path.getFileName().toString().replace(".java", "");

		// Determine the configurer interface
		String configurerInterface = isFlux ? "WebFluxConfigurer" : "WebMvcConfigurer";
		String configurerPackage = isFlux 
				? "org.springframework.web.reactive.config" 
				: "org.springframework.web.servlet.config.annotation";

		// Generate the source code using text block and formatted
		String sourceCode = """
			package %s;
			
			import org.springframework.context.annotation.Configuration;
			import %s.%s;
			
			@Configuration
			public class %s implements %s {
			}
			""".formatted(packageName, configurerPackage, configurerInterface, className, configurerInterface);

		// Parse the generated source with type stubs
		JavaParser javaParser = JavaParser.fromJavaVersion()
				.classpath("spring-context")
				.dependsOn(
					"""
					package org.springframework.web.servlet.config.annotation;
					public interface WebMvcConfigurer {}
					""",
					"""
					package org.springframework.web.reactive.config;
					public interface WebFluxConfigurer {}
					"""
				)
				.build();

		J.CompilationUnit cu = javaParser.parse(sourceCode)
				.findFirst()
				.map(J.CompilationUnit.class::cast)
				.orElse(null);

		if (cu != null) {
			// Set the source path
			cu = cu.withSourcePath(path);
			
			// Auto-format the compilation unit
			cu = (J.CompilationUnit) new org.openrewrite.java.format.AutoFormatVisitor<>()
					.visit(cu, ctx);
		}

		return cu != null ? Collections.singletonList(cu) : Collections.emptyList();
	}

}
