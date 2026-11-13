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
package org.springframework.ide.vscode.boot.kotlin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.lsp4j.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.beans.CachedIndexElement;
import org.springframework.ide.vscode.boot.java.handlers.SpringComponentIndexer;
import org.springframework.ide.vscode.boot.java.lombok.LombokSettings;
import org.springframework.ide.vscode.boot.java.reconcilers.ReconcilingIndex;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypesIndexer;
import org.springframework.ide.vscode.boot.java.utils.DocumentUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaAstScanner;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaDependencyTracker;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaScanResult;
import org.springframework.ide.vscode.boot.kotlin.ast.KotlinDomBuilder;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinDeclarationIndex;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypeResolver;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;

/**
 * The stereotype index elements (the ones the structure view is built from) that are created for
 * Kotlin source snippets: types with their annotations, supertypes and locations, and the
 * annotated functions as method elements. The path under test is the one of the language server,
 * from the parser, through the JDT AST and its bindings, to the very same {@link StereotypesIndexer}
 * that indexes Java sources. Runs against every registered Kotlin parser.
 *
 * @author Martin Lippert
 */
@AllKotlinParsers
public class KotlinStereotypeElementsTest {

	private static final String INDEXED = "org.springframework.stereotype.Indexed";
	private static final String MAPPING = "org.springframework.web.bind.annotation.Mapping";
	private static final String REFLECTIVE = "org.springframework.aot.hint.annotation.Reflective";
	private static final String COMPONENT = "org.springframework.stereotype.Component";
	private static final String SERVICE = "org.springframework.stereotype.Service";
	private static final String CONTROLLER = "org.springframework.stereotype.Controller";
	private static final String REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";
	private static final String REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";
	private static final String GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping";
	private static final String POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping";

	@Parameter
	String parser;

	private MavenJavaProject project;

	@BeforeEach
	void setup() throws Exception {
		project = ProjectsHarness.INSTANCE.mavenProjectAlreadyBuilt("test-stereotypes-support");
	}

	/** what was indexed for one Kotlin source */
	private record Indexed(String source, List<StereotypeClassElement> types) {

		StereotypeClassElement type(String name) {
			return types.stream().filter(type -> type.getType().equals(name)).findFirst()
					.orElseThrow(() -> new AssertionError("no element for " + name + ", but for " + types.stream().map(StereotypeClassElement::getType).toList()));
		}

		List<String> typeNames() {
			return types.stream().map(StereotypeClassElement::getType).toList();
		}

		/** the text of the source the location points to */
		String textAt(Location location) {
			TextDocument doc = DocumentUtils.createTempTextDocument("file:///probe/Probe.kt", source);
			try {
				int start = doc.toOffset(location.getRange().getStart());
				int end = doc.toOffset(location.getRange().getEnd());
				return source.substring(start, end);
			}
			catch (Exception e) {
				throw new AssertionError(e);
			}
		}
	}

	private Indexed index(String source) throws Exception {
		return index(source, true);
	}

	private Indexed index(String source, boolean checkGaps) throws Exception {
		String docURI = "file:///probe/Probe.kt";

		KotlinSyntax.File file = KotlinTestParsers.reader(parser).read("Probe.kt", source);
		KotlinDeclarationIndex declarations = new KotlinDeclarationIndex();
		declarations.put(docURI, file);
		CompilationUnit cu = new KotlinDomBuilder().build(file, new KotlinTypeResolver(project.getIndex(), declarations)).compilationUnit();
		AnnotationHierarchies.set(cu, new AnnotationHierarchies());

		SpringComponentIndexer[] indexers = { new StereotypesIndexer() };
		SpringIndexerJavaAstScanner scanner = new SpringIndexerJavaAstScanner(indexers, new SpringIndexerJavaDependencyTracker(), (context, index) -> {});

		TextDocument doc = DocumentUtils.createTempTextDocument(docURI, source);
		SpringIndexerJavaScanResult result = new SpringIndexerJavaScanResult(project, new String[] { "/probe/Probe.kt" });
		IProblemCollector none = new IProblemCollector() {
			@Override public void beginCollecting() {}
			@Override public void endCollecting() {}
			@Override public void accept(ReconcileProblem problem) {}
		};
		SpringIndexerJavaContext context = new SpringIndexerJavaContext(project, cu, docURI, "/probe/Probe.kt", 0, doc, source, none,
				new ArrayList<>(), true, false, result, LombokSettings.DISABLED);

		scanner.scanAST(context, false, new ReconcilingIndex(), false);

		List<StereotypeClassElement> types = new ArrayList<>();
		for (CachedIndexElement element : result.getGeneratedIndexElements()) {
			if (element.getIndexElement() instanceof StereotypeClassElement type) {
				types.add(type);
			}
		}
		return new Indexed(source, types);
	}

