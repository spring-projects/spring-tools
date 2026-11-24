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
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ClassDeclaration;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Function;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;

/**
 * What is specific to the reader of the Kotlin parser of JetBrains that is written in Kotlin: the bodies of
 * functions are not parsed, and that changes nothing of what is read. The contract of the syntax model is in
 * {@link KotlinSyntaxReaderTest}.
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

	/** functions with bodies that are hard to skip: braces in strings, characters and comments, generic and expression bodies */
	static final String TRICKY_FUNCTIONS = """
			package a

			import org.springframework.web.bind.annotation.GetMapping

			@Service
			class Tricky<T> {

				@GetMapping("/block")
				fun block(a: Int): String {
					val text = "}{ \\" ${ "}" } " + '}' + '{' // } {
					/* { */ fun local() { return }
					return text
				}

				@Bean fun expression(a: Int) = a + 1

				fun expressionWithBlock() = run { 1 }

				abstract fun noBody(): String
				fun another() {}

				fun <R : Comparable<R>> generic(list: List<R>, f: (R) -> Unit = { println("{") }): Map<String, List<R>> where R : Any {
					return mapOf()
				}

				fun functionTypeResult(): (Int) -> Unit {
					return { }
				}

				fun withDefault(x: Int = 5, g: () -> Unit = { }): Unit
				{
					println()
				}

				fun String.extension(): Int
				{ return 1 }

				@Anno(value = ["{"], other = "}")
				fun annotated() { }

				val property: Int get() { return 1 }

				fun last(): Int {
					return 1
				}
			}

			fun interface Handler { fun handle() }

			class AfterAll { @Bean fun inTheEnd() {} }
			""";

	@Test
	void skippingTheBodiesOfFunctionsChangesNothingOfWhatIsRead() {
		KotlinSyntax.File lazy = new KotlinSyntaxApiReader(true).read("Test.kt", TRICKY_FUNCTIONS);
		KotlinSyntax.File eager = new KotlinSyntaxApiReader(false).read("Test.kt", TRICKY_FUNCTIONS);

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
