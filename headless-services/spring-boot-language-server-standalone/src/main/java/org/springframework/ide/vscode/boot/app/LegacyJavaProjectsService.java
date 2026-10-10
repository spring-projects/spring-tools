/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.app;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.ide.vscode.boot.jdt.ls.JavaProjectsService;
import org.springframework.ide.vscode.commons.gradle.GradleCore;
import org.springframework.ide.vscode.commons.gradle.GradleProjectCache;
import org.springframework.ide.vscode.commons.gradle.GradleProjectFinder;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IJavadocProvider;
import org.springframework.ide.vscode.commons.javadoc.JavaDocProviders;
import org.springframework.ide.vscode.commons.languageserver.java.CompositeJavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.CompositeProjectOvserver;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectChangeNotifier;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.maven.MavenCore;
import org.springframework.ide.vscode.commons.maven.java.MavenProjectCache;
import org.springframework.ide.vscode.commons.maven.java.MavenProjectFinder;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;

/**
 * Implementation of {@link JavaProjectsService} for standalone mode (no JDT Language Server).
 * Uses Maven and Gradle project caches to discover projects directly from the workspace,
 * and Jandex for type indexing (via {@code LegacyJavaProject}).
 *
 * <p>This class mirrors the setup in {@code BootLanguageServerHarness.createTestDefault()}.
 * Its presence on the classpath causes {@link BootLanguageServerBootApp} to skip creating
 * the JDT-LS-backed {@code JavaProjectsService} bean.
 */
public class LegacyJavaProjectsService implements JavaProjectsService, ApplicationListener<ContextRefreshedEvent>, ProjectChangeNotifier {

	private static final Logger log = LoggerFactory.getLogger(LegacyJavaProjectsService.class);

	private static final java.util.Set<String> IGNORED_DIRECTORIES = java.util.Set.of(
			"target", "node_modules", "build", ".git", "bin"
	);

	private final CompositeJavaProjectFinder projectFinder;
	private final CompositeProjectOvserver projectObserver;
	private final MavenProjectCache mavenProjectCache;
	private final GradleProjectCache gradleProjectCache;

	public LegacyJavaProjectsService(SimpleLanguageServer server) {
		this.mavenProjectCache = new MavenProjectCache(server, MavenCore.getDefault(), false, null,
					(uri, cpe) -> JavaDocProviders.createFor(cpe));
		this.mavenProjectCache.setAlwaysFireEventOnFileChanged(true);

		this.gradleProjectCache = new GradleProjectCache(server, GradleCore.getDefault(), false, null,
					(uri, cpe) -> JavaDocProviders.createFor(cpe));
		this.gradleProjectCache.setAlwaysFireEventOnFileChanged(true);
		
		this.projectFinder = new CompositeJavaProjectFinder();
		projectFinder.addJavaProjectFinder(new MavenProjectFinder(mavenProjectCache));
		projectFinder.addJavaProjectFinder(new GradleProjectFinder(gradleProjectCache));

		this.projectObserver = new CompositeProjectOvserver(Arrays.asList(mavenProjectCache, gradleProjectCache));
	}
	
	public void onApplicationEvent(ContextRefreshedEvent event) {
		String projectDir = System.getProperty("spring.boot.ls.project.dir");
		if (projectDir != null && !projectDir.isEmpty()) {
			initializeProject(new java.io.File(projectDir));
		}
	}

	@Override
	public Optional<IJavaProject> find(TextDocumentIdentifier doc) {
		return projectFinder.find(doc);
	}

	@Override
	public Collection<? extends IJavaProject> all() {
		return projectFinder.all();
	}

	@Override
	public void addListener(ProjectObserver.Listener listener) {
		projectObserver.addListener(listener);
	}

	@Override
	public void removeListener(ProjectObserver.Listener listener) {
		projectObserver.removeListener(listener);
	}
	
	@Override
	public void notifyProjectsChanged(boolean clean) {
		this.projectObserver.notifyProjectsChanged(clean);
	}

	@Override
	public boolean isSupported() {
		return projectObserver.isSupported();
	}

	public void initializeProject(java.io.File projectRoot) {
		if (projectRoot == null || !projectRoot.exists() || !projectRoot.isDirectory()) {
			return;
		}
		log.info("Initializing workspace project directory: {}", projectRoot.getAbsolutePath());
		try {
			java.nio.file.Files.walkFileTree(projectRoot.toPath(), java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), 10, new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
				@Override
				public java.nio.file.FileVisitResult preVisitDirectory(java.nio.file.Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
					String name = dir.getFileName().toString();
					if (IGNORED_DIRECTORIES.contains(name)) {
						return java.nio.file.FileVisitResult.SKIP_SUBTREE;
					}

					java.io.File dirFile = dir.toFile();
					java.io.File pomFile = new java.io.File(dirFile, MavenCore.POM_XML);
					if (pomFile.exists() && pomFile.isFile()) {
						log.info("Found Maven project: {}", pomFile.getAbsolutePath());
						mavenProjectCache.project(pomFile);
						// Multi-module reactor: keep walking so that module poms are imported as
						// projects as well; the subtree of a leaf pom holds no further projects.
						return pomDeclaresModules(pomFile)
								? java.nio.file.FileVisitResult.CONTINUE
								: java.nio.file.FileVisitResult.SKIP_SUBTREE;
					}

					java.io.File gradleFile = new java.io.File(dirFile, GradleCore.GRADLE_BUILD_FILE);
					if (gradleFile.exists() && gradleFile.isFile()) {
						log.info("Found Gradle project: {}", gradleFile.getAbsolutePath());
						gradleProjectCache.project(gradleFile);
						return java.nio.file.FileVisitResult.SKIP_SUBTREE;
					}

					java.io.File gradleKtsFile = new java.io.File(dirFile, GradleCore.GRADLE_KTS_BUILD_FILE);
					if (gradleKtsFile.exists() && gradleKtsFile.isFile()) {
						log.info("Found Gradle Kotlin project: {}", gradleKtsFile.getAbsolutePath());
						gradleProjectCache.project(gradleKtsFile);
						return java.nio.file.FileVisitResult.SKIP_SUBTREE;
					}

					return java.nio.file.FileVisitResult.CONTINUE;
				}
				@Override
				public java.nio.file.FileVisitResult visitFileFailed(java.nio.file.Path file, java.io.IOException exc) {
					return java.nio.file.FileVisitResult.CONTINUE;
				}
			});
		} catch (Exception e) {
			// ignore
		}
	}

	/**
	 * Best-effort check whether the given pom declares a {@code <modules>} section, i.e. is the
	 * root of a multi-module Maven reactor. A false positive only makes the walk descend into a
	 * subtree that holds no further poms; a false negative falls back to skipping the subtree.
	 *
	 * @param pomFile the pom file to check
	 * @return {@code true} if the pom declares modules
	 */
	private static boolean pomDeclaresModules(File pomFile) {
		try {
			String pom = Files.readString(pomFile.toPath(), StandardCharsets.UTF_8);
			return pom.contains("<modules>") && pom.contains("</modules>");
		}
		catch (Exception e) {
			return false;
		}
	}

	@Override
	public IJavadocProvider javadocProvider(URI projectUri, CPE classpathEntry) {
		return JavaDocProviders.createFor(classpathEntry);
	}

}
