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
import static org.springframework.ide.vscode.boot.java.SpringAiProblemType.DEFAULT_TOOL_DESCRIPTION_MIN_LENGTH;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.app.ProblemParameterProvider;
import org.springframework.ide.vscode.boot.java.SpringAiProblemType;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.SpringAiDescriptionTooShortReconciler;
import org.springframework.ide.vscode.commons.languageserver.config.LanguageServerProperties;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.languageserver.util.Settings;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;

import com.google.gson.JsonObject;

public class SpringAiDescriptionTooShortReconcilerTest extends BaseReconcilerTest {

	private SimpleLanguageServer languageServer;

	@Override
	protected String getFolder() {
		return "springai/shortdesc";
	}

	@Override
	protected String getProjectName() {
		return "test-spring-ai-indexing";
	}

	@Override
	protected JdtAstReconciler getReconciler() {
		return new SpringAiDescriptionTooShortReconciler(
				new ProblemParameterProvider(new BootJavaConfig(languageServer)));
	}

	@BeforeEach
	void setup() throws Exception {
		super.setup();
		languageServer = new SimpleLanguageServer("test", null, new LanguageServerProperties());
	}

	@AfterEach
	void tearDown() throws Exception {
		super.tearDown();
	}

	@Test
	void toolWithTooShortDescription_shouldWarn() throws Exception {
		String shortDesc = "X".repeat(DEFAULT_TOOL_DESCRIPTION_MIN_LENGTH - 1);
		String source = """
				package example.springai;

				import org.springframework.ai.tool.annotation.Tool;

				class MyTools {

					@Tool(description = "%s")
					public String compute(String input) {
						return input;
					}

				}
				""".formatted(shortDesc);
		List<ReconcileProblem> problems = reconcile("MyTools.java", source, true);

		assertEquals(1, problems.size());

		ReconcileProblem problem = problems.get(0);
		assertEquals(SpringAiProblemType.SPRING_AI_TOOL_DESCRIPTION_TOO_SHORT, problem.getType());
	}

	@Test
	void toolWithDescriptionAtMinimumLength_shouldNotWarn() throws Exception {
		String okDesc = "X".repeat(DEFAULT_TOOL_DESCRIPTION_MIN_LENGTH);
		String source = """
				package example.springai;

				import org.springframework.ai.tool.annotation.Tool;

				class MyTools {

					@Tool(description = "%s")
					public String compute(String input) {
						return input;
					}

				}
				""".formatted(okDesc);
		List<ReconcileProblem> problems = reconcile("MyTools.java", source, true);

		assertEquals(0, problems.size());
	}

	@Test
	void toolWithClearlyTooShortDescription_shouldWarn() throws Exception {
		String source = """
				package example.springai;

				import org.springframework.ai.tool.annotation.Tool;

				class MyTools {

					@Tool(description = "Add numbers")
					public int add(int a, int b) {
						return a + b;
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("MyTools.java", source, true);

		assertEquals(1, problems.size());

		ReconcileProblem problem = problems.get(0);
		assertEquals(SpringAiProblemType.SPRING_AI_TOOL_DESCRIPTION_TOO_SHORT, problem.getType());

		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("@Tool(description = \"Add numbers\")", markedStr);
	}

	@Test
	void toolWithNoDescription_shouldNotWarn() throws Exception {
		// Missing description is the concern of SpringAiDescriptionExistsReconciler, not this one
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

		assertEquals(0, problems.size());
	}

	@Test
	void toolWithEmptyDescription_shouldNotWarn() throws Exception {
		// Empty description is the concern of SpringAiDescriptionExistsReconciler, not this one
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

		assertEquals(0, problems.size());
	}

	@Test
	void mcpToolWithTooShortDescription_shouldWarn() throws Exception {
		String source = """
				package example.springai;

				import org.springframework.ai.mcp.annotation.McpTool;

				class MyMcpTools {

					@McpTool(description = "Calc")
					public int calculate(int x, int y) {
						return x + y;
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("MyMcpTools.java", source, true);

		assertEquals(1, problems.size());
		assertEquals(SpringAiProblemType.SPRING_AI_TOOL_DESCRIPTION_TOO_SHORT, problems.get(0).getType());
	}

	@Test
	void mcpToolWithAdequateDescription_shouldNotWarn() throws Exception {
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
	void customMinLength_shorterThanDefault_descriptionBetweenCustomAndDefault_shouldNotWarn() throws Exception {
		// This description is longer than the custom minimum (10) but shorter than the standard default (30).
		// With the custom minimum in effect, it should not warn.
		String desc = "X".repeat(20);
		String source = """
				package example.springai;

				import org.springframework.ai.tool.annotation.Tool;

				class MyTools {

					@Tool(description = "%s")
					public String compute(String input) {
						return input;
					}

				}
				""".formatted(desc);

		List<ReconcileProblem> problems = reconcile(
				() -> new SpringAiDescriptionTooShortReconciler(new ProblemParameterProvider(configWithMinLength(10))),
				"MyTools.java", source, true);

		assertEquals(0, problems.size());
	}

	@Test
	void customMinLength_longerThanDefault_descriptionBetweenDefaultAndCustom_shouldWarn() throws Exception {
		// This description is longer than the standard default (30) but shorter than the custom minimum (50).
		// With the custom minimum in effect, it should still warn.
		String desc = "X".repeat(35);
		String source = """
				package example.springai;

				import org.springframework.ai.tool.annotation.Tool;

				class MyTools {

					@Tool(description = "%s")
					public String compute(String input) {
						return input;
					}

				}
				""".formatted(desc);

		List<ReconcileProblem> problems = reconcile(
				() -> new SpringAiDescriptionTooShortReconciler(new ProblemParameterProvider(configWithMinLength(50))),
				"MyTools.java", source, true);

		assertEquals(1, problems.size());
		assertEquals(SpringAiProblemType.SPRING_AI_TOOL_DESCRIPTION_TOO_SHORT, problems.get(0).getType());
	}

	private BootJavaConfig configWithMinLength(int minLength) {
		JsonObject tooShort = new JsonObject();
		tooShort.addProperty("minimum-length", minLength);
		JsonObject springAi = new JsonObject();
		springAi.add("SPRING_AI_TOOL_DESCRIPTION_TOO_SHORT", tooShort);
		JsonObject problemParameters = new JsonObject();
		problemParameters.add("spring-ai", springAi);
		JsonObject ls = new JsonObject();
		ls.add("problem-parameters", problemParameters);
		JsonObject springBoot = new JsonObject();
		springBoot.add("ls", ls);
		JsonObject root = new JsonObject();
		root.add("spring-boot", springBoot);

		BootJavaConfig config = new BootJavaConfig(languageServer);
		config.handleConfigurationChange(new Settings(root));
		return config;
	}

	@Test
	void multipleToolsWithMixedDescriptionLengths_shouldWarnForShortOnly() throws Exception {
		String source = """
				package example.springai;

				import org.springframework.ai.tool.annotation.Tool;

				class MyTools {

					@Tool(description = "Adds two integers and returns their sum as a result")
					public int add(int a, int b) {
						return a + b;
					}

					@Tool(description = "Multiply")
					public int multiply(int a, int b) {
						return a * b;
					}

					@Tool(description = "Subtracts the second integer from the first and returns the difference")
					public int subtract(int a, int b) {
						return a - b;
					}

				}
				""";
		List<ReconcileProblem> problems = reconcile("MyTools.java", source, true);

		assertEquals(1, problems.size());
		assertEquals(SpringAiProblemType.SPRING_AI_TOOL_DESCRIPTION_TOO_SHORT, problems.get(0).getType());
	}

}
