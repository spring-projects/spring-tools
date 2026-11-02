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
package org.springframework.ide.vscode.boot.java.jmolecules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.events.EventListenerIndexElement;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * jMolecules annotations treated as the Spring annotations they stand for.
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class JMoleculesAnnotationsTest {

	private static final String PROJECT_NAME = "test-jmolecules-annotations";

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private SpringSymbolIndex indexer;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		File directory = new File(ProjectsHarness.class.getResource("/test-projects/" + PROJECT_NAME + "/").toURI());
		projectFinder.find(new TextDocumentIdentifier(directory.toURI().toString())).get();
		indexer.waitOperation().get(30, TimeUnit.SECONDS);
	}

	@Test
	void jmoleculesStereotypesAreSpringBeans() throws Exception {
		bean("orderService");
		bean("orders");
		bean("orderFactory");
		bean("metaAnnotatedService");
	}

	@Test
	void domainEventHandlersAreEventListeners() throws Exception {
		List<EventListenerIndexElement> listeners = eventListenersOf(bean("orderService"));
		assertEquals(1, listeners.size());
		assertEquals("com.example.jmolecules.OrderPlaced", listeners.get(0).getEventType());

		assertEquals(1, eventListenersOf(bean("handlerConfiguration")).size());
	}

	private Bean bean(String name) {
		Bean[] beans = springIndex.getBeansWithName(PROJECT_NAME, name);
		assertEquals(1, beans.length, name);
		return beans[0];
	}

	private static List<EventListenerIndexElement> eventListenersOf(Bean bean) {
		return bean.getChildren().stream()
				.filter(EventListenerIndexElement.class::isInstance)
				.map(EventListenerIndexElement.class::cast)
				.toList();
	}

}
