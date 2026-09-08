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
import static org.springframework.ide.vscode.boot.java.commands.StructureTreeTestFixture.findNode;

import java.io.File;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.gson.JsonObject;

/**
 * A change is highlighted on the member it happened in, never on the type around it - for every
 * kind of member that gets a node, not just the ones whose method happens to be annotated.
 *
 * <p>Which members those are is only known once every indexer has run for a file, so the type's
 * content hash is computed in a pass afterwards (see {@code SpringIndexerJavaAstScanner}). These
 * tests use members whose methods carry no Spring annotation at all - an {@code ApplicationListener}
 * implementation and a method publishing an event - because those are the ones an annotation-based
 * rule gets wrong.
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class StructureViewMemberHighlightingTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;

	private IJavaProject project;
	private StructureTreeTestFixture tree;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		File directory = new File(ProjectsHarness.class.getResource("/test-projects/test-events-indexing/").toURI());
		project = projectFinder.find(new TextDocumentIdentifier(directory.toURI().toString())).get();
		tree = new StructureTreeTestFixture(harness, indexer, directory);

		indexer.waitOperation().get(15, TimeUnit.SECONDS);
	}

	@Test
	void changingAnApplicationListenerImplementationMarksTheListenerNotItsClass() throws Exception {
		captureBaseline();

		// onApplicationEvent carries nothing but @Override, so nothing about it says "annotated"
		tree.edit("src/main/java/com/example/events/demo/EventListenerPerInterface.java",
				"System.out.println(\"Event received via listener implementation: \" + event);",
				"System.out.println(\"changed: \" + event);");

		// scoped to the edited class: the project holds several listeners and publishers
		Node listenerClass = findNode(tree.structureTrees(), JsonNodeHandler.KIND_TYPE, "EventListenerPerInterface");

		assertEquals("modified", findNode(listenerClass, JsonNodeHandler.KIND_MEMBER, "listens on").getAttribute(JsonNodeHandler.CHANGE));
		assertEquals("containsChanges", listenerClass.getAttribute(JsonNodeHandler.CHANGE),
				"the listener's class must not be highlighted for a change inside the listener method");
	}

	@Test
	void changingAnEventPublishingMethodMarksThePublisherNotItsClass() throws Exception {
		captureBaseline();

		// the publishing method is a plain, unannotated method
		tree.edit("src/main/java/com/example/events/demo/CustomEventPublisher.java",
				"\t\tthis.publisher.publishEvent(new CustomEvent());",
				"\t\tSystem.out.println(\"about to publish\");\n\t\tthis.publisher.publishEvent(new CustomEvent());");

		Node publisherClass = findNode(tree.structureTrees(), JsonNodeHandler.KIND_TYPE, ".CustomEventPublisher");

		assertEquals("modified", findNode(publisherClass, JsonNodeHandler.KIND_MEMBER, "publishes").getAttribute(JsonNodeHandler.CHANGE));
		assertEquals("containsChanges", publisherClass.getAttribute(JsonNodeHandler.CHANGE),
				"the publisher's class must not be highlighted for a change inside the publishing method");
	}

	@Test
	void changingSomethingWithoutANodeOfItsOwnStillMarksTheClass() throws Exception {
		captureBaseline();

		// a plain field has no node anywhere, so the class is the only place it can show up
		tree.edit("src/main/java/com/example/events/demo/CustomEventPublisher.java",
				"public class CustomEventPublisher {",
				"public class CustomEventPublisher {\n\n\tprivate int counter;\n");

		assertEquals("modified", findNode(tree.structureTrees(), JsonNodeHandler.KIND_TYPE, ".CustomEventPublisher").getAttribute(JsonNodeHandler.CHANGE));
	}

	private void captureBaseline() throws Exception {
		harness.getServer().getWorkspaceService().executeCommand(
				new ExecuteCommandParams("sts/spring-boot/structure/captureBaseline", List.of(project.getElementName()))).get();
	}




}
