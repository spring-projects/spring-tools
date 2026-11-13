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

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.kotlin.KotlinTestParsers;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.AnnotationUse;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ClassDeclaration;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Function;

/**
 * What is specific to the tree-sitter reader. Everything that has to be the same for all the
 * parsers is in {@link KotlinSyntaxReaderTest}, which runs for this reader, too.
 * <p>
 * The tests about the limits of the grammar are there to find out when a newer version of the
 * grammar gets rid of one: such a test fails then, and is to be turned into a test of what works.
 *
 * @author Martin Lippert
 */
public class KotlinTreeSitterReaderTest {

	private static KotlinSyntax.File read(String source) {
		return KotlinTestParsers.reader(KotlinSyntaxReaders.TREE_SITTER).read("Test.kt", source);
	}

	@Test
	void classesBehindABrokenFunctionAreRecovered() {
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

		assertThat(file.classes()).extracting(ClassDeclaration::name).containsExactly("First", "Second", "Third");
		assertThat(file.classes().get(0).annotations()).extracting(AnnotationUse::typeName).containsExactly("Service");
		assertThat(file.classes().get(2).annotations()).extracting(AnnotationUse::typeName).containsExactly("Component");
	}

	@Test
	void kindsOfClassesThatAreNotClosed() {
		record Case(String source, String name, KotlinSyntax.ClassKind kind) {
		}

		List<Case> cases = List.of(
				new Case("package a\n\n@Marker\nannotation class Ann {\n", "Ann", KotlinSyntax.ClassKind.ANNOTATION),
				new Case("package a\n\nenum class Color {\n\tRED\n", "Color", KotlinSyntax.ClassKind.ENUM),
				new Case("package a\n\ninterface Port {\n\tfun run()\n", "Port", KotlinSyntax.ClassKind.INTERFACE),
				new Case("package a\n\nobject Single {\n\tfun one() {}\n", "Single", KotlinSyntax.ClassKind.OBJECT),
				new Case("package a\n\nopen class Plain : Base() {\n\tfun one() {}\n", "Plain", KotlinSyntax.ClassKind.CLASS));

		for (Case testCase : cases) {
			List<ClassDeclaration> classes = read(testCase.source()).classes();

			assertThat(classes).as(testCase.source()).extracting(ClassDeclaration::name).containsExactly(testCase.name());
			assertThat(classes.get(0).kind()).as(testCase.source()).isEqualTo(testCase.kind());
		}

		ClassDeclaration annotation = read(cases.get(0).source()).classes().get(0);
		assertThat(annotation.annotations()).extracting(AnnotationUse::typeName).containsExactly("Marker");
		assertThat(read(cases.get(2).source()).classes().get(0).functions()).extracting(Function::name).containsExactly("run");
		assertThat(read(cases.get(4).source()).classes().get(0).supertypes()).extracting(KotlinSyntax.TypeRef::name).containsExactly("Base");
	}

	@Test
	void veryLongSourcesAreNoProblem() {
		StringBuilder source = new StringBuilder("package a\n\nimport org.springframework.web.bind.annotation.GetMapping\n\n");
		for (int i = 0; i < 2000; i++) {
			source.append("/** doc ").append(i).append(" */\n@Service(\"s").append(i).append("\")\nclass C").append(i).append(" {\n\t@GetMapping(\"/m").append(i)
					.append("\")\n\tfun f").append(i).append("(a: Int): String = \"x\" // trailing\n}\n\n");
		}

		long start = System.currentTimeMillis();
		KotlinSyntax.File file = read(source.toString());

		assertThat(file.classes()).hasSize(2000);
		assertThat(file.classes().get(1999).functions()).extracting(Function::name).containsExactly("f1999");
		assertThat(file.classes().get(1999).annotations().get(0).arguments().get(0).value()).isEqualTo(new KotlinSyntax.StringValue("s1999"));
		assertThat(System.currentTimeMillis() - start).as("time to read 2000 classes in ms").isLessThan(20_000);
	}

	@Test
	void blankingOutFunInterfaceKeepsTheLengthAndOnlyTouchesTheKeyword() {
		String source = "@A fun interface X { fun run() }\nval s = \"fun  interface\"\nfun interfaces() {}\nfun `interface`() {}\n";

		String blanked = KotlinTreeSitterReader.blankOutFunInterface(source);

		assertThat(blanked).hasSameSizeAs(source);
		assertThat(blanked).startsWith("@A     interface X { fun run() }");
		assertThat(blanked).contains("fun interfaces() {}").contains("fun `interface`() {}");
		assertThat(KotlinTreeSitterReader.blankOutFunInterface("class A")).isEqualTo("class A");
	}

