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
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ClassDeclaration;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Function;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;

/**
 * What is specific to the reader of the Kotlin parser of JetBrains that is written in Kotlin. Everything that
 * has to be the same for all the parsers is in {@link KotlinSyntaxReaderTest}, which runs for this reader, too.
 * <p>
 * This parser has the same grammar as the one of the Kotlin compiler (PSI), and it binds comments to
 * declarations the same way, so it is held to more than the other parsers: it reads exactly what the PSI
 * reader reads, ranges next to comments included.
 *
 * @author Martin Lippert
 */
public class KotlinSyntaxApiReaderTest {

	private static List<Path> kotlinFilesOfTheTestProjects() throws Exception {
		File projects = new File(ProjectsHarness.class.getResource("/test-projects/").toURI());
		List<Path> files = new ArrayList<>();
		for (File directory : projects.listFiles(file -> file.isDirectory() && file.getName().contains("kotlin"))) {
			try (Stream<Path> walk = Files.walk(directory.toPath())) {
				walk.filter(path -> path.toString().endsWith(".kt")).sorted().forEach(files::add);
			}
		}
		return files;
	}

	@Test
	void readsExactlyWhatThePsiReaderReadsOnTheTestProjects() throws Exception {
		KotlinSyntaxReader psi = KotlinTestParsers.reader(KotlinSyntaxReaders.PSI);
		KotlinSyntaxReader syntaxApi = KotlinTestParsers.reader(KotlinSyntaxReaders.SYNTAX_API);
		List<Path> files = kotlinFilesOfTheTestProjects();

		assertThat(files).hasSizeGreaterThan(20);
		for (Path file : files) {
			String source = Files.readString(file);
			assertThat(syntaxApi.read("A.kt", source)).as(file.toString()).isEqualTo(psi.read("A.kt", source));
		}
	}

	@Test
	void readsExactlyWhatThePsiReaderReadsEvenWithCommentsAndErrors() {
		String source = """
				package a // after the package
				import b.C // after the import
				import d.*
				// a comment
				/* a block comment */
				class A // after the class

				// own line
				@Anno("x") // after the annotation
				class B {
					/** doc */
					// own line
					@GetMapping("/y") fun f() {} // trailing
					fun broken( {
				}

				@Service
				class Unclosed {
					@Bean
					fun x() {}
				""";

		assertThat(KotlinTestParsers.reader(KotlinSyntaxReaders.SYNTAX_API).read("A.kt", source))
				.isEqualTo(KotlinTestParsers.reader(KotlinSyntaxReaders.PSI).read("A.kt", source));
	}

	@Test
	void skippingTheBodiesOfFunctionsChangesNothingOfWhatIsRead() {
		KotlinSyntax.File lazy = new KotlinSyntaxApiReader(true).read("Test.kt", KotlinTreeSitterReaderTest.TRICKY_FUNCTIONS);
		KotlinSyntax.File eager = new KotlinSyntaxApiReader(false).read("Test.kt", KotlinTreeSitterReaderTest.TRICKY_FUNCTIONS);

		assertThat(lazy).isEqualTo(eager);
		assertThat(lazy.classes()).extracting(ClassDeclaration::name).containsExactly("Tricky", "Handler", "AfterAll");
		assertThat(lazy.classes().get(0).functions()).extracting(Function::name).containsExactly("block", "expression", "expressionWithBlock", "noBody",
				"another", "generic", "functionTypeResult", "withDefault", "extension", "annotated", "last");
	}

	@Test
	void skippingTheBodiesOfFunctionsChangesNothingOnTheTestProjects() throws Exception {
		KotlinSyntaxApiReader lazy = new KotlinSyntaxApiReader(true);
		KotlinSyntaxApiReader eager = new KotlinSyntaxApiReader(false);

		for (Path file : kotlinFilesOfTheTestProjects()) {
			String source = Files.readString(file);
			assertThat(lazy.read("A.kt", source)).as(file.toString()).isEqualTo(eager.read("A.kt", source));
		}
	}

	@Test
	void funInterfacesNeedNoSpecialTreatment() {
		KotlinSyntax.File file = new KotlinSyntaxApiReader().read("A.kt", "package a\n\n@Service\nfun interface Handler { fun run() }\n");

		assertThat(file.classes()).extracting(ClassDeclaration::name).containsExactly("Handler");
		assertThat(file.classes().get(0).kind()).isEqualTo(KotlinSyntax.ClassKind.INTERFACE);
	}

	@Test
	void identifiersInAnyScriptAreUnderstood() {
		KotlinSyntax.File file = new KotlinSyntaxApiReader().read("A.kt", "package a\n\nclass A {\n\tfun 日本語() {}\n\tfun fine() {}\n}\n\nclass `class with spaces` {\n\tfun `a test`() {}\n}\n");

		assertThat(file.classes().get(0).functions()).extracting(Function::name).containsExactly("日本語", "fine");
		assertThat(file.classes().get(1).name()).isEqualTo("class with spaces");
	}

	@Test
	void aClassThatDelegatesWithByAndHasABodyHasItsMembers() {
		KotlinSyntax.File file = new KotlinSyntaxApiReader().read("A.kt", "package a\n\nclass Decorator(repo: Repo) : Repo by repo {\n\t@Bean\n\tfun method() {}\n}\n");

		assertThat(file.classes().get(0).functions()).extracting(Function::name).containsExactly("method");
	}

}
