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
import org.openrewrite.test.RewriteTest;

public class AddWebConfigurerBeanTest implements RewriteTest {

	@Test
	void generatesWebMvcConfigurerWhenFileDoesNotExist() {
		rewriteRun(
			spec -> spec.recipe(new AddWebConfigurerBean(
					"com/example/config/WebMvcConfig.java",
					"com.example.config",
					false)),
			java(
				null,
				"""
				package com.example.config;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class WebMvcConfig implements WebMvcConfigurer {
				}
				""",
				spec -> spec.path(Path.of("com/example/config/WebMvcConfig.java"))
			)
		);
	}

	@Test
	void generatesWebFluxConfigurerWhenFileDoesNotExist() {
		rewriteRun(
			spec -> spec.recipe(new AddWebConfigurerBean(
					"com/example/config/WebFluxConfig.java",
					"com.example.config",
					true)),
			java(
				null,
				"""
				package com.example.config;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.reactive.config.WebFluxConfigurer;
				
				@Configuration
				public class WebFluxConfig implements WebFluxConfigurer {
				}
				""",
				spec -> spec.path(Path.of("com/example/config/WebFluxConfig.java"))
			)
		);
	}

	@Test
	void doesNotGenerateFileWhenFileAlreadyExists() {
		rewriteRun(
			spec -> spec.recipe(new AddWebConfigurerBean(
					"com/example/config/ExistingConfig.java",
					"com.example.config",
					false)),
			java(
				"""
				package com.example.config;
				
				import org.springframework.context.annotation.Configuration;
				
				@Configuration
				public class ExistingConfig {
					// Existing configuration
				}
				""",
				spec -> spec.path(Path.of("com/example/config/ExistingConfig.java"))
			)
		);
	}
}
