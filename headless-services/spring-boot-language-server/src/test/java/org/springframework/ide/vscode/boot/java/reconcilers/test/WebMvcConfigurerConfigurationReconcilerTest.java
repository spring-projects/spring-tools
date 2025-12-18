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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.WebConfigurerConfigurationReconciler;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;

/**
 * Tests for {@link WebConfigurerConfigurationReconciler}
 * 
 * @author Martin Lippert
 */
public class WebMvcConfigurerConfigurationReconcilerTest extends BaseReconcilerTest {

	@Override
	protected String getFolder() {
		return "webconfigurer";
	}

	@Override
	protected String getProjectName() {
		return "test-spring-indexing";
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
	void classImplementingWebMvcConfigurerWithoutConfiguration() throws Exception {
		String source = """
				package example;
				
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				public class MyWebConfig implements WebMvcConfigurer {
				}
				""";

		List<ReconcileProblem> problems = reconcile("MyWebConfig.java", source, false);
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.WEB_CONFIGURER_CONFIGURATION, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("MyWebConfig", markedStr);
	}

	@Test
	void classImplementingWebMvcConfigurerWithConfiguration() throws Exception {
		String source = """
				package example;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class MyWebConfig implements WebMvcConfigurer {
				}
				""";

		List<ReconcileProblem> problems = reconcile("MyWebConfig.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void classNotImplementingWebMvcConfigurer() throws Exception {
		String source = """
				package example;
				
				public class RegularClass {
				}
				""";

		List<ReconcileProblem> problems = reconcile("RegularClass.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void abstractClassImplementingWebMvcConfigurer() throws Exception {
		String source = """
				package example;
				
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				public abstract class AbstractWebConfig implements WebMvcConfigurer {
				}
				""";

		List<ReconcileProblem> problems = reconcile("AbstractWebConfig.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void interfaceExtendingWebMvcConfigurer() throws Exception {
		String source = """
				package example;
				
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				public interface MyWebInterface extends WebMvcConfigurer {
				}
				""";

		List<ReconcileProblem> problems = reconcile("MyWebInterface.java", source, false);
		assertEquals(0, problems.size());
	}

	@Test
	void classWithOtherAnnotation() throws Exception {
		String source = """
				package example;
				
				import org.springframework.stereotype.Component;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Component
				public class MyWebConfig implements WebMvcConfigurer {
				}
				""";

		List<ReconcileProblem> problems = reconcile("MyWebConfig.java", source, false);
		// @Component is a meta-annotation that includes @Configuration characteristics
		// but for this reconciler, we want explicit @Configuration
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.WEB_CONFIGURER_CONFIGURATION, problem.getType());
	}

	@Test
	void classImplementingWebMvcConfigurerIndirectly() throws Exception {
		String source1 = """
				package example;
				
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				public interface CustomConfigurer extends WebMvcConfigurer {
				}
				""";
		
		String source2 = """
				package example;
				
				public class MyWebConfig implements CustomConfigurer {
				}
				""";

		Path extraSource = createFile("CustomConfigurer.java", source1);
		List<ReconcileProblem> problems = reconcile("MyWebConfig.java", source2, false, extraSource);
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.WEB_CONFIGURER_CONFIGURATION, problem.getType());
	}

	@Test
	void innerClassImplementingWebMvcConfigurer() throws Exception {
		String source = """
				package example;
				
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				public class OuterClass {
					public class InnerConfig implements WebMvcConfigurer {
					}
				}
				""";

		List<ReconcileProblem> problems = reconcile("OuterClass.java", source, false);
		// Inner non-static classes are not applicable
		assertEquals(0, problems.size());
	}

	@Test
	void staticInnerClassImplementingWebMvcConfigurer() throws Exception {
		String source = """
				package example;
				
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				public class OuterClass {
					public static class InnerConfig implements WebMvcConfigurer {
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

	// Note: WebFluxConfigurer is also supported by this reconciler, using the same validation logic.
	// Testing WebFluxConfigurer requires a test project with spring-webflux dependencies (e.g., test-webflux-project),
	// but the implementation uses ASTUtils.findInTypeHierarchy() which checks for both WEB_MVC_CONFIGURER_INTERFACE
	// and WEB_FLUX_CONFIGURER_INTERFACE, so WebFlux configurers are validated identically to WebMvc configurers.

}

