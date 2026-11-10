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

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.AnnotationUse;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ArrayValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.BooleanValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ClassDeclaration;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ClassKind;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ClassValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ConcatValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Function;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.NumberValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Range;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ReferenceValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.StringValue;

/**
 * @author Martin Lippert
 */
public class KotlinPsiReaderTest {

	private static final KotlinPsiReader reader = new KotlinPsiReader();

	@AfterAll
	static void tearDown() {
		reader.dispose();
	}

	private static KotlinSyntax.File read(String source) {
		return reader.read("Test.kt", source);
	}

	private static String text(String source, Range range) {
		return source.substring(range.start(), range.end());
	}

	@Test
	void packageAndImports() {
		String source = """
				package com.example.demo

				import org.springframework.stereotype.Service
				import org.springframework.web.bind.annotation.*
				import java.util.List as JList

				class A
				""";

		KotlinSyntax.File file = read(source);

		assertThat(file.packageName()).isEqualTo("com.example.demo");
		assertThat(text(source, file.packageRange())).isEqualTo("package com.example.demo");
		assertThat(text(source, file.packageNameRange())).isEqualTo("com.example.demo");
		assertThat(file.length()).isEqualTo(source.length());

		assertThat(file.imports()).hasSize(3);
		assertThat(file.imports().get(0).name()).isEqualTo("org.springframework.stereotype.Service");
		assertThat(file.imports().get(0).allUnder()).isFalse();
		assertThat(file.imports().get(0).alias()).isNull();
		assertThat(text(source, file.imports().get(0).range())).isEqualTo("import org.springframework.stereotype.Service");
		assertThat(file.imports().get(1).name()).isEqualTo("org.springframework.web.bind.annotation");
		assertThat(file.imports().get(1).allUnder()).isTrue();
		assertThat(file.imports().get(2).name()).isEqualTo("java.util.List");
		assertThat(file.imports().get(2).alias()).isEqualTo("JList");
	}

	@Test
	void fileWithoutPackage() {
		KotlinSyntax.File file = read("class A");

		assertThat(file.packageName()).isEmpty();
		assertThat(file.packageRange()).isNull();
		assertThat(file.packageNameRange()).isNull();
		assertThat(file.classes()).hasSize(1);
	}

	@Test
	void kindsOfClasses() {
		String source = """
				package a

				class C
				open class Open
				data class Data(val x: Int)
				interface I
				annotation class Ann
				enum class E { ONE, TWO }
				object O
				""";

		List<ClassDeclaration> classes = read(source).classes();

		assertThat(classes).extracting(ClassDeclaration::name).containsExactly("C", "Open", "Data", "I", "Ann", "E", "O");
		assertThat(classes).extracting(ClassDeclaration::kind).containsExactly(ClassKind.CLASS, ClassKind.CLASS, ClassKind.CLASS,
				ClassKind.INTERFACE, ClassKind.ANNOTATION, ClassKind.ENUM, ClassKind.OBJECT);
	}

	@Test
	void rangesOfClasses() {
		String source = """
				package a

				@Service
				class MyService {
				}
				""";

		ClassDeclaration declaration = read(source).classes().get(0);

		assertThat(text(source, declaration.nameRange())).isEqualTo("MyService");
		assertThat(text(source, declaration.range())).isEqualTo("@Service\nclass MyService {\n}");
	}

	@Test
	void nestedClassesAndCompanionObjects() {
		String source = """
				package a

				class Outer {
					class Inner {
						class Deeper
					}
					companion object {
						const val X = "x"
					}
					object Named
				}
				""";

		ClassDeclaration outer = read(source).classes().get(0);

		assertThat(outer.nestedClasses()).extracting(ClassDeclaration::name).containsExactly("Inner", "Companion", "Named");
		assertThat(outer.nestedClasses()).extracting(ClassDeclaration::kind).containsExactly(ClassKind.CLASS, ClassKind.COMPANION, ClassKind.OBJECT);
		assertThat(outer.nestedClasses().get(0).nestedClasses()).extracting(ClassDeclaration::name).containsExactly("Deeper");
	}

	@Test
	void supertypes() {
		String source = """
				package a

				class A : Base(), First, org.example.Second<String>, Third by delegate
				""";

		ClassDeclaration declaration = read(source).classes().get(0);

		assertThat(declaration.supertypes()).extracting(KotlinSyntax.TypeRef::name).containsExactly("Base", "First", "org.example.Second", "Third");
		assertThat(declaration.supertypes().get(2).arguments()).extracting(KotlinSyntax.TypeRef::name).containsExactly("String");
	}

