/*******************************************************************************
 * Copyright (c) 2024, 2026 Broadcom, Inc.
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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.GitBaselineTracker;
import org.springframework.ide.vscode.boot.java.commands.JarDependencySource;
import org.springframework.ide.vscode.boot.java.commands.Misc;
import org.springframework.ide.vscode.boot.java.commands.SpringIndexCommands;
import org.springframework.ide.vscode.boot.java.commands.StructureBaselineStorage;
import org.springframework.ide.vscode.boot.java.commands.StructureDependencySource;
import org.springframework.ide.vscode.boot.java.commands.StructureDependencySources;
import org.springframework.ide.vscode.boot.java.commands.StructureSnapshotStore;
import org.springframework.ide.vscode.boot.java.commands.StructureViewProvider;
import org.springframework.ide.vscode.boot.java.commands.WorkingTreeStatus;
import org.springframework.ide.vscode.boot.java.commands.WorkspaceBootExecutableProjects;
import org.springframework.ide.vscode.boot.java.commands.WorkspaceProjectDependencySource;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeCatalogRegistry;
import org.springframework.ide.vscode.boot.modulith.ModulithService;
import org.springframework.ide.vscode.commons.java.ClasspathDependencyResolver;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.protocol.spring.IndexUpdatedParams;

@Configuration(proxyBeanMethods = false)
public class CommandsConfig {

	@Bean WorkspaceBootExecutableProjects workspaceBootProjects(SimpleLanguageServer server, JavaProjectFinder projectFinder, SpringSymbolIndex symbolIndex) {
		return new WorkspaceBootExecutableProjects(server, projectFinder, symbolIndex);
	}

	@Bean
	StructureViewProvider structureViewProvider(SpringMetamodelIndex symbolIndex, ModulithService modulithService,
			StereotypeCatalogRegistry stereotypeCatalogRegistry, SourceLinks sourceLinks, StructureDependencySources structureDependencySources,
			BootJavaConfig config, SimpleLanguageServer server, JavaProjectFinder projectFinder) {

		// every tree changes when the package-nodes setting does - so the client gets to build all of
		// them again, the same way it does after an index update. Told by the server, which has the
		// new value by then: asked by the client right away, it could still get trees built with the
		// old one
		AtomicBoolean packageNodes = new AtomicBoolean(config.isStructurePackageNodesEnabled());
		config.addListener(v -> {
			boolean now = config.isStructurePackageNodesEnabled();
			if (packageNodes.getAndSet(now) != now && server.getClient() != null) {
				Set<String> projects = projectFinder.all().stream().map(IJavaProject::getElementName).collect(Collectors.toSet());
				if (!projects.isEmpty()) {
					server.getClient().indexUpdated(IndexUpdatedParams.of(projects));
				}
			}
		});

		return new StructureViewProvider(symbolIndex, modulithService, stereotypeCatalogRegistry, sourceLinks, structureDependencySources,
				config::isStructurePackageNodesEnabled);
	}

	@Bean
	ClasspathDependencyResolver classpathDependencyResolver(JavaProjectFinder projectFinder) {
		return new ClasspathDependencyResolver(projectFinder);
	}

	@Bean
	WorkspaceProjectDependencySource workspaceProjectDependencySource(ClasspathDependencyResolver resolver, JavaProjectFinder projectFinder) {
		return new WorkspaceProjectDependencySource(resolver, projectFinder);
	}

	@Bean
	JarDependencySource jarDependencySource(ClasspathDependencyResolver resolver, SimpleLanguageServer server) {
		return new JarDependencySource(resolver, server.getProgressService());
	}

	@Bean
	StructureDependencySources structureDependencySources(List<StructureDependencySource> sources, SimpleLanguageServer server) {
		// dependencies prepared in the background (JARs being scanned) are left out of the tree
		// until they are ready - the client then gets to rebuild that one project's tree, the same
		// way it does for any other update of the index the tree is built from
		return new StructureDependencySources(sources, project -> {
			if (server.getClient() != null) {
				server.getClient().indexUpdated(IndexUpdatedParams.of(project.getElementName()));
			}
		});
	}

	@Bean
	SpringIndexCommands springIndexCommands(SimpleLanguageServer server, JavaProjectFinder projectFinder,
			SpringMetamodelIndex springIndex, SpringSymbolIndex symbolIndex, StructureViewProvider structureViewProvider,
			StructureSnapshotStore structureSnapshotStore, StructureDependencySources structureDependencySources) {
		return new SpringIndexCommands(server, springIndex, symbolIndex, projectFinder, structureViewProvider,
				structureSnapshotStore, structureDependencySources);
	}

	/**
	 * {@code true} when running under the test harness. Structure baselines are pinned by project
	 * name, and there are many, mutually independent test configuration combinations across this
	 * module - rather than relying on every one of them to separately override this bean with an
	 * isolated directory (easy to miss one, as happened while building this out), the bean detects
	 * test mode itself and always gets a fresh, isolated temp directory instead of the real
	 * {@code ~/.sts4/.structureBaselines}, with no test-side wiring required at all.
	 */
	private static final boolean RUNNING_UNDER_TEST_HARNESS = isClassPresent("org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness");

	private static boolean isClassPresent(String className) {
		try {
			Class.forName(className);
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	@Bean
	StructureBaselineStorage structureBaselineStorage(BootLsConfigProperties props) {
		if (RUNNING_UNDER_TEST_HARNESS) {
			try {
				return new StructureBaselineStorage(Files.createTempDirectory("sts4-test-structure-baselines").toFile());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return new StructureBaselineStorage(new File(props.getStructureBaselineDir()));
	}

	@Bean
	StructureSnapshotStore structureSnapshotStore(StructureViewProvider structureViewProvider, StructureBaselineStorage structureBaselineStorage,
			BootJavaConfig config) {
		return new StructureSnapshotStore(structureViewProvider, structureBaselineStorage, config);
	}

	@Bean
	GitBaselineTracker gitBaselineTracker(JavaProjectFinder projectFinder, SpringSymbolIndex symbolIndex,
			BootJavaConfig config, StructureSnapshotStore structureSnapshotStore, SimpleLanguageServer server) {
		return new GitBaselineTracker(projectFinder, symbolIndex, config, structureSnapshotStore,
				new WorkingTreeStatus.IndexRelevant(symbolIndex), server);
	}

	@Bean
	Misc misc(SimpleLanguageServer server) {
		return new Misc(server);
	}

}
