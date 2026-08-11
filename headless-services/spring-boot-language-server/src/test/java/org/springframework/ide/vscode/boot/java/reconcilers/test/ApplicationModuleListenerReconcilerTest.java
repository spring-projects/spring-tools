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
import org.springframework.ide.vscode.boot.java.Boot3JavaProblemType;
import org.springframework.ide.vscode.boot.java.reconcilers.ApplicationModuleListenerReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;

/**
 * Tests for {@link ApplicationModuleListenerReconciler}
 *
 * @author Martin Lippert
 */
public class ApplicationModuleListenerReconcilerTest extends BaseReconcilerTest {

	@Override
	protected String getFolder() {
		return "applicationmodulelistener";
	}

	@Override
	protected String getProjectName() {
		return "spring-modulith-example-full";
	}

	@Override
	protected JdtAstReconciler getReconciler() {
		return new ApplicationModuleListenerReconciler(new QuickfixRegistry());
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
				package applicationmodulelistener;

				import org.springframework.scheduling.annotation.Async;
				import org.springframework.transaction.annotation.Transactional;
				import org.springframework.transaction.event.TransactionalEventListener;

				class A {

					@Async
					@Transactional
					@TransactionalEventListener
					void on(Object event) {
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);

		assertEquals(1, problems.size());

		ReconcileProblem problem = problems.get(0);
		assertEquals(Boot3JavaProblemType.MODULITH_APPLICATION_MODULE_LISTENER, problem.getType());

		int expectedStart = source.indexOf("@Async");
		int expectedEnd = source.indexOf("@TransactionalEventListener") + "@TransactionalEventListener".length();
		assertEquals(expectedStart, problem.getOffset());
		assertEquals(expectedEnd - expectedStart, problem.getLength());

		assertEquals(2, problem.getQuickfixes().size());
	}

	@Test
	void supportedAttributesAreStillFlagged() throws Exception {
		String source = """
				package applicationmodulelistener;

				import org.springframework.scheduling.annotation.Async;
				import org.springframework.transaction.annotation.Transactional;
				import org.springframework.transaction.event.TransactionalEventListener;

				class A {

					@Async
					@Transactional(readOnly = true)
					@TransactionalEventListener(id = "myId", condition = "#event != null")
					void on(Object event) {
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);

		assertEquals(1, problems.size());
		assertEquals(Boot3JavaProblemType.MODULITH_APPLICATION_MODULE_LISTENER, problems.get(0).getType());
	}

	@Test
	void unsupportedTransactionalAttributeIsNotFlagged() throws Exception {
		String source = """
				package applicationmodulelistener;

				import org.springframework.scheduling.annotation.Async;
				import org.springframework.transaction.annotation.Transactional;
				import org.springframework.transaction.event.TransactionalEventListener;

				class A {

					@Async
					@Transactional(timeout = 5)
					@TransactionalEventListener
					void on(Object event) {
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);

		assertEquals(0, problems.size());
	}

	@Test
	void unsupportedEventListenerAttributeIsNotFlagged() throws Exception {
		String source = """
				package applicationmodulelistener;

				import org.springframework.scheduling.annotation.Async;
				import org.springframework.transaction.annotation.Transactional;
				import org.springframework.transaction.event.TransactionPhase;
				import org.springframework.transaction.event.TransactionalEventListener;

				class A {

					@Async
					@Transactional
					@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
					void on(Object event) {
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);

		assertEquals(0, problems.size());
	}

	@Test
	void unsupportedAsyncAttributeIsNotFlagged() throws Exception {
		String source = """
				package applicationmodulelistener;

				import org.springframework.scheduling.annotation.Async;
				import org.springframework.transaction.annotation.Transactional;
				import org.springframework.transaction.event.TransactionalEventListener;

				class A {

					@Async("myExecutor")
					@Transactional
					@TransactionalEventListener
					void on(Object event) {
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);

		assertEquals(0, problems.size());
	}

	@Test
	void missingAnnotationIsNotFlagged() throws Exception {
		String source = """
				package applicationmodulelistener;

				import org.springframework.transaction.annotation.Transactional;
				import org.springframework.transaction.event.TransactionalEventListener;

				class A {

					@Transactional
					@TransactionalEventListener
					void on(Object event) {
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);

		assertEquals(0, problems.size());
	}

}
