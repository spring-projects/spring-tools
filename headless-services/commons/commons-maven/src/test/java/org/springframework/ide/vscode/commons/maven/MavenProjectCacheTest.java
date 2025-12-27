/*******************************************************************************
 * Copyright (c) 2017, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.ide.vscode.commons.java.ClasspathFileBasedCache;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.javadoc.JavaDocProviders;
import org.springframework.ide.vscode.commons.languageserver.DiagnosticService;
import org.springframework.ide.vscode.commons.languageserver.PercentageProgressTask;
import org.springframework.ide.vscode.commons.languageserver.ProgressService;
import org.springframework.ide.vscode.commons.languageserver.Sts4LanguageServer;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver.Listener;
import org.springframework.ide.vscode.commons.languageserver.util.ShowMessageException;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleWorkspaceService;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.maven.java.MavenProjectCache;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.util.BasicFileObserver;

import com.google.common.collect.ImmutableList;

/**
 * Tests for {@link MavenProjectCache}
 *
 * @author Alex Boyko
 *
 */
@Disabled
public class MavenProjectCacheTest {

	private static final int TIMEOUT_SECONDS = 60;

	private Sts4LanguageServer server;
	private BasicFileObserver fileObserver;
	private Path testProjectPath;
	private File pomFile;
	private String pomFileContents;

	@BeforeEach
	public void setup() throws Exception {
		fileObserver = new BasicFileObserver();
		server = mock(Sts4LanguageServer.class);
		SimpleWorkspaceService workspaceService = mock(SimpleWorkspaceService.class);
		when(workspaceService.getFileObserver()).thenReturn(fileObserver);
		when(server.getWorkspaceService()).thenReturn(workspaceService);

		testProjectPath = Paths
				.get(DependencyTreeTest.class.getResource("/empty-boot-project-with-classpath-file").toURI());
		pomFile = testProjectPath.resolve(MavenCore.POM_XML).toFile();
		pomFileContents = new String(Files.readAllBytes(pomFile.toPath()), Charset.defaultCharset());

		Path cacheFolerPath = testProjectPath.resolve(IJavaProject.PROJECT_CACHE_FOLDER);
		if (cacheFolerPath.toFile().exists()) {
			Files.walk(cacheFolerPath, FileVisitOption.FOLLOW_LINKS).sorted(Comparator.reverseOrder()).map(Path::toFile)
					.forEach(File::delete);
		}
	}

	@AfterEach
	public void tearDown() throws Exception {
		// restore original content
		writeContent(pomFile, pomFileContents);
	}

	private static void writeContent(File file, String content) throws IOException {
		FileWriter writer = null;
		try {
			writer = new FileWriter(file);
			writer.write(content);
		} finally {
			writer.close();
		}
	}

	@Test
	public void testPomFileChanges() throws Exception {
		MavenProjectCache cache = new MavenProjectCache(server, MavenCore.getDefault(), false, null, (uri, cpe) -> JavaDocProviders.createFor(cpe));
		IJavaProject[] projectChanged = new IJavaProject[] { null };
		IJavaProject[] projectDeleted = new IJavaProject[] { null };
		cache.addListener(new Listener() {
			@Override
			public void created(IJavaProject project) {
			}

			@Override
			public void changed(IJavaProject project) {
				projectChanged[0] = project;
			}

			@Override
			public void deleted(IJavaProject project) {
				projectDeleted[0] = project;
			}
		});

		// Get the project from cache
		MavenJavaProject cachedProject = cache.project(pomFile);
		assertNotNull(cachedProject);

		ImmutableList<CPE> calculatedClassPath = cachedProject.getClasspath().getClasspathEntries();
		assertEquals(51, calculatedClassPath.stream().filter(cpe -> !cpe.isSystem()).count());

		fileObserver.notifyFileChanged(pomFile.toURI().toASCIIString());
		assertNull(projectChanged[0]);

		writeContent(pomFile,
				new String(Files.readAllBytes(testProjectPath.resolve("pom.newxml")), Charset.defaultCharset()));
		fileObserver.notifyFileChanged(pomFile.toURI().toASCIIString());
		assertNotNull(projectChanged[0]);
		assertEquals(cachedProject, projectChanged[0]);
		calculatedClassPath = cachedProject.getClasspath().getClasspathEntries();
		assertEquals(52, calculatedClassPath.stream().filter(cpe -> !cpe.isSystem()).count());

		fileObserver.notifyFileDeleted(pomFile.toURI().toASCIIString());
		assertEquals(cachedProject, projectDeleted[0]);
	}