	@Test
	void annotationsWithoutArguments() {
		String source = """
				package a

				@Service
				@org.springframework.context.annotation.Configuration
				class A
				""";

		List<AnnotationUse> annotations = read(source).classes().get(0).annotations();

		assertThat(annotations).extracting(AnnotationUse::typeName).containsExactly("Service", "org.springframework.context.annotation.Configuration");
		assertThat(annotations.get(0).arguments()).isEmpty();
		assertThat(text(source, annotations.get(0).range())).isEqualTo("@Service");
		assertThat(text(source, annotations.get(0).typeNameRange())).isEqualTo("Service");
	}

	@Test
	void annotationArguments() {
		String source = """
				package a

				@Anno("hello", name = "x", count = 5, flag = true, type = Foo::class, constant = Constants.PATH, list = ["a", "b"], other = arrayOf(1, 2), template = "a${b}c")
				class A
				""";

		AnnotationUse annotation = read(source).classes().get(0).annotations().get(0);

		assertThat(annotation.arguments()).hasSize(9);
		assertThat(annotation.arguments().get(0).name()).isNull();
		assertThat(annotation.arguments().get(0).value()).isEqualTo(new StringValue("hello"));
		assertThat(text(source, annotation.arguments().get(0).valueRange())).isEqualTo("\"hello\"");
		assertThat(annotation.arguments().get(1).name()).isEqualTo("name");
		assertThat(text(source, annotation.arguments().get(1).range())).isEqualTo("name = \"x\"");
		assertThat(text(source, annotation.arguments().get(1).valueRange())).isEqualTo("\"x\"");
		assertThat(annotation.arguments().get(2).value()).isEqualTo(new NumberValue("5"));
		assertThat(annotation.arguments().get(3).value()).isEqualTo(new BooleanValue(true));
		assertThat(annotation.arguments().get(4).value()).isEqualTo(new ClassValue("Foo"));
		assertThat(annotation.arguments().get(5).value()).isEqualTo(new ReferenceValue("Constants.PATH"));
		assertThat(annotation.arguments().get(6).value()).isEqualTo(new ArrayValue(List.of(new StringValue("a"), new StringValue("b"))));
		assertThat(annotation.arguments().get(7).value()).isEqualTo(new ArrayValue(List.of(new NumberValue("1"), new NumberValue("2"))));

		ArrayValue strings = (ArrayValue) annotation.arguments().get(6).value();
		assertThat(text(source, strings.rangeOf(0))).isEqualTo("\"a\"");
		assertThat(text(source, strings.rangeOf(1))).isEqualTo("\"b\"");
		ArrayValue numbers = (ArrayValue) annotation.arguments().get(7).value();
		assertThat(text(source, numbers.rangeOf(1))).isEqualTo("2");
		assertThat(annotation.arguments().get(8).value())
				.isEqualTo(new ConcatValue(List.of(new StringValue("a"), new ReferenceValue("b"), new StringValue("c"))));
	}

	@Test
	void stringEscapes() {
		String source = "package a\n\n@Anno(\"tab\\there \\\"quoted\\\"\")\nclass A\n";

		AnnotationUse annotation = read(source).classes().get(0).annotations().get(0);

		assertThat(annotation.arguments().get(0).value()).isEqualTo(new StringValue("tab\there \"quoted\""));
	}

	@Test
	void annotationUseSiteTargets() {
		String source = """
				package a

				@field:Autowired
				@get:JsonIgnore
				class A
				""";

		List<AnnotationUse> annotations = read(source).classes().get(0).annotations();

		assertThat(annotations).extracting(AnnotationUse::useSiteTarget).containsExactly("field", "get");
		assertThat(annotations).extracting(AnnotationUse::typeName).containsExactly("Autowired", "JsonIgnore");
	}

