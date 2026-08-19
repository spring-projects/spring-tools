/*******************************************************************************
 * Copyright (c) 2023, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.SpringAotJavaProblemType;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.PreciseBeanTypeReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.RequiredCompleteAstException;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtFixDescriptor;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;

public class PreciseBeanTypeReconcilerTest extends BaseReconcilerTest {

	@Override
	protected String getFolder() {
		return "precisebeantype";
	}

	@Override
	protected String getProjectName() {
		return "test-spring-validations";
	}

	@Override
	protected JdtAstReconciler getReconciler() {
		return new PreciseBeanTypeReconciler(new QuickfixRegistry());
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
	void requireFullAst() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				
				class A {
				
					@Bean
					Number bean() {
						return Integer.valueOf(5);
					};
					
				}
				""";
		try {
			reconcile("A.java", source, false);
			fail("Should require full AST with method bodies");
		} catch (RequiredCompleteAstException e) {
			// pass
		}
		
	}

	@Test
	void singleReturnStatement() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				
				class A {
				
					@Bean
					Number bean() {
						return Integer.valueOf(5);
					};
					
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);
		
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		
		assertEquals(SpringAotJavaProblemType.JAVA_CONCRETE_BEAN_TYPE, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("Number", markedStr);

		assertEquals(1, problem.getQuickfixes().size());
		
	}

	@Test
	void singleReturnStatementWithPrimitiveType() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				
				class A {
				
					@Bean
					Number bean() {
						return 5;
					};
					
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);
		
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		
		assertEquals(SpringAotJavaProblemType.JAVA_CONCRETE_BEAN_TYPE, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("Number", markedStr);

		assertEquals(1, problem.getQuickfixes().size());
		
	}
	
	@Test
	void multipleReturnStatementSameType() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				
				class A {
				
					boolean b
				
					@Bean
					Number bean() {
						if (b) {
							return 3;
						} else {
							return 5;
						}
					};
					
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);
		
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		
		assertEquals(SpringAotJavaProblemType.JAVA_CONCRETE_BEAN_TYPE, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("Number", markedStr);

		assertEquals(1, problem.getQuickfixes().size());
		
	}
	
	@Test
	void multipleReturnStatementDifferentType() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				
				class A {
				
					boolean b
				
					@Bean
					Number bean() {
						if (b) {
							return 3.45;
						} else {
							return 5;
						}
					};
					
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);
		
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		
		assertEquals(SpringAotJavaProblemType.JAVA_CONCRETE_BEAN_TYPE, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("Number", markedStr);

		assertEquals(0, problem.getQuickfixes().size());
		
	}
	
	@Test
	void multipleReturnStatementDifferentType2() throws Exception {
		String source = """
				package example.demo;
				
				import java.util.*;
				import org.springframework.context.annotation.Bean;
				
				class A {
				
					boolean b
				
					@Bean
					Collection<Integer> bean() {
						if (b) {
							LinkedList<Integer> l = new LinkedList<>(); 
							return l;
						} else {
							return List.of(5);
						}
					};
					
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);
		
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		
		assertEquals(SpringAotJavaProblemType.JAVA_CONCRETE_BEAN_TYPE, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("Collection<Integer>", markedStr);

		assertEquals(1, problem.getQuickfixes().size());
		
		JdtFixDescriptor d = (JdtFixDescriptor) problem.getQuickfixes().get(0).params;

		assertEquals("Replace return type with 'List<Integer>'", d.label());
		
	}
	
	@Test
	void noProblemSingleReturnStatement() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				
				class A {
				
					@Bean
					Integer bean() {
						return 5;
					};
					
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);
		
		assertEquals(0, problems.size());
		
	}

	@Test
	void multipleReturnStatement() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				
				class A {
				
					boolean b
				
					@Bean
					Double bean() {
						if (b) {
							return 3.45;
						} else {
							return 5;
						}
					};
					
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);
		
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		
		assertEquals(SpringAotJavaProblemType.JAVA_CONCRETE_BEAN_TYPE, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("Double", markedStr);

		assertEquals(0, problem.getQuickfixes().size());

	}

	@Test
	void twoMismatchedBeanMethodsOfferFileWideFixOnBoth() throws Exception {
		String source = """
				package example.demo;

				import org.springframework.context.annotation.Bean;

				class A {

					@Bean
					Number bean1() {
						return Integer.valueOf(5);
					};

					@Bean
					Number bean2() {
						return Integer.valueOf(6);
					};

				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);

		assertEquals(2, problems.size());

		for (ReconcileProblem problem : problems) {
			assertEquals(2, problem.getQuickfixes().size());

			JdtFixDescriptor nodeFix = (JdtFixDescriptor) problem.getQuickfixes().get(0).params;
			assertEquals("Replace return type with 'Integer'", nodeFix.label());

			JdtFixDescriptor fileFix = (JdtFixDescriptor) problem.getQuickfixes().get(1).params;
			assertEquals("Ensure concrete bean type in file", fileFix.label());
		}
	}

	@Test
	void noProblemMultipleReturnStatement() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.context.annotation.Bean;
				
				class A {
				
					boolean b
				
					@Bean
					Double bean() {
						if (b) {
							return 3.45;
						} else {
							return 5.4;
						}
					};
					
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);
		
		assertEquals(0, problems.size());

	}

}
