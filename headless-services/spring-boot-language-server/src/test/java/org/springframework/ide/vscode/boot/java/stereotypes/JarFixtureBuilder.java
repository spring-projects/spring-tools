/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.stereotypes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Compiles a handful of {@code .java} sources and packs some of the result into real
 * {@code .jar} files, so JAR-scanning tests (see {@link JarStereotypeScanner}) can exercise real
 * bytecode rather than hand-built {@code ClassInfo}s.
 *
 * <p>Splitting classes across several JARs still needs them all compiled <em>together</em> (a
 * class in one jar may reference a class meant for another) - hence {@link #compileAll} and
 * {@link #packageJar} being separate steps, the former shared by however many of the latter a test
 * needs.
 *
 * @author Martin Lippert
 */
public class JarFixtureBuilder {

	/**
	 * Compiles every given source together into one shared output directory, so classes destined
	 * for different JARs can still reference one another.
	 *
	 * @param sourcesByFqn Java source, keyed by the fully qualified type name it declares
	 */
	public static Path compileAll(Path tempDir, Map<String, String> sourcesByFqn) throws IOException {
		Path classesDir = Files.createDirectories(tempDir.resolve("classes"));

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			throw new IllegalStateException("no system Java compiler available - tests need to run on a JDK, not a JRE");
		}

		List<JavaFileObject> sources = new ArrayList<>();
		for (Map.Entry<String, String> entry : sourcesByFqn.entrySet()) {
			sources.add(new StringSource(entry.getKey(), entry.getValue()));
		}

		StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
		ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
		boolean success = compiler.getTask(new PrintWriter(diagnostics), fileManager, null,
				List.of("-d", classesDir.toString()), null, sources).call();

		if (!success) {
			throw new IllegalStateException("failed to compile jar fixture sources:\n" + diagnostics);
		}

		return classesDir;
	}

	/**
	 * Packages the given top-level types (and any of their nested types, e.g. {@code Outer$Inner})
	 * out of {@code classesDir} into a new jar, together with the given extra resource files.
	 */
	public static File packageJar(Path classesDir, Path tempDir, String jarName, Iterable<String> topLevelFqns, Map<String, String> resources)
			throws IOException {

		File jarFile = tempDir.resolve(jarName + ".jar").toFile();
		try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(jarFile))) {
			for (String fqn : topLevelFqns) {
				String relativePrefix = fqn.replace('.', '/');
				Files.walk(classesDir)
						.filter(Files::isRegularFile)
						.filter(path -> {
							String entryName = classesDir.relativize(path).toString().replace(File.separatorChar, '/');
							String withoutExtension = entryName.substring(0, entryName.length() - ".class".length());
							return withoutExtension.equals(relativePrefix) || withoutExtension.startsWith(relativePrefix + "$");
						})
						.forEach(classFile -> addEntry(jar, classesDir.relativize(classFile).toString().replace(File.separatorChar, '/'), classFile));
			}

			for (Map.Entry<String, String> resource : resources.entrySet()) {
				jar.putNextEntry(new JarEntry(resource.getKey()));
				jar.write(resource.getValue().getBytes(StandardCharsets.UTF_8));
				jar.closeEntry();
			}
		}

		return jarFile;
	}

	/**
	 * Compiles the given sources and packages every one of them into a single jar - the common
	 * case of a test that has no need to split classes across several JARs.
	 */
	public static File buildJar(Path tempDir, String jarName, Map<String, String> sourcesByFqn, Map<String, String> resources) throws IOException {
		Path classesDir = compileAll(tempDir, sourcesByFqn);
		return packageJar(classesDir, tempDir, jarName, sourcesByFqn.keySet(), resources);
	}

	private static void addEntry(JarOutputStream jar, String entryName, Path classFile) {
		try {
			jar.putNextEntry(new JarEntry(entryName));
			jar.write(Files.readAllBytes(classFile));
			jar.closeEntry();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static class StringSource extends SimpleJavaFileObject {

		private final String source;

		StringSource(String fqn, String source) {
			super(URI.create("string:///" + fqn.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
			this.source = source;
		}

		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors) {
			return source;
		}
	}

}
