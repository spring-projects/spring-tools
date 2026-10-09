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

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.UnnecessarySpringExtensionReconciler;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;

public class UnnecessarySpringExtensionReconcilerTest extends BaseReconcilerTest {

	@Override
	protected String getFolder() {
		// TODO Auto-generated method stub
		return "unnecassaryextendwith";
	}

	@Override
	protected String getProjectName() {
		return "test-spring-validations";
	}

	@Override
	protected JdtAstReconciler getReconciler() {
		return new UnnecessarySpringExtensionReconciler(new QuickfixRegistry());
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
	void sanity() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.boot.test.context.SpringBootTest;
				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.test.context.junit.jupiter.SpringExtension;
				
				@ExtendWith(SpringExtension.class)
				@SpringBootTest
				class A {
				
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);
		
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		
		assertEquals(Boot2JavaProblemType.JAVA_TEST_SPRING_EXTENSION, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("@ExtendWith(SpringExtension.class)", markedStr);

		assertEquals(1, problem.getQuickfixes().size());
		
	}

	@Test
	void noTestAnnotation() throws Exception {
		String source = """
				package example.demo;
				
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
	void noExtendWithAnnotation() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.boot.test.context.SpringBootTest;
				
				@SpringBootTest
				class A {
				
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);
		
		assertEquals(0, problems.size());
		
	}

	@Test
	void graphQlTestSlice() throws Exception {
		String source = """
				package example.demo;
				
				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
				import org.springframework.test.context.junit.jupiter.SpringExtension;
				
				@ExtendWith(SpringExtension.class)
				@GraphQlTest
				class A {
				
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);
		
		assertEquals(1, problems.size());
		assertEquals(Boot2JavaProblemType.JAVA_TEST_SPRING_EXTENSION, problems.get(0).getType());
	}

	@Test
	void webServiceServerTestSlice() throws Exception {
		String source = """
				package example.demo;
				
				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.boot.test.autoconfigure.webservices.server.WebServiceServerTest;
				import org.springframework.test.context.junit.jupiter.SpringExtension;
				
				@ExtendWith(SpringExtension.class)
				@WebServiceServerTest
				class A {
				
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, true);
		
		assertEquals(1, problems.size());
		assertEquals(Boot2JavaProblemType.JAVA_TEST_SPRING_EXTENSION, problems.get(0).getType());
	}
}
