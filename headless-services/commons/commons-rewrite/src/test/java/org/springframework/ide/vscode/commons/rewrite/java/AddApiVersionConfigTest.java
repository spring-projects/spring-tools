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
import org.springframework.ide.vscode.commons.rewrite.java.AddApiVersioningConfigurationCall.ConfigType;

public class AddApiVersionConfigTest implements RewriteTest {

	@Override
	public void defaults(RecipeSpec spec) {
		spec.parser(JavaParser.fromJavaVersion()
				.classpath("spring-context")
				.dependsOn(
					AddApiVersioningConfigurationCall.API_VERSION_CONFIGURER_STUB,
					AddApiVersioningConfigurationCall.MEDIA_TYPE_STUB,
					"""
					package org.springframework.web.servlet.config.annotation;
					public interface WebMvcConfigurer {
						default void configureApiVersioning(ApiVersionConfigurer configurer) {}
					}
					""",
					"""
					package org.springframework.web.reactive.config;
					public interface WebFluxConfigurer {
						default void configureApiVersioning(org.springframework.web.servlet.config.annotation.ApiVersionConfigurer configurer) {}
					}
					"""
				));
	}

	@Test
	void createsWebMvcConfigurerWithHeaderConfiguration() {
		rewriteRun(
			spec -> spec.recipe(new AddApiVersionConfig(
					"com/example/config/WebConfig.java",
					"com.example.config",
					false,
					ConfigType.HEADER,
					"X-API-Version")),
			java(
				null,
				"""
				package com.example.config;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class WebConfig implements WebMvcConfigurer {
				    @Override
				    public void configureApiVersioning(ApiVersionConfigurer configurer) {
				        configurer.useRequestHeader("X-API-Version");
				    }
				}
				""",
				spec -> spec.path(Path.of("com/example/config/WebConfig.java"))
			)
		);
	}

	@Test
	void createsWebFluxConfigurerWithQueryParamConfiguration() {
		rewriteRun(
			spec -> spec.recipe(new AddApiVersionConfig(
					"com/example/config/WebFluxConfig.java",
					"com.example.config",
					true,
					ConfigType.QUERY,
					"version")),
			java(
				null,
				"""
				package com.example.config;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.reactive.config.WebFluxConfigurer;
				import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
				
				@Configuration
				public class WebFluxConfig implements WebFluxConfigurer {
				    @Override
				    public void configureApiVersioning(ApiVersionConfigurer configurer) {
				        configurer.useQueryParam("version");
				    }
				}
				""",
				spec -> spec.path(Path.of("com/example/config/WebFluxConfig.java"))
			)
		);
	}

	@Test
	void createsWebMvcConfigurerWithPathSegmentConfiguration() {
		rewriteRun(
			spec -> spec.recipe(new AddApiVersionConfig(
					"com/example/config/WebConfig.java",
					"com.example.config",
					false,
					ConfigType.PATH,
					"0")),
			java(
				null,
				"""
				package com.example.config;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class WebConfig implements WebMvcConfigurer {
				    @Override
				    public void configureApiVersioning(ApiVersionConfigurer configurer) {
				        configurer.usePathSegment(0);
				    }
				}
				""",
				spec -> spec.path(Path.of("com/example/config/WebConfig.java"))
			)
		);
	}

	@Test
	void createsWebMvcConfigurerWithMediaTypeConfiguration() {
		rewriteRun(
			spec -> spec.recipe(new AddApiVersionConfig(
					"com/example/config/WebConfig.java",
					"com.example.config",
					false,
					ConfigType.MEDIA_TYPE,
					"v")),
			java(
				null,
				"""
				package com.example.config;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.http.MediaType;
				import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class WebConfig implements WebMvcConfigurer {
				    @Override
				    public void configureApiVersioning(ApiVersionConfigurer configurer) {
				        configurer.useMediaTypeParameter(MediaType.ALL, "v");
				    }
				}
				""",
				spec -> spec.path(Path.of("com/example/config/WebConfig.java"))
			)
		);
	}

	@Test
	void addsMethodAndConfigurationToExistingConfigurer() {
		rewriteRun(
			spec -> spec.recipe(new AddApiVersionConfig(
					"com/example/config/WebConfig.java",
					"com.example.config",
					false,
					ConfigType.HEADER,
					"X-API-Version")),
			java(
				"""
				package com.example.config;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class WebConfig implements WebMvcConfigurer {
				}
				""",
				"""
				package com.example.config;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class WebConfig implements WebMvcConfigurer {
				    @Override
				    public void configureApiVersioning(ApiVersionConfigurer configurer) {
				        configurer.useRequestHeader("X-API-Version");
				    }
				}
				""",
				spec -> spec.path(Path.of("com/example/config/WebConfig.java"))
			)
		);
	}

	@Test
	void addsOnlyConfigurationCallWhenMethodAlreadyExists() {
		rewriteRun(
			spec -> spec.recipe(new AddApiVersionConfig(
					"com/example/config/WebConfig.java",
					"com.example.config",
					false,
					ConfigType.QUERY,
					"v")),
			java(
				"""
				package com.example.config;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class WebConfig implements WebMvcConfigurer {
				
				    @Override
				    public void configureApiVersioning(ApiVersionConfigurer configurer) {
				        configurer.useRequestHeader("X-API-Version");
				    }
				}
				""",
				"""
				package com.example.config;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class WebConfig implements WebMvcConfigurer {
				
				    @Override
				    public void configureApiVersioning(ApiVersionConfigurer configurer) {
				        configurer.useRequestHeader("X-API-Version");
				        configurer.useQueryParam("v");
				    }
				}
				""",
				spec -> spec.path(Path.of("com/example/config/WebConfig.java"))
			)
		);
	}

	@Test
	void doesNothingWhenEverythingAlreadyExists() {
		rewriteRun(
			spec -> spec.recipe(new AddApiVersionConfig(
					"com/example/config/WebConfig.java",
					"com.example.config",
					false,
					ConfigType.HEADER,
					"X-API-Version")),
			java(
				"""
				package com.example.config;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class WebConfig implements WebMvcConfigurer {
				
				    @Override
				    public void configureApiVersioning(ApiVersionConfigurer configurer) {
				        configurer.useRequestHeader("X-Custom-Header");
				    }
				}
				""",
				spec -> spec.path(Path.of("com/example/config/WebConfig.java"))
			)
		);
	}
}
