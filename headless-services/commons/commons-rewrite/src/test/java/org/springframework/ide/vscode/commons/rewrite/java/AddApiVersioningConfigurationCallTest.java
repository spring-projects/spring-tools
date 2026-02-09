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

public class AddApiVersioningConfigurationCallTest implements RewriteTest {

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
	void addsRequestHeaderConfiguration() {
		rewriteRun(
			spec -> spec.recipe(new AddApiVersioningConfigurationCall(
					"com/example/config/WebMvcConfig.java",
					ConfigType.HEADER,
					"X-API-Version")),
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
				    }
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
				        configurer.useRequestHeader("X-API-Version");
				    }
				}
				""",
				spec -> spec.path(Path.of("com/example/config/WebMvcConfig.java"))
			)
		);
	}

	@Test
	void addsQueryParamConfiguration() {
		rewriteRun(
			spec -> spec.recipe(new AddApiVersioningConfigurationCall(
					"com/example/config/WebMvcConfig.java",
					ConfigType.QUERY,
					"version")),
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
				    }
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
				        configurer.useQueryParam("version");
				    }
				}
				""",
				spec -> spec.path(Path.of("com/example/config/WebMvcConfig.java"))
			)
		);
	}

	@Test
	void addsPathSegmentConfiguration() {
		rewriteRun(
			spec -> spec.recipe(new AddApiVersioningConfigurationCall(
					"com/example/config/WebMvcConfig.java",
					ConfigType.PATH,
					"0")),
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
				    }
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
				        configurer.usePathSegment(0);
				    }
				}
				""",
				spec -> spec.path(Path.of("com/example/config/WebMvcConfig.java"))
			)
		);
	}

	@Test
	void addsMediaTypeParameterConfiguration() {
		rewriteRun(
			spec -> spec.recipe(new AddApiVersioningConfigurationCall(
					"com/example/config/WebMvcConfig.java",
					ConfigType.MEDIA_TYPE,
					"v")),
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
				    }
				}
				""",
				"""
				package com.example.config;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.http.MediaType;
				import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class WebMvcConfig implements WebMvcConfigurer {
				
				    @Override
				    public void configureApiVersioning(ApiVersionConfigurer configurer) {
				        configurer.useMediaTypeParameter(MediaType.ALL, "v");
				    }
				}
				""",
				spec -> spec.path(Path.of("com/example/config/WebMvcConfig.java"))
			)
		);
	}

	@Test
	void doesNotAddWhenConfigurationAlreadyExists() {
		rewriteRun(
			spec -> spec.recipe(new AddApiVersioningConfigurationCall(
					"com/example/config/WebMvcConfig.java",
					ConfigType.HEADER,
					"X-API-Version")),
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
				        configurer.useRequestHeader("X-Custom-Version");
				    }
				}
				""",
				spec -> spec.path(Path.of("com/example/config/WebMvcConfig.java"))
			)
		);
	}

	@Test
	void addsMultipleConfigurationsWhenCalledMultipleTimes() {
		rewriteRun(
			spec -> spec.recipe(new AddApiVersioningConfigurationCall(
					"com/example/config/WebMvcConfig.java",
					ConfigType.QUERY,
					"v")),
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
				public class WebMvcConfig implements WebMvcConfigurer {
				
				    @Override
				    public void configureApiVersioning(ApiVersionConfigurer configurer) {
				        configurer.useRequestHeader("X-API-Version");
				        configurer.useQueryParam("v");
				    }
				}
				""",
				spec -> spec.path(Path.of("com/example/config/WebMvcConfig.java"))
			)
		);
	}

	@Test
	void doesNotModifyFileWithDifferentPath() {
		rewriteRun(
			spec -> spec.recipe(new AddApiVersioningConfigurationCall(
					"com/example/config/WebMvcConfig.java",
					ConfigType.HEADER,
					"X-API-Version")),
			java(
				"""
				package com.example.other;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class OtherWebConfig implements WebMvcConfigurer {
				
				    @Override
				    public void configureApiVersioning(ApiVersionConfigurer configurer) {
				    }
				}
				""",
				spec -> spec.path(Path.of("com/example/other/OtherWebConfig.java"))
			)
		);
	}

	@Test
	void usesCorrectParameterNameFromMethodDeclaration() {
		rewriteRun(
			spec -> spec.recipe(new AddApiVersioningConfigurationCall(
					"com/example/config/WebMvcConfig.java",
					ConfigType.HEADER,
					"X-API-Version")),
			java(
				"""
				package com.example.config;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class WebMvcConfig implements WebMvcConfigurer {
				
				    @Override
				    public void configureApiVersioning(ApiVersionConfigurer apiVersionConfig) {
				    }
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
				    public void configureApiVersioning(ApiVersionConfigurer apiVersionConfig) {
				        apiVersionConfig.useRequestHeader("X-API-Version");
				    }
				}
				""",
				spec -> spec.path(Path.of("com/example/config/WebMvcConfig.java"))
			)
		);
	}
}