	@Test
	void blankingOutCommentsKeepsTheLengthAndTheLineBreaks() {
		String source = "a /* one\r\ntwo */ b // three\nc";
		List<int[]> comments = List.of(new int[] { 2, source.indexOf(" b"), }, new int[] { source.indexOf("//"), source.indexOf("\nc") });

		String blanked = KotlinTreeSitterReader.blankOut(source, comments);

		assertThat(blanked).hasSameSizeAs(source);
		assertThat(blanked).isEqualTo("a " + " ".repeat(6) + "\r\n" + " ".repeat(6) + " b " + " ".repeat(8) + "\nc");
	}

	@Test
	void parsersAreReusedAndReleasedAgainAndAgain() {
		KotlinTreeSitterReader reader = new KotlinTreeSitterReader();
		try {
			for (int i = 0; i < 50; i++) {
				assertThat(reader.read("A.kt", "class A" + i).classes()).extracting(ClassDeclaration::name).containsExactly("A" + i);
			}
			reader.dispose();
			reader.dispose();
			assertThat(reader.read("A.kt", "class B").classes()).hasSize(1);
		}
		finally {
			reader.dispose();
		}
	}

	// ---- the comment scanner

	/** the comments the tree-sitter lexer finds in the source, as offsets into the text */
	private static List<int[]> commentsOfTreeSitter(String source) {
		byte[] data = source.getBytes(java.nio.charset.StandardCharsets.UTF_16LE);
		org.treesitter.TSParser parser = new org.treesitter.TSParser();
		parser.setLanguage(new org.treesitter.TreeSitterKotlin());
		org.treesitter.TSTree tree = parser.parse(new byte[8192], null, (buffer, offset, point) -> {
			if (offset >= data.length) {
				return 0;
			}
			int count = Math.min(buffer.length, data.length - offset);
			System.arraycopy(data, offset, buffer, 0, count);
			return count;
		}, org.treesitter.TSInputEncoding.TSInputEncodingUTF16LE);

		List<int[]> comments = new java.util.ArrayList<>();
		collectComments(tree.getRootNode(), comments);
		tree.close();
		parser.close();
		return comments;
	}

	private static void collectComments(org.treesitter.TSNode node, List<int[]> comments) {
		if (node.getType().equals("line_comment") || node.getType().equals("multiline_comment")) {
			comments.add(new int[] { node.getStartByte() / 2, node.getEndByte() / 2 });
			return;
		}
		for (int i = 0; i < node.getChildCount(); i++) {
			collectComments(node.getChild(i), comments);
		}
	}

	private static List<String> texts(String source, List<int[]> comments) {
		return comments.stream().map(c -> source.substring(c[0], c[1])).toList();
	}

	@Test
	void theCommentScannerFindsWhatTreeSitterLexesAsComments() {
		String source = """
				package a // one
				/* two */
				/** three /* nested */ still three */
				@Anno("/* not */", "// not", \"\"\"raw // not
				/* not either */\"\"\", "tpl ${ "a" + /* four */ b } // not", 'x', '"', '\\'', '\\u0041') // five
				class `name // not a comment` { // six
					val c = '/' // seven
					fun f() = "$x // not" + "${y /* eight */}" // nine
				} /* ten */
				/// eleven
				/**/
				// last, without a line break at the end""";

		List<int[]> scanned = KotlinTreeSitterReader.SourceScanner.comments(source);

		assertThat(texts(source, scanned)).isEqualTo(texts(source, commentsOfTreeSitter(source)));
		assertThat(texts(source, scanned)).contains("// one", "/* two */", "/** three /* nested */ still three */", "/* four */", "// five", "// six",
				"// seven", "/* eight */", "// nine", "/* ten */", "/// eleven", "/**/");
		assertThat(texts(source, scanned)).doesNotContain("/* not */", "// not");
	}

