/*******************************************************************************
 * Copyright (c) 2018 Pivotal, Inc.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Supplier;

/**
 * Various Java installation utility methods
 *
 * @author Alex Boyko
 *
 */
public class JavaUtils {

	private static Logger log = LoggerFactory.getLogger(JavaUtils.class);
	
	private static final String JRE = "jre"; //$NON-NLS-1$

	/**
	 * The list of locations in which to look for the java executable in candidate
	 * VM install locations, relative to the VM install location. From Java 9 onwards, there may not be a jre directory.
	 */
	private static final List<Path> CANDIDATE_JAVA_FILES = Stream.of("javaw", "javaw.exe", "java", "java.exe", "j9w", "j9w.exe", "j9", "j9.exe").map(Path::of).toList(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$
	private static final Path[] CANDIDATE_JAVA_LOCATIONS = { Path.of(""), Path.of("bin"), Path.of(JRE, "bin") }; //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
	private static final Path BIN = Path.of("bin"); //$NON-NLS-1$

	/**
	 * Starting in the specified VM install location, attempt to find the 'java' executable
	 * file.  If found, return the corresponding <code>File</code> object, otherwise return
	 * <code>null</code>.
	 * @param vmInstallLocation the {@link File} location to look in
	 * @return the {@link File} for the Java executable or <code>null</code>
	 */
	public static File findJavaExecutable(File vmInstallLocation) {
		// Try each candidate in order.  The first one found wins.  Thus, the order
		// of fgCandidateJavaLocations and fgCandidateJavaFiles is significant.

		Path filePath = vmInstallLocation.toPath();
		boolean isBin = filePath.endsWith(BIN);
		for (Path exeName : CANDIDATE_JAVA_FILES) {
			for (int j = 0; j < CANDIDATE_JAVA_LOCATIONS.length; j++) {
				if (!isBin && j == 0) {
					// search in "." only under bin for java executables for Java 9 and above
					continue;
				}
				Path javaFile = filePath.resolve(CANDIDATE_JAVA_LOCATIONS[j]).resolve(exeName);
				if (Files.isRegularFile(javaFile)) {
					return javaFile.toFile();
				}
			}
		}
		return null;
	}


	/**
	 * Find JRE libs jars
	 *
	 * @param javaMinorVersionSupplier
	 * @param javaHomeSupplier
	 * @param bootClasspathSupplier
	 * @return
	 */
	public static Stream<Path> jreLibs(Supplier<String> javaMinorVersionSupplier, Supplier<Optional<Path>> javaHomeSupplier, Supplier<String> bootClasspathSupplier) {
		String versionString = javaMinorVersionSupplier.get();
		try {
			int version = versionString == null ? 8 : Integer.valueOf(versionString);
			if (version > 8) {
				Optional<Path> javaHomeOpt = javaHomeSupplier.get();
				if (javaHomeOpt.isPresent()) {
					Path rtPath= javaHomeOpt.get().resolve("lib").resolve("jrt-fs.jar");
					if (Files.exists(rtPath)) {
						return Stream.of(rtPath);
					} else {
						log.error("Cannot find file " + rtPath);
					}
				}
			} else {
				String s = bootClasspathSupplier.get();
				if (s != null) {
					return  Arrays.stream(s.split(File.pathSeparator)).map(File::new).filter(f -> f.canRead()).map(f -> Paths.get(f.toURI()));
				}
			}
		} catch (NumberFormatException e) {
			log.error("Cannot extract java minor version number.", e);
		}
		return Stream.empty();
	}

	/**
	 * Extracts java version string from full version string, i.e. "8" from 1.8.0_151, "9" from 9.0.1, "10" from 10.0.1
	 *
	 * @param fullVersion
	 * @return
	 */
	public static String getJavaRuntimeMinorVersion(String fullVersion) {
		String[] tokenized = fullVersion.split("\\.");
		if ("1".equals(tokenized[0])) {
			if (tokenized.length > 1) {
				return tokenized[1];
			} else {
				log.error("Cannot determine minor version for the Java Runtime Version: " + fullVersion);
				return null;
			}
		} else {
			String version = tokenized[0];
			int idx = version.indexOf('+');
			return idx >= 0 ? version.substring(0, idx) : version;
		}
	}

	public static Path javaHomeFromLibJar(Path libJar) {
		Path root = libJar.getRoot();
		for (Path home = libJar; !root.equals(home.getParent()); home = home.getParent()) {
			Path bin = home.resolve("bin");
			Path lib = home.resolve("lib");
			Path include = home.resolve("include");
			Path man = home.resolve("man");
			Path legal = home.resolve("legal");
			if (Files.isDirectory(bin) && Files.isDirectory(lib) && Files.isDirectory(include) && (Files.isDirectory(man) || Files.isDirectory(legal))) {
				return home;
			}
		}
		return null;
	}

	public static Path jreSources(Path libJar) {
		Path home = javaHomeFromLibJar(libJar);
		if (home != null) {
			Path sources = JavaUtils.sourceZip(home);
			if (sources == null) {
				sources = JavaUtils.sourceZip(home.resolve("lib"));
			}
			return sources;
		}
		return null;
	}

	private static Path sourceZip(Path containerFolder) {
		Path sourcesZip = containerFolder.resolve("src.zip");
		if (Files.exists(sourcesZip)) {
			return sourcesZip;
		}
		return null;
	}

	public static String typeBindingKeyToFqName(String bindingKey) {
		return bindingKey == null ? null : bindingKey.substring(1, bindingKey.length() - 1).replace('/', '.');
	}

	public static String typeFqNameToBindingKey(String fqName) {
		StringBuilder sb = new StringBuilder(2 + fqName.length());
		sb.append('L');
		sb.append(fqName.replace('.', '/'));
		sb.append(';');
		return sb.toString();
	}

}
