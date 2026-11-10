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
package org.springframework.ide.vscode.boot.java.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.gson.JsonObject;

/**
 * The structure view of a Kotlin project, through the structure command the client calls.
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class KotlinStructureTreeTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;

	private IJavaProject kotlinProject;

	@BeforeEach
	void setup() throws Exception {
		harness.intialize(null);
		setKotlinSupport(true);

		File directory = new File(ProjectsHarness.class.getResource("/test-projects/test-stereotypes-support-kotlin/").toURI());
		kotlinProject = projectFinder.find(new TextDocumentIdentifier(directory.toURI().toString())).get();
		indexer.waitOperation().get(15, TimeUnit.SECONDS);
	}

	@AfterEach
	void tearDown() throws Exception {
		// the Spring context is shared with other tests, which should not see anything of the Kotlin project
		setKotlinSupport(false);
		springIndex.removeProject(kotlinProject.getElementName());
	}

	private void setKotlinSupport(boolean enabled) throws Exception {
		harness.changeConfiguration(Map.of("boot-java", Map.of("kotlin-support", Map.of("on", enabled))));
		indexer.waitOperation().get(15, TimeUnit.SECONDS);
	}

	@Test
	void typesAreGroupedByTheirStereotypesWithTheirRequestMappings() throws Exception {
		JsonObject params = new JsonObject();
		params.addProperty("updateMetadata", false);
		@SuppressWarnings("unchecked")
		List<Node> trees = (List<Node>) harness.getServer().getWorkspaceService()
				.executeCommand(new ExecuteCommandParams("sts/spring-boot/structure", List.of(params))).get();

		Node tree = trees.stream().filter(node -> "test-stereotypes-support-kotlin".equals(node.attributes.get(JsonNodeHandler.TEXT))).findFirst().orElseThrow();

		// the groups, by the name of the stereotype (the order of the types within a group is not fixed)
		Map<String, String> groups = new java.util.HashMap<>();
		for (Node group : tree.children) {
			groups.put(String.valueOf(group.attributes.get(JsonNodeHandler.TEXT)), render(group, ""));
		}

		assertThat(groups.get("Request Mappings (Spring Web)")).contains("  method: @/greeting -- GET\n");
		assertThat(groups.get("Configuration classes (Spring)")).contains("  type: e.application.MainClass\n");
		assertThat(groups.get("Controllers (Spring Web)")).contains("  type: e.application.SampleController\n    member: @/greeting -- GET\n");
		assertThat(groups.get("Controllers (Spring Web)")).contains("  type: e.MyController\n");
	}

	private static String render(Node node, String indent) {
		StringBuilder result = new StringBuilder();
		result.append(indent).append(node.attributes.get(JsonNodeHandler.KIND)).append(": ").append(node.attributes.get(JsonNodeHandler.TEXT)).append('\n');
		for (Node child : node.children) {
			result.append(render(child, indent + "  "));
		}
		return result.toString();
	}

}
