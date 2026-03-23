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
package org.springframework.ide.vscode.boot.java.utils;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.ide.vscode.boot.index.cache.IndexCache;
import org.springframework.ide.vscode.boot.index.cache.IndexCacheKey;
import org.springframework.ide.vscode.boot.java.beans.CachedIndexElement;
import org.springframework.ide.vscode.boot.java.reconcilers.CachedDiagnostics;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.util.UriUtil;

import com.google.common.collect.Multimap;
import com.google.gson.JsonObject;

/**
 * Cache keys, freshness checks, and index/diagnostics {@link IndexCache} operations for {@link SpringIndexerJava}.
 *
 * @author Martin Lippert
 */
public class SpringIndexerJavaCacheHelper {

	public static final String INDEX_KEY = "index";
	public static final String DIAGNOSTICS_KEY = "diagnostics";

	private final IndexCache cache;
	private final String generation;
	private JsonObject validationSeveritySettings;

	public SpringIndexerJavaCacheHelper(IndexCache cache, String generation, JsonObject validationSeveritySettings) {
		this.cache = cache;
		this.generation = generation;
		this.validationSeveritySettings = validationSeveritySettings;
	}

	public void setValidationSeveritySettings(JsonObject validationSeveritySettings) {
		this.validationSeveritySettings = validationSeveritySettings;
	}

	public IndexCacheKey getCacheKey(IJavaProject project, String elementType) {
		IClasspath classpath = project.getClasspath();
		Stream<File> classpathEntries = IClasspathUtil.getBinaryRoots(classpath, cpe -> !Classpath.ENTRY_KIND_SOURCE.equals(cpe.getKind())).stream();

		String classpathIdentifier = classpathEntries
				.filter(file -> file.exists())
				.map(file -> file.getAbsolutePath() + "#" + file.lastModified())
				.collect(Collectors.joining(","));

		return new IndexCacheKey(project.getElementName(), "java", elementType,
				DigestUtils.md5Hex(generation + "-" + validationSeveritySettings.toString() + "-" + classpathIdentifier).toUpperCase());
	}

	public boolean isCacheOutdated(IndexCacheKey cacheKey, String docURI, long modifiedTimestamp) {
		long cachedModificationTimestamp = cache.getModificationTimestamp(cacheKey, UriUtil.toFileString(docURI));
		return modifiedTimestamp > cachedModificationTimestamp;
	}

	public boolean isIndexOrDiagnosticsCacheOutdated(IJavaProject project, String docURI, long modifiedTimestamp) {
		return isCacheOutdated(getCacheKey(project, INDEX_KEY), docURI, modifiedTimestamp)
				|| isCacheOutdated(getCacheKey(project, DIAGNOSTICS_KEY), docURI, modifiedTimestamp);
	}

	/**
	 * Keeps documents that are Java sources, in scope, and stale in either the index or diagnostics cache.
	 */
	public DocumentDescriptor[] filterDocumentsNeedingRefresh(IJavaProject project, DocumentDescriptor[] updatedDocs,
			BiPredicate<IJavaProject, String> shouldProcessDocument) {
		return Arrays.stream(updatedDocs)
				.filter(doc -> doc.getDocURI().endsWith(".java"))
				.filter(doc -> shouldProcessDocument.test(project, doc.getDocURI()))
				.filter(doc -> isIndexOrDiagnosticsCacheOutdated(project, doc.getDocURI(), doc.getLastModified()))
				.toArray(DocumentDescriptor[]::new);
	}

	public void removeProjectCaches(IJavaProject project) {
		cache.remove(getCacheKey(project, INDEX_KEY));
		cache.remove(getCacheKey(project, DIAGNOSTICS_KEY));
	}

	public void removeFilesFromCaches(IJavaProject project, String[] absolutePaths) {
		cache.removeFiles(getCacheKey(project, INDEX_KEY), absolutePaths, CachedIndexElement.class);
		cache.removeFiles(getCacheKey(project, DIAGNOSTICS_KEY), absolutePaths, CachedDiagnostics.class);
	}

	public void updateAfterSingleFileScan(IJavaProject project, String file, long lastModified, SpringIndexerJavaScanResult result,
			Set<String> dependencies) {
		cache.update(getCacheKey(project, INDEX_KEY), file, lastModified, result.getGeneratedIndexElements(), dependencies, CachedIndexElement.class);
		cache.update(getCacheKey(project, DIAGNOSTICS_KEY), file, lastModified, result.getGeneratedDiagnostics(), dependencies, CachedDiagnostics.class);
	}

	public void updateAfterBatchScan(IJavaProject project, String[] javaFiles, long[] lastModified, SpringIndexerJavaScanResult result,
			Multimap<String, String> dependencies) {
		cache.update(getCacheKey(project, INDEX_KEY), javaFiles, lastModified, result.getGeneratedIndexElements(), dependencies, CachedIndexElement.class);
		cache.update(getCacheKey(project, DIAGNOSTICS_KEY), javaFiles, lastModified, result.getGeneratedDiagnostics(), dependencies, CachedDiagnostics.class);
	}

	public FullScanRetrieveResult retrieveForFullScan(IJavaProject project, String[] javaFiles) {
		Pair<CachedIndexElement[], Multimap<String, String>> indexElements = cache.retrieve(getCacheKey(project, INDEX_KEY), javaFiles,
				CachedIndexElement.class);
		Pair<CachedDiagnostics[], Multimap<String, String>> diagnostics = cache.retrieve(getCacheKey(project, DIAGNOSTICS_KEY), javaFiles,
				CachedDiagnostics.class);
		return new FullScanRetrieveResult(indexElements, diagnostics);
	}

	public void storeFullScanResults(IJavaProject project, String[] javaFiles, SpringIndexerJavaScanResult result,
			Multimap<String, String> allDependencies) {
		cache.store(getCacheKey(project, INDEX_KEY), javaFiles, result.getGeneratedIndexElements(), allDependencies, CachedIndexElement.class);
		cache.store(getCacheKey(project, DIAGNOSTICS_KEY), javaFiles, result.getGeneratedDiagnostics(), allDependencies, CachedDiagnostics.class);
	}

	public void updateDiagnosticsAfterReconcile(IJavaProject project, String[] javaFiles, long[] modificationTimestamps,
			List<CachedDiagnostics> diagnostics, Multimap<String, String> allDependencies) {
		cache.update(getCacheKey(project, DIAGNOSTICS_KEY), javaFiles, modificationTimestamps, diagnostics, allDependencies, CachedDiagnostics.class);
	}

	public record FullScanRetrieveResult(
			Pair<CachedIndexElement[], Multimap<String, String>> indexElements,
			Pair<CachedDiagnostics[], Multimap<String, String>> diagnostics) {
	}
}
