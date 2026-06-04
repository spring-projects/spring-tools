/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.gradle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.ide.vscode.languageserver.testharness.ClasspathTestUtil.getOutputFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.assertj.core.util.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.javadoc.JavaDocProviders;
import org.springframework.ide.vscode.commons.languageserver.Sts4LanguageServer;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver.Listener;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleWorkspaceService;
import org.springframework.ide.vscode.commons.util.BasicFileObserver;

/**
 * Tests covering Gradle project data
 *
 * @author Alex Boyko
 *
 */
public class GradleProjectTest {

	private Sts4LanguageServer server;
	private BasicFileObserver fileObserver;

	@BeforeEach
	public void setup() throws Exception {
		fileObserver = new BasicFileObserver();
		server = mock(Sts4LanguageServer.class);
		SimpleWorkspaceService workspaceService = mock(SimpleWorkspaceService.class);
		when(workspaceService.getFileObserver()).thenReturn(fileObserver);
		when(server.getWorkspaceService()).thenReturn(workspaceService);
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

	private GradleJavaProject getGradleProject(String projectName) throws Exception {
		Path testProjectPath = Paths.get(GradleProjectTest.class.getResource("/" + projectName + "/build.gradle").toURI());
		return GradleJavaProject.create(fileObserver, GradleCore.getDefault(), testProjectPath.toFile(), (uri, cpe) -> JavaDocProviders.createFor(cpe));
	}

    @Test
    void testEclipseGradleProject() throws Exception {
        GradleJavaProject project = getGradleProject("empty-gradle-project");
        List<File> nonSystemClasspathEntries = IClasspathUtil.getBinaryRoots(project.getClasspath(), (cpe) -> !cpe.isSystem());
        assertEquals(69, nonSystemClasspathEntries.size());
    }

    @Test
    void outputFolder() throws Exception {
        GradleJavaProject project = getGradleProject("test-app-1");
        String of = getOutputFolder(project).toString();
        assertTrue(of.endsWith("/bin") || of.endsWith("/bin/main"));
    }

    @Test
    void gradleClasspathResource() throws Exception {
        GradleJavaProject project = getGradleProject("test-app-1");
        List<String> resources = IClasspathUtil.getClasspathResources(project.getClasspath());
        assertArrayEquals(new String[]{"test-resource-1.txt"}, resources.toArray(new String[resources.size()]));
    }

    @Test
    void testGradleFileChanges() throws Exception {
        Path testProjectPath = Paths.get(GradleProjectTest.class.getResource("/empty-gradle-project").toURI());
        File gradleFile = testProjectPath.resolve(GradleCore.GRADLE_BUILD_FILE).toFile();

        String gradelFileContents = Files.contentOf(gradleFile, Charset.defaultCharset());

        try {
            GradleProjectCache manager = createProjectCache();
            IJavaProject[] projectChanged = new IJavaProject[]{null};
            IJavaProject[] projectDeleted = new IJavaProject[]{null};
            manager.addListener(new Listener() {
                @Override
                public void created(IJavaProject project) {
                }

                @Override
                public void changed(IJavaProject project, boolean clean) {
                    projectChanged[0] = project;
                }
                @Override
                public void deleted(IJavaProject project) {
                    projectDeleted[0] = project;
                }
            });

            // Get the project from cache
            GradleJavaProject cachedProject = manager.project(gradleFile);
            assertNotNull(cachedProject);

            List<File> nonSystemClasspathEntries = IClasspathUtil.getBinaryRoots(cachedProject.getClasspath(), (cpe) -> !cpe.isSystem());
            assertEquals(69, nonSystemClasspathEntries.size());

            fileObserver.notifyFileChanged(gradleFile.toURI().toASCIIString());
            assertNull(projectChanged[0]);

            writeContent(gradleFile, Files.contentOf(testProjectPath.resolve("build.newgradle").toFile(), Charset.defaultCharset()));
            fileObserver.notifyFileChanged(gradleFile.toURI().toASCIIString());
            assertNotNull(projectChanged[0]);
            assertEquals(cachedProject, projectChanged[0]);
            nonSystemClasspathEntries = IClasspathUtil.getBinaryRoots(cachedProject.getClasspath(), (cpe) -> !cpe.isSystem());
            assertEquals(70, nonSystemClasspathEntries.size());


            fileObserver.notifyFileDeleted(gradleFile.toURI().toASCIIString());
            assertEquals(cachedProject, projectDeleted[0]);
        } finally {
            writeContent(gradleFile, gradelFileContents);
        }
    }

	private GradleProjectCache createProjectCache() {
		return new GradleProjectCache(server, GradleCore.getDefault(), false, null, (uri, cpe) -> JavaDocProviders.createFor(cpe));
	}

    @Test
    void findGradleProjectWithStandardBuildFile() throws Exception {
        GradleProjectFinder finder = new GradleProjectFinder(createProjectCache());
        File sourceFile = new File(GradleProjectTest.class.getResource("/test-app-1/src/main/java/Library.java").toURI());
        Optional<IJavaProject> project = finder.find(sourceFile);
        assertTrue(project.isPresent());
        assertTrue(project.get() instanceof GradleJavaProject);
        GradleJavaProject gradleProject = (GradleJavaProject) project.get();
        assertEquals(new File(GradleProjectTest.class.getResource("/test-app-1").toURI()), new File(gradleProject.getLocationUri()));
    }

    @Test
    void findGradleProjectWithNonStandardBuildFile() throws Exception {
        GradleProjectFinder finder = new GradleProjectFinder(createProjectCache());
        File sourceFile = new File(GradleProjectTest.class.getResource("/test-app-2/src/main/java/Library.java").toURI());
        Optional<IJavaProject> project = finder.find(sourceFile);
        assertTrue(project.isPresent());
        assertTrue(project.get() instanceof GradleJavaProject);
        GradleJavaProject gradleProject = (GradleJavaProject) project.get();
        assertEquals(new File(GradleProjectTest.class.getResource("/test-app-2").toURI()), new File(gradleProject.getLocationUri()));
    }

}
