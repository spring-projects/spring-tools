/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.cron;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.commons.languageserver.reconcile.BasicProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;

public class CronReconcilerTest {
	
	private CronReconciler reconciler = new CronReconciler();
	private List<ReconcileProblem> problems;
	private IProblemCollector collector;
	
	@BeforeEach
	void setup() {
		problems = new ArrayList<>();
		collector = new BasicProblemCollector(problems);
	}
	
	@Test
	void noProblems_1() {
		reconciler.reconcile("0 0 0 L-3 * *", Function.identity(), collector);
		assertEquals(0, problems.size());
	}

	@Test
	void DayOfTheWeekProblems_1() {
		reconciler.reconcile("0 0 0 8 * MAR-JUL", Function.identity(), collector);
		assertEquals(1, problems.size());
		assertReconcileProblem(problems.get(0), CronProblemType.FIELD, 10, 6);
	}

	@Test
	void noProblems_3() {
		reconciler.reconcile("MAR-JUL 0 0 8 * *", Function.identity(), collector);
		assertEquals(1, problems.size());
		assertReconcileProblem(problems.get(0), CronProblemType.FIELD, 0, 7);
	}

	@Test
	void syntax_and_field_problems_1() {
		reconciler.reconcile("0 0 0 8LW * MARCH-JUL", Function.identity(), collector);
		assertEquals(3, problems.size());
		assertReconcileProblem(problems.get(0), CronProblemType.SYNTAX, 7, 2);
		assertReconcileProblem(problems.get(1), CronProblemType.SYNTAX, 12, 5);		
		assertReconcileProblem(problems.get(2), CronProblemType.FIELD, 18, 2);		
	}
	
	@Test
	void syntax_problems_2() {
		reconciler.reconcile("qq#3 0 Blah 1-88LW * JUL-MARCH", Function.identity(), collector);
		assertEquals(1, problems.size());
		assertReconcileProblem(problems.get(0), CronProblemType.SYNTAX, 0, 2);
	}
	
	@Test
	void syntax_problems_3() {
		reconciler.reconcile("10/2. * * ? * MON-5", Function.identity(), collector);
		assertEquals(1, problems.size());
		assertReconcileProblem(problems.get(0), CronProblemType.SYNTAX, 4, 0);
	}
	
	static void assertReconcileProblem(ReconcileProblem p, ProblemType type, int offset, int length) {
		assertEquals(offset, p.getOffset());
		assertEquals(length, p.getLength());
		assertEquals(type, p.getType());
	}
}
