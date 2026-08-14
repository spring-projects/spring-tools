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
package org.springframework.ide.vscode.project.harness;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.eclipse.lsp4j.TextDocumentIdentifier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.springframework.ide.vscode.boot.app.BootLanguageServerParams;
import org.springframework.ide.vscode.boot.editor.harness.PropertyIndexHarness;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.metadata.DefaultSpringPropertyIndexProvider;
import org.springframework.ide.vscode.boot.metadata.ValueProviderRegistry;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil;
import org.springframework.ide.vscode.commons.gradle.GradleCore;
import org.springframework.ide.vscode.commons.gradle.GradleProjectCache;
import org.springframework.ide.vscode.commons.gradle.GradleProjectFinder;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.javadoc.JavaDocProviders;
import org.springframework.ide.vscode.commons.languageserver.java.CompositeJavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.CompositeProjectOvserver;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.maven.MavenCore;
import org.springframework.ide.vscode.commons.maven.java.MavenProjectCache;
import org.springframework.ide.vscode.commons.maven.java.MavenProjectFinder;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.ClasspathTestUtil;
import org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness;

public class BootLanguageServerHarness extends LanguageServerHarness {

	private final PropertyIndexHarness indexHarness;
	private final JavaProjectFinder projectFinder;
	private final BootLanguageServerParams serverParams;
	private final String defaultFileExtension;

//	/**
//	 * Creates a builder and initializes it so that it sets up a test harness with
//	 * the 'real stuff'. I.e project finder and other injected components are like
//	 * they would be in 'production' environment.
//	 * <p>
//	 * Builder methods can still be called to replace some of the components with
//	 * mocks selectively.
//	 */
//	public static Builder builder() {
//		return new Builder();
//	}
//
//	public static class Builder {
//
//		LSFactory<BootLanguageServerParams> defaultsFactory = BootLanguageServerParams.createTestDefault();
//		private JavaProjectFinder projectFinder = null;
//		private ProjectObserver projectObserver = null;
//		private SpringPropertyIndexProvider indexProvider = null;
//		private SpringPropertyIndexProvider adHocIndexProvider = null;
//		private RunningAppProvider runningAppProvider = null;
//		private PropertyIndexHarness indexHarness = null;
//		private Duration watchDogInterval = null;
//		private TypeUtilProvider typeUtilProvider = null;
//
//		public BootJavaLanguageServerHarness build() throws Exception {
//			BootJavaLanguageServerHarness harness = new BootJavaLanguageServerHarness(this);
//			return harness;
//		}
//
//		public Builder mockDefaults() {
//			indexHarness = new PropertyIndexHarness();
//			projectFinder = indexHarness.getProjectFinder();
//			indexProvider = indexHarness.getIndexProvider();
//			adHocIndexProvider = indexHarness.adHocIndexProvider;
//			projectObserver = ProjectObserver.NULL;
//			runningAppProvider = RunningAppProvider.NULL;
//			return this;
//		}
//
//		public Builder runningAppProvider(RunningAppProvider provider) {
//			this.runningAppProvider = () -> provider.getAllRunningSpringApps();
//			return this;
//		}
//
//		public Builder projectFinder(JavaProjectFinder projectFinder) {
//			this.projectFinder = projectFinder;
//			return this;
//		}
//
//		public Builder propertyIndexProvider(SpringPropertyIndexProvider propertyIndexProvider) {
//			this.indexProvider = propertyIndexProvider;
//			return this;
//		}
//
//		public Builder watchDogInterval(Duration watchDogInterval) {
//			this.watchDogInterval = watchDogInterval;
//			return this;
//		}
//	}

	/**
	 * This constructor is private. Use the builder api instead.
	 * @param projectFinder
	 */
	public BootLanguageServerHarness(
			SimpleLanguageServer server,
			BootLanguageServerParams serverParams,
			PropertyIndexHarness indexHarness,
			JavaProjectFinder projectFinder,
			LanguageId defaultLanguageId,
			String defaultFileExtension
	) throws Exception {
		super(server, defaultLanguageId);
		this.serverParams = serverParams;
		this.indexHarness = indexHarness;
		this.projectFinder = projectFinder;
		this.defaultFileExtension = defaultFileExtension;
	}

	public BootLanguageServerParams getServerParams() {
		return serverParams;
	}

	@Override
	protected String getFileExtension() {
		return defaultFileExtension;
	}

	public JavaProjectFinder getProjectFinder() {
		return projectFinder;
	}

	public PropertyIndexHarness getPropertyIndexHarness() {
		assertNotNull(indexHarness); //only supported in some types of instantations of the harness (i.e. when indexer is controlled by indexer harness.
		return indexHarness;
	}

	public void useProject(IJavaProject p) throws Exception {
		indexHarness.useProject(p);
	}

	public Path getOutputFolder() throws Exception {
		IClasspath classpath = getProjectFinder().find(null).get().getClasspath();
		for (CPE cpe : classpath.getClasspathEntries()) {
			if (Classpath.isSource(cpe)) {
				if (cpe.getPath().endsWith(ClasspathTestUtil.MAIN_JAVA)) {
					return Paths.get(cpe.getOutputFolder());
				}
			}
		}
		return null;
	}
	
	public static BootLanguageServerParams createTestDefault(SimpleLanguageServer server, ValueProviderRegistry valueProviders) {
		// Initialize project finders, project caches and project observers
		CompositeJavaProjectFinder javaProjectFinder = new CompositeJavaProjectFinder();
		MavenProjectCache mavenProjectCache = new MavenProjectCache(server, MavenCore.getDefault(), false, null, (uri, cpe) -> JavaDocProviders.createFor(cpe));
		mavenProjectCache.setAlwaysFireEventOnFileChanged(true);
		javaProjectFinder.addJavaProjectFinder(new MavenProjectFinder(mavenProjectCache));

		GradleProjectCache gradleProjectCache = new GradleProjectCache(server, GradleCore.getDefault(), false, null, (uri, cpe) -> JavaDocProviders.createFor(cpe));
		gradleProjectCache.setAlwaysFireEventOnFileChanged(true);
		javaProjectFinder.addJavaProjectFinder(new GradleProjectFinder(gradleProjectCache));

		CompositeProjectOvserver projectObserver = new CompositeProjectOvserver(Arrays.asList(mavenProjectCache, gradleProjectCache));

		DefaultSpringPropertyIndexProvider indexProvider = new DefaultSpringPropertyIndexProvider(javaProjectFinder, projectObserver, server.getWorkspaceService().getFileObserver(), valueProviders, null);
		indexProvider.setProgressService(server.getProgressService());

		return new BootLanguageServerParams(
				javaProjectFinder.filter(project -> SpringProjectUtil.isBootProject(project) || SpringProjectUtil.isSpringProject(project)),
				projectObserver,
				indexProvider,
				(SourceLinks sourceLinks, IDocument doc) -> new TypeUtil(sourceLinks, javaProjectFinder.find(new TextDocumentIdentifier(doc.getUri())))
		);
	}


}