	@Test
	void classWithDirectAnnotation() throws Exception {
		Indexed indexed = index("""
				package example

				import org.springframework.stereotype.Service

				@Service
				class MyService
				""");

		assertThat(indexed.typeNames()).containsExactly("example.MyService");

		StereotypeClassElement type = indexed.type("example.MyService");
		assertThat(type.getAnnotationTypes()).containsExactlyInAnyOrder(SERVICE, COMPONENT, INDEXED);
		assertThat(type.isAnnotatedWith(COMPONENT)).isTrue();
		assertThat(type.isAnnotatedWith(CONTROLLER)).isFalse();
		assertThat(type.getSupertypes()).isEmpty();
		assertThat(type.getMethods()).isEmpty();
		assertThat(type.getContentHash()).hasSize(12);
	}

	@Test
	void classWithoutAnnotationsAndInDefaultPackage() throws Exception {
		Indexed indexed = index("""
				class Plain
				""");

		StereotypeClassElement type = indexed.type("Plain");
		assertThat(type.getAnnotationTypes()).isEmpty();
		assertThat(type.getSupertypes()).isEmpty();
		assertThat(indexed.textAt(type.getLocation())).isEqualTo("Plain");
	}

	@Test
	void metaAnnotationsAreIncluded() throws Exception {
		Indexed indexed = index("""
				package example

				import org.springframework.web.bind.annotation.RestController

				@RestController
				class Api
				""");

		assertThat(indexed.type("example.Api").getAnnotationTypes()).contains(REST_CONTROLLER, CONTROLLER, COMPONENT);
	}

	@Test
	void annotationsWrittenInDifferentWays() throws Exception {
		Indexed indexed = index("""
				package example

				import org.springframework.stereotype.Service as Svc
				import org.springframework.web.bind.annotation.*

				@Svc
				class Aliased

				@RestController
				class ByStarImport

				@org.springframework.stereotype.Component
				class FullyQualified

				@org.springframework.stereotype.Service("named")
				class WithArguments

				@Suppress("unused") @org.springframework.stereotype.Component
				class OtherAnnotationsAround
				""");

		assertThat(indexed.type("example.Aliased").getAnnotationTypes()).contains(SERVICE, COMPONENT);
		assertThat(indexed.type("example.ByStarImport").getAnnotationTypes()).contains(REST_CONTROLLER, COMPONENT);
		assertThat(indexed.type("example.FullyQualified").getAnnotationTypes()).contains(COMPONENT);
		assertThat(indexed.type("example.WithArguments").getAnnotationTypes()).contains(SERVICE, COMPONENT);
		assertThat(indexed.type("example.OtherAnnotationsAround").getAnnotationTypes()).contains(COMPONENT);
	}

	@Test
	void annotationsOfTheSourceAreResolvedThroughTheirMetaAnnotations() throws Exception {
		Indexed indexed = index("""
				package example

				import org.springframework.stereotype.Service

				@Service
				annotation class MyStereotype

				@MyStereotype
				class Marked
				""");

		assertThat(indexed.typeNames()).as("annotation types get no element, as for Java sources").containsExactly("example.Marked");
		assertThat(indexed.type("example.Marked").getAnnotationTypes()).containsExactlyInAnyOrder("example.MyStereotype", SERVICE, COMPONENT, INDEXED);
	}

	@Test
	void useSiteTargetsDoNotHideAnnotations() throws Exception {
		Indexed indexed = index("""
				package example

				import org.springframework.stereotype.Component

				@Component
				@Suppress("unused")
				class Both
				""");

		// annotations from java.* and kotlin.* packages are no stereotypes
		assertThat(indexed.type("example.Both").getAnnotationTypes()).contains(COMPONENT).doesNotContain("java.lang.SuppressWarnings");
	}

