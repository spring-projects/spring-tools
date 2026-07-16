/*******************************************************************************
 * Copyright (c) 2018, 2026 Pivotal, Inc.
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
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;

/**
 * Keeps cache of system libs Jandex Indexes
 *
 * @author Alex Boyko
 *
 */
public class JandexSystemLibsIndex {

//	private static final String DEFAULT_JAVA_VERSION = "1.8.0";
//
//	private static final Pattern JAVA_VERSION_PATTERN = Pattern.compile("^java version \"(.*)\"$");

	public static final Logger log = LoggerFactory.getLogger(JandexSystemLibsIndex.class);

	private static final Supplier<JandexSystemLibsIndex> INSTANCE = Suppliers.memoize(() -> new JandexSystemLibsIndex());

	private LoadingCache<Path, ImmutableList<ModuleJandexIndex>> indexCache;

	private JandexSystemLibsIndex() {
		this.indexCache = CacheBuilder.newBuilder().build(new CacheLoader<Path, ImmutableList<ModuleJandexIndex>>() {

			@Override
			public ImmutableList<ModuleJandexIndex> load(Path path) throws Exception {
				File file = path.toFile();
				return IndexRoutines.fromClasspathBinaryEntry(file, findIndexFile(path));
			}

		});
	}

	public static JandexSystemLibsIndex getInstance() {
		return INSTANCE.get();
	}

	private File findIndexFile(Path path) {
		File locationFolder = new File(BasicJandexIndex.getIndexFolder(), JandexClasspath.digestPath(path.getParent().toString()));
		return new File(locationFolder, path.getFileName() + ".jdx");
	}

	public ImmutableList<ModuleJandexIndex> index(Path path) {
		try {
			return indexCache.get(path);
		} catch (ExecutionException e) {
			log.error("", e);
			return null;
		}
	}

//	private static String getJavaVersion(Path path) {
//		// Find valid /bin folder
//		for (; path != null && !(Files.isDirectory(path.resolve("bin")) && Files.isReadable(path.resolve("bin"))); path = path.getParent());
//		// If found, assume it's the java home bin folder
//		if (path != null) {
//			Path javaBin = path.resolve("bin");
//			try {
//				Process p = new ProcessBuilder().directory(javaBin.toFile()).command("./java", "-version").start();
//				BufferedReader buffer = new BufferedReader(new InputStreamReader(p.getErrorStream()));
//				int exitCode = p.waitFor();
//				if (exitCode == 0) {
//					return buffer.lines().map(l -> JAVA_VERSION_PATTERN.matcher(l)).filter(m -> m.find()).findFirst().map(m -> m.group(1)).orElse(DEFAULT_JAVA_VERSION);
//				} else {
//					log.error("Failed to compute java version in folder: " + javaBin + ". 'java -version' exit code is " + exitCode);
//				}
//			} catch (IOException | InterruptedException e) {
//				log.error("Failed to compute java version in folder: " + javaBin, e);
//			}
//		}
//		return DEFAULT_JAVA_VERSION;
//	}
//
//	private static String extractVersionForJavadoc(String javaVersion) {
//		if (javaVersion.startsWith("1.")) {
//			int idx = javaVersion.indexOf('.', 2);
//			return idx >= 0 ? javaVersion.substring(2, idx) : javaVersion.substring(2);
//		} else {
//			int idx = javaVersion.indexOf('.');
//			return idx >= 0 ? javaVersion.substring(0, idx) : javaVersion;
//		}
//	}

}
