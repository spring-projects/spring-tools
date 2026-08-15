/*******************************************************************************
 * Copyright (c) 2018, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Optional;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.gradle.internal.impldep.com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.links.VSCodeSourceLinks;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.javadoc.JavaDocProviders;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.maven.MavenBuilder;
import org.springframework.ide.vscode.commons.maven.MavenCore;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.util.BasicFileObserver;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

/**
 * Tests for creation of VSCode links in hover documentation
 *
 * @author Alex Boyko
 *
 */
public class VSCodeSourceLinksTest {

	private static LoadingCache<String, MavenJavaProject> mavenProjectsCache = CacheBuilder.newBuilder().build(new CacheLoader<String, MavenJavaProject>() {

		@Override
		public MavenJavaProject load(String projectName) throws Exception {
			Path testProjectPath = Paths.get(VSCodeSourceLinksTest.class.getResource("/test-projects/" + projectName).toURI());
			MavenBuilder.newBuilder(testProjectPath).clean().pack().javadoc().skipTests().execute();
					return MavenJavaProject.create(new BasicFileObserver(), MavenCore.getDefault(),
							testProjectPath.resolve(MavenCore.POM_XML).toFile(),
							(uri, cpe) -> JavaDocProviders.createFor(cpe));
				}

	});

    @Test
    void testJavaSourceUrl() throws Exception {
        MavenJavaProject project = mavenProjectsCache.get("empty-boot-15-web-app");
        Optional<String> url = new VSCodeSourceLinks(new CompilationUnitCache(null, null, null, null), null).sourceLinkUrlForFQName(project, "com.example.EmptyBoot15WebAppApplication");
        assertTrue(url.isPresent());
        Path projectPath = Paths.get(project.pom().getParent());
        URI uri = URI.create(url.get());

        // Use File to get rid of the fragment parts of the URL. The URL may have fragments that indicate line and column numbers
        uri = new File(uri.getPath()).toURI();

        Path relativePath = projectPath.relativize(Paths.get(uri));
        assertEquals(Paths.get("src/main/java/com/example/EmptyBoot15WebAppApplication.java"), relativePath);
        String positionPart = url.get().substring(url.get().lastIndexOf('#'));
        assertEquals("#7,14", positionPart);
    }

    @Test
    void testClasspathResourceOnTomcatUrl() throws Exception {
        MavenJavaProject project = mavenProjectsCache.get("empty-boot-15-web-app");
        Optional<String> url = new VSCodeSourceLinks(new CompilationUnitCache(null, null, null, null), new JavaProjectFinder() {

            @Override
            public Optional<IJavaProject> find(TextDocumentIdentifier doc) {
                return Optional.of(project);
            }

            @Override
            public Collection<? extends IJavaProject> all() {
                return ImmutableList.of(project);
            }
        })
                .sourceLinkUrlForClasspathResource("Users/aboyko/pivotal-tc-server/instances/base/wtpwebapps/empty-boot-15-web-app/WEB-INF/classes/com/example/EmptyBoot15WebAppApplication.class");
        assertTrue(url.isPresent());
        Path projectPath = Paths.get(project.pom().getParent());
        URI uri = URI.create(url.get());

        // Use File to get rid of the fragment parts of the URL. The URL may have fragments that indicate line and column numbers
        uri = new File(uri.getPath()).toURI();

        Path relativePath = projectPath.relativize(Paths.get(uri));
        assertEquals(Paths.get("src/main/java/com/example/EmptyBoot15WebAppApplication.java"), relativePath);
        String positionPart = url.get().substring(url.get().lastIndexOf('#'));
        assertEquals("#7,14", positionPart);
    }

    @Test
    void testJarUrl() throws Exception {
        MavenJavaProject project = mavenProjectsCache.get("empty-boot-15-web-app");
        Optional<String> url = new VSCodeSourceLinks(new CompilationUnitCache(null, null, null, null), null).sourceLinkUrlForFQName(project, "org.springframework.boot.autoconfigure.SpringBootApplication");
        assertTrue(url.isPresent());
        String headerPart = url.get().substring(0, url.get().indexOf('?'));
        assertEquals("jdt://contents/spring-boot-autoconfigure-1.5.8.RELEASE.jar/org.springframework.boot.autoconfigure/SpringBootApplication.class", headerPart);
        String positionPart = url.get().substring(url.get().lastIndexOf('#'));
        assertEquals("#55,19", positionPart);
    }

    @Test
    void testJarUrlInnerType() throws Exception {
        MavenJavaProject project = mavenProjectsCache.get("empty-boot-15-web-app");
        Optional<String> url = new VSCodeSourceLinks(new CompilationUnitCache(null, null, null, null), null).sourceLinkUrlForFQName(project, "org.springframework.web.client.RestTemplate$AcceptHeaderRequestCallback");
        assertTrue(url.isPresent());
        String headerPart = url.get().substring(0, url.get().indexOf('?'));
        assertEquals("jdt://contents/spring-web-4.3.12.RELEASE.jar/org.springframework.web.client/RestTemplate$AcceptHeaderRequestCallback.class", headerPart);
        String positionPart = url.get().substring(url.get().lastIndexOf('#'));
        assertEquals("#747,16", positionPart);
    }

}
