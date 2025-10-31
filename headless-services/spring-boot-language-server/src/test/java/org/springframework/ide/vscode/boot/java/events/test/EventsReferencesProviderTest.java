/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.events.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class EventsReferencesProviderTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-events-indexing/").toURI());

		String projectDir = directory.toURI().toString();
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}
	
	@Test
	public void testEventListenerFindsAllPublishers() throws Exception {
        String tempJavaDocUri = directory.toPath().resolve("src/main/java/com/example/events/demo/CustomEventListener.java").toUri().toString();

        Editor editor = harness.newEditor(LanguageId.JAVA, """
        		package com.example.events.demo;
        		
        		import org.springframework.context.event.EventListener;
        		import org.springframework.stereotype.Component;

        		@Component
        		public class CustomEventListener {
	
        			@EventListener
        			public void handle<*>Event(CustomEvent event) {
        				System.out.println(event);
        			}
        		}""", tempJavaDocUri);
		
		List<? extends Location> references = editor.getReferences();
		assertEquals(3, references.size());
		
		String expectedDefinitionUri1 = directory.toPath().resolve("src/main/java/com/example/events/demo/CustomEventPublisher.java").toUri().toString();
		Location expectedLocation1 = new Location(expectedDefinitionUri1, new Range(new Position(15, 2), new Position(15, 48)));
		assertTrue(references.contains(expectedLocation1));

		// from type hierarchy of specialzed custom event
		String expectedDefinitionUri2 = directory.toPath().resolve("src/main/java/com/example/events/demo/SpecializedCustomEventPublisher.java").toUri().toString();
		Location expectedLocation2 = new Location(expectedDefinitionUri2, new Range(new Position(15, 2), new Position(15, 59)));
		assertTrue(references.contains(expectedLocation2));

		String expectedDefinitionUri3 = directory.toPath().resolve("src/main/java/com/example/events/demo/CustomEventPublisherWithAdditionalElements.java").toUri().toString();
		Location expectedLocation3 = new Location(expectedDefinitionUri3, new Range(new Position(17, 2), new Position(17, 48)));
		assertTrue(references.contains(expectedLocation3));
	}

	@Test
	public void testEventPublisherFindsAllListeners() throws Exception {
        String tempJavaDocUri = directory.toPath().resolve("src/main/java/com/example/events/demo/CustomEventPublisher.java").toUri().toString();

        Editor editor = harness.newEditor(LanguageId.JAVA, """
        		package com.example.events.demo;

        		import org.springframework.context.ApplicationEventPublisher;
        		import org.springframework.stereotype.Component;

        		@Component
        		public class CustomEventPublisher {
	
        			private ApplicationEventPublisher publisher;

        			public CustomEventPublisher(ApplicationEventPublisher publisher) {
        				this.publisher = publisher;
        			}
	
        			public void foo() {
        				this.publisher.pu<*>blishEvent(new CustomEvent());
        			}
        		}""", tempJavaDocUri);
		
		List<? extends Location> references = editor.getReferences();
		assertEquals(1, references.size());
		
		String expectedDefinitionUri1 = directory.toPath().resolve("src/main/java/com/example/events/demo/CustomEventListener.java").toUri().toString();
		Location expectedLocation1 = new Location(expectedDefinitionUri1, new Range(new Position(9, 13), new Position(9, 24)));
		
		assertTrue(references.contains(expectedLocation1));
	}

	@Test
	public void testEventPublisherFindsAllListenersIncludingThoseFromListenersWithoutAnnotation() throws Exception {
        String tempJavaDocUri = directory.toPath().resolve("src/main/java/com/example/events/demo/CustomApplicationEventPublisher.java").toUri().toString();

        Editor editor = harness.newEditor(LanguageId.JAVA, """
        		package com.example.events.demo;

        		import org.springframework.context.ApplicationEventPublisher;
        		import org.springframework.stereotype.Component;

        		@Component
        		public class CustomApplcationEventPublisher {
	
        			private ApplicationEventPublisher publisher;

        			public CustomApplcationEventPublisher(ApplicationEventPublisher publisher) {
						this.publisher = publisher;
					}
	
					public void foo() {
						this.publisher.pub<*>lishEvent(new CustomApplicationEvent(null));
					}
        		}""", tempJavaDocUri);
		
		List<? extends Location> references = editor.getReferences();
		assertEquals(3, references.size());
		
		String expectedDefinitionUri1 = directory.toPath().resolve("src/main/java/com/example/events/demo/EventListenerPerInterface.java").toUri().toString();
		Location expectedLocation1 = new Location(expectedDefinitionUri1, new Range(new Position(10, 13), new Position(10, 31)));
		
		assertTrue(references.contains(expectedLocation1));

		String expectedDefinitionUri2 = directory.toPath().resolve("src/main/java/com/example/events/demo/EventListenerPerAnnotation.java").toUri().toString();
		Location expectedLocation2 = new Location(expectedDefinitionUri2, new Range(new Position(10, 13), new Position(10, 24)));
		
		assertTrue(references.contains(expectedLocation2));

		String expectedDefinitionUri3 = directory.toPath().resolve("src/main/java/com/example/events/demo/EventListenerPerInterfaceAndBeanMethod.java").toUri().toString();
		Location expectedLocation3 = new Location(expectedDefinitionUri3, new Range(new Position(7, 13), new Position(7, 31)));
		
		assertTrue(references.contains(expectedLocation3));
	}

}