	@Test
	void supertypesFromSourceAndClasspath() throws Exception {
		Indexed indexed = index("""
				package example

				import org.springframework.stereotype.Service

				interface Port

				open class Base : Port

				class Impl : Base(), java.io.Serializable

				@Service
				class Direct : Port
				""");

		assertThat(indexed.type("example.Impl").getSupertypes()).contains("example.Base", "example.Port", "java.io.Serializable").doesNotContain("java.lang.Object");
		assertThat(indexed.type("example.Base").getSupertypes()).containsExactly("example.Port");
		assertThat(indexed.type("example.Direct").getSupertypes()).containsExactly("example.Port");
		assertThat(indexed.type("example.Port").getSupertypes()).isEmpty();
		assertThat(indexed.type("example.Impl").doesImplement("example.Port")).isTrue();
	}

	@Test
	void annotationsOfSupertypesAreInherited() throws Exception {
		Indexed indexed = index("""
				package example

				import org.springframework.stereotype.Component

				@Component
				open class Parent

				class Child : Parent()
				""");

		assertThat(indexed.type("example.Child").getAnnotationTypes()).contains(COMPONENT);
	}

	@Test
	void kindsOfTypes() throws Exception {
		Indexed indexed = index("""
				package example

				interface Port
				open class Open
				data class Data(val x: Int)
				object Singleton
				annotation class Ann
				enum class Color { RED, GREEN }
				""");

		assertThat(indexed.typeNames()).contains("example.Port", "example.Open", "example.Data", "example.Singleton");
		assertThat(indexed.typeNames()).doesNotContain("example.Ann");
	}

	@Test
	void nestedTypesGetBinaryNamesAndElementsOfTheirOwn() throws Exception {
		Indexed indexed = index("""
				package example

				import org.springframework.stereotype.Component

				class Outer {
					@Component
					class Inner {
						class Deeper
					}
					object Named
					companion object
				}
				""");

		assertThat(indexed.typeNames()).contains("example.Outer", "example.Outer$Inner", "example.Outer$Inner$Deeper", "example.Outer$Named",
				"example.Outer$Companion");
		assertThat(indexed.type("example.Outer$Inner").getAnnotationTypes()).contains(COMPONENT);
		assertThat(indexed.type("example.Outer").getAnnotationTypes()).isEmpty();
		assertThat(indexed.textAt(indexed.type("example.Outer$Inner$Deeper").getLocation())).isEqualTo("Deeper");
	}

	@Test
	void locationsPointToTheNameOfTheType() throws Exception {
		String source = """
				package example

				import org.springframework.stereotype.Service

				/** documentation */
				@Service
				class First

				@Service
				class Second {
				}
				""";
		Indexed indexed = index(source);

		Location first = indexed.type("example.First").getLocation();
		assertThat(first.getUri()).isEqualTo("file:///probe/Probe.kt");
		assertThat(first.getRange().getStart().getLine()).isEqualTo(6);
		assertThat(first.getRange().getStart().getCharacter()).isEqualTo(6);
		assertThat(first.getRange().getEnd().getCharacter()).isEqualTo(11);
		assertThat(indexed.textAt(first)).isEqualTo("First");
		assertThat(indexed.textAt(indexed.type("example.Second").getLocation())).isEqualTo("Second");
	}

