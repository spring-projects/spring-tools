/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;

public class SpringProjectUtil {

	public static final String SPRING_BOOT = "spring-boot";
	public static final String SPRING_WEB = "spring-web";
	
	private static final String GENERATION_VERSION_STR = "([0-9]+)";

	public static final Logger log = LoggerFactory.getLogger(SpringProjectUtil.class);
		
	private static final Pattern GENERATION_VERSION = Pattern.compile(GENERATION_VERSION_STR);
	
	public static boolean isSpringProject(IJavaProject jp) {
		return jp.getClasspath().findBinaryLibraryByPrefix("spring-core").isPresent();
	}

	public static boolean isBootProject(IJavaProject jp) {
		return jp.getClasspath().findBinaryLibraryByPrefix(SPRING_BOOT).isPresent();
	}

	public static boolean hasBootActuators(IJavaProject jp) {
		return jp.getClasspath().findBinaryLibraryByPrefix("spring-boot-actuator-").isPresent();
	}
	
	/**
	 *  Parses version from the given generation name (e.g. "2.1.x"
	 * @param name
	 * @return Version if valid generation name with major and minor components
	 * @throws Exception if invalid generation name
	 */
	public static Version getVersionFromGeneration(String name) throws Exception {
		Matcher matcher = GENERATION_VERSION.matcher(name);
		String major = null;
		String minor = null;
		
		if (matcher.find()) {
			int start = matcher.start();
			int end = matcher.end();
			major =  name.substring(start, end);
		}
		
		if (matcher.find()) {
			int start = matcher.start();
			int end = matcher.end();
			minor =  name.substring(start, end);
		}
		
		if (major != null && minor != null) {
			return new Version(
					Integer.parseInt(major),
					Integer.parseInt(minor),
					0,
					null
			);
		}

		throw new IllegalArgumentException("Invalid semver. Unable to parse major and minor version from: " + name);
	}

	public static Version getDependencyVersionByPrefix(IJavaProject jp, String dependencyPrefix) {
		return jp.getClasspath().findBinaryLibraryByPrefix(dependencyPrefix).map(cpe -> cpe.getVersion()).orElse(null);
	}
	
	public static Version getDependencyVersionByName(IJavaProject jp, String name) {		
		return jp.getClasspath().findBinaryLibraryByName(name).map(cpe -> cpe.getVersion()).orElse(null);
	}
	
	public static boolean hasDependencyStartingWith(IJavaProject jp, String dependency, Predicate<CPE> filter) {
		IClasspath classpath = jp.getClasspath();
		return classpath.findBinaryLibraryByPrefix(dependency).or(() -> {
			try {
				for (CPE cpe : classpath.getClasspathEntries()) {
					if (filter == null || filter.test(cpe)) {
						if (Classpath.ENTRY_KIND_SOURCE.equals(cpe.getKind()) && !cpe.isOwn()) {
							if (cpe.getExtra() != null && cpe.getExtra().containsKey("project")) {
								if (new File(cpe.getExtra().get("project")).getName().startsWith(dependency)) {
									return Optional.of(cpe);
								}
							} else {
								if (new File(cpe.getPath()).getName().startsWith(dependency)) {
									return Optional.of(cpe);
								}
							}
						}
					}
				}
			} catch (Exception e) {
				log.error("", e);
			}
			return Optional.empty();
		}).isPresent();
	}

	public static Version getSpringBootVersion(IJavaProject jp) {
		return getDependencyVersionByName(jp, SPRING_BOOT);
	}
	
	public static Predicate<IJavaProject> springBootVersionGreaterOrEqual(int major, int minor, int patch) {
		return libraryVersionGreaterOrEqual(SPRING_BOOT, major, minor, patch);
	}

	public static Predicate<IJavaProject> libraryVersionGreaterOrEqual(String libraryName, int major, int minor, int patch) {
		return project -> {
			Version version = getDependencyVersionByName(project, libraryName);
			if (version == null) {
				return false;
			}
			if (major > version.getMajor()) {
				return false;
			}
			if (major == version.getMajor()) {
				if (minor > version.getMinor()) {
					return false;
				}
				if (minor == version.getMinor()) {
					return patch <= version.getPatch();
				}
			}
			return true;
		};
	}
	
	/**
	 * Finds Spring Boot application.properties and application.yml files in the project's resource folders.
	 * Prioritizes main application.properties/yml files over profile-specific files (application-*.properties/yml).
	 * Excludes test resources.
	 * 
	 * @param project the Java project
	 * @return list of Boot properties/YAML file paths, with main files prioritized over profile-specific files
	 */
	public static List<Path> findBootPropertiesFiles(IJavaProject project) {
		try {
			return project.getClasspath().getClasspathEntries().stream()
				.filter(Classpath::isSource)
				.filter(cpe -> !cpe.isTest())  // Exclude test resources
				.filter(cpe -> !cpe.isJavaContent())  // Only resource folders
				.map(cpe -> new File(cpe.getPath()).toPath())
				.flatMap(folder -> {
					try {
						return Files.list(folder);
					} catch (IOException e) {
						return Stream.empty();
					}
				})
				.filter(path -> path.getFileName().toString().matches("application(-.*)?\\.(properties|ya?ml)"))
				.toList();
		} catch (Exception e) {
			// ignore
			return Collections.emptyList();
		}
		
	}
	

}
