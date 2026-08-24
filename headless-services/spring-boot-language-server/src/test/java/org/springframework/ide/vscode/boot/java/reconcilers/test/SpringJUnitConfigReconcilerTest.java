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

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.SpringJUnitConfigReconciler;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;

/**
 * Tests for {@link SpringJUnitConfigReconciler}
 *
 * @author Martin Lippert
 */
public class SpringJUnitConfigReconcilerTest extends BaseReconcilerTest {

	@Override
	protected String getFolder() {
		return "springjunitconfig";
	}

	@Override
	protected String getProjectName() {
		return "test-spring-validations";
	}

	@Override
	protected JdtAstReconciler getReconciler() {
		return new SpringJUnitConfigReconciler(new QuickfixRegistry());
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
	void plainCombinationIsFlagged() throws Exception {
		String source = """
				package springjunitconfig;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.test.context.ContextConfiguration;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				@ExtendWith(SpringExtension.class)
				@ContextConfiguration
				class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);

		assertEquals(1, problems.size());

		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.SPRING_JUNIT_CONFIG_COMBINATION, problem.getType());

		int expectedStart = source.indexOf("@ExtendWith");
		int expectedEnd = source.indexOf("@ContextConfiguration") + "@ContextConfiguration".length();
		assertEquals(expectedStart, problem.getOffset());
		assertEquals(expectedEnd - expectedStart, problem.getLength());

		assertEquals(2, problem.getQuickfixes().size());
	}

	@Test
	void combinationWithConfigurationClassesIsFlagged() throws Exception {
		String source = """
				package springjunitconfig;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.context.annotation.Configuration;
				import org.springframework.test.context.ContextConfiguration;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				@ExtendWith(SpringExtension.class)
				@ContextConfiguration(classes = A.TestConfig.class)
				class A {
					@Configuration
					static class TestConfig {
					}
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);

		assertEquals(1, problems.size());
		assertEquals(Boot2JavaProblemType.SPRING_JUNIT_CONFIG_COMBINATION, problems.get(0).getType());
	}

	@Test
	void missingContextConfigurationIsNotFlagged() throws Exception {
		String source = """
				package springjunitconfig;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				@ExtendWith(SpringExtension.class)
				class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);

		assertEquals(0, problems.size());
	}

	@Test
	void missingExtendWithIsNotFlagged() throws Exception {
		String source = """
				package springjunitconfig;

				import org.springframework.test.context.ContextConfiguration;

				@ContextConfiguration
				class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);

		assertEquals(0, problems.size());
	}

	@Test
	void otherExtensionIsNotFlagged() throws Exception {
		String source = """
				package springjunitconfig;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.mockito.junit.jupiter.MockitoExtension;
				import org.springframework.test.context.ContextConfiguration;

				@ExtendWith(MockitoExtension.class)
				@ContextConfiguration
				class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);

		assertEquals(0, problems.size());
	}

	@Test
	void furtherExtensionsAreNotFlagged() throws Exception {
		String source = """
				package springjunitconfig;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.mockito.junit.jupiter.MockitoExtension;
				import org.springframework.test.context.ContextConfiguration;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				@ExtendWith({ SpringExtension.class, MockitoExtension.class })
				@ContextConfiguration
				class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);

		assertEquals(0, problems.size());
	}

	@Test
	void repeatedExtendWithIsFlagged() throws Exception {
		String source = """
				package springjunitconfig;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.mockito.junit.jupiter.MockitoExtension;
				import org.springframework.test.context.ContextConfiguration;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				@ExtendWith(SpringExtension.class)
				@ExtendWith(MockitoExtension.class)
				@ContextConfiguration
				class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);

		assertEquals(1, problems.size());
		assertEquals(Boot2JavaProblemType.SPRING_JUNIT_CONFIG_COMBINATION, problems.get(0).getType());
	}

	@Test
	void springJUnitConfigAloneIsNotFlagged() throws Exception {
		String source = """
				package springjunitconfig;

				import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

				@SpringJUnitConfig
				class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);

		assertEquals(0, problems.size());
	}

	@Test
	void springBootTestIsNotFlagged() throws Exception {
		String source = """
				package springjunitconfig;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.boot.test.context.SpringBootTest;
				import org.springframework.test.context.ContextConfiguration;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				@ExtendWith(SpringExtension.class)
				@SpringBootTest
				@ContextConfiguration
				class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);

		assertEquals(0, problems.size());
	}

	@Test
	void testSliceAnnotationIsNotFlagged() throws Exception {
		String source = """
				package springjunitconfig;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
				import org.springframework.test.context.ContextConfiguration;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				@ExtendWith(SpringExtension.class)
				@WebMvcTest
				@ContextConfiguration
				class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);

		assertEquals(0, problems.size());
	}

	@Test
	void multipleTypesInOneFileAreAllFlagged() throws Exception {
		String source = """
				package springjunitconfig;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.test.context.ContextConfiguration;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				@ExtendWith(SpringExtension.class)
				@ContextConfiguration
				class A {
				}

				@ExtendWith(SpringExtension.class)
				@ContextConfiguration
				class B {
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);

		assertEquals(2, problems.size());
	}

}
