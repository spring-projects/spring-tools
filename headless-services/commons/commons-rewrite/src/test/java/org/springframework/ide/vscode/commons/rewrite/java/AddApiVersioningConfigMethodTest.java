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

import static org.openrewrite.java.Assertions.java;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

public class AddApiVersioningConfigMethodTest implements RewriteTest {

	@Override
	public void defaults(RecipeSpec spec) {
		spec.parser(JavaParser.fromJavaVersion()
				.classpath("spring-context")
				.dependsOn(
					"""
					package org.springframework.web.servlet.config.annotation;
					public interface ApiVersionConfigurer {}
					""",
					"""
					package org.springframework.web.servlet.config.annotation;
					import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
					public interface WebMvcConfigurer {
						default void configureApiVersioning(ApiVersionConfigurer configurer) {}
					}
					""",
					"""
					package org.springframework.web.reactive.config;
					import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
					public interface WebFluxConfigurer {
						default void configureApiVersioning(ApiVersionConfigurer configurer) {}
					}
					"""
				));
	}

	@Test
	void addsMethodToWebMvcConfigurerClass() {
		rewriteRun(
			spec -> spec.recipe(new AddApiVersioningConfigMethod("com/example/config/WebMvcConfig.java")),
			java(
				"""
				package com.example.config;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class WebMvcConfig implements WebMvcConfigurer {
				}
				""",
				"""
				package com.example.config;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class WebMvcConfig implements WebMvcConfigurer {
				    @Override
				    public void configureApiVersioning(ApiVersionConfigurer configurer) {
				    }
				}
				""",
				spec -> spec.path(Path.of("com/example/config/WebMvcConfig.java"))
			)
		);
	}

	@Test
	void addsMethodToWebFluxConfigurerClass() {
		rewriteRun(
			spec -> spec.recipe(new AddApiVersioningConfigMethod("com/example/config/WebFluxConfig.java")),
			java(
				"""
				package com.example.config;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.reactive.config.WebFluxConfigurer;
				
				@Configuration
				public class WebFluxConfig implements WebFluxConfigurer {
				}
				""",
				"""
				package com.example.config;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.reactive.config.WebFluxConfigurer;
				import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
				
				@Configuration
				public class WebFluxConfig implements WebFluxConfigurer {
				    @Override
				    public void configureApiVersioning(ApiVersionConfigurer configurer) {
				    }
				}
				""",
				spec -> spec.path(Path.of("com/example/config/WebFluxConfig.java"))
			)
		);
	}

	@Test
	void doesNotAddMethodWhenAlreadyExists() {
		rewriteRun(
			spec -> spec.recipe(new AddApiVersioningConfigMethod("com/example/config/WebMvcConfig.java")),
			java(
				"""
				package com.example.config;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class WebMvcConfig implements WebMvcConfigurer {
				
				    @Override
				    public void configureApiVersioning(ApiVersionConfigurer configurer) {
				        // Existing implementation
				    }
				}
				""",
				spec -> spec.path(Path.of("com/example/config/WebMvcConfig.java"))
			)
		);
	}

	@Test
	void doesNotModifyClassThatDoesNotImplementWebConfigurer() {
		rewriteRun(
			spec -> spec.recipe(new AddApiVersioningConfigMethod("com/example/config/OtherConfig.java")),
			java(
				"""
				package com.example.config;
				
				import org.springframework.context.annotation.Configuration;
				
				@Configuration
				public class OtherConfig {
				}
				""",
				spec -> spec.path(Path.of("com/example/config/OtherConfig.java"))
			)
		);
	}

	@Test
	void doesNotModifyFileWithDifferentPath() {
		rewriteRun(
			spec -> spec.recipe(new AddApiVersioningConfigMethod("com/example/config/WebMvcConfig.java")),
			java(
				"""
				package com.example.other;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class OtherWebConfig implements WebMvcConfigurer {
				}
				""",
				spec -> spec.path(Path.of("com/example/other/OtherWebConfig.java"))
			)
		);
	}
}
