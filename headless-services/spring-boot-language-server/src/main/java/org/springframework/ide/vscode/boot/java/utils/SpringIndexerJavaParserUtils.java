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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jdt.core.JavaCore;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;

/**
 * Classpath resolution, Java compliance level, JDT {@link ASTParserCleanupEnabled} construction,
 * and batching helpers used by {@link SpringIndexerJava}.
 *
 * @author Martin Lippert
 */
public class SpringIndexerJavaParserUtils {

	public static ASTParserCleanupEnabled createParser(IJavaProject project, AnnotationHierarchies annotationHierarchies, boolean ignoreMethodBodies)
			throws Exception {
		String[] classpathEntries = getClasspathEntries(project);
		String[] sourceEntries = getSourceEntries(project);
		String complianceJavaVersion = getComplianceJavaVersion(
				project.getClasspath().getJre() == null ? null : project.getClasspath().getJre().version());

		return new ASTParserCleanupEnabled(classpathEntries, sourceEntries, complianceJavaVersion, annotationHierarchies, ignoreMethodBodies);
	}

	public static String getComplianceJavaVersion(String javaVersion) {
		String complianceLevel = extractComplianceVersion(javaVersion);
		// Currently the java version in the classpath seems to be 1.8, 17, 21 etc.
		return complianceLevel == null || complianceLevel.isBlank() ? JavaCore.VERSION_25 : complianceLevel;
	}

	private static String extractComplianceVersion(String versionString) {
		if (versionString != null && versionString.contains(".")) {
			String[] parts = versionString.split("\\.");
			if ("1".equals(parts[0])) {
				if (parts.length > 1) {
					return "%s.%s".formatted(parts[0], parts[1]);
				}
			}
			else {
				String version = parts[0];
				int idx = version.indexOf('+');
				return idx >= 0 ? version.substring(0, idx) : version;
			}
		}
		return null;
	}

	public static String[] getClasspathEntries(IJavaProject project) throws Exception {
		IClasspath classpath = project.getClasspath();
		Stream<File> classpathEntries = IClasspathUtil.getAllBinaryRoots(classpath).stream();
		return classpathEntries
				.filter(file -> file.exists())
				.map(file -> file.getAbsolutePath())
				.toArray(String[]::new);
	}

	public static String[] getSourceEntries(IJavaProject project) throws Exception {
		IClasspath classpath = project.getClasspath();
		Stream<File> sourceEntries = IClasspathUtil.getSourceFolders(classpath);
		return sourceEntries
				.filter(file -> file.exists())
				.map(file -> file.getAbsolutePath())
				.toArray(String[]::new);
	}

	public static List<String[]> createChunks(String[] sourceArray, int chunkSize) {
		int numberOfChunks = (int) Math.ceil((double) sourceArray.length / chunkSize);
		List<String[]> result = new ArrayList<>();

		for (int i = 0; i < numberOfChunks; i++) {
			int startIdx = i * chunkSize;
			int endIdx = Math.min(startIdx + chunkSize, sourceArray.length);

			String[] chunk = new String[endIdx - startIdx];
			System.arraycopy(sourceArray, startIdx, chunk, 0, endIdx - startIdx);

			result.add(chunk);
		}

		return result;
	}
}