	@Test
	void annotatedFunctionsGetMethodElements() throws Exception {
		String source = """
				package example

				import org.springframework.web.bind.annotation.GetMapping
				import org.springframework.web.bind.annotation.PostMapping
				import org.springframework.web.bind.annotation.RestController

				@RestController
				class Api {

					fun notAnnotated() {}

					@GetMapping("/a")
					fun read(name: String): String = name

					@PostMapping("/b")
					@Suppress("unused")
					fun write() {}
				}
				""";
		Indexed indexed = index(source);

		List<StereotypeMethodElement> methods = indexed.type("example.Api").getMethods();

		assertThat(methods).extracting(StereotypeMethodElement::getMethodName).containsExactly("read", "write");

		StereotypeMethodElement read = methods.get(0);
		assertThat(read.getAnnotationTypes()).containsExactlyInAnyOrder(GET_MAPPING, REQUEST_MAPPING, MAPPING, REFLECTIVE);
		assertThat(read.isAnnotatedWith(REQUEST_MAPPING)).isTrue();
		assertThat(read.isAnnotatedWith(POST_MAPPING)).isFalse();
		assertThat(read.getMethodSignature()).isEqualTo("example.Api.read(java.lang.String) : java.lang.String");
		assertThat(read.getMethodLabel()).isEqualTo("Api.read(String) : String");
		assertThat(indexed.textAt(read.getLocation())).isEqualTo("read");
		assertThat(read.getContentHash()).hasSize(12);

		StereotypeMethodElement write = methods.get(1);
		assertThat(write.getAnnotationTypes()).containsExactlyInAnyOrder(POST_MAPPING, REQUEST_MAPPING, MAPPING, REFLECTIVE);
		assertThat(write.getMethodSignature()).isEqualTo("example.Api.write() : V");
		assertThat(write.getMethodLabel()).isEqualTo("Api.write() : void");
		assertThat(indexed.textAt(write.getLocation())).isEqualTo("write");

		assertThat(read.getContentHash()).isNotEqualTo(write.getContentHash());
	}

	@Test
	void methodSignaturesFollowTheJvmTypesOfTheParameters() throws Exception {
		Indexed indexed = index("""
				package example

				import org.springframework.web.bind.annotation.RequestMapping

				class Api {
					@RequestMapping("/a")
					fun primitives(count: Int, flag: Boolean, ratio: Double, big: Long): Int = count

					@RequestMapping("/b")
					fun nullables(name: String?, count: Int?): String? = name

					@RequestMapping("/c")
					fun generics(items: List<String>, byName: Map<String, Int>): List<String> = items

					@RequestMapping("/d")
					fun qualified(stream: java.io.InputStream, other: example.Api): Unit {}

					@RequestMapping("/e")
					fun arrays(items: Array<String>): Unit {}
				}
				""");

		List<StereotypeMethodElement> methods = indexed.type("example.Api").getMethods();
		assertThat(methods).extracting(StereotypeMethodElement::getMethodName).containsExactly("primitives", "nullables", "generics", "qualified", "arrays");

		assertThat(methods.get(0).getMethodLabel()).isEqualTo("Api.primitives(int, boolean, double, long) : int");
		assertThat(methods.get(1).getMethodLabel()).isEqualTo("Api.nullables(String, Integer) : String");
		assertThat(methods.get(2).getMethodLabel()).isEqualTo("Api.generics(List<String>, Map<String,Integer>) : List<String>");
		assertThat(methods.get(3).getMethodLabel()).isEqualTo("Api.qualified(InputStream, Api) : void");
		assertThat(methods.get(0).getMethodSignature()).isEqualTo("example.Api.primitives(I, Z, D, J) : I");
		assertThat(methods.get(1).getMethodSignature()).isEqualTo("example.Api.nullables(java.lang.String, java.lang.Integer) : java.lang.String");
		assertThat(methods.get(2).getMethodSignature()).isEqualTo("example.Api.generics(java.util.List, java.util.Map) : java.util.List");
		assertThat(methods.get(3).getMethodSignature()).isEqualTo("example.Api.qualified(java.io.InputStream, example.Api) : V");
		assertThat(methods.get(4).getMethodSignature()).isEqualTo("example.Api.arrays([Ljava.lang.String;) : V");
		assertThat(methods.get(4).getMethodLabel()).isEqualTo("Api.arrays(String[]) : void");
	}

	@Test
	void functionsOfNestedAndObjectTypes() throws Exception {
		Indexed indexed = index("""
				package example

				import org.springframework.context.annotation.Bean

				class Outer {
					class Inner {
						@Bean
						fun innerBean() {}
					}
					companion object {
						@Bean
						fun companionBean() {}
					}
				}

				object Singleton {
					@Bean
					fun singletonBean() {}
				}
				""");

		assertThat(indexed.type("example.Outer$Inner").getMethods()).extracting(StereotypeMethodElement::getMethodName).containsExactly("innerBean");
		assertThat(indexed.type("example.Outer$Companion").getMethods()).extracting(StereotypeMethodElement::getMethodName).containsExactly("companionBean");
		assertThat(indexed.type("example.Singleton").getMethods()).extracting(StereotypeMethodElement::getMethodName).containsExactly("singletonBean");
		assertThat(indexed.type("example.Outer").getMethods()).isEmpty();
	}

