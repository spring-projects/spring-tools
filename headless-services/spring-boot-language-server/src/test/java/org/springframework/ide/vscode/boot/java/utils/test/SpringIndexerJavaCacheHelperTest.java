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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ide.vscode.boot.index.cache.IndexCache;
import org.springframework.ide.vscode.boot.index.cache.IndexCacheKey;
import org.springframework.ide.vscode.boot.java.beans.CachedIndexElement;
import org.springframework.ide.vscode.boot.java.reconcilers.CachedDiagnostic;
import org.springframework.ide.vscode.boot.java.utils.DocumentDescriptor;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaCacheHelper;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.util.UriUtil;

import com.google.common.collect.ArrayListMultimap;
import com.google.gson.JsonObject;

/**
 * Unit tests for {@link SpringIndexerJavaCacheHelper}.
 */
class SpringIndexerJavaCacheHelperTest {

	private IndexCache cache;
	private SpringIndexerJavaCacheHelper helper;
	private IJavaProject project;

	@BeforeEach
	void setUp() throws Exception {
		cache = mock(IndexCache.class);
		JsonObject settings = new JsonObject();
		settings.addProperty("severity", "warning");
		helper = new SpringIndexerJavaCacheHelper(cache, "GEN-TEST", settings);

		project = mock(IJavaProject.class);
		when(project.getElementName()).thenReturn("test-project");
		IClasspath classpath = mock(IClasspath.class);
		when(classpath.getClasspathEntries()).thenReturn(Collections.emptyList());
		when(project.getClasspath()).thenReturn(classpath);
	}

	@Test
	void isCacheOutdated_trueWhenDocumentNewerThanCached() {
		IndexCacheKey key = new IndexCacheKey("p", "java", "index", "v1");
		File f = new File("SomeFile.java");
		String docUri = UriUtil.toUri(f).toASCIIString();
		String filePath = f.getAbsolutePath();
		when(cache.getModificationTimestamp(eq(key), eq(filePath))).thenReturn(100L);

		assertTrue(helper.isCacheOutdated(key, docUri, 200L));
		assertFalse(helper.isCacheOutdated(key, docUri, 100L));
		assertFalse(helper.isCacheOutdated(key, docUri, 50L));
	}

	@Test
	void isIndexOrDiagnosticsCacheOutdated_trueIfEitherStale() {
		File f = new File("Stale.java");
		String docUri = UriUtil.toUri(f).toASCIIString();
		String filePath = f.getAbsolutePath();
		when(cache.getModificationTimestamp(any(IndexCacheKey.class), eq(filePath))).thenAnswer(invocation -> {
			IndexCacheKey k = invocation.getArgument(0);
			if (SpringIndexerJavaCacheHelper.INDEX_KEY.equals(k.getCategory())) {
				return 100L;
			}
			return 500L;
		});

		assertTrue(helper.isIndexOrDiagnosticsCacheOutdated(project, docUri, 200L));

		when(cache.getModificationTimestamp(any(IndexCacheKey.class), eq(filePath))).thenReturn(300L);
		assertFalse(helper.isIndexOrDiagnosticsCacheOutdated(project, docUri, 200L));
	}

	@Test
	void filterDocumentsNeedingRefresh_filtersNonJava_andShouldProcess_andStale(@TempDir Path dir) throws Exception {
		File javaFile = dir.resolve("Source.java").toFile();
		assertTrue(javaFile.createNewFile());
		String javaPath = javaFile.getAbsolutePath();
		String javaUri = UriUtil.toUri(javaFile).toASCIIString();

		File xmlFile = dir.resolve("data.xml").toFile();
		assertTrue(xmlFile.createNewFile());

		when(cache.getModificationTimestamp(any(IndexCacheKey.class), eq(javaPath))).thenReturn(10L);

		DocumentDescriptor javaDoc = DocumentDescriptor.createFromFile(javaPath, 100L);
		DocumentDescriptor xmlDoc = DocumentDescriptor.createFromFile(xmlFile.getAbsolutePath(), 100L);

		DocumentDescriptor[] out = helper.filterDocumentsNeedingRefresh(project,
				new DocumentDescriptor[] { javaDoc, xmlDoc }, (p, uri) -> uri.endsWith("Source.java"));
		assertEquals(1, out.length);
		assertEquals(javaUri, out[0].getDocURI());

		out = helper.filterDocumentsNeedingRefresh(project,
				new DocumentDescriptor[] { javaDoc }, (p, uri) -> false);
		assertEquals(0, out.length);
	}

