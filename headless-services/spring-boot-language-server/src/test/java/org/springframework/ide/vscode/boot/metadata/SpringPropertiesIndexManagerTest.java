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
package org.springframework.ide.vscode.boot.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.ProgressService;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.util.BasicFileObserver;

/**
 * Tests which changes of metadata json files discard the properties indexes.
 *
 * @author Martin Lippert
 */
public class SpringPropertiesIndexManagerTest {

	private static final String METADATA = """
			{"properties": [{"name": "demo.mode", "type": "java.lang.String"}]}
			""";

	private static final String CHANGED_METADATA = """
			{"properties": [{"name": "demo.mode", "type": "java.lang.String"}, {"name": "demo.size", "type": "java.lang.Integer"}]}
			""";

	@TempDir
	Path tempDir;

	private Path metadataFile;
	private IJavaProject project;
	private BasicFileObserver fileObserver;
	private SpringPropertiesIndexManager indexManager;
	private List<IJavaProject> notified;

	@BeforeEach
	void setup() throws Exception {
		Path outputFolder = tempDir.resolve("project/target/classes");
		metadataFile = outputFolder.resolve(PropertiesLoader.MAIN_SPRING_CONFIGURATION_METADATA_JSON);
		Files.createDirectories(metadataFile.getParent());
		Files.writeString(metadataFile, METADATA);

		IClasspath classpath = mock(IClasspath.class);
		when(classpath.getClasspathEntries()).thenReturn(List.of(CPE.source(tempDir.resolve("project/src/main/java").toFile(), outputFolder.toFile())));
		project = mock(IJavaProject.class);
		when(project.getClasspath()).thenReturn(classpath);

		fileObserver = new BasicFileObserver();
		indexManager = new SpringPropertiesIndexManager(new ValueProviderRegistry(), null, fileObserver);
		notified = new ArrayList<>();
		indexManager.addListener(notified::addAll);
	}

	private SpringPropertyIndex index() {
		return indexManager.get(project, ProgressService.NO_PROGRESS);
	}

	@Test
	void rewriteWithSameContentKeepsIndexes() throws Exception {
		SpringPropertyIndex index = index();

		Files.writeString(metadataFile, METADATA);
		fileObserver.notifyFileChanged(metadataFile.toUri().toString());

		assertEquals(List.of(), notified);
		assertSame(index, index());
	}

	@Test
	void changedContentDiscardsIndexes() throws Exception {
		SpringPropertyIndex index = index();

		Files.writeString(metadataFile, CHANGED_METADATA);
		fileObserver.notifyFileChanged(metadataFile.toUri().toString());

		assertEquals(List.of(project), notified);
		assertNotSame(index, index());
		assertNotNull(index().getProperties().get("demo.size"));
	}

	@Test
	void deletedFileDiscardsIndexes() throws Exception {
		index();

		Files.delete(metadataFile);
		fileObserver.notifyFileDeleted(metadataFile.toUri().toString());

		assertEquals(List.of(project), notified);
	}

	@Test
	void fileNoIndexWasBuiltFromDiscardsIndexes() throws Exception {
		index();

		// e.g. created by the first compile with the annotation processor of a project on the classpath
		Path otherFile = tempDir.resolve("other/target/classes").resolve(PropertiesLoader.MAIN_SPRING_CONFIGURATION_METADATA_JSON);
		Files.createDirectories(otherFile.getParent());
		Files.writeString(otherFile, METADATA);
		fileObserver.notifyFileCreated(otherFile.toUri().toString());

		assertEquals(List.of(project), notified);
	}

	@Test
	void unchangedAndChangedFileTogetherDiscardIndexes() throws Exception {
		Path otherFile = tempDir.resolve("other/target/classes").resolve(PropertiesLoader.MAIN_SPRING_CONFIGURATION_METADATA_JSON);
		Files.createDirectories(otherFile.getParent());
		Files.writeString(otherFile, METADATA);
		index();

		Files.writeString(metadataFile, METADATA);
		fileObserver.notifyFilesChanged(new String[] { metadataFile.toUri().toString(), otherFile.toUri().toString() });

		assertEquals(List.of(project), notified);
	}

}
