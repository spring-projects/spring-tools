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
package org.springframework.ide.vscode.boot.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.DiagnosticService;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver.Listener;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleWorkspaceService;
import org.springframework.ide.vscode.commons.util.BasicFileObserver;

/**
 * A pom change has to reach the project's Jandex type index before anyone is told about the change: every project
 * change listener, no matter in which order it is called, must already see the types of the new classpath.
 *
 * @author Martin Lippert
 */
class LegacyJavaProjectsServiceIndexUpdateTest {

	private static final String LANG3 = "<dependency><groupId>org.apache.commons</groupId><artifactId>commons-lang3</artifactId><version>3.9</version></dependency>";
	private static final String GSON = "<dependency><groupId>com.google.code.gson</groupId><artifactId>gson</artifactId><version>2.11.0</version></dependency>";

	private static final String LANG3_TYPE = "org.apache.commons.lang3.StringUtils";
	private static final String GSON_TYPE = "com.google.gson.Gson";

	@TempDir Path workspace;

	private BasicFileObserver fileObserver;
	private LegacyJavaProjectsService service;

	@BeforeEach
	void setup() {
		fileObserver = new BasicFileObserver();
		SimpleLanguageServer server = mock(SimpleLanguageServer.class);
		SimpleWorkspaceService workspaceService = mock(SimpleWorkspaceService.class);
		when(workspaceService.getFileObserver()).thenReturn(fileObserver);
		when(server.getWorkspaceService()).thenReturn(workspaceService);
		when(server.getDiagnosticService()).thenReturn(mock(DiagnosticService.class));
		service = new LegacyJavaProjectsService(server, new StandaloneProperties());
	}

	@Test
	void pomChange_reachesIndexBeforeListenersAreNotified() throws Exception {
		writePom(LANG3);
		service.initializeProject(workspace.toFile());
		assertThat(service.all()).hasSize(1);
		IJavaProject project = service.all().iterator().next();

		assertThat(project.getIndex().findType(LANG3_TYPE)).as("initial classpath resolved").isNotNull();
		assertThat(project.getIndex().findType(GSON_TYPE)).isNull();

		List<String> seenByListeners = new CopyOnWriteArrayList<>();
		for (String name : List.of("first", "second")) {
			service.addListener(new Listener() {
				@Override
				public void created(IJavaProject p) {
				}

				@Override
				public void changed(IJavaProject p, boolean clean) {
					seenByListeners.add(name + ":" + (p.getIndex().findType(GSON_TYPE) != null));
				}

				@Override
				public void deleted(IJavaProject p) {
				}
			});
		}

		// dependency added
		writePom(LANG3 + GSON);
		fileObserver.notifyFileChanged(pom().toUri().toASCIIString());

		assertThat(seenByListeners).containsExactly("first:true", "second:true");
		assertThat(project.getIndex().findType(GSON_TYPE)).isNotNull();
		assertThat(project.getIndex().findType(LANG3_TYPE)).isNotNull();

		// dependency removed
		seenByListeners.clear();
		writePom(LANG3);
		fileObserver.notifyFileChanged(pom().toUri().toASCIIString());

		assertThat(seenByListeners).containsExactly("first:false", "second:false");
		assertThat(project.getIndex().findType(GSON_TYPE)).isNull();
	}

	private Path pom() {
		return workspace.resolve("pom.xml");
	}

	private void writePom(String dependencies) throws Exception {
		Files.writeString(pom(), """
				<?xml version="1.0" encoding="UTF-8"?>
				<project xmlns="http://maven.apache.org/POM/4.0.0">
					<modelVersion>4.0.0</modelVersion>
					<groupId>test</groupId>
					<artifactId>index-update</artifactId>
					<version>1.0.0</version>
					<dependencies>%s</dependencies>
				</project>
				""".formatted(dependencies));
	}

}
