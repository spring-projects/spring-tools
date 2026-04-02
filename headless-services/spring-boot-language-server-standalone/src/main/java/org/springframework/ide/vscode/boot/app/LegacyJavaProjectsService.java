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

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.springframework.ide.vscode.boot.jdt.ls.JavaProjectsService;
import org.springframework.ide.vscode.commons.gradle.GradleCore;
import org.springframework.ide.vscode.commons.gradle.GradleProjectCache;
import org.springframework.ide.vscode.commons.gradle.GradleProjectFinder;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IJavadocProvider;
import org.springframework.ide.vscode.commons.javadoc.JavaDocProviders;
import org.springframework.ide.vscode.commons.languageserver.java.CompositeJavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.CompositeProjectOvserver;
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
public class LegacyJavaProjectsService implements JavaProjectsService {

	private final CompositeJavaProjectFinder projectFinder;
	private final CompositeProjectOvserver projectObserver;

	public LegacyJavaProjectsService(SimpleLanguageServer server) {
		MavenProjectCache mavenProjectCache = new MavenProjectCache(server, MavenCore.getDefault(), false, null,
				(uri, cpe) -> JavaDocProviders.createFor(cpe));
		mavenProjectCache.setAlwaysFireEventOnFileChanged(true);

		GradleProjectCache gradleProjectCache = new GradleProjectCache(server, GradleCore.getDefault(), false, null,
				(uri, cpe) -> JavaDocProviders.createFor(cpe));
		gradleProjectCache.setAlwaysFireEventOnFileChanged(true);

		this.projectFinder = new CompositeJavaProjectFinder();
		projectFinder.addJavaProjectFinder(new MavenProjectFinder(mavenProjectCache));
		projectFinder.addJavaProjectFinder(new GradleProjectFinder(gradleProjectCache));

		this.projectObserver = new CompositeProjectOvserver(Arrays.asList(mavenProjectCache, gradleProjectCache));
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
	public boolean isSupported() {
		return projectObserver.isSupported();
	}

	@Override
	public IJavadocProvider javadocProvider(URI projectUri, CPE classpathEntry) {
		return JavaDocProviders.createFor(classpathEntry);
	}

}
