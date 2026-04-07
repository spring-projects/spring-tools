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
import org.springframework.ide.vscode.boot.java.SpringAiProblemType;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.SpringAiDescriptionExistsReconciler;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;

public class SpringAiDescriptionExistsReconcilerTest extends BaseReconcilerTest {

	@Override
	protected String getFolder() {
		return "springai/reconciler";
	}

	@Override
	protected String getProjectName() {
		return "test-spring-ai-indexing";
	}

	@Override
	protected JdtAstReconciler getReconciler() {
		return new SpringAiDescriptionExistsReconciler();
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
	void toolWithNoDescription_shouldWarn() throws Exception {
		String source = """
				package example.springai;

				import org.springframework.ai.tool.annotation.Tool;

				class MyTools {

					@Tool
					public String compute(String input) {
						return input;
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("MyTools.java", source, true);

		assertEquals(1, problems.size());

		ReconcileProblem problem = problems.get(0);
		assertEquals(SpringAiProblemType.SPRING_AI_TOOL_MISSING_DESCRIPTION, problem.getType());

		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("@Tool", markedStr);
	}

	@Test
	void toolWithEmptyDescription_shouldWarn() throws Exception {
		String source = """
				package example.springai;

				import org.springframework.ai.tool.annotation.Tool;

				class MyTools {

					@Tool(description = "")
					public String compute(String input) {
						return input;
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("MyTools.java", source, true);

		assertEquals(1, problems.size());

		ReconcileProblem problem = problems.get(0);
		assertEquals(SpringAiProblemType.SPRING_AI_TOOL_MISSING_DESCRIPTION, problem.getType());

		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("@Tool(description = \"\")", markedStr);
	}

	@Test
	void toolWithBlankDescription_shouldWarn() throws Exception {
		String source = """
				package example.springai;

				import org.springframework.ai.tool.annotation.Tool;

				class MyTools {

					@Tool(description = "   ")
					public String compute(String input) {
						return input;
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("MyTools.java", source, true);

		assertEquals(1, problems.size());
		assertEquals(SpringAiProblemType.SPRING_AI_TOOL_MISSING_DESCRIPTION, problems.get(0).getType());
	}

	@Test
	void toolWithDescription_shouldNotWarn() throws Exception {
		String source = """
				package example.springai;

				import org.springframework.ai.tool.annotation.Tool;

				class MyTools {

					@Tool(description = "Computes the result for the given input")
					public String compute(String input) {
						return input;
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("MyTools.java", source, true);

		assertEquals(0, problems.size());
	}

	@Test
	void mcpToolWithNoDescription_shouldWarn() throws Exception {
		String source = """
				package example.springai;

				import org.springframework.ai.mcp.annotation.McpTool;

				class MyMcpTools {

					@McpTool
					public int add(int a, int b) {
						return a + b;
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("MyMcpTools.java", source, true);

		assertEquals(1, problems.size());
		assertEquals(SpringAiProblemType.SPRING_AI_TOOL_MISSING_DESCRIPTION, problems.get(0).getType());
	}

	@Test
	void mcpToolWithDescription_shouldNotWarn() throws Exception {
		String source = """
				package example.springai;

				import org.springframework.ai.mcp.annotation.McpTool;

				class MyMcpTools {

					@McpTool(description = "Adds two integer numbers and returns their sum")
					public int add(int a, int b) {
						return a + b;
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("MyMcpTools.java", source, true);

		assertEquals(0, problems.size());
	}

	@Test
	void mcpPromptWithNoDescription_shouldWarn() throws Exception {
		String source = """
				package example.springai;

				import org.springframework.ai.mcp.annotation.McpPrompt;

				class MyMcpPrompts {

					@McpPrompt
					public String greet(String name) {
						return "Hello " + name;
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("MyMcpPrompts.java", source, true);

		assertEquals(1, problems.size());
		assertEquals(SpringAiProblemType.SPRING_AI_TOOL_MISSING_DESCRIPTION, problems.get(0).getType());
	}

	@Test
	void multipleToolsWithMixedDescriptions_shouldWarnOnlyForMissing() throws Exception {
		String source = """
				package example.springai;

				import org.springframework.ai.tool.annotation.Tool;

				class MyTools {

					@Tool(description = "Adds two numbers together and returns the sum")
					public int add(int a, int b) {
						return a + b;
					}

					@Tool
					public int subtract(int a, int b) {
						return a - b;
					}

					@Tool(description = "")
					public int multiply(int a, int b) {
						return a * b;
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("MyTools.java", source, true);

		assertEquals(2, problems.size());
		problems.forEach(p -> assertEquals(SpringAiProblemType.SPRING_AI_TOOL_MISSING_DESCRIPTION, p.getType()));
	}

}
