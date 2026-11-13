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
package org.springframework.ide.vscode.boot.kotlin.bindings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.springframework.ide.vscode.boot.kotlin.AllKotlinParsers;
import org.springframework.ide.vscode.boot.kotlin.KotlinTestParsers;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.kotlin.jdt.KotlinJdtBindings;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedAnnotation;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedMemberValuePair;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedType;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedVariable;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntaxReader;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;

/**
 * @author Martin Lippert
 */
@AllKotlinParsers
public class KotlinConstantResolverTest {

	@Parameter
	String parser;

	private KotlinSyntaxReader reader() {
		return KotlinTestParsers.reader(parser);
	}

	private KotlinDeclarationIndex declarations;
	private KotlinTypeResolver resolver;

	@BeforeEach
	void setup() throws Exception {
		MavenJavaProject project = ProjectsHarness.INSTANCE.mavenProjectAlreadyBuilt("test-stereotypes-support");
		declarations = new KotlinDeclarationIndex();
		resolver = new KotlinTypeResolver(project.getIndex(), declarations);
	}

	private void file(String name, String source) {
		declarations.put(name, reader().read(name + ".kt", source));
		resolver.clearCaches();
	}

	/** the value of the first argument of the annotation on the class */
	private Object valueOfAnnotationOn(String binaryName) {
		KotlinSourceClass source = declarations.find(binaryName);
		KotlinSyntax.AnnotationUse use = source.declaration().annotations().get(0);
		return resolver.constants().evaluate(use.arguments().get(0).value(), source.file(), source.outer());
	}

	private List<ResolvedMemberValuePair> pairsOfAnnotationOn(String binaryName) {
		KotlinSourceClass source = declarations.find(binaryName);
		ResolvedAnnotation annotation = resolver.resolveAnnotation(source.declaration().annotations().get(0), source.file(), source.outer());
		return annotation.getDeclaredMemberValuePairs();
	}

	@Test
	void literalsAndConcatenation() {
		file("A", """
				package a

				@Anno("x" + "y" + 'z')
				class Concatenated
				@Anno(1)
				class Int
				@Anno(10000000000L)
				class Long
				@Anno(true)
				class Bool
				""");

		assertThat(valueOfAnnotationOn("a.Concatenated")).isNull(); // chars are not understood
		assertThat(valueOfAnnotationOn("a.Int")).isEqualTo(1);
		assertThat(valueOfAnnotationOn("a.Long")).isEqualTo(10000000000L);
		assertThat(valueOfAnnotationOn("a.Bool")).isEqualTo(true);
	}

	@Test
	void constantsOfObjectsAndCompanions() {
		file("A", """
				package a

				object Constants {
					const val PATH = "/object"
					const val CHAINED = PATH + "/chained"
				}

				class WithCompanion {
					companion object {
						const val PATH = "/companion"
					}
				}

				@Anno(Constants.PATH)
				class Object
				@Anno(Constants.CHAINED)
				class Chained
				@Anno(WithCompanion.PATH)
				class Companion
				@Anno(WithCompanion.Companion.PATH)
				class NamedCompanion
				""");

		assertThat(valueOfAnnotationOn("a.Object")).isEqualTo("/object");
		assertThat(valueOfAnnotationOn("a.Chained")).isEqualTo("/object/chained");
		assertThat(valueOfAnnotationOn("a.Companion")).isEqualTo("/companion");
		assertThat(valueOfAnnotationOn("a.NamedCompanion")).isEqualTo("/companion");
	}

	@Test
	void constantsInTheClassTheyAreUsedIn() {
		file("A", """
				package a

				class Outer {
					private companion object {
						const val PATH = "/own"
					}
					@Anno(PATH)
					class Inner
				}
				""");

		assertThat(valueOfAnnotationOn("a.Outer$Inner")).isEqualTo("/own");
	}

	@Test
	void topLevelConstants() {
		file("Constants", """
				package a.constants

				const val BASE = "/base"
				const val OTHER = "/other"
				""");
		file("A", """
				package a

				import a.constants.BASE
				import a.constants.*

				const val LOCAL = "/local"

				@Anno(BASE)
				class Imported
				@Anno(OTHER)
				class StarImported
				@Anno(LOCAL)
				class SamePackage
				@Anno(LOCAL + BASE)
				class Combined
				""");

		assertThat(valueOfAnnotationOn("a.Imported")).isEqualTo("/base");
		assertThat(valueOfAnnotationOn("a.StarImported")).isEqualTo("/other");
		assertThat(valueOfAnnotationOn("a.SamePackage")).isEqualTo("/local");
		assertThat(valueOfAnnotationOn("a.Combined")).isEqualTo("/local/base");
	}

	@Test
	void templates() {
		file("A", """
				package a

				object C { const val X = "x" }

				@Anno("1${C.X}2")
				class Braces
				@Anno("1$LOCAL2")
				class Simple
				const val LOCAL = "l"
				""");

		assertThat(valueOfAnnotationOn("a.Braces")).isEqualTo("1x2");
		assertThat(valueOfAnnotationOn("a.Simple")).isNull(); // LOCAL2 is not a constant
	}