	@Test
	void methodsOfInterfacesAndAbstractFunctions() throws Exception {
		Indexed indexed = index("""
				package example

				import org.springframework.web.bind.annotation.GetMapping

				interface Contract {
					@GetMapping("/x")
					fun declared(): String
				}

				abstract class Skeleton {
					@GetMapping("/y")
					abstract fun abstractOne(): String
				}
				""");

		assertThat(indexed.type("example.Contract").getMethods()).extracting(StereotypeMethodElement::getMethodName).containsExactly("declared");
		assertThat(indexed.type("example.Skeleton").getMethods()).extracting(StereotypeMethodElement::getMethodName).containsExactly("abstractOne");
	}

	@Test
	void contentHashesFollowTheSource() throws Exception {
		String template = """
				package example

				import org.springframework.web.bind.annotation.GetMapping

				class Api {
					@GetMapping("/a")
					fun read(): String = "%s"
				}

				class Other
				""";

		Indexed before = index(template.formatted("one"));
		Indexed same = index(template.formatted("one"));
		Indexed changed = index(template.formatted("two"));

		assertThat(same.type("example.Api").getContentHash()).isEqualTo(before.type("example.Api").getContentHash());
		assertThat(changed.type("example.Api").getMethods().get(0).getContentHash()).isNotEqualTo(before.type("example.Api").getMethods().get(0).getContentHash());
		assertThat(changed.type("example.Other").getContentHash()).isEqualTo(before.type("example.Other").getContentHash());
		assertThat(before.type("example.Api").getContentHash()).isNotEqualTo(before.type("example.Other").getContentHash());
	}

	@Test
	void windowsLineSeparatorsDoNotMoveLocations() throws Exception {
		String source = "package example\r\n\r\nimport org.springframework.web.bind.annotation.GetMapping\r\n\r\n"
				+ "class Api {\r\n\r\n\t@GetMapping(\"/a\")\r\n\tfun read() {}\r\n}\r\n\r\nclass Other\r\n";
		Indexed indexed = index(source);

		assertThat(indexed.textAt(indexed.type("example.Api").getLocation())).isEqualTo("Api");
		assertThat(indexed.textAt(indexed.type("example.Api").getMethods().get(0).getLocation())).isEqualTo("read");
		assertThat(indexed.textAt(indexed.type("example.Other").getLocation())).isEqualTo("Other");
	}

	@Test
	void unicodeInTheSourceDoesNotMoveLocations() throws Exception {
		String source = "package example\n\n// größe ✓ 日本語 😀\nimport org.springframework.stereotype.Service\n\n@Service\nclass Größe\n\nclass After\n";
		Indexed indexed = index(source);

		assertThat(indexed.textAt(indexed.type("example.Größe").getLocation())).isEqualTo("Größe");
		assertThat(indexed.textAt(indexed.type("example.After").getLocation())).isEqualTo("After");
	}

	@Test
	void sourceWithSyntaxErrorsStillIndexesWhatIsThere() throws Exception {
		Indexed indexed = index("""
				package example

				import org.springframework.stereotype.Service

				@Service
				class Good

				class Broken {
					fun oops( {
				}
				""");

		assertThat(indexed.typeNames()).contains("example.Good");
		assertThat(indexed.type("example.Good").getAnnotationTypes()).contains(SERVICE);
	}

	@Test
	void emptyAndCommentOnlySources() throws Exception {
		assertThat(index("").types()).isEmpty();
		assertThat(index("package example\n// nothing here\n").types()).isEmpty();
	}

	@Test
	void unresolvableAnnotationsAreLeftOut() throws Exception {
		Indexed indexed = index("""
				package example

				import does.not.Exist
				import org.springframework.stereotype.Service

				@Exist
				@Service
				class Partly
				""");

		assertThat(indexed.type("example.Partly").getAnnotationTypes()).contains(SERVICE).doesNotContain("does.not.Exist");
	}

}
