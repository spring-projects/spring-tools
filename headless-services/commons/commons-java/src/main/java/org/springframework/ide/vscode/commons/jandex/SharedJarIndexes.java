/*******************************************************************************
 * Copyright (c) 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.jandex;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Process wide cache of the Jandex index of a single jar, shared by all projects that have that jar on their classpath.
 * <p>
 * Thread safety:
 * <ul>
 * <li>The cache itself is a Guava cache, i.e. safe for concurrent use; for a given key the {@code ModuleJandexIndex}
 * is created exactly once even when many projects ask for it at the same time.</li>
 * <li>Creating the {@code ModuleJandexIndex} is cheap; the expensive part (reading/creating the index, which also
 * writes the .jdx file) happens in the {@link RetryingMemoizedSupplier} inside of it, which lets exactly one thread do
 * the work while others wait, so a jar is never indexed twice concurrently within this process. No lock is held while
 * indexing other jars, so different jars index in parallel. Across processes the .jdx file is protected by being
 * written to a temp file and moved into place.</li>
 * <li>A Jandex {@code Index} is immutable once built, so any number of threads can query it concurrently.</li>
 * </ul>
 * Entries are weakly held: they live as long as at least one project's classpath refers to the index and are
 * collected afterwards. The key contains path, modification time and size of the jar, so a rebuilt jar gets a new
 * entry rather than a stale one.
 * <p>
 * Sharing can be switched off with {@code -Djandex.sharedJarIndexes=false}.
 *
 * @author Martin Lippert
 */
final class SharedJarIndexes {

	private record Key(String jarPath, long lastModified, long length, String indexFile) {}

	private static final Cache<Key, ModuleJandexIndex> CACHE = CacheBuilder.newBuilder().weakValues().build();

	private SharedJarIndexes() {
	}

	static boolean isEnabled() {
		return !"false".equals(System.getProperty("jandex.sharedJarIndexes"));
	}

	static ModuleJandexIndex get(File jar, File indexFile, Supplier<ModuleJandexIndex> factory) {
		if (!isEnabled()) {
			return factory.get();
		}
		Key key = new Key(canonicalPath(jar), jar.lastModified(), jar.length(),
				indexFile == null ? null : indexFile.getAbsolutePath());
		try {
			return CACHE.get(key, factory::get);
		} catch (ExecutionException e) {
			throw new IllegalStateException(e.getCause());
		}
	}

	private static String canonicalPath(File file) {
		try {
			return file.getCanonicalPath();
		} catch (IOException e) {
			return file.getAbsolutePath();
		}
	}

}
