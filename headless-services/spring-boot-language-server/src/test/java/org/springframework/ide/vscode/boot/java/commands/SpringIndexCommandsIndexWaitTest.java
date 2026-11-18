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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.ExecuteCommandHandler;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Tests that a {@code sts/spring-boot/structure} request only waits for the index to settle when a
 * tree of it is compared against a baseline - otherwise a workspace that is still being indexed
 * would show nothing until all of it is indexed.
 *
 * @author Martin Lippert
 */
public class SpringIndexCommandsIndexWaitTest {

	private SpringSymbolIndex symbolIndex;
	private StructureSnapshotStore snapshotStore;
	private IJavaProject project;
	private ExecuteCommandHandler structureCommand;

	@BeforeEach
	void setup() {
		project = mock(IJavaProject.class);
		when(project.getElementName()).thenReturn("demo");
		JavaProjectFinder projectFinder = mock(JavaProjectFinder.class);
		when(projectFinder.all()).thenAnswer(invocation -> List.of(project));

		// an index that never settles: a request waiting for it would only answer after the timeout
		symbolIndex = mock(SpringSymbolIndex.class);
		when(symbolIndex.waitOperation()).thenReturn(new CompletableFuture<>());

		StructureViewProvider structureViewProvider = mock(StructureViewProvider.class);
		when(structureViewProvider.createTree(eq(project), any(), anyBoolean(), any(), any(), anyBoolean())).thenAnswer(invocation -> new Node(null));
		snapshotStore = mock(StructureSnapshotStore.class);

		SimpleLanguageServer server = mock(SimpleLanguageServer.class);
		new SpringIndexCommands(server, mock(SpringMetamodelIndex.class), symbolIndex, projectFinder,
				structureViewProvider, snapshotStore, mock(StructureDependencySources.class));

		ArgumentCaptor<ExecuteCommandHandler> handler = ArgumentCaptor.forClass(ExecuteCommandHandler.class);
		verify(server).onCommand(eq("sts/spring-boot/structure"), handler.capture());
		structureCommand = handler.getValue();
	}

	private List<?> structure(boolean changes, boolean dependencyMode) throws Exception {
		JsonObject args = new JsonObject();
		args.addProperty("changes", changes);
		if (dependencyMode) {
			JsonObject dependencies = new JsonObject();
			JsonArray ids = new JsonArray();
			ids.add("org.example:lib");
			dependencies.add("demo", ids);
			args.add("dependencies", dependencies);
		}
		return (List<?>) structureCommand.handle(new ExecuteCommandParams("sts/spring-boot/structure", List.of(args)))
				.get(5, TimeUnit.SECONDS);
	}

	@Test
	void doesNotWaitForTheIndexWithoutABaseline() throws Exception {
		when(snapshotStore.hasBaseline(project)).thenReturn(false);

		assertEquals(1, structure(true, false).size());
		verify(symbolIndex, never()).waitOperation();
	}

	@Test
	void doesNotWaitForTheIndexWhenTheClientShowsNoChanges() throws Exception {
		when(snapshotStore.hasBaseline(project)).thenReturn(true);

		List<?> trees = structure(false, false);

		verify(symbolIndex, never()).waitOperation();
		// what the project node tells about its baseline does not depend on the index, so it is still there
		assertEquals(Boolean.TRUE, ((Node) trees.get(0)).attributes.get(JsonNodeHandler.HAS_BASELINE));
	}

	@Test
	void doesNotWaitForTheIndexInDependencyMode() throws Exception {
		when(snapshotStore.hasBaseline(project)).thenReturn(true);

		assertEquals(1, structure(true, true).size());
		verify(symbolIndex, never()).waitOperation();
	}

	@Test
	void waitsForTheIndexBeforeComparingAgainstABaseline() throws Exception {
		when(snapshotStore.hasBaseline(project)).thenReturn(true);
		when(symbolIndex.waitOperation()).thenReturn(CompletableFuture.completedFuture(null));

		structure(true, false);

		verify(symbolIndex).waitOperation();
		verify(snapshotStore).annotateWithChangesSinceBaseline(eq(project), any(), any(), any());
	}

}
