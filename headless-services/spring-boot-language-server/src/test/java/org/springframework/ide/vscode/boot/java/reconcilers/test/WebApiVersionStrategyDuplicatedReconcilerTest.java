/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.Boot4JavaProblemType;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.WebApiVersionStrategyDuplicatedReconciler;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;

public class WebApiVersionStrategyDuplicatedReconcilerTest extends BaseReconcilerTest {

	@Override
	protected String getFolder() {
		return "webapiversioning";
	}

	@Override
	protected String getProjectName() {
		return "sf7-validation";
	}

	@Override
	protected JdtAstReconciler getReconciler() {
		return new WebApiVersionStrategyDuplicatedReconciler();
	}

	@BeforeEach
	void setup() throws Exception {
		super.setup();
	}
	
	@AfterEach
	void tearDown() throws Exception {
		super.tearDown();
	}
	
	@Test
	void webConfigWithNonDuplicatedStrategies() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class A implements WebMvcConfigurer {
					
					@Override
					public void configureApiVersioning(ApiVersionConfigurer configurer) {
						configurer.useRequestHeader("X-API-Version");
						configurer.usePathSegment(1);
					}
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			return new WebApiVersionStrategyDuplicatedReconciler();
		}, "A.java", source, true);
		
		assertEquals(0, problems.size());
	}

	@Test
	void webConfigWithNonDuplicatedStrategiesButOtherDuplicatedMethodInvocations() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class A implements WebMvcConfigurer {
					
					@Override
					public void configureApiVersioning(ApiVersionConfigurer configurer) {
						configurer.useRequestHeader("X-API-Version").addSupportedVersions("1");
						configurer.usePathSegment(1).addSupportedVersions("1");
					}
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			return new WebApiVersionStrategyDuplicatedReconciler();
		}, "A.java", source, true);
		
		assertEquals(0, problems.size());
	}

	@Test
	void webConfigHasDuplicatedStrategy() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class A implements WebMvcConfigurer {
					
					@Override
					public void configureApiVersioning(ApiVersionConfigurer configurer) {
						configurer.useRequestHeader("X-API-Version");
						configurer.usePathSegment(1);
						configurer.usePathSegment(1);
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile(() -> {
			return new WebApiVersionStrategyDuplicatedReconciler();
		}, "A.java", source, true);
		
		assertEquals(2, problems.size());
		
		ReconcileProblem problem1 = problems.get(0);
		assertEquals(Boot4JavaProblemType.API_VERSIONING_STRATEGY_CONFIGURATION_DUPLICATED, problem1.getType());
		String markedStr1 = source.substring(problem1.getOffset(), problem1.getOffset() + problem1.getLength());
		assertEquals("configurer.usePathSegment(1)", markedStr1);
		assertEquals(0, problem1.getQuickfixes().size());

		ReconcileProblem problem2 = problems.get(1);
		assertEquals(Boot4JavaProblemType.API_VERSIONING_STRATEGY_CONFIGURATION_DUPLICATED, problem2.getType());
		String markedStr2 = source.substring(problem2.getOffset(), problem2.getOffset() + problem2.getLength());
		assertEquals("configurer.usePathSegment(1)", markedStr2);
		assertEquals(0, problem2.getQuickfixes().size());
	}

	@Test
	void webConfigHasMultipleDuplicatedStrategies() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class A implements WebMvcConfigurer {
					
					@Override
					public void configureApiVersioning(ApiVersionConfigurer configurer) {
						configurer.useRequestHeader("X-API-Version");
						configurer.usePathSegment(1);
						configurer.useRequestHeader("X-API-Version");
						configurer.usePathSegment(1);
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile(() -> {
			return new WebApiVersionStrategyDuplicatedReconciler();
		}, "A.java", source, true);
		
		assertEquals(4, problems.size());
		
		ReconcileProblem problem1 = problems.get(0);
		assertEquals(Boot4JavaProblemType.API_VERSIONING_STRATEGY_CONFIGURATION_DUPLICATED, problem1.getType());
		String markedStr1 = source.substring(problem1.getOffset(), problem1.getOffset() + problem1.getLength());
		assertEquals("configurer.usePathSegment(1)", markedStr1);
		assertEquals(0, problem1.getQuickfixes().size());

		ReconcileProblem problem2 = problems.get(1);
		assertEquals(Boot4JavaProblemType.API_VERSIONING_STRATEGY_CONFIGURATION_DUPLICATED, problem2.getType());
		String markedStr2 = source.substring(problem2.getOffset(), problem2.getOffset() + problem2.getLength());
		assertEquals("configurer.usePathSegment(1)", markedStr2);
		assertEquals(0, problem2.getQuickfixes().size());

		ReconcileProblem problem3 = problems.get(2);
		assertEquals(Boot4JavaProblemType.API_VERSIONING_STRATEGY_CONFIGURATION_DUPLICATED, problem3.getType());
		String markedStr3 = source.substring(problem3.getOffset(), problem3.getOffset() + problem3.getLength());
		assertEquals("configurer.useRequestHeader(\"X-API-Version\")", markedStr3);
		assertEquals(0, problem3.getQuickfixes().size());

		ReconcileProblem problem4 = problems.get(3);
		assertEquals(Boot4JavaProblemType.API_VERSIONING_STRATEGY_CONFIGURATION_DUPLICATED, problem4.getType());
		String markedStr4 = source.substring(problem4.getOffset(), problem4.getOffset() + problem4.getLength());
		assertEquals("configurer.useRequestHeader(\"X-API-Version\")", markedStr4);
		assertEquals(0, problem4.getQuickfixes().size());
	}



}