	@Test
	void theCommentScannerFindsWhatTreeSitterLexesInAllTheKotlinTestProjects() throws Exception {
		java.io.File projects = new java.io.File(org.springframework.ide.vscode.project.harness.ProjectsHarness.class.getResource("/test-projects/").toURI());
		int checked = 0;
		for (java.io.File directory : projects.listFiles(file -> file.isDirectory() && file.getName().contains("kotlin"))) {
			try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(directory.toPath())) {
				for (java.nio.file.Path path : walk.filter(p -> p.toString().endsWith(".kt")).toList()) {
					String source = java.nio.file.Files.readString(path);
					assertThat(texts(source, KotlinTreeSitterReader.SourceScanner.comments(source))).as(path.toString())
							.isEqualTo(texts(source, commentsOfTreeSitter(source)));
					checked++;
				}
			}
		}
		assertThat(checked).isGreaterThan(20);
	}

	// ---- skipping the bodies of functions

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
		KotlinSyntax.File withBodies = new KotlinTreeSitterReader(false).read("Test.kt", TRICKY_FUNCTIONS);
		KotlinSyntax.File withoutBodies = new KotlinTreeSitterReader(true).read("Test.kt", TRICKY_FUNCTIONS);

		assertThat(withoutBodies).isEqualTo(withBodies);
		assertThat(withoutBodies.classes()).extracting(ClassDeclaration::name).containsExactly("Tricky", "Handler", "AfterAll");
		assertThat(withoutBodies.classes().get(0).functions()).extracting(Function::name).containsExactly("block", "expression", "expressionWithBlock", "noBody",
				"another", "generic", "functionTypeResult", "withDefault", "extension", "annotated", "last");
	}

	@Test
	void skippingTheBodiesOfFunctionsChangesNothingInAllTheKotlinTestProjects() throws Exception {
		java.io.File projects = new java.io.File(org.springframework.ide.vscode.project.harness.ProjectsHarness.class.getResource("/test-projects/").toURI());
		KotlinTreeSitterReader with = new KotlinTreeSitterReader(false);
		KotlinTreeSitterReader without = new KotlinTreeSitterReader(true);
		int checked = 0;
		for (java.io.File directory : projects.listFiles(file -> file.isDirectory() && file.getName().contains("kotlin"))) {
			try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(directory.toPath())) {
				for (java.nio.file.Path path : walk.filter(p -> p.toString().endsWith(".kt")).toList()) {
					String source = java.nio.file.Files.readString(path);
					assertThat(without.read("A.kt", source)).as(path.toString()).isEqualTo(with.read("A.kt", source));
					checked++;
				}
			}
		}
		assertThat(checked).isGreaterThan(20);
	}

	@Test
	void theBodiesOfFunctionsAreFoundBetweenTheirBraces() {
		String source = TRICKY_FUNCTIONS;

		List<String> bodies = KotlinTreeSitterReader.SourceScanner.scan(source, true).bodies().stream()
				.map(b -> source.substring(b[0] - 1, b[1] + 1).replaceAll("\\s+", " ").trim()).toList();

		// block, another, generic, functionTypeResult, withDefault, extension, annotated, last, inTheEnd (the local function is in a body)
		assertThat(bodies).hasSize(9);
		assertThat(bodies.get(0)).startsWith("{ val text").endsWith("return text }");
		assertThat(bodies).contains("{}", "{ }", "{ return 1 }", "{ return mapOf() }", "{ println() }");
		assertThat(bodies).noneMatch(b -> b.equals("{ return }"));
		assertThat(bodies).noneMatch(b -> b.contains("a + 1") || b.contains("run {"));
		assertThat(KotlinTreeSitterReader.SourceScanner.scan(source, false).bodies()).isEmpty();
	}

	// ---- the limits of the grammar

	@Test
	void limit_identifiersWithLettersOfSomeScriptsAreNotUnderstood() {
		String source = """
				package a

				class A {
					fun fine() {}
					fun 日本語() {}
				}
				""";

		ClassDeclaration declaration = read(source).classes().get(0);

		// the function with the Chinese name is left out, the one before it is not affected
		assertThat(declaration.functions()).extracting(Function::name).contains("fine").doesNotContain("日本語");
	}

	@Test
	void limit_aClassThatDelegatesWithByAndHasABodyHasNoMembers() {
		String source = """
				package a

				class Decorator(repo: Repo) : Repo by repo {
					@Bean
					fun method() {}
				}
				""";

		KotlinSyntax.File file = read(source);

		// the body is read as a lambda that is passed to the delegate
		assertThat(file.classes()).extracting(ClassDeclaration::name).contains("Decorator");
		assertThat(file.classes().get(0).functions()).isEmpty();
	}

}
