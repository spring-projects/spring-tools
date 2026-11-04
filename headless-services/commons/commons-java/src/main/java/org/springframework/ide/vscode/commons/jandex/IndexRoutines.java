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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.stream.Stream;

import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.IndexWriter;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.JarIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;

public class IndexRoutines {

	private static final Logger log = LoggerFactory.getLogger(IndexRoutines.class);

	private static final URI JRT_URI = URI.create("jrt:/");

	static ImmutableList<ModuleJandexIndex> fromClasspathBinaryEntry(File file, File indexFile) {
		ImmutableList.Builder<ModuleJandexIndex> builder = ImmutableList.builder();
		if (file != null) {
			if (file.isFile()) {
				if (file.getName().endsWith("jrt-fs.jar")) {
					builder.addAll(IndexRoutines.fromJrtFs(file, indexFile));
				} else if (file.getName().endsWith(".jar")) {
					builder.add(IndexRoutines.fromJar(file, indexFile));
				}
			} else if (file.isDirectory()) {
				builder.add(IndexRoutines.fromFolder(file));
			}
		}
		return builder.build();
	}

	static ImmutableList<ModuleJandexIndex> fromCPE(CPE cpe, File indexFile) {
		File binaryLocation = IClasspathUtil.binaryLocation(cpe);
		if (cpe.isSystem()) {
			return JandexSystemLibsIndex.getInstance().index(binaryLocation.toPath());
		} else {
			return IndexRoutines.fromClasspathBinaryEntry(binaryLocation, indexFile);
		}
	}

	private static ModuleJandexIndex fromJar(File file, File indexFile) {
		return SharedJarIndexes.get(file, indexFile,
				() -> new ModuleJandexIndex(file, null, new RetryingMemoizedSupplier<>(() -> indexJar(file, indexFile))));
	}

	private static ModuleJandexIndex fromFolder(File folder) {
		return new ModuleJandexIndex(folder, null, Suppliers.memoize(() -> indexFolder(folder)));
	}

	private static ModuleJandexIndex fromModule(File container, Path modulePath, File indexFolder) {
		String module = modulePath.getFileName().toString();
		File indexFile = new File(indexFolder, module + ".jdx");
		return new ModuleJandexIndex(container, module, new RetryingMemoizedSupplier<>(() -> indexModule(modulePath, indexFile)));
	}

	private static ImmutableList<ModuleJandexIndex> fromJrtFs(File jrtFsJar, File indexFolder) {
		// little hack for backwards compatibility
		if (indexFolder.isFile()) {
			indexFolder.delete();
		}

		ImmutableList.Builder<ModuleJandexIndex> builder = ImmutableList.builder();
		String javaVersion = System.getProperty("java.version"); //$NON-NLS-1$
		FileSystem fs = null;
		Path jdkHome = jrtFsJar.toPath().getParent().getParent();
		try {
			if (javaVersion != null && javaVersion.startsWith("1.8")) { //$NON-NLS-1$
				URLClassLoader loader = new URLClassLoader(new URL[] { jrtFsJar.toURI().toURL() });
				HashMap<String, ?> env = new HashMap<>();
				fs = FileSystems.newFileSystem(JRT_URI, env, loader);
			} else {
				HashMap<String, String> env = new HashMap<>();
				env.put("java.home", jdkHome.toString()); //$NON-NLS-1$
				fs = FileSystems.newFileSystem(JRT_URI, env);
			}
			if (fs != null) {
				try (Stream<Path> modules = Files.list(fs.getPath("/modules"))) {
					modules.filter(Files::isDirectory).forEach(path -> builder.add(fromModule(jrtFsJar, path, indexFolder)));
				}
//				builder.add(fromModule(jrtFsJar, fs.getPath("modules", "java.base"), indexFolder));
			}
		} catch (IOException e) {
			log.error("", e);
		}
		return builder.build();
	}

	private static IndexView indexModule(Path modulePath, File indexFile) {
		return createOrLoadIndex(indexFile, () -> createModuleIndex(modulePath, indexFile));
	}

