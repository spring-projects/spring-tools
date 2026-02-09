/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.WebConfigurerConfigurationReconciler;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;

/**
 * Tests for {@link WebConfigurerConfigurationReconciler} specifically for WebFlux cases
 * 
 * @author Martin Lippert
 */
public class WebFluxConfigurerConfigurationReconcilerTest extends BaseReconcilerTest {

	@Override
	protected String getFolder() {
		return "webfluxconfigurer";
	}

	@Override
	protected String getProjectName() {
		return "test-webflux-project";
	}

	@Override
	protected JdtAstReconciler getReconciler() {
		return new WebConfigurerConfigurationReconciler(new QuickfixRegistry());
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
	void classImplementingWebFluxConfigurerWithoutConfiguration() throws Exception {
		String source = """
				package example;
				
				import org.springframework.web.reactive.config.WebFluxConfigurer;
				
				public class MyWebFluxConfig implements WebFluxConfigurer {
				}
				""";

		List<ReconcileProblem> problems = reconcile("MyWebFluxConfig.java", source, false);
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.WEB_CONFIGURER_CONFIGURATION, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("MyWebFluxConfig", markedStr);
	}

	@Test
	void classImplementingWebFluxConfigurerWithConfiguration() throws Exception {
		String source = """
				package example;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.reactive.config.WebFluxConfigurer;
				
				@Configuration
				public class MyWebFluxConfig implements WebFluxConfigurer {
				}
				""";

		List<ReconcileProblem> problems = reconcile("MyWebFluxConfig.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void classNotImplementingWebFluxConfigurer() throws Exception {
		String source = """
				package example;
				
				public class RegularClass {
				}
				""";

		List<ReconcileProblem> problems = reconcile("RegularClass.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void abstractClassImplementingWebFluxConfigurer() throws Exception {
		String source = """
				package example;
				
				import org.springframework.web.reactive.config.WebFluxConfigurer;
				
				public abstract class AbstractWebFluxConfig implements WebFluxConfigurer {
				}
				""";

		List<ReconcileProblem> problems = reconcile("AbstractWebFluxConfig.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void interfaceExtendingWebFluxConfigurer() throws Exception {
		String source = """
				package example;
				
				import org.springframework.web.reactive.config.WebFluxConfigurer;
				
				public interface MyWebFluxInterface extends WebFluxConfigurer {
				}
				""";

		List<ReconcileProblem> problems = reconcile("MyWebFluxInterface.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void classWithOtherAnnotation() throws Exception {
		String source = """
				package example;
				
				import org.springframework.stereotype.Component;
				import org.springframework.web.reactive.config.WebFluxConfigurer;
				
				@Component
				public class MyWebFluxConfig implements WebFluxConfigurer {
				}
				""";

		List<ReconcileProblem> problems = reconcile("MyWebFluxConfig.java", source, false);
		// @Component is a meta-annotation that includes @Configuration characteristics
		// but for this reconciler, we want explicit @Configuration
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.WEB_CONFIGURER_CONFIGURATION, problem.getType());
	}

	@Test
	void classImplementingWebFluxConfigurerIndirectly() throws Exception {
		String source1 = """
				package example;
				
				import org.springframework.web.reactive.config.WebFluxConfigurer;
				
				public interface CustomConfigurer extends WebFluxConfigurer {
				}
				""";
		
		String source2 = """
				package example;
				
				public class MyWebFluxConfig implements CustomConfigurer {
				}
				""";

		Path extraSource = createFile("CustomConfigurer.java", source1);
		List<ReconcileProblem> problems = reconcile("MyWebFluxConfig.java", source2, false, extraSource);
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.WEB_CONFIGURER_CONFIGURATION, problem.getType());
	}

	@Test
	void innerClassImplementingWebFluxConfigurer() throws Exception {
		String source = """
				package example;
				
				import org.springframework.web.reactive.config.WebFluxConfigurer;
				
				public class OuterClass {
					public class InnerConfig implements WebFluxConfigurer {
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile("OuterClass.java", source, false);
		// Inner non-static classes are not applicable
		assertEquals(0, problems.size());
	}

	@Test
	void staticInnerClassImplementingWebFluxConfigurer() throws Exception {
		String source = """
				package example;
				
				import org.springframework.web.reactive.config.WebFluxConfigurer;
				
				public class OuterClass {
					public static class InnerConfig implements WebFluxConfigurer {
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile("OuterClass.java", source, false);
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.WEB_CONFIGURER_CONFIGURATION, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("InnerConfig", markedStr);
	}

	@Test
	void classWithEnableWebFluxAnnotation() throws Exception {
		String source = """
				package example;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.reactive.config.EnableWebFlux;
				import org.springframework.web.reactive.config.WebFluxConfigurer;
				
				@Configuration
				@EnableWebFlux
				public class MyWebFluxConfig implements WebFluxConfigurer {
				}
				""";

		List<ReconcileProblem> problems = reconcile("MyWebFluxConfig.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void classImplementingWebFluxConfigurerWithMultipleMethods() throws Exception {
		String source = """
				package example;
				
				import org.springframework.web.reactive.config.CorsRegistry;
				import org.springframework.web.reactive.config.WebFluxConfigurer;
				
				public class MyWebFluxConfig implements WebFluxConfigurer {
				
					@Override
					public void addCorsMappings(CorsRegistry registry) {
						registry.addMapping("/**");
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile("MyWebFluxConfig.java", source, false);
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.WEB_CONFIGURER_CONFIGURATION, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("MyWebFluxConfig", markedStr);
	}

}

