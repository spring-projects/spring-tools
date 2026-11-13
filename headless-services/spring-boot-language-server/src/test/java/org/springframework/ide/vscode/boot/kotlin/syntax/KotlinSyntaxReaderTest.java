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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.springframework.ide.vscode.boot.kotlin.AllKotlinParsers;
import org.springframework.ide.vscode.boot.kotlin.KotlinTestParsers;
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
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.UnsupportedValue;

/**
 * @author Martin Lippert
 */
@AllKotlinParsers
public class KotlinSyntaxReaderTest {

	@Parameter
	String parser;

	private KotlinSyntaxReader reader() {
		return KotlinTestParsers.reader(parser);
	}

	private KotlinSyntax.File read(String source) {
		return reader().read("Test.kt", source);
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

	@Test
	void docCommentsBelongToTheRangeOfTheirDeclaration() {
		String source = """
				package a

				// a line comment is not part of the class
				/** the doc comment is */
				@Service
				class A {
					/** and so it is for functions */
					@Bean
					fun bean() {}
				}
				""";

		ClassDeclaration declaration = read(source).classes().get(0);

		assertThat(text(source, declaration.range())).startsWith("/** the doc comment is */\n@Service\nclass A {");
		assertThat(text(source, declaration.functions().get(0).range())).startsWith("/** and so it is for functions */\n\t@Bean\n\tfun bean()");
		assertThat(text(source, declaration.nameRange())).isEqualTo("A");
	}

	@Test
	void annotationsOnObjectsCompanionsNestedClassesAndFunctionsInThem() {
		String source = """
				package a

				@Configuration
				object Config {
					@Bean
					fun inObject() {}
				}

				class Outer {
					@Component
					class Inner {
						@Bean
						fun inInner() {}
					}

					@Marker
					companion object Factory {
						@Bean
						fun inCompanion() {}
					}
				}
				""";

		List<ClassDeclaration> classes = read(source).classes();
		ClassDeclaration config = classes.get(0);
		ClassDeclaration inner = classes.get(1).nestedClasses().get(0);
		ClassDeclaration companion = classes.get(1).nestedClasses().get(1);

		assertThat(config.annotations()).extracting(AnnotationUse::typeName).containsExactly("Configuration");
		assertThat(config.functions()).extracting(Function::name).containsExactly("inObject");
		assertThat(config.functions().get(0).annotations()).extracting(AnnotationUse::typeName).containsExactly("Bean");
		assertThat(inner.annotations()).extracting(AnnotationUse::typeName).containsExactly("Component");
		assertThat(inner.functions()).extracting(Function::name).containsExactly("inInner");
		assertThat(companion.name()).isEqualTo("Factory");
		assertThat(companion.kind()).isEqualTo(ClassKind.COMPANION);
		assertThat(companion.annotations()).extracting(AnnotationUse::typeName).containsExactly("Marker");
		assertThat(companion.functions()).extracting(Function::name).containsExactly("inCompanion");
		assertThat(classes.get(1).functions()).isEmpty();
		assertThat(classes.get(1).annotations()).isEmpty();
	}

	@Test
	void moreKindsOfClasses() {
		String source = """
				package a

				sealed class Sealed
				abstract class Abstract
				inner class Inner
				@JvmInline value class Value(val x: Int)
				fun interface Fun { fun run() }
				sealed interface SealedInterface
				enum class Color(val rgb: Int) { RED(1) { override fun toString() = "r" }, GREEN(2); fun hex() = rgb }
				class Generic<T : Any>(val t: T)
				private class Private
				internal object Internal
				""";

		List<ClassDeclaration> classes = read(source).classes();

		assertThat(classes).extracting(ClassDeclaration::name).containsExactly("Sealed", "Abstract", "Inner", "Value", "Fun", "SealedInterface", "Color",
				"Generic", "Private", "Internal");
		assertThat(classes).extracting(ClassDeclaration::kind).containsExactly(ClassKind.CLASS, ClassKind.CLASS, ClassKind.CLASS, ClassKind.CLASS,
				ClassKind.INTERFACE, ClassKind.INTERFACE, ClassKind.ENUM, ClassKind.CLASS, ClassKind.CLASS, ClassKind.OBJECT);
		assertThat(classes.get(3).annotations()).extracting(AnnotationUse::typeName).containsExactly("JvmInline");
		assertThat(classes.get(4).functions()).extracting(Function::name).containsExactly("run");
	}

	@Test
	void supertypeVariants() {
		String source = """
				package a

				class A(val x: Int) : Base(1, 2), Repo<User, Map<String, List<Int>>>, java.io.Serializable, Delegating by delegate, Star<*>
				""";

		List<KotlinSyntax.TypeRef> supertypes = read(source).classes().get(0).supertypes();

		assertThat(supertypes).extracting(KotlinSyntax.TypeRef::name).containsExactly("Base", "Repo", "java.io.Serializable", "Delegating", "Star");

		KotlinSyntax.TypeRef repo = supertypes.get(1);
		assertThat(repo.arguments()).extracting(KotlinSyntax.TypeRef::name).containsExactly("User", "Map");
		assertThat(repo.arguments().get(1).arguments()).extracting(KotlinSyntax.TypeRef::name).containsExactly("String", "List");
		assertThat(repo.arguments().get(1).arguments().get(1).arguments()).extracting(KotlinSyntax.TypeRef::name).containsExactly("Int");
		assertThat(text(source, repo.range())).isEqualTo("Repo<User, Map<String, List<Int>>>");
		assertThat(text(source, supertypes.get(0).range())).isEqualTo("Base");
		assertThat(supertypes.get(4).arguments()).extracting(KotlinSyntax.TypeRef::name).containsExactly("*");
		assertThat(text(source, supertypes.get(4).arguments().get(0).range())).isEqualTo("*");
	}

	@Test
	void theEntriesOfAnEnumAreNoNestedClasses() {
		String source = """
				package a

				enum class Color(val rgb: Int) {
					RED(1), GREEN(2) { override fun toString() = "green" };

					fun hex() = rgb

					class Helper
				}
				""";

		ClassDeclaration color = read(source).classes().get(0);

		assertThat(color.kind()).isEqualTo(ClassKind.ENUM);
		assertThat(color.nestedClasses()).extracting(ClassDeclaration::name).containsExactly("Helper");
		assertThat(color.functions()).extracting(Function::name).containsExactly("hex");
	}

	@Test
	void annotationsOnTheConstructorAreNoAnnotationsOfTheClass() {
		String source = """
				package a

				@Service
				class A @Inject constructor(@Value("x") val x: String, private val y: Int)
				""";

		ClassDeclaration declaration = read(source).classes().get(0);

		assertThat(declaration.annotations()).extracting(AnnotationUse::typeName).containsExactly("Service");
		assertThat(declaration.nameRange()).isEqualTo(new Range(source.indexOf("class A") + 6, 1));
	}

	@Test
	void fileAnnotationsAndTopLevelDeclarationsThatAreNoClasses() {
		String source = """
				@file:JvmName("Util")
				@file:Suppress("unused")
				package a

				import b.C

				typealias Name = String

				val property = "x"

				fun topLevel() {}

				@Service
				class A

				fun anotherTopLevel() = 1

				class B
				""";

		KotlinSyntax.File file = read(source);

		assertThat(file.packageName()).isEqualTo("a");
		assertThat(text(source, file.packageRange())).isEqualTo("package a");
		assertThat(file.imports()).extracting(KotlinSyntax.Import::name).containsExactly("b.C");
		assertThat(file.classes()).extracting(ClassDeclaration::name).containsExactly("A", "B");
		assertThat(file.constants()).isEmpty();
	}

	@Test
	void namedEmptyAndTrailingCommaArguments() {
		String source = """
				package a

				@Empty()
				@Named(value = "x",)
				@Both("a", other = "b")
				class A
				""";

		List<AnnotationUse> annotations = read(source).classes().get(0).annotations();

		assertThat(annotations.get(0).arguments()).isEmpty();
		assertThat(text(source, annotations.get(0).range())).isEqualTo("@Empty()");
		assertThat(annotations.get(1).arguments()).hasSize(1);
		assertThat(annotations.get(1).arguments().get(0).name()).isEqualTo("value");
		assertThat(annotations.get(1).arguments().get(0).value()).isEqualTo(new StringValue("x"));
		assertThat(annotations.get(2).arguments()).extracting(KotlinSyntax.Argument::name).containsExactly(null, "other");
	}

	@Test
	void stringLiterals() {
		String source = "package a\n\n@Anno(\"\", \"\"\"raw\\nstring \"quoted\" \"\"\", \"dollar \\$x\", \"unicode \\u0041\", \"$a/b\", \"${c.d}\", \"x${e}y${f}z\")\nclass A\n";

		List<KotlinSyntax.Argument> arguments = read(source).classes().get(0).annotations().get(0).arguments();

		assertThat(arguments.get(0).value()).isEqualTo(new StringValue(""));
		assertThat(arguments.get(1).value()).isEqualTo(new StringValue("raw\\nstring \"quoted\" "));
		assertThat(arguments.get(2).value()).isEqualTo(new StringValue("dollar $x"));
		assertThat(arguments.get(3).value()).isEqualTo(new StringValue("unicode A"));
		assertThat(arguments.get(4).value()).isEqualTo(new ConcatValue(List.of(new ReferenceValue("a"), new StringValue("/b"))));
		assertThat(arguments.get(5).value()).isEqualTo(new ConcatValue(List.of(new ReferenceValue("c.d"))));
		assertThat(arguments.get(6).value()).isEqualTo(new ConcatValue(List.of(new StringValue("x"), new ReferenceValue("e"), new StringValue("y"),
				new ReferenceValue("f"), new StringValue("z"))));
	}

	@Test
	void numberAndBooleanLiterals() {
		String source = """
				package a

				@Anno(1, 42L, 0x1F, 1_000, 1.5, 2.5f, 1e3, true, false)
				class A
				""";

		List<KotlinSyntax.Argument> arguments = read(source).classes().get(0).annotations().get(0).arguments();

		assertThat(arguments).extracting(KotlinSyntax.Argument::value).containsExactly(new NumberValue("1"), new NumberValue("42L"),
				new NumberValue("0x1F"), new NumberValue("1_000"), new NumberValue("1.5"), new NumberValue("2.5f"), new NumberValue("1e3"),
				new BooleanValue(true), new BooleanValue(false));
	}

	@Test
	void valuesThatAreNotUnderstoodKeepTheirSourceText() {
		String source = """
				package a

				@Anno(null, compute(1), "x".length, if (flag) 1 else 2, Inner("x"), Foo::class.java)
				class A
				""";

		List<KotlinSyntax.Argument> arguments = read(source).classes().get(0).annotations().get(0).arguments();

		assertThat(arguments).extracting(KotlinSyntax.Argument::value).containsExactly(new UnsupportedValue("null"), new UnsupportedValue("compute(1)"),
				new UnsupportedValue("\"x\".length"), new UnsupportedValue("if (flag) 1 else 2"), new UnsupportedValue("Inner(\"x\")"),
				new UnsupportedValue("Foo::class.java"));
		assertThat(text(source, arguments.get(1).valueRange())).isEqualTo("compute(1)");
	}

	@Test
	void classLiteralsAndReferences() {
		String source = """
				package a

				@Anno(types = [a.b.Foo::class, Bar::class], method = RequestMethod.GET, methods = [RequestMethod.GET, RequestMethod.POST], simple = NAME)
				class A
				""";

		List<KotlinSyntax.Argument> arguments = read(source).classes().get(0).annotations().get(0).arguments();

		assertThat(arguments.get(0).value()).isEqualTo(new ArrayValue(List.of(new ClassValue("a.b.Foo"), new ClassValue("Bar"))));
		assertThat(arguments.get(1).value()).isEqualTo(new ReferenceValue("RequestMethod.GET"));
		assertThat(arguments.get(2).value()).isEqualTo(new ArrayValue(List.of(new ReferenceValue("RequestMethod.GET"), new ReferenceValue("RequestMethod.POST"))));
		assertThat(arguments.get(3).value()).isEqualTo(new ReferenceValue("NAME"));
	}

	@Test
	void arrayVariants() {
		String source = """
				package a

				@Anno(empty = [], emptyCall = arrayOf(), single = ["x"], nested = [["a"], ["b"]], list = listOf("l"), set = setOf("s"), ints = intArrayOf(1, 2), concat = ["a" + B, C])
				class A
				""";

		List<KotlinSyntax.Argument> arguments = read(source).classes().get(0).annotations().get(0).arguments();

		assertThat(arguments.get(0).value()).isEqualTo(new ArrayValue(List.of()));
		assertThat(arguments.get(1).value()).isEqualTo(new ArrayValue(List.of()));
		assertThat(arguments.get(2).value()).isEqualTo(new ArrayValue(List.of(new StringValue("x"))));
		assertThat(arguments.get(3).value()).isEqualTo(new ArrayValue(List.of(new ArrayValue(List.of(new StringValue("a"))), new ArrayValue(List.of(new StringValue("b"))))));
		assertThat(arguments.get(4).value()).isEqualTo(new ArrayValue(List.of(new StringValue("l"))));
		assertThat(arguments.get(5).value()).isEqualTo(new ArrayValue(List.of(new StringValue("s"))));
		assertThat(arguments.get(6).value()).isEqualTo(new ArrayValue(List.of(new NumberValue("1"), new NumberValue("2"))));
		assertThat(arguments.get(7).value()).isEqualTo(new ArrayValue(List.of(new ConcatValue(List.of(new StringValue("a"), new ReferenceValue("B"))), new ReferenceValue("C"))));

		ArrayValue concat = (ArrayValue) arguments.get(7).value();
		assertThat(text(source, concat.rangeOf(0))).isEqualTo("\"a\" + B");
		assertThat(text(source, concat.rangeOf(1))).isEqualTo("C");
		assertThat(concat.rangeOf(2)).isNull();
		assertThat(text(source, arguments.get(7).valueRange())).isEqualTo("[\"a\" + B, C]");
	}

	@Test
	void functionShapes() {
		String source = """
				package a

				class A {
					fun String.extension() {}
					fun withDefaults(a: Int = 5, b: String = "x") {}
					fun withVararg(vararg names: String) {}
					fun withFunctionType(f: (Int) -> String) {}
					fun <T> generic(t: T): T = t
					private suspend fun modifiers(): Unit {}
					override fun toString() = "a"
					fun nullableGenerics(items: List<String?>?): Map<String, List<Int>?>? = null
					fun containing() { fun local() {} }
					abstract fun noBody(): String
				}
				""";

		List<Function> functions = read(source).classes().get(0).functions();

		assertThat(functions).extracting(Function::name).containsExactly("extension", "withDefaults", "withVararg", "withFunctionType", "generic",
				"modifiers", "toString", "nullableGenerics", "containing", "noBody");
		assertThat(functions.get(0).parameters()).isEmpty();
		assertThat(functions.get(1).parameters()).extracting(KotlinSyntax.Parameter::name).containsExactly("a", "b");
		assertThat(functions.get(1).parameters()).extracting(p -> p.type().name()).containsExactly("Int", "String");
		assertThat(functions.get(2).parameters()).extracting(KotlinSyntax.Parameter::name).containsExactly("names");
		assertThat(functions.get(2).parameters().get(0).type().name()).isEqualTo("String");
		assertThat(functions.get(4).parameters().get(0).type().name()).isEqualTo("T");
		assertThat(functions.get(4).returnType().name()).isEqualTo("T");
		assertThat(functions.get(5).returnType().name()).isEqualTo("Unit");
		assertThat(functions.get(6).returnType()).isNull();

		KotlinSyntax.TypeRef items = functions.get(7).parameters().get(0).type();
		assertThat(items.name()).isEqualTo("List");
		assertThat(items.nullable()).isTrue();
		assertThat(items.arguments()).hasSize(1);
		assertThat(items.arguments().get(0).name()).isEqualTo("String");
		assertThat(items.arguments().get(0).nullable()).isTrue();
		assertThat(functions.get(7).returnType().name()).isEqualTo("Map");
		assertThat(functions.get(7).returnType().nullable()).isTrue();
		assertThat(functions.get(7).returnType().arguments().get(1).nullable()).isTrue();

		assertThat(text(source, functions.get(0).nameRange())).isEqualTo("extension");
		assertThat(text(source, functions.get(9).nameRange())).isEqualTo("noBody");
	}

	@Test
	void rangesAreOffsetsInTheOriginalTextWhateverIsInFrontOfThem() {
		String source = "package a\n\n// größe ✓ 日本語 😀 emoji outside the BMP\nimport b.C\n\n/* ünïcödé */\n@Service(\"ü😀\")\nclass Größe {\n\t@Bean fun größe() {}\n}\n\nclass After\n";

		KotlinSyntax.File file = read(source);

		assertThat(file.length()).isEqualTo(source.length());
		assertThat(text(source, file.packageRange())).isEqualTo("package a");
		assertThat(text(source, file.imports().get(0).range())).isEqualTo("import b.C");
		assertThat(file.classes()).extracting(ClassDeclaration::name).containsExactly("Größe", "After");
		assertThat(text(source, file.classes().get(0).nameRange())).isEqualTo("Größe");
		assertThat(text(source, file.classes().get(0).annotations().get(0).range())).isEqualTo("@Service(\"ü😀\")");
		assertThat(file.classes().get(0).annotations().get(0).arguments().get(0).value()).isEqualTo(new StringValue("ü😀"));
		assertThat(text(source, file.classes().get(0).functions().get(0).nameRange())).isEqualTo("größe");
		assertThat(text(source, file.classes().get(1).nameRange())).isEqualTo("After");
	}

	@Test
	void mixedAndOldStyleLineSeparators() {
		String source = "package a\n\n@Service\r\nclass A {\r\n\t@Bean\rfun bean() {}\n}\r\rclass B\r\n";

		KotlinSyntax.File file = read(source);

		assertThat(file.length()).isEqualTo(source.length());
		assertThat(file.classes()).extracting(ClassDeclaration::name).containsExactly("A", "B");
		assertThat(text(source, file.classes().get(0).nameRange())).isEqualTo("A");
		assertThat(text(source, file.classes().get(0).annotations().get(0).range())).isEqualTo("@Service");
		assertThat(text(source, file.classes().get(0).functions().get(0).nameRange())).isEqualTo("bean");
		assertThat(text(source, file.classes().get(1).nameRange())).isEqualTo("B");
	}

	@Test
	void rangesStayInsideTheirParents() {
		String source = """
				@file:Suppress("x")
				package com.example

				import org.springframework.stereotype.Service
				import org.springframework.web.bind.annotation.*

				const val TOP = "top"

				/** docs */
				@Service("a" + TOP)
				@RequestMapping(path = ["/a", "/b"])
				open class Outer<T>(val x: Int) : Base(), Port<T> {

					@Bean
					@Suppress("unused")
					fun bean(a: List<String>, b: Map<String, Int?>): Map<String, List<Int>> = mapOf()

					class Inner {
						@GetMapping(value = ["/x"])
						fun inner() {}
					}

					companion object {
						const val X = "x"
					}
				}
				""";

		KotlinSyntax.File file = read(source);

		assertThat(file.classes()).isNotEmpty();
		Range whole = new Range(0, source.length());
		for (ClassDeclaration declaration : file.classes()) {
			assertClassRangesAreInside(declaration, whole, source);
		}
		for (KotlinSyntax.Import i : file.imports()) {
			assertInside(i.range(), whole);
		}
		assertInside(file.packageRange(), whole);
		assertInside(file.packageNameRange(), file.packageRange());
	}

	private static void assertInside(Range inner, Range outer) {
		assertThat(inner).isNotNull();
		assertThat(inner.start()).isGreaterThanOrEqualTo(outer.start());
		assertThat(inner.end()).isLessThanOrEqualTo(outer.end());
		assertThat(inner.length()).isGreaterThanOrEqualTo(0);
	}

	private static void assertClassRangesAreInside(ClassDeclaration declaration, Range parent, String source) {
		assertInside(declaration.range(), parent);
		assertInside(declaration.nameRange(), declaration.range());
		for (AnnotationUse annotation : declaration.annotations()) {
			assertAnnotationRangesAreInside(annotation, declaration.range());
		}
		for (KotlinSyntax.TypeRef supertype : declaration.supertypes()) {
			assertInside(supertype.range(), declaration.range());
		}
		for (Function function : declaration.functions()) {
			assertInside(function.range(), declaration.range());
			assertInside(function.nameRange(), function.range());
			for (AnnotationUse annotation : function.annotations()) {
				assertAnnotationRangesAreInside(annotation, function.range());
			}
		}
		for (ClassDeclaration nested : declaration.nestedClasses()) {
			assertClassRangesAreInside(nested, declaration.range(), source);
		}
	}

	private static void assertAnnotationRangesAreInside(AnnotationUse annotation, Range parent) {
		assertInside(annotation.range(), parent);
		assertInside(annotation.typeNameRange(), annotation.range());
		for (KotlinSyntax.Argument argument : annotation.arguments()) {
			assertInside(argument.range(), annotation.range());
			assertInside(argument.valueRange(), argument.range());
		}
	}

	@Test
	void funInterfacesAreInterfaces() {
		String source = """
				package a

				import org.springframework.stereotype.Component

				@Component
				fun interface Handler {
					@Bean
					fun handle(input: String): String
				}

				fun interface Other { fun run() }

				class After
				""";

		KotlinSyntax.File file = read(source);

		assertThat(file.classes()).extracting(ClassDeclaration::name).containsExactly("Handler", "Other", "After");
		assertThat(file.classes()).extracting(ClassDeclaration::kind).containsExactly(ClassKind.INTERFACE, ClassKind.INTERFACE, ClassKind.CLASS);
		assertThat(file.classes().get(0).annotations()).extracting(AnnotationUse::typeName).containsExactly("Component");
		assertThat(file.classes().get(0).functions()).extracting(Function::name).containsExactly("handle");
		assertThat(file.classes().get(0).functions().get(0).annotations()).extracting(AnnotationUse::typeName).containsExactly("Bean");
		assertThat(text(source, file.classes().get(0).nameRange())).isEqualTo("Handler");
		assertThat(text(source, file.classes().get(0).range())).startsWith("@Component\nfun interface Handler {").endsWith("}");
		assertThat(file.classes().get(1).functions()).extracting(Function::name).containsExactly("run");
	}

	@Test
	void textThatLooksLikeFunInterfaceInStringsAndCommentsIsLeftAlone() {
		String source = """
				package a

				// a fun interface in a comment
				@Anno("a fun interface in a string")
				class A
				""";

		AnnotationUse annotation = read(source).classes().get(0).annotations().get(0);

		assertThat(annotation.arguments().get(0).value()).isEqualTo(new StringValue("a fun interface in a string"));
	}

	@Test
	void commentsAroundDeclarationsHideNothingAndMoveNothing() {
		String source = """
				package a

				import b.C // trailing comment of an import

				/* block comment */
				class A // after the class
				class B { fun f() {} } // after the body
				// own line
				@Anno("x")
				class D // and one at the end

				class E {} // trailing
				@Anno("y") // after the annotation
				class F

				class G {}

				/** doc */
				@Anno("z", other = [1, 2])
				class H {
					fun a() {} // trailing
					// own line
					@GetMapping("/x")
					fun b() {}
					/** doc */
					@GetMapping(value = ["/y"]) // trailing
					fun c() {}
					/* block */ @Other fun d() {}
				}

				// last comment
				""";

		KotlinSyntax.File file = read(source);

		assertThat(file.imports()).extracting(KotlinSyntax.Import::name).containsExactly("b.C");
		assertThat(file.classes()).extracting(ClassDeclaration::name).containsExactly("A", "B", "D", "E", "F", "G", "H");
		for (ClassDeclaration declaration : file.classes()) {
			assertThat(text(source, declaration.nameRange())).isEqualTo(declaration.name());
			assertThat(text(source, declaration.range())).contains(declaration.name());
		}

		assertThat(file.classes().get(1).functions()).extracting(Function::name).containsExactly("f");
		assertThat(file.classes().get(2).annotations()).extracting(AnnotationUse::typeName).containsExactly("Anno");
		assertThat(file.classes().get(2).annotations().get(0).arguments().get(0).value()).isEqualTo(new StringValue("x"));
		assertThat(file.classes().get(4).annotations()).extracting(AnnotationUse::typeName).containsExactly("Anno");
		assertThat(file.classes().get(4).annotations().get(0).arguments().get(0).value()).isEqualTo(new StringValue("y"));
		assertThat(file.classes().get(5).annotations()).isEmpty();

		ClassDeclaration h = file.classes().get(6);
		assertThat(h.annotations()).extracting(AnnotationUse::typeName).containsExactly("Anno");
		assertThat(h.annotations().get(0).arguments()).hasSize(2);
		assertThat(h.functions()).extracting(Function::name).containsExactly("a", "b", "c", "d");
		assertThat(h.functions().get(1).annotations().get(0).arguments().get(0).value()).isEqualTo(new StringValue("/x"));
		assertThat(h.functions().get(2).annotations().get(0).arguments().get(0).value()).isEqualTo(new ArrayValue(List.of(new StringValue("/y"))));
		assertThat(h.functions().get(3).annotations()).extracting(AnnotationUse::typeName).containsExactly("Other");
		assertThat(text(source, h.functions().get(1).nameRange())).isEqualTo("b");
		assertThat(text(source, h.annotations().get(0).range())).isEqualTo("@Anno(\"z\", other = [1, 2])");
	}

	@Test
	void commentsAreNotLookedForInStringsAndStringsAreNotLookedForInComments() {
		String source = """
				package a

				@Anno("/* not a comment */", "// not a comment either", "a" + /* a real one */ "b")
				class A // a comment with a "quote
				@Anno2("after")
				class B
				""";

		KotlinSyntax.File file = read(source);

		assertThat(file.classes()).extracting(ClassDeclaration::name).containsExactly("A", "B");
		List<KotlinSyntax.Argument> arguments = file.classes().get(0).annotations().get(0).arguments();
		assertThat(arguments.get(0).value()).isEqualTo(new StringValue("/* not a comment */"));
		assertThat(arguments.get(1).value()).isEqualTo(new StringValue("// not a comment either"));
		assertThat(arguments.get(2).value()).isEqualTo(new ConcatValue(List.of(new StringValue("a"), new StringValue("b"))));
		assertThat(file.classes().get(1).annotations().get(0).arguments().get(0).value()).isEqualTo(new StringValue("after"));
	}

	@Test
	void semicolonsAndCommentsAfterPackageAndImports() {
		String source = """
				package a; // after the package
				import b.C; // after the import
				import d.E // another one
				import f.G as H

				class A
				""";

		KotlinSyntax.File file = read(source);

		assertThat(file.packageName()).isEqualTo("a");
		assertThat(text(source, file.packageNameRange())).isEqualTo("a");
		assertThat(file.imports()).extracting(KotlinSyntax.Import::name).containsExactly("b.C", "d.E", "f.G");
		assertThat(file.imports()).extracting(KotlinSyntax.Import::alias).containsExactly(null, null, "H");
		assertThat(source.substring(file.imports().get(0).range().start())).startsWith("import b.C");
		assertThat(source.substring(file.imports().get(2).range().start(), file.imports().get(2).range().end())).isEqualTo("import f.G as H");
		assertThat(file.classes()).extracting(ClassDeclaration::name).containsExactly("A");
	}

	@Test
	void backtickedNames() {
		String source = """
				package a

				class `My Class` {
					@Test
					fun `does something useful`(`the input`: String) {}
				}
				""";

		ClassDeclaration declaration = read(source).classes().get(0);

		assertThat(declaration.name()).isEqualTo("My Class");
		assertThat(declaration.functions()).extracting(Function::name).containsExactly("does something useful");
		assertThat(declaration.functions().get(0).parameters()).extracting(KotlinSyntax.Parameter::name).containsExactly("the input");
		assertThat(text(source, declaration.functions().get(0).nameRange())).isEqualTo("`does something useful`");
	}

	@Test
	void realisticSpringKotlinSource() {
		String source = """
				package com.example.demo

				import org.springframework.beans.factory.annotation.Autowired
				import org.springframework.web.bind.annotation.*
				import org.springframework.http.MediaType

				/**
				 * The controller.
				 */
				@RestController
				@RequestMapping("/api", produces = [MediaType.APPLICATION_JSON_VALUE])
				class DemoController(private val service: DemoService) : BaseController() {

					@Autowired
					lateinit var other: Other

					@GetMapping("/items/{id}")
					fun item(@PathVariable id: Long, @RequestParam(required = false) filter: String?): Item = service.find(id)

					@PostMapping(value = ["/items"], consumes = [MediaType.APPLICATION_JSON_VALUE])
					@ResponseStatus(HttpStatus.CREATED)
					fun create(@RequestBody item: Item): Item {
						return service.save(item)
					}

					companion object {
						const val PREFIX = "/api"
						private val log = LoggerFactory.getLogger(DemoController::class.java)
					}
				}

				data class Item(val id: Long, val name: String)
				""";

		KotlinSyntax.File file = read(source);

		assertThat(file.classes()).extracting(ClassDeclaration::name).containsExactly("DemoController", "Item");
		ClassDeclaration controller = file.classes().get(0);
		assertThat(controller.annotations()).extracting(AnnotationUse::typeName).containsExactly("RestController", "RequestMapping");
		assertThat(controller.annotations().get(1).arguments()).hasSize(2);
		assertThat(controller.annotations().get(1).arguments().get(1).value()).isEqualTo(new ArrayValue(List.of(new ReferenceValue("MediaType.APPLICATION_JSON_VALUE"))));
		assertThat(controller.supertypes()).extracting(KotlinSyntax.TypeRef::name).containsExactly("BaseController");
		assertThat(controller.functions()).extracting(Function::name).containsExactly("item", "create");
		assertThat(controller.functions().get(0).parameters()).extracting(KotlinSyntax.Parameter::name).containsExactly("id", "filter");
		assertThat(controller.functions().get(1).annotations()).extracting(AnnotationUse::typeName).containsExactly("PostMapping", "ResponseStatus");
		assertThat(controller.nestedClasses()).extracting(ClassDeclaration::name).containsExactly("Companion");
		assertThat(controller.nestedClasses().get(0).constants()).extracting(KotlinSyntax.Constant::name).containsExactly("PREFIX");
		assertThat(text(source, controller.range())).startsWith("/**\n * The controller.\n */\n@RestController");
	}

	@Test
	void anUnclosedClassIsStillRecognizedWithWhatIsInIt() {
		String source = """
				package a

				import org.springframework.stereotype.Service

				@Service
				class Unclosed : Base(), Port {
					@Bean
					fun first() {}

					@GetMapping("/x")
					fun second(a: Int): String = "x"
				""";

		KotlinSyntax.File file = read(source);

		assertThat(file.classes()).extracting(ClassDeclaration::name).containsExactly("Unclosed");
		ClassDeclaration declaration = file.classes().get(0);
		assertThat(declaration.kind()).isEqualTo(ClassKind.CLASS);
		assertThat(declaration.annotations()).extracting(AnnotationUse::typeName).containsExactly("Service");
		assertThat(declaration.supertypes()).extracting(KotlinSyntax.TypeRef::name).containsExactly("Base", "Port");
		assertThat(declaration.functions()).extracting(Function::name).containsExactly("first", "second");
		assertThat(text(source, declaration.nameRange())).isEqualTo("Unclosed");
		assertThat(declaration.range().start()).isEqualTo(source.indexOf("@Service"));
	}

	@Test
	void aBrokenFunctionDoesNotHideTheClassItIsIn() {
		String source = """
				package a

				@Service
				class First {
					fun broken( {
				}

				@Component
				class Second

				@Component
				class Third
				""";

		KotlinSyntax.File file = read(source);

		// the part behind the error is recovered as far as the parser can, what is before it always is
		assertThat(file.classes()).extracting(ClassDeclaration::name).contains("First");
		assertThat(file.classes().get(0).annotations()).extracting(AnnotationUse::typeName).containsExactly("Service");
	}


	@Test
	void deeplyNestedValuesAndTypesAreNoProblem() {
		int depth = 150;
		String nested = "[".repeat(depth) + "\"x\"" + "]".repeat(depth);
		String parentheses = "(".repeat(depth) + "\"y\"" + ")".repeat(depth);
		String type = "List<".repeat(depth) + "String" + ">".repeat(depth);
		String source = "package a\n\n@Anno(" + nested + ", " + parentheses + ")\nclass A {\n\tfun f(x: " + type + ") {}\n}\n";

		KotlinSyntax.File file = read(source);

		assertThat(file.classes()).extracting(ClassDeclaration::name).containsExactly("A");
		assertThat(file.classes().get(0).annotations().get(0).arguments()).hasSize(2);
		assertThat(file.classes().get(0).annotations().get(0).arguments().get(1).value()).isEqualTo(new StringValue("y"));
		assertThat(file.classes().get(0).functions().get(0).parameters().get(0).type().name()).isEqualTo("List");
	}

	@Test
	void incompleteSourceNeverFails() {
		List<String> sources = List.of(
				"@Service",
				"@Service(",
				"@Service(\"unterminated",
				"package",
				"package a.",
				"import",
				"import a.",
				"class",
				"class A {",
				"class A : ",
				"class A { fun",
				"class A { fun x(",
				"class A { @Bean fun x(a: ) {} }",
				"@Anno(a = [1, 2",
				"@Anno(a = \"x\" + )\nclass A",
				"}}}}",
				"\u0000\u0001garbage \uFFFF",
				"class A { class B { class C {",
				"/* unterminated comment\nclass A",
				"\"\"\"unterminated raw string\nclass A");

		for (String source : sources) {
			KotlinSyntax.File file = read(source);

			assertThat(file).as(source).isNotNull();
			assertThat(file.length()).as(source).isEqualTo(source.length());
			Range whole = new Range(0, source.length());
			for (ClassDeclaration declaration : file.classes()) {
				assertClassRangesAreInside(declaration, whole, source);
			}
		}
	}

	@Test
	void whatIsBeforeASyntaxErrorIsStillRecognized() {
		String source = """
				package a

				import org.springframework.stereotype.Service

				@Service
				class Good {
					@Bean
					fun fine() {}
				}

				@Service
				class Incomplete {
					@Bean
					fun fine2() {}
				""";

		KotlinSyntax.File file = read(source);

		assertThat(file.packageName()).isEqualTo("a");
		assertThat(file.imports()).hasSize(1);
		assertThat(file.classes()).extracting(ClassDeclaration::name).containsExactly("Good", "Incomplete");
		assertThat(file.classes().get(0).annotations()).extracting(AnnotationUse::typeName).containsExactly("Service");
		assertThat(file.classes().get(0).functions()).extracting(Function::name).containsExactly("fine");
		assertThat(file.classes().get(1).functions()).extracting(Function::name).containsExactly("fine2");
	}

	@Test
	void readingTheSameSourceAgainGivesTheSameResult() {
		String source = """
				package a

				@Service(path = ["/a"])
				class A : Base() {
					@Bean
					fun bean(x: Int): String = "x"
				}
				""";

		assertThat(read(source)).isEqualTo(read(source));
	}

	@Test
	void theParserIsUsableAgainAfterItWasDisposed() {
		KotlinSyntaxReader own = KotlinSyntaxReaders.create(parser);
		try {
			assertThat(own.read("A.kt", "class A").classes()).hasSize(1);
			own.dispose();
			assertThat(own.read("B.kt", "class B").classes()).extracting(ClassDeclaration::name).containsExactly("B");
		}
		finally {
			own.dispose();
		}
	}

	@Test
	void concurrentReadsGiveTheSameResults() throws Exception {
		String source = """
				package a

				import org.springframework.stereotype.Service

				@Service("x")
				class A {
					@Bean
					fun bean(): String = "x"
				}
				""";
		KotlinSyntax.File expected = read(source);

		ExecutorService executor = Executors.newFixedThreadPool(8);
		try {
			List<Future<KotlinSyntax.File>> results = new ArrayList<>();
			for (int i = 0; i < 64; i++) {
				results.add(executor.submit(() -> reader().read("Concurrent.kt", source)));
			}
			for (Future<KotlinSyntax.File> result : results) {
				assertThat(result.get(60, TimeUnit.SECONDS)).isEqualTo(expected);
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

}
