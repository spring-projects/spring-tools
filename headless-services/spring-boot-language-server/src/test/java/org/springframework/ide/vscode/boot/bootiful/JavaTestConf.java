/*******************************************************************************
 * Copyright (c) 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.bootiful;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ide.vscode.boot.jdt.ls.JavaProjectsService;
import org.springframework.ide.vscode.commons.gradle.GradleCore;
import org.springframework.ide.vscode.commons.gradle.GradleProjectCache;
import org.springframework.ide.vscode.commons.gradle.GradleProjectFinder;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IJavadocProvider;
import org.springframework.ide.vscode.commons.javadoc.JavaDocProviders;
import org.springframework.ide.vscode.commons.languageserver.java.CompositeJavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.CompositeProjectOvserver;
import org.springframework.ide.vscode.commons.languageserver.java.JavadocService;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.maven.MavenCore;
import org.springframework.ide.vscode.commons.maven.java.MavenProjectCache;
import org.springframework.ide.vscode.commons.maven.java.MavenProjectFinder;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;

@Configuration
public class JavaTestConf {
	
	@Bean JavaProjectsService javaProjectsService(SimpleLanguageServer server) {
		CompositeJavaProjectFinder javaProjectFinder = new CompositeJavaProjectFinder();

		JavadocService javadocService = (uri, cpe) -> JavaDocProviders.createFor(cpe);

		MavenProjectCache mavenProjectCache = new MavenProjectCache(server, MavenCore.getDefault(), true, Paths.get(IJavaProject.PROJECT_CACHE_FOLDER), javadocService);
		javaProjectFinder.addJavaProjectFinder(new MavenProjectFinder(mavenProjectCache));

		GradleProjectCache gradleProjectCache = new GradleProjectCache(server, GradleCore.getDefault(), true, Paths.get(IJavaProject.PROJECT_CACHE_FOLDER), javadocService);
		javaProjectFinder.addJavaProjectFinder(new GradleProjectFinder(gradleProjectCache));

		CompositeProjectOvserver projectObserver = new CompositeProjectOvserver(Arrays.asList(mavenProjectCache, gradleProjectCache));

		return new JavaProjectsService() {

			@Override
			public void removeListener(Listener listener) {
				projectObserver.removeListener(listener);
			}

			@Override
			public void addListener(Listener listener) {
				projectObserver.addListener(listener);
			}

			@Override
			public Optional<IJavaProject> find(TextDocumentIdentifier doc) {
				return javaProjectFinder.find(doc);
			}

			@Override
			public IJavadocProvider javadocProvider(URI projectUri, CPE cpe) {
				return javadocService.javadocProvider(projectUri, cpe);
			}

			@Override
			public Collection<? extends IJavaProject> all() {
				return javaProjectFinder.all();
			}
		};
	}

}
