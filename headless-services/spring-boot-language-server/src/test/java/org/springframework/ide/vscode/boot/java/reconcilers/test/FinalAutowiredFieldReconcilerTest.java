/*******************************************************************************
 * Copyright (c) 2026 Broadcom
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
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.reconcilers.FinalAutowiredFieldReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;

public class FinalAutowiredFieldReconcilerTest extends BaseReconcilerTest {

	@Override
	protected String getFolder() {
		return "finalautowiredfieldtest";
	}

	@Override
	protected String getProjectName() {
		return "test-spring-indexing";
	}

	protected JdtAstReconciler getReconciler() {
		return new FinalAutowiredFieldReconciler();
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
	void finalAutowiredField() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.beans.factory.annotation.Autowired;
				
				class A {
				
					@Autowired
					final String a = "";
					
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);

		assertEquals(1, problems.size());

		ReconcileProblem problem = problems.get(0);

		assertEquals(Boot2JavaProblemType.JAVA_FINAL_AUTOWIRED_FIELD, problem.getType());

		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("@Autowired\n\tfinal String a = \"\";", markedStr);
	}

	@Test
	void nonFinalAutowiredField() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.beans.factory.annotation.Autowired;
				
				class A {
				
					@Autowired
					String a;
					
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);

		assertEquals(0, problems.size());
	}

	@Test
	void noAutowiredAnnotation() throws Exception {
		String source = """
				package example.demo;
				
				class A {
				
					final String a = "";
					
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);

		assertEquals(0, problems.size());
	}

	@Test
	void finalAutowiredFieldWithPrivateModifier() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.beans.factory.annotation.Autowired;
				
				class A {
				
					@Autowired
					private final String a = "";
					
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);

		assertEquals(1, problems.size());

		ReconcileProblem problem = problems.get(0);

		assertEquals(Boot2JavaProblemType.JAVA_FINAL_AUTOWIRED_FIELD, problem.getType());
	}

	@Test
	void multipleFinalAutowiredFields() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.beans.factory.annotation.Autowired;
				
				class A {
				
					@Autowired
					final String a = "";
					
					@Autowired
					final String b = "";
					
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);

		assertEquals(2, problems.size());

		assertEquals(Boot2JavaProblemType.JAVA_FINAL_AUTOWIRED_FIELD, problems.get(0).getType());
		assertEquals(Boot2JavaProblemType.JAVA_FINAL_AUTOWIRED_FIELD, problems.get(1).getType());
	}

	@Test
	void mixedFinalAndNonFinalAutowiredFields() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.beans.factory.annotation.Autowired;
				
				class A {
				
					@Autowired
					final String a = "";
					
					@Autowired
					String b;
					
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);

		assertEquals(1, problems.size());

		ReconcileProblem problem = problems.get(0);

		assertEquals(Boot2JavaProblemType.JAVA_FINAL_AUTOWIRED_FIELD, problem.getType());

		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("@Autowired\n\tfinal String a = \"\";", markedStr);
	}

}