	@Test
	void constantsOfJavaClassesOnTheClasspath() {
		file("A", """
				package a

				import org.springframework.http.MediaType
				import org.springframework.http.MediaType.APPLICATION_JSON_VALUE

				@Anno(MediaType.TEXT_PLAIN_VALUE)
				class Qualified
				@Anno(APPLICATION_JSON_VALUE)
				class Imported
				@Anno(org.springframework.beans.factory.support.AbstractBeanDefinition.INFER_METHOD)
				class FullyQualified
				""");

		assertThat(valueOfAnnotationOn("a.Qualified")).isEqualTo("text/plain");
		assertThat(valueOfAnnotationOn("a.Imported")).isEqualTo("application/json");
		assertThat(valueOfAnnotationOn("a.FullyQualified")).isEqualTo("(inferred)");
	}

	@Test
	void whatIsNotAConstantHasNoValue() {
		file("A", """
				package a

				import org.springframework.web.bind.annotation.RequestMethod

				@Anno(RequestMethod.GET)
				class EnumConstant
				@Anno(Unknown.VALUE)
				class UnknownClass
				@Anno(missing)
				class Missing
				@Anno(Circular.A)
				class CircularReference
				object Circular {
					const val A = B
					const val B = A
				}
				""");

		assertThat(valueOfAnnotationOn("a.EnumConstant")).isNull();
		assertThat(valueOfAnnotationOn("a.UnknownClass")).isNull();
		assertThat(valueOfAnnotationOn("a.Missing")).isNull();
		assertThat(valueOfAnnotationOn("a.CircularReference")).isNull();
	}

	@Test
	void valuesOfAnnotationsOfKotlinSources() {
		file("A", """
				package a

				import org.springframework.context.annotation.Import
				import org.springframework.web.bind.annotation.RequestMapping
				import org.springframework.web.bind.annotation.RequestMethod.*

				object C { const val PATH = "/c" }

				@RequestMapping(path = [C.PATH, "/b"], method = [GET, POST], produces = ["a/b"], name = "n")
				class Mapped
				@RequestMapping("/positional")
				class Positional
				@Import(value = [String::class, Int::class])
				class Classes
				""");

		List<ResolvedMemberValuePair> pairs = pairsOfAnnotationOn("a.Mapped");
		assertThat(pairs).extracting(ResolvedMemberValuePair::getName).containsExactly("path", "method", "produces", "name");
		assertThat((Object[]) pairs.get(0).getValue()).containsExactly("/c", "/b");

		Object[] methods = (Object[]) pairs.get(1).getValue();
		assertThat(methods).allSatisfy(method -> assertThat(method).isInstanceOf(ResolvedVariable.class));
		assertThat(values(pairs.get(1))).containsExactly("GET", "POST");
		assertThat(values(pairs.get(0))).containsExactly("/c", "/b");
		assertThat(pairs.get(3).getValue()).isEqualTo("n");

		List<ResolvedMemberValuePair> positional = pairsOfAnnotationOn("a.Positional");
		assertThat(positional).extracting(ResolvedMemberValuePair::getName).containsExactly("value");
		assertThat(positional.get(0).getValue()).isEqualTo("/positional");

		List<ResolvedMemberValuePair> classes = pairsOfAnnotationOn("a.Classes");
		Object[] types = (Object[]) classes.get(0).getValue();
		assertThat(((ResolvedType) types[0]).getQualifiedName()).isEqualTo("java.lang.String");
		assertThat(values(classes.get(0))).containsExactly("java.lang.String", "int");
	}

	@Test
	void valuesOfAnnotationsOnTheClasspath() {
		file("A", """
				package a

				import org.springframework.web.bind.annotation.PostMapping

				@PostMapping
				class Mapped
				""");

		KotlinSourceClass source = declarations.find("a.Mapped");
		ResolvedAnnotation postMapping = source.declaration().annotations().isEmpty() ? null
				: resolver.resolveAnnotation(source.declaration().annotations().get(0), source.file(), null);

		// @PostMapping is meta-annotated with @RequestMapping(method = RequestMethod.POST)
		ResolvedAnnotation requestMapping = postMapping.getAnnotationType().getAnnotations().stream()
				.filter(a -> a.getAnnotationType().getQualifiedName().equals("org.springframework.web.bind.annotation.RequestMapping"))
				.findFirst().get();

		ResolvedMemberValuePair method = requestMapping.getDeclaredMemberValuePairs().stream().filter(p -> p.getName().equals("method")).findFirst().get();
		assertThat(values(method)).containsExactly("POST");
	}


	/** the values of the attribute, read from its JDT binding the way the indexers read them */
	private String[] values(ResolvedMemberValuePair pair) {
		return ASTUtils.getValuesFromValuePair(new KotlinJdtBindings(resolver).memberValuePair(pair)).values;
	}

}