	private static IndexView createModuleIndex(Path modulePath, File indexFile) {
		try {
			Indexer indexer = new Indexer();
			try (Stream<Path> entries = Files.walk(modulePath)) {
				entries.forEach(entry -> {
					if (entry.getFileName().toString().endsWith(".class")) {
						try (InputStream stream = Files.newInputStream(entry)) {
							indexer.index(stream);
						} catch (Exception e) {
							log.debug("", e);
						}
					}
				});
			}
			Index index = indexer.complete();
			return writeAtomically(indexFile, tmpFile -> {
				try (FileOutputStream out = new FileOutputStream(tmpFile)) {
					new IndexWriter(out).write(index);
				}
				return index;
			});
		} catch (IOException e) {
			log.error("", e);
		}
		return null;
	}

	private static IndexView indexFolder(File folder) {
		Indexer indexer = new Indexer();
		for (Iterator<File> itr = com.google.common.io.Files.fileTraverser().breadthFirst(folder)
				.iterator(); itr.hasNext();) {
			File file = itr.next();
			if (file.isFile() && file.getName().endsWith(".class")) {
				try {
					final InputStream stream = new FileInputStream(file);
					try {
						indexer.index(stream);
					} finally {
						try {
							stream.close();
						} catch (Exception ignore) {
						}
					}
				} catch (Exception e) {
					log.error("Failed to index file " + file, e);
				}
			}
		}
		return indexer.complete();
	}

	private static IndexView indexJar(File file, File indexFile) {
		return createOrLoadIndex(indexFile, () -> createJarIndex(indexFile, file));
	}

	private static IndexView createJarIndex(File indexFile, File jarFile) {
		try {
			return writeAtomically(indexFile, tmpFile -> JarIndexer.createJarIndex(jarFile, new Indexer(), tmpFile, false,
					false, false, new PrintStream(new ByteArrayOutputStream()), System.err).getIndex());
		} catch (Exception e) {
			log.error("Failed to index '" + jarFile + "'", e);
			return null;
		}
	}

	@FunctionalInterface
	interface IndexFileWriter {
		Index write(File tmpFile) throws IOException;
	}

	/**
	 * Lets the writer produce the index file under a temporary name in the target folder and then moves it into place,
	 * so that readers (other threads or other language server processes sharing the same index folder) never see a
	 * partially written index file.
	 */
	static Index writeAtomically(File indexFile, IndexFileWriter writer) throws IOException {
		File folder = indexFile.getParentFile();
		folder.mkdirs();
		Path tmp = Files.createTempFile(folder.toPath(), indexFile.getName() + "-", ".tmp");
		try {
			Index index = writer.write(tmp.toFile());
			try {
				Files.move(tmp, indexFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, indexFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				// e.g. on Windows the target can't be replaced while another process reads it. If someone else
				// managed to write it, theirs is as good as ours.
				if (!indexFile.isFile()) {
					throw e;
				}
				log.debug("Index file '" + indexFile + "' was written concurrently, keeping the existing one", e);
			}
			return index;
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	private static IndexView createOrLoadIndex(File indexFile, Supplier<IndexView> indexCreator) {
		if (indexFile != null) {
			if (!indexFile.getParentFile().exists()) {
				indexFile.getParentFile().mkdirs();
			}
			if (!indexFile.exists()) {
				return indexCreator.get();
			} else {
				try (FileInputStream in = new FileInputStream(indexFile)) {
					IndexView index = new IndexReader(in).read();
					IndexCacheCleanup.markUsed(indexFile);
					return index;
				} catch (FileNotFoundException e) {
					// removed in the meantime, e.g. by the cache clean-up of another process
					return indexCreator.get();
				} catch (IOException e) {
					log.error("Failed to read index file '" + indexFile + "'. Creating new index file.", e);
					if (indexFile.delete()) {
						return createOrLoadIndex(indexFile, indexCreator);
					} else {
						log.error("Failed to read index file '" + indexFile);
					}
				}
			}
		}
		return null;
	}

}
