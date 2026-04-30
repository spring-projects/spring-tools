/*******************************************************************************
 * Copyright (c) 2020, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.index.cache.test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.ide.vscode.boot.index.cache.IndexCache;
import org.springframework.ide.vscode.boot.index.cache.IndexCacheKey;
import org.springframework.ide.vscode.boot.index.cache.IndexCacheable;
import org.springframework.ide.vscode.boot.java.utils.QualifiedTypeName;
import org.springframework.ide.vscode.boot.java.utils.SourceJavaFile;

import com.google.common.collect.Multimap;

/**
 * @author Martin Lippert
 */
public class IndexCacheTimestampsOnly implements IndexCache {
	
	private Map<IndexCacheKey, Map<String, Long>> timestampCache; 
	
	public IndexCacheTimestampsOnly() {
		this.timestampCache = new HashMap<>();
	}

	@Override
	public <T extends IndexCacheable> void store(IndexCacheKey cacheKey, String[] files, List<T> generatedSymbols, Multimap<SourceJavaFile, QualifiedTypeName> dependencies, Class<T> type) {
		SortedMap<String, Long> timestampedFiles = new TreeMap<>();

		timestampedFiles = Arrays.stream(files)
				.filter(file -> new File(file).exists())
				.collect(Collectors.toMap(file -> file, file -> {
					try {
						return Files.getLastModifiedTime(new File(file).toPath()).toMillis();
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}, (v1,v2) -> { throw new RuntimeException(String.format("Duplicate key for values %s and %s", v1, v2));}, TreeMap::new));

		timestampCache.put(cacheKey, timestampedFiles);
	}

	@Override
	public <T extends IndexCacheable> Pair<T[], Multimap<SourceJavaFile, QualifiedTypeName>> retrieve(IndexCacheKey cacheKey, String[] files, Class<T> type) {
		return null;
	}

	@Override
	public <T extends IndexCacheable> void update(IndexCacheKey cacheKey, String file, long lastModified, List<T> generatedSymbols, Set<QualifiedTypeName> dependencies, Class<T> type) {
		Map<String, Long> timestampMap = timestampCache.get(cacheKey);
		timestampMap.put(file, lastModified);
	}

	@Override
	public <T extends IndexCacheable> void update(IndexCacheKey cacheKey, String[] files, long[] lastModified, List<T> generatedSymbols, Multimap<SourceJavaFile, QualifiedTypeName> dependencies, Class<T> type) {
		Map<String, Long> timestampMap = timestampCache.get(cacheKey);

		for (int i = 0; i < files.length; i++) {
			timestampMap.put(files[i], lastModified[i]);
		}
	}

	@Override
	public void remove(IndexCacheKey cacheKey) {
	}

	@Override
	public <T extends IndexCacheable> void removeFile(IndexCacheKey symbolCacheKey, String file, Class<T> type) {
	}

	@Override
	public <T extends IndexCacheable> void removeFiles(IndexCacheKey symbolCacheKey, String[] files, Class<T> type) {
	}

	@Override
	public long getModificationTimestamp(IndexCacheKey cacheKey, String file) {
		Map<String, Long> timestampMap = timestampCache.get(cacheKey);
		if (timestampMap != null) {
			Long timestamp = timestampMap.get(file);
			if (timestamp != null) {
				return timestamp;
			}
		}
		
		return 0;
	}

}
