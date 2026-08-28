/*******************************************************************************
 * Copyright (c) 2018, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.java;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.util.CollectorUtil;

import com.google.common.collect.ImmutableList;

public class IClasspathUtil {

	private static final Logger log = LoggerFactory.getLogger(IClasspath.class);

	public static CPE findEntryForBinaryRoot(IClasspath cp, File binaryClasspathtRoot) {
		try {
			for (CPE cpe : cp.getClasspathEntries()) {
				if (correspondsToBinaryLocation(cpe, binaryClasspathtRoot)) {
					return cpe;
				}
			}
		} catch (Exception e) {
			log.error("", e);
		}
		return null;
	}

	public static List<File> getAllBinaryRoots(IClasspath cp) {
		return getBinaryRoots(cp, null);
	}

	public static List<File> getBinaryRoots(IClasspath cp, Predicate<CPE> filter) {
		ImmutableList.Builder<File> roots = ImmutableList.builder();
		try {
			for (CPE cpe : cp.getClasspathEntries()) {
				if (filter == null || filter.test(cpe)) {
					File loc = binaryLocation(cpe);
					if (loc != null) {
						roots.add(loc);
					}
				}
			}
		} catch (Exception e) {
			log.error("", e);
		}
		return roots.build();
	}

	private static boolean correspondsToBinaryLocation(CPE cpe, File classpathEntryFile) {
		File canonicalFile = binaryLocation(cpe);
		return Objects.equals(canonicalFile, classpathEntryFile);
	}

	public static File binaryLocation(CPE cpe) {
		switch (cpe.getKind()) {
		case Classpath.ENTRY_KIND_BINARY:
			return new File(cpe.getPath());
		case Classpath.ENTRY_KIND_SOURCE:
			return new File(cpe.getOutputFolder());
		default:
			throw new IllegalStateException("Missing switch case?");
		}
	}

	public static Stream<File> getSourceFolders(IClasspath classpath) {
		try {
			if (classpath != null) {
				return classpath.getClasspathEntries().stream().filter(Classpath::isSource)
						.map(cpe -> new File(cpe.getPath()));
			}
		} catch (Exception e) {
			log.error("", e);
		}
		return Stream.empty();
	}
	
	public static Stream<File> getProjectJavaSourceFolders(IClasspath classpath) {
		try {
			if (classpath != null) {
				return classpath.getClasspathEntries().stream().filter(Classpath::isProjectJavaSource)
						.map(cpe -> new File(cpe.getPath()));
			}
		} catch (Exception e) {
			log.error("", e);
		}
		return Stream.empty();
	}
	
	public static Stream<File> getProjectJavaSourceFoldersWithoutTests(IClasspath classpath) {
		try {
			if (classpath != null) {
				return classpath.getClasspathEntries().stream().filter(Classpath::isProjectNonTestJavaSource)
						.map(cpe -> new File(cpe.getPath()));
			}
		} catch (Exception e) {
			log.error("", e);
		}
		return Stream.empty();
	}
	
	public static Stream<File> getProjectTestJavaSources(IClasspath classpath) {
		try {
			if (classpath != null) {
				return classpath.getClasspathEntries().stream().filter(Classpath::isProjectTestJavaSource)
						.map(cpe -> new File(cpe.getPath()));
			}
		} catch (Exception e) {
			log.error("", e);
		}
		return Stream.empty();
	}

	public static Stream<File> getOutputFolders(IClasspath classpath) {
		try {
			return classpath.getClasspathEntries().stream()
					.filter(Classpath::isProjectSource)
					.map(cpe -> new File(cpe.getOutputFolder()));
		} catch (Exception e) {
			log.error("", e);
		}
		return Stream.empty();
	}

	public static Optional<URL> sourceContainer(IClasspath classpath, File binaryClasspathRoot) {
		CPE cpe = IClasspathUtil.findEntryForBinaryRoot(classpath, binaryClasspathRoot);
		return cpe == null ? Optional.empty() : Optional.ofNullable(cpe.getSourceContainerUrl());
	}

	/**
	 * Classpath resources paths relative to the source folder path
	 * @return classpath resource relative paths
	 */
	public static ImmutableList<String> getClasspathResources(IClasspath classpath) {
		return IClasspathUtil.getSourceFolders(classpath)
		.flatMap(folder -> {
			try {
				return Files.walk(folder.toPath())
						.filter(path -> Files.isRegularFile(path))
						.filter(path -> {
							try {
								return !Files.isHidden(path);
							}
							catch (IOException e) {
								return false;
							}
						})
						.map(path -> folder.toPath().relativize(path))
						.map(relativePath -> relativePath.toString())
						.filter(pathString -> !pathString.endsWith(".java") && !pathString.endsWith(".class"));
			} catch (IOException e) {
				return Stream.empty();
			}
		})
		.collect(CollectorUtil.toImmutableList());
	}
	
	public static Stream<Path> getClasspathResourcesFullPaths(IClasspath classpath) {
		return IClasspathUtil.getSourceFolders(classpath)
		.flatMap(folder -> {
			try {
				return Files.walk(folder.toPath())
						.filter(path -> Files.isRegularFile(path))
						.filter(path -> {
							String fileName = path.getFileName().toString();
							return !fileName.endsWith(".java") && !fileName.endsWith(".class");
						});
			} catch (IOException e) {
				return Stream.empty();
			}
		});
	}

}