	@Test
	public void testClasspathCaching() throws Exception {
		Path testProjectPath = Paths
				.get(DependencyTreeTest.class.getResource("/empty-boot-project-with-classpath-file").toURI());

		Path cacheFolder = testProjectPath.resolve(IJavaProject.PROJECT_CACHE_FOLDER);

		final File classpathCacheFile = cacheFolder.resolve(ClasspathFileBasedCache.CLASSPATH_DATA_CACHE_FILE)
				.toFile();

		AtomicBoolean progressDone = new AtomicBoolean();

		// Mock the progress task that gets returned
		PercentageProgressTask mockTask = mock(PercentageProgressTask.class);
		doAnswer(new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				progressDone.set(true);
				return null;
			}
		}).when(mockTask).done();

		ProgressService progressService = mock(ProgressService.class);
		when(progressService.createPercentageProgressTask(any(String.class), any(Integer.class), any(String.class)))
			.thenReturn(mockTask);

		when(server.getProgressService()).thenReturn(progressService);

		assertFalse(classpathCacheFile.exists());

		MavenProjectCache cache = new MavenProjectCache(server, MavenCore.getDefault(), true, cacheFolder, (uri, cpe) -> JavaDocProviders.createFor(cpe));
		MavenJavaProject project = cache.project(pomFile);
		assertTrue(project.getClasspath().getClasspathEntries().isEmpty());

		CompletableFuture.runAsync(() -> {
			while (!progressDone.get()) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

		assertTrue(classpathCacheFile.exists());
		assertEquals(51, project.getClasspath().getClasspathEntries().stream().filter(cpe -> !cpe.isSystem()).count());

		progressDone.set(false);

		// Reset the cache
		cache = new MavenProjectCache(server, MavenCore.getDefault(), true, cacheFolder, (uri, cpe) -> JavaDocProviders.createFor(cpe));

		// Check loaded from cache file
		project = cache.project(pomFile);
		assertEquals(51, project.getClasspath().getClasspathEntries().stream().filter(cpe -> !cpe.isSystem()).count());
	}

	@Test
	public void testErrorLoadingProject() throws Exception {
		Path testProjectPath = Paths
				.get(DependencyTreeTest.class.getResource("/empty-boot-project-with-classpath-file").toURI());

		Path cacheFolder = testProjectPath.resolve(IJavaProject.PROJECT_CACHE_FOLDER);

		AtomicBoolean progressDone = new AtomicBoolean();

		// Mock the progress task that gets returned
		PercentageProgressTask mockTask = mock(PercentageProgressTask.class);
		doAnswer(new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				progressDone.set(true);
				return null;
			}
		}).when(mockTask).done();

		ProgressService progressService = mock(ProgressService.class);
		when(progressService.createPercentageProgressTask(any(String.class), any(Integer.class), any(String.class)))
			.thenReturn(mockTask);

		when(server.getProgressService()).thenReturn(progressService);

		DiagnosticService diagnosticService = mock(DiagnosticService.class);
		when(server.getDiagnosticService()).thenReturn(diagnosticService);

		MavenProjectCache cache = new MavenProjectCache(server, MavenCore.getDefault(), true, cacheFolder, (uri, cpe) -> JavaDocProviders.createFor(cpe));
		MavenJavaProject project = cache.project(pomFile);
		assertTrue(project.getClasspath().getClasspathEntries().isEmpty());

		CompletableFuture.runAsync(() -> {
			while (!progressDone.get()) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		progressDone.set(false);
		verify(diagnosticService, never()).diagnosticEvent(any(ShowMessageException.class));

		writeContent(pomFile, "");
		fileObserver.notifyFileChanged(pomFile.toURI().toASCIIString());
		CompletableFuture.runAsync(() -> {
			while (!progressDone.get()) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		progressDone.set(false);
		verify(diagnosticService, times(1)).diagnosticEvent(any(ShowMessageException.class));
		assertTrue(project.getClasspath().getClasspathEntries().isEmpty());
		assertFalse(cacheFolder.resolve(ClasspathFileBasedCache.CLASSPATH_DATA_CACHE_FILE).toFile().exists());
	}
}