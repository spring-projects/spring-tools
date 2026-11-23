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
package org.springframework.ide.vscode.boot.mcp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.provider.tool.SyncMcpToolProvider;
import org.springframework.ide.vscode.boot.mcp.ComponentAnalysisMcpTools;
import org.springframework.ide.vscode.boot.mcp.DiagnosticsMcpTools;
import org.springframework.ide.vscode.boot.mcp.FileChangesMcpTools;
import org.springframework.ide.vscode.boot.mcp.ProjectInformation;
import org.springframework.ide.vscode.boot.mcp.RequestMappingMcpTools;
import org.springframework.ide.vscode.boot.mcp.SpringIndexAccess;
import org.springframework.ide.vscode.boot.mcp.SpringIoApiMcpTools;
import org.springframework.ide.vscode.boot.mcp.SpringVersionsAndGenerations;
import org.springframework.ide.vscode.boot.mcp.StereotypeInformation;
import org.springframework.web.client.RestClient;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;

/**
 * Verifies that every MCP tool publishes read-only/write hints to clients.
 *
 * @author Patrick Baumgartner
 */
class McpToolAnnotationsTest {

	private static final Set<String> WRITE_TOOLS = Set.of(
			"fileChanged", "fileDeleted", "refreshWorkspace",
			"captureLogicalStructureBaseline", "clearLogicalStructureBaseline");

	private static final Set<String> OPEN_WORLD_TOOLS = Set.of(
			"getReleases", "getGenerations", "getUpcomingReleases", "getLatestBootVersionsFromMavenRepo");

	@Test
	void allToolsPublishTheirHints() {
		List<Object> toolBeans = List.of(
				new SpringVersionsAndGenerations(null, null, null),
				SpringIoApiMcpTools.forTesting(RestClient.builder().build(), RestClient.builder().build(), 180,
						Clock.systemUTC()),
				new SpringIndexAccess(null, null),
				new ProjectInformation(null),
				new StereotypeInformation(null, null, null, null, null, null, null, null),
				new RequestMappingMcpTools(null, null),
				new ComponentAnalysisMcpTools(null, null),
				new DiagnosticsMcpTools(null, null, null),
				new FileChangesMcpTools(null, null, null));

		List<SyncToolSpecification> specifications = new SyncMcpToolProvider(toolBeans).getToolSpecifications();
		Map<String, ToolAnnotations> annotations = specifications.stream()
				.collect(Collectors.toMap(spec -> spec.tool().name(), spec -> spec.tool().annotations()));

		assertEquals(27, annotations.size());
		assertNotNull(annotations.get("getProjectList"));
		assertTrue(annotations.values().stream().allMatch(a -> a != null && a.readOnlyHint() != null));

		annotations.forEach((name, hint) -> {
			assertEquals(!WRITE_TOOLS.contains(name), hint.readOnlyHint(), "readOnlyHint for " + name);
			assertEquals(OPEN_WORLD_TOOLS.contains(name), hint.openWorldHint(), "openWorldHint for " + name);
			assertEquals(name.equals("clearLogicalStructureBaseline"), hint.destructiveHint(),
					"destructiveHint for " + name);
			assertTrue(hint.idempotentHint(), "idempotentHint for " + name);
		});
	}

}
