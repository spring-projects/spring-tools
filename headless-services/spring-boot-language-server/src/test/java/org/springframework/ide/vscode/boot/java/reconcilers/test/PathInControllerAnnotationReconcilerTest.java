/*******************************************************************************
 * Copyright (c) 2023, 2025 VMware, Inc.
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
import org.springframework.ide.vscode.boot.java.reconcilers.PathInControllerAnnotationReconciler;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;

public class PathInControllerAnnotationReconcilerTest extends BaseReconcilerTest {

	@Override
	protected String getFolder() {
		return "pathincontrollerannotation";
	}

	@Override
	protected String getProjectName() {
		return "test-spring-validations";
	}

	@Override
	protected JdtAstReconciler getReconciler() {
		return new PathInControllerAnnotationReconciler();
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
	void restControllerWithPath() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController("/mypath")
				public class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);
		
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		
		assertEquals(Boot2JavaProblemType.PATH_IN_CONTROLLER_ANNOTATION, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("\"/mypath\"", markedStr);

		assertEquals(0, problem.getQuickfixes().size());
	}

	@Test
	void restControllerWithoutPath() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController("mypath")
				public class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);
		
		assertEquals(0, problems.size());
	}

	@Test
	void restControllerWithoutAnything() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.web.bind.annotation.RestController;
				
				@RestController
				public class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);
		
		assertEquals(0, problems.size());
	}

	@Test
	void restSimpleControllerWithoutAnything() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.stereotype.Controller;
				
				@Controller
				public class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile("A.java", source, false);
		
		assertEquals(0, problems.size());
	}

}