	@Test
	void getCacheKey_sameProjectAndSettings_producesEqualKeysPerCategory() {
		IndexCacheKey index = helper.getCacheKey(project, SpringIndexerJavaCacheHelper.INDEX_KEY);
		IndexCacheKey index2 = helper.getCacheKey(project, SpringIndexerJavaCacheHelper.INDEX_KEY);
		assertEquals(index, index2);

		IndexCacheKey diag = helper.getCacheKey(project, SpringIndexerJavaCacheHelper.DIAGNOSTICS_KEY);
		assertNotEquals(index, diag);
		assertEquals(SpringIndexerJavaCacheHelper.INDEX_KEY, index.getCategory());
		assertEquals(SpringIndexerJavaCacheHelper.DIAGNOSTICS_KEY, diag.getCategory());
	}

	@Test
	void setValidationSeveritySettings_changesCacheKeyVersion() {
		IndexCacheKey before = helper.getCacheKey(project, SpringIndexerJavaCacheHelper.INDEX_KEY);
		JsonObject other = new JsonObject();
		other.addProperty("severity", "error");
		helper.setValidationSeveritySettings(other);
		IndexCacheKey after = helper.getCacheKey(project, SpringIndexerJavaCacheHelper.INDEX_KEY);
		assertNotEquals(before.getVersion(), after.getVersion());
	}

	@Test
	void removeProjectCaches_removesIndexAndDiagnostics() {
		helper.removeProjectCaches(project);
		ArgumentCaptor<IndexCacheKey> captor = ArgumentCaptor.forClass(IndexCacheKey.class);
		verify(cache, times(2)).remove(captor.capture());
		List<IndexCacheKey> keys = captor.getAllValues();
		assertEquals(SpringIndexerJavaCacheHelper.INDEX_KEY, keys.get(0).getCategory());
		assertEquals(SpringIndexerJavaCacheHelper.DIAGNOSTICS_KEY, keys.get(1).getCategory());
	}

	@Test
	void removeFilesFromCaches_delegatesForBothCaches() {
		String[] paths = { "/a/A.java", "/b/B.java" };
		helper.removeFilesFromCaches(project, paths);
		verify(cache).removeFiles(any(IndexCacheKey.class), eq(paths), eq(CachedIndexElement.class));
		verify(cache).removeFiles(any(IndexCacheKey.class), eq(paths), eq(CachedDiagnostic.class));
	}

	@Test
	void retrieveForFullScan_delegatesToCache() {
		String[] files = { "/x/X.java" };
		when(cache.retrieve(any(IndexCacheKey.class), eq(files), eq(CachedIndexElement.class))).thenReturn(null);
		when(cache.retrieve(any(IndexCacheKey.class), eq(files), eq(CachedDiagnostic.class))).thenReturn(null);

		var result = helper.retrieveForFullScan(project, files);
		assertNull(result.indexElements());
		assertNull(result.diagnostics());
		verify(cache).retrieve(any(IndexCacheKey.class), eq(files), eq(CachedIndexElement.class));
		verify(cache).retrieve(any(IndexCacheKey.class), eq(files), eq(CachedDiagnostic.class));
	}

	@Test
	void updateDiagnosticsAfterReconcile_delegatesToCache() {
		String[] javaFiles = { "/p/T.java" };
		long[] ts = { 42L };
		List<CachedDiagnostic> diags = List.of();
		var deps = ArrayListMultimap.<String, String>create();
		helper.updateDiagnosticsAfterReconcile(project, javaFiles, ts, diags, deps);
		verify(cache).update(any(IndexCacheKey.class), eq(javaFiles), eq(ts), eq(diags), eq(deps), eq(CachedDiagnostic.class));
	}
}