	@Test
	void functions() {
		String source = """
				package a

				class A {

					fun plain() {}

					@GetMapping("/x")
					fun annotated(name: String, count: Int?, items: List<String>): Map<String, Int> = mapOf()

					val property = 5
				}
				""";

		List<Function> functions = read(source).classes().get(0).functions();

		assertThat(functions).extracting(Function::name).containsExactly("plain", "annotated");
		assertThat(functions.get(0).parameters()).isEmpty();
		assertThat(functions.get(0).returnType()).isNull();
		assertThat(functions.get(0).annotations()).isEmpty();

		Function annotated = functions.get(1);
		assertThat(annotated.annotations()).extracting(AnnotationUse::typeName).containsExactly("GetMapping");
		assertThat(annotated.parameters()).extracting(KotlinSyntax.Parameter::name).containsExactly("name", "count", "items");
		assertThat(annotated.parameters().get(0).type().name()).isEqualTo("String");
		assertThat(annotated.parameters().get(0).type().nullable()).isFalse();
		assertThat(annotated.parameters().get(1).type().name()).isEqualTo("Int");
		assertThat(annotated.parameters().get(1).type().nullable()).isTrue();
		assertThat(annotated.parameters().get(2).type().arguments()).extracting(KotlinSyntax.TypeRef::name).containsExactly("String");
		assertThat(annotated.returnType().name()).isEqualTo("Map");
		assertThat(annotated.returnType().arguments()).extracting(KotlinSyntax.TypeRef::name).containsExactly("String", "Int");
		assertThat(text(source, annotated.nameRange())).isEqualTo("annotated");
		assertThat(text(source, annotated.range())).startsWith("@GetMapping(\"/x\")\n\tfun annotated(").endsWith("= mapOf()");
	}

	@Test
	void lineSeparatorsDoNotMoveRanges() {
		String source = "package a\r\n\r\n@Service\r\nclass A {\r\n\r\n\t@Bean\r\n\tfun bean() {}\r\n}\r\n";

		KotlinSyntax.File file = read(source);
		ClassDeclaration declaration = file.classes().get(0);

		assertThat(file.length()).isEqualTo(source.length());
		assertThat(text(source, file.packageRange())).isEqualTo("package a");
		assertThat(text(source, declaration.nameRange())).isEqualTo("A");
		assertThat(text(source, declaration.annotations().get(0).range())).isEqualTo("@Service");
		assertThat(text(source, declaration.functions().get(0).nameRange())).isEqualTo("bean");
		assertThat(text(source, declaration.functions().get(0).annotations().get(0).range())).isEqualTo("@Bean");
	}

	@Test
	void sourceWithSyntaxErrorsStillGivesWhatIsThere() {
		String source = """
				package a

				@Service
				class A {
					fun broken( {
				}

				class B
				""";

		KotlinSyntax.File file = read(source);

		assertThat(file.classes()).extracting(ClassDeclaration::name).contains("A");
	}

	@Test
	void emptySource() {
		KotlinSyntax.File file = read("");

		assertThat(file.classes()).isEmpty();
		assertThat(file.imports()).isEmpty();
		assertThat(file.packageName()).isEmpty();
	}

	@Test
	void constants() {
		String source = """
				package a

				const val TOP = "top"
				val notConstant = "x"

				object O {
					const val FIRST = "first"
					const val SECOND = FIRST + "/second" + TOP
					const val TEMPLATE = "$FIRST-${TOP}"
					const val NUMBER = 5
					val notConstantEither = "y"
					companion object {
						const val IN_COMPANION = "companion"
					}
				}
				""";

		KotlinSyntax.File file = read(source);
		ClassDeclaration object = file.classes().get(0);

		assertThat(file.constants()).extracting(KotlinSyntax.Constant::name).containsExactly("TOP");
		assertThat(file.constants().get(0).value()).isEqualTo(new StringValue("top"));

		assertThat(object.constants()).extracting(KotlinSyntax.Constant::name).containsExactly("FIRST", "SECOND", "TEMPLATE", "NUMBER");
		assertThat(object.constants().get(1).value()).isEqualTo(new ConcatValue(List.of(new ReferenceValue("FIRST"), new StringValue("/second"), new ReferenceValue("TOP"))));
		assertThat(object.constants().get(2).value()).isEqualTo(new ConcatValue(List.of(new ReferenceValue("FIRST"), new StringValue("-"), new ReferenceValue("TOP"))));
		assertThat(object.constants().get(3).value()).isEqualTo(new NumberValue("5"));
		assertThat(object.nestedClasses().get(0).constants()).extracting(KotlinSyntax.Constant::name).containsExactly("IN_COMPANION");
	}

	@Test
	void concatenationsAreFlattenedAndParenthesesAreLookedThrough() {
		String source = """
				package a

				@Anno("a" + ("b" + C) + "d")
				class A
				""";

		AnnotationUse annotation = read(source).classes().get(0).annotations().get(0);

		assertThat(annotation.arguments().get(0).value()).isEqualTo(new ConcatValue(List.of(new StringValue("a"), new StringValue("b"),
				new ReferenceValue("C"), new StringValue("d"))));
	}

}
