/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils.test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ide.vscode.boot.index.cache.IndexCache;
import org.springframework.ide.vscode.boot.index.cache.IndexCacheKey;
import org.springframework.ide.vscode.boot.java.beans.CachedIndexElement;
import org.springframework.ide.vscode.boot.java.handlers.SpringComponentIndexer;
import org.springframework.ide.vscode.boot.java.reconcilers.CachedDiagnostic;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtReconciler;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJava;
import org.springframework.ide.vscode.boot.java.utils.SymbolHandler;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.ProgressService;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gson.JsonObject;

/**
 * Ensures a cache-only {@link SpringIndexerJava#initializeProject} restores the dependency tracker from dependency
 * multimaps in both the index cache and the diagnostics cache. The diagnostics cache can carry extra edges after
 * {@code reconcileWithCompleteIndex} / {@code updateDiagnosticsAfterReconcile}.
 */
class SpringIndexerJavaDependencyRestoreFromCacheTest {

	private static final String DEP_FROM_INDEX = "dep.snapshot.at.index.store";
	private static final String DEP_FROM_RECONCILE = "dep.added.during.reconcile.with.complete.index";

	@Test
	void initializeProject_fromFullScanCache_shouldRestoreAllCachedDependencies(@TempDir Path tempDir) throws Exception {
		Path sourceRoot = tempDir.resolve("src");
		Files.createDirectories(sourceRoot);
		Path javaFile = sourceRoot.resolve("Sample.java");
		Files.writeString(javaFile, "public class Sample {}\n");
		String javaFilePath = javaFile.toAbsolutePath().normalize().toString();

		Multimap<String, String> indexCacheDeps = MultimapBuilder.hashKeys().hashSetValues().build();
		indexCacheDeps.replaceValues(javaFilePath, Set.of(DEP_FROM_INDEX));

		Multimap<String, String> diagnosticsCacheDeps = MultimapBuilder.hashKeys().hashSetValues().build();
		diagnosticsCacheDeps.replaceValues(javaFilePath, Set.of(DEP_FROM_INDEX, DEP_FROM_RECONCILE));

		IndexCache cache = mock(IndexCache.class);
		when(cache.retrieve(any(IndexCacheKey.class), any(String[].class), eq(CachedIndexElement.class)))
				.thenReturn(Pair.of(new CachedIndexElement[0], indexCacheDeps));
		when(cache.retrieve(any(IndexCacheKey.class), any(String[].class), eq(CachedDiagnostic.class)))
				.thenReturn(Pair.of(new CachedDiagnostic[0], diagnosticsCacheDeps));

		IJavaProject project = mock(IJavaProject.class);
		when(project.getElementName()).thenReturn("dependency-restore-test");
		CPE sourceEntry = new CPE(Classpath.ENTRY_KIND_SOURCE, sourceRoot.toString());
		sourceEntry.setOwn(true);
		sourceEntry.setJavaContent(true);
		sourceEntry.setTest(false);
		sourceEntry.setOutputFolder(tempDir.resolve("out").toString());
		IClasspath classpath = mock(IClasspath.class);
		when(classpath.getClasspathEntries()).thenReturn(List.of(sourceEntry));
		when(project.getClasspath()).thenReturn(classpath);

		SymbolHandler symbolHandler = mock(SymbolHandler.class);
		JdtReconciler jdtReconciler = mock(JdtReconciler.class);
		JavaProjectFinder projectFinder = mock(JavaProjectFinder.class);
		CompilationUnitCache cuCache = mock(CompilationUnitCache.class);

		BiFunction<TextDocument, BiConsumer<String, Diagnostic>, IProblemCollector> problemCollectorCreator = (doc,
				diagConsumer) -> mock(IProblemCollector.class);

		JsonObject validationSettings = new JsonObject();
		validationSettings.addProperty("severity", "warning");

		SpringIndexerJava indexer = new SpringIndexerJava(symbolHandler, new SpringComponentIndexer[0], cache, projectFinder,
				ProgressService.NO_PROGRESS, jdtReconciler, problemCollectorCreator, validationSettings, cuCache);

		indexer.initializeProject(project, false);

		Set<String> restored = Set.copyOf(indexer.getDependencyTracker().get(project, javaFilePath));
		assertTrue(restored.contains(DEP_FROM_INDEX), "sanity: index-cache dependencies should be restored");
		assertTrue(restored.contains(DEP_FROM_RECONCILE),
				"dependencies persisted only with the diagnostics cache must be restored too "
						+ "(currently restore uses index-cache deps only)");
	}

}
