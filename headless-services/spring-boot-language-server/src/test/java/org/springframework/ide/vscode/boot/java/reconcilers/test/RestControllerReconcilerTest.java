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
import org.springframework.ide.vscode.boot.java.reconcilers.RestControllerReconciler;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;

/**
 * Tests for {@link RestControllerReconciler}
 *
 * @author Martin Lippert
 */
public class RestControllerReconcilerTest extends BaseReconcilerTest {

	@Override
	protected String getFolder() {
		return "restcontroller";
	}

	@Override
	protected String getProjectName() {
		return "test-spring-validations";
	}

	@Override
	protected JdtAstReconciler getReconciler() {
		return new RestControllerReconciler(new QuickfixRegistry());
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
				package restcontroller;

				import org.springframework.stereotype.Controller;
				import org.springframework.web.bind.annotation.ResponseBody;

				@Controller
				@ResponseBody
				class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);

		assertEquals(1, problems.size());

		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot2JavaProblemType.REST_CONTROLLER_COMBINATION, problem.getType());

		int expectedStart = source.indexOf("@Controller");
		int expectedEnd = source.indexOf("@ResponseBody") + "@ResponseBody".length();
		assertEquals(expectedStart, problem.getOffset());
		assertEquals(expectedEnd - expectedStart, problem.getLength());

		assertEquals(2, problem.getQuickfixes().size());
	}

	@Test
	void beanNameValueIsStillFlagged() throws Exception {
		String source = """
				package restcontroller;

				import org.springframework.stereotype.Controller;
				import org.springframework.web.bind.annotation.ResponseBody;

				@Controller("myController")
				@ResponseBody
				class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);

		assertEquals(1, problems.size());
		assertEquals(Boot2JavaProblemType.REST_CONTROLLER_COMBINATION, problems.get(0).getType());
	}

	@Test
	void missingResponseBodyIsNotFlagged() throws Exception {
		String source = """
				package restcontroller;

				import org.springframework.stereotype.Controller;

				@Controller
				class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);

		assertEquals(0, problems.size());
	}

	@Test
	void missingControllerIsNotFlagged() throws Exception {
		String source = """
				package restcontroller;

				import org.springframework.web.bind.annotation.ResponseBody;

				@ResponseBody
				class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);

		assertEquals(0, problems.size());
	}

	@Test
	void restControllerAloneIsNotFlagged() throws Exception {
		String source = """
				package restcontroller;

				import org.springframework.web.bind.annotation.RestController;

				@RestController
				class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);

		assertEquals(0, problems.size());
	}

}
