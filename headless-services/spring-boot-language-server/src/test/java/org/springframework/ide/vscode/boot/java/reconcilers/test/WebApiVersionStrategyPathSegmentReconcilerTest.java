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
import org.springframework.ide.vscode.boot.java.reconcilers.WebApiVersionStrategyPathSegmentReconciler;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;

public class WebApiVersionStrategyPathSegmentReconcilerTest extends BaseReconcilerTest {

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
		return new WebApiVersionStrategyPathSegmentReconciler();
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
	void webConfigUsesPathSegmentAndSomethingElseShowsError() throws Exception {
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
			return new WebApiVersionStrategyPathSegmentReconciler();
		}, "A.java", source, true);
		
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		
		assertEquals(Boot4JavaProblemType.API_VERSIONING_VIA_PATH_SEGMENT_CONFIGURED_IN_COMBINATION, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("configurer.usePathSegment(1)", markedStr);

		assertEquals(0, problem.getQuickfixes().size());
	}

	@Test
	void webConfigUsesPathSegmentwithoutAnythingElseShowsNoError() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class A implements WebMvcConfigurer {
					
					@Override
					public void configureApiVersioning(ApiVersionConfigurer configurer) {
						configurer.usePathSegment(1);
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile(() -> {
			return new WebApiVersionStrategyPathSegmentReconciler();
		}, "A.java", source, true);
		
		assertEquals(0, problems.size());
	}

	@Test
	void webConfigUsesNonPathSegmentStrategyWithoutPathSegmentStrategyShowsNoError() throws Exception {
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
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile(() -> {
			return new WebApiVersionStrategyPathSegmentReconciler();
		}, "A.java", source, true);
		
		assertEquals(0, problems.size());
	}

}
