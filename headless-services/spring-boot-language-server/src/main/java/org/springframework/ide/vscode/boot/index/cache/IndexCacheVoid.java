/*******************************************************************************
 * Copyright (c) 2019, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.index.cache;

import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Multimap;

/**
 * @author Martin Lippert
 */
public class IndexCacheVoid implements IndexCache {

	@Override
	public <T extends IndexCacheable> void store(IndexCacheKey cacheKey, String[] files, List<T> generatedSymbols, Multimap<String, String> dependencies, Class<T> type) {
	}

	@Override
	public <T extends IndexCacheable> Pair<T[], Multimap<String, String>> retrieve(IndexCacheKey cacheKey, String[] files, Class<T> type) {
		return null;
	}

	@Override
	public <T extends IndexCacheable> void update(IndexCacheKey cacheKey, String file, long lastModified, List<T> generatedSymbols, Set<String> dependencies, Class<T> type) {
	}

	@Override
	public <T extends IndexCacheable> void update(IndexCacheKey cacheKey, String[] files, long[] lastModified, List<T> generatedSymbols, Multimap<String, String> dependencies, Class<T> type) {
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
	public long getModificationTimestamp(IndexCacheKey cacheKey, String docURI) {
		return -1;
	}

}
