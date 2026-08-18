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
package org.springframework.ide.vscode.boot.java.reconcilers.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.ScopeAnnotationReconciler;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;

/**
 * Tests for {@link ScopeAnnotationReconciler}
 *
 * @author Martin Lippert
 */
public class ScopeAnnotationReconcilerTest extends BaseReconcilerTest {

	@Override
	protected String getFolder() {
		return "scopeannotation";
	}

	@Override
	protected String getProjectName() {
		return "test-spring-validations";
	}

	@Override
	protected JdtAstReconciler getReconciler() {
		return new ScopeAnnotationReconciler(new QuickfixRegistry());
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
	void isApplicableWhenSpringWebIsOnClasspath() throws Exception {
		assertTrue(getReconciler().isApplicable(project));
	}

	@Test
	void isNotApplicableWithoutSpringWeb() throws Exception {
		IJavaProject nonWebProject = ProjectsHarness.INSTANCE.mavenProject("empty-boot-2.4.4-app");
		assertFalse(getReconciler().isApplicable(nonWebProject));
	}

	@Test
	void requestScopeWithoutProxyModeOffersTwoFixes() throws Exception {
		String source = """
				package scopeannotation;

				import org.springframework.context.annotation.Scope;
				import org.springframework.stereotype.Component;

				@Component
				@Scope("request")
				class FooBean {
				}
				""";
		List<ReconcileProblem> problems = reconcile("FooBean.java", source, false);

		assertEquals(1, problems.size());
		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.JAVA_PRECISE_SCOPE, problem.getType());

		int expectedStart = source.indexOf("@Scope(\"request\")");
		assertEquals(expectedStart, problem.getOffset());
		assertEquals("@Scope(\"request\")".length(), problem.getLength());

		assertEquals(2, problem.getQuickfixes().size());
		assertEquals("Replace with `@RequestScope`, keeping its current proxy mode (`DEFAULT`)",
				problem.getQuickfixes().get(0).title);
		assertEquals("Replace with `@RequestScope` (changes its proxy mode to the default, `TARGET_CLASS`)",
				problem.getQuickfixes().get(1).title);
	}

	@Test
	void sessionScopeWithExplicitTargetClassProxyModeOffersSingleFix() throws Exception {
		String source = """
				package scopeannotation;

				import org.springframework.context.annotation.Scope;
				import org.springframework.context.annotation.ScopedProxyMode;
				import org.springframework.stereotype.Component;

				@Component
				@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
				class FooBean {
				}
				""";
		List<ReconcileProblem> problems = reconcile("FooBean.java", source, false);

		assertEquals(1, problems.size());
		ReconcileProblem problem = problems.get(0);
		assertEquals(1, problem.getQuickfixes().size());
		assertEquals("Replace with `@SessionScope`", problem.getQuickfixes().get(0).title);
	}

	@Test
	void applicationScopeWithNonTargetClassProxyModeOffersTwoFixes() throws Exception {
		String source = """
				package scopeannotation;

				import org.springframework.context.annotation.Bean;
				import org.springframework.context.annotation.Configuration;
				import org.springframework.context.annotation.Scope;
				import org.springframework.context.annotation.ScopedProxyMode;

				@Configuration
				class FooConfig {

					@Bean
					@Scope(value = "application", proxyMode = ScopedProxyMode.NO)
					FooBean fooBean() {
						return new FooBean();
					}

					static class FooBean {
					}
				}
				""";
		List<ReconcileProblem> problems = reconcile("FooConfig.java", source, false);

		assertEquals(1, problems.size());
		ReconcileProblem problem = problems.get(0);
		assertEquals(2, problem.getQuickfixes().size());
		assertEquals("Replace with `@ApplicationScope`, keeping its current proxy mode (`NO`)",
				problem.getQuickfixes().get(0).title);
		assertEquals("Replace with `@ApplicationScope` (changes its proxy mode to the default, `TARGET_CLASS`)",
				problem.getQuickfixes().get(1).title);
	}

	@Test
	void otherScopeNamesAreNotFlagged() throws Exception {
		String source = """
				package scopeannotation;

				import org.springframework.context.annotation.Scope;
				import org.springframework.stereotype.Component;

				@Component
				@Scope("prototype")
				class FooBean {
				}
				""";
		List<ReconcileProblem> problems = reconcile("FooBean.java", source, false);

		assertTrue(problems.isEmpty());
	}

	@Test
	void alreadyPreciseScopeAnnotationIsNotFlagged() throws Exception {
		String source = """
				package scopeannotation;

				import org.springframework.stereotype.Component;
				import org.springframework.web.context.annotation.RequestScope;

				@Component
				@RequestScope
				class FooBean {
				}
				""";
		List<ReconcileProblem> problems = reconcile("FooBean.java", source, false);

		assertTrue(problems.isEmpty());
	}

	@Test
	void constantScopeNameIsResolved() throws Exception {
		String source = """
				package scopeannotation;

				import org.springframework.context.annotation.Scope;
				import org.springframework.stereotype.Component;
				import org.springframework.web.context.WebApplicationContext;

				@Component
				@Scope(WebApplicationContext.SCOPE_REQUEST)
				class FooBean {
				}
				""";
		List<ReconcileProblem> problems = reconcile("FooBean.java", source, false);

		assertEquals(1, problems.size());
		assertEquals(2, problems.get(0).getQuickfixes().size());
	}

}
