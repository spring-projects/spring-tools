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
package org.springframework.ide.vscode.boot.kotlin.syntax;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.kotlin.KotlinTestParsers;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;

/**
 * Every registered Kotlin parser has to read the same source into the very same
 * {@link KotlinSyntax}, ranges included, as the default parser does. This checks that on all the
 * Kotlin files of the test projects, so a parser that is added to {@link KotlinSyntaxReaders}
 * is compared against the reference on real code without writing a single test.
 *
 * @author Martin Lippert
 */
public class KotlinParserParityTest {

	private static List<Path> kotlinFiles() throws Exception {
		File projects = new File(ProjectsHarness.class.getResource("/test-projects/").toURI());
		List<Path> files = new ArrayList<>();
		File[] directories = projects.listFiles(file -> file.isDirectory() && file.getName().contains("kotlin"));
		assertThat(directories).as("the Kotlin test projects").isNotEmpty();

		for (File directory : directories) {
			try (Stream<Path> walk = Files.walk(directory.toPath())) {
				walk.filter(path -> path.toString().endsWith(".kt")).sorted().forEach(files::add);
			}
		}
		return files;
	}

	@Test
	void everyParserReadsTheTestProjectsLikeTheDefaultParser() throws Exception {
		List<Path> files = kotlinFiles();
		assertThat(files).hasSizeGreaterThan(20);

		KotlinSyntaxReader reference = KotlinTestParsers.reader(KotlinSyntaxReaders.DEFAULT);

		for (String name : KotlinSyntaxReaders.names()) {
			if (name.equals(KotlinSyntaxReaders.DEFAULT)) {
				continue;
			}
			KotlinSyntaxReader candidate = KotlinTestParsers.reader(name);

			for (Path path : files) {
				String source = Files.readString(path);
				assertThat(candidate.read(path.getFileName().toString(), source))
						.as("parser %s, file %s", name, path.getFileName())
						.isEqualTo(reference.read(path.getFileName().toString(), source));
			}
		}
	}

	@Test
	void everyParserReadsTheTestProjectsWithWindowsLineSeparatorsLikeTheDefaultParser() throws Exception {
		KotlinSyntaxReader reference = KotlinTestParsers.reader(KotlinSyntaxReaders.DEFAULT);

		for (String name : KotlinSyntaxReaders.names()) {
			if (name.equals(KotlinSyntaxReaders.DEFAULT)) {
				continue;
			}
			KotlinSyntaxReader candidate = KotlinTestParsers.reader(name);

			for (Path path : kotlinFiles()) {
				String source = Files.readString(path).replace("\r\n", "\n").replace("\n", "\r\n");
				assertThat(candidate.read(path.getFileName().toString(), source))
						.as("parser %s, file %s", name, path.getFileName())
						.isEqualTo(reference.read(path.getFileName().toString(), source));
			}
		}
	}

}
