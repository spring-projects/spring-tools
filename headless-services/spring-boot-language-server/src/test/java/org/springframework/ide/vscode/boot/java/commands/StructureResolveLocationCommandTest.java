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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.protocol.STS4LanguageClient;
import org.springframework.ide.vscode.commons.protocol.java.JavaDataParams;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.gson.JsonObject;

/**
 * {@code sts/spring-boot/structure/resolveLocation}: a structure node's
 * {@link JavaElementReference} - what a node for an element read from a JAR carries instead of a
 * location - is resolved by the IDE's Java tooling when the node is opened.
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class StructureResolveLocationCommandTest {

	private static final String RESOLVE_LOCATION_CMD = "sts/spring-boot/structure/resolveLocation";

	private static final Location IN_CLASS_FILE = new Location("jdt://contents/lib.jar/com.example/Lib.class",
			new Range(new Position(3, 0), new Position(3, 10)));

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private SimpleLanguageServer server;

	private STS4LanguageClient client;
	private STS4LanguageClient original;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		// the harness's client answers every javaLocation with null - this one knows one element
		original = server.getClient();
		client = spy(original);
		server.connect(client);
	}

	@AfterEach
	public void restoreClient() {
		server.connect(original);
	}

	@Test
	void theReferenceIsResolvedByTheIdesJavaTooling() throws Exception {
		doReturn(CompletableFuture.completedFuture(IN_CLASS_FILE)).when(client).javaLocation(any());

		Object location = resolve(reference("file:///ws/app/", "Lcom/example/Lib;"));

		assertEquals(IN_CLASS_FILE, location);

		ArgumentCaptor<JavaDataParams> params = ArgumentCaptor.forClass(JavaDataParams.class);
		verify(client).javaLocation(params.capture());
		assertEquals("file:///ws/app/", params.getValue().getProjectUri());
		assertEquals("Lcom/example/Lib;", params.getValue().getBindingKey());
	}

	@Test
	void anElementTheIdeCannotFindResolvesToNothing() throws Exception {
		doReturn(CompletableFuture.completedFuture(null)).when(client).javaLocation(any());

		assertNull(resolve(reference("file:///ws/app/", "Lcom/example/Unknown;")));
	}

	@Test
	void aFailingLookupResolvesToNothingRatherThanFailingTheCommand() throws Exception {
		doReturn(CompletableFuture.failedFuture(new IllegalStateException("no java tooling"))).when(client).javaLocation(any());

		assertNull(resolve(reference("file:///ws/app/", "Lcom/example/Lib;")));
	}

	@Test
	void aMemberTheIdeCannotFindOpensItsClass() throws Exception {
		doReturn(CompletableFuture.completedFuture(null)).when(client).javaLocation(argThat(p -> p != null && p.getBindingKey().contains(".")));
		doReturn(CompletableFuture.completedFuture(IN_CLASS_FILE)).when(client).javaLocation(argThat(p -> p != null && "Lcom/example/Lib;".equals(p.getBindingKey())));

		assertEquals(IN_CLASS_FILE, resolve(reference("file:///ws/app/", "Lcom/example/Lib;.run(Ljava/lang/Object;)V")));
	}

	/**
	 * What a client sends back is the node's attribute as it received it.
	 */
	@Test
	void theReferenceIsAcceptedAsTheNodeAttributeItself() throws Exception {
		doReturn(CompletableFuture.completedFuture(IN_CLASS_FILE)).when(client).javaLocation(any());

		assertEquals(IN_CLASS_FILE, resolve(new JavaElementReference("file:///ws/app/", "Lcom/example/Lib;")));
	}

	private Object resolve(Object reference) throws Exception {
		return harness.getServer().getWorkspaceService().executeCommand(new ExecuteCommandParams(RESOLVE_LOCATION_CMD, List.of(reference))).get();
	}

	private static JsonObject reference(String projectUri, String bindingKey) {
		JsonObject json = new JsonObject();
		json.addProperty("projectUri", projectUri);
		json.addProperty("bindingKey", bindingKey);
		return json;
	}

}
