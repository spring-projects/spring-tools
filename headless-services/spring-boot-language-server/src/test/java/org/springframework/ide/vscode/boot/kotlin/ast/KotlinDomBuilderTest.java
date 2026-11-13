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
package org.springframework.ide.vscode.boot.kotlin.ast;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.springframework.ide.vscode.boot.kotlin.AllKotlinParsers;
import org.springframework.ide.vscode.boot.kotlin.KotlinTestParsers;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinDeclarationIndex;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinNodeBindings;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypeResolver;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntaxReader;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;

/**
 * @author Martin Lippert
 */
@AllKotlinParsers
public class KotlinDomBuilderTest {

	@Parameter
	String parser;

	private KotlinSyntaxReader reader() {
		return KotlinTestParsers.reader(parser);
	}

	private MavenJavaProject project;
	private KotlinDomBuilder builder = new KotlinDomBuilder();

	@BeforeEach
	void setup() throws Exception {
		project = ProjectsHarness.INSTANCE.mavenProjectAlreadyBuilt("test-stereotypes-support");
	}

	private KotlinDomBuilder.Result build(String source) {
		KotlinSyntax.File file = reader().read("Test.kt", source);
		KotlinDeclarationIndex declarations = new KotlinDeclarationIndex();
		declarations.put("Test.kt", file);
		return builder.build(file, new KotlinTypeResolver(project.getIndex(), declarations));
	}

	private static String text(String source, ASTNode node) {
		return source.substring(node.getStartPosition(), node.getStartPosition() + node.getLength());
	}

	private static <T extends ASTNode> List<T> find(CompilationUnit cu, Class<T> type) {
		List<T> result = new ArrayList<>();
		cu.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				if (type.isInstance(node)) {
					result.add(type.cast(node));
				}
			}
		});
		return result;
	}

	@Test
	void packageAndTypes() {
		String source = """
				package com.example.demo

				import org.springframework.stereotype.Service

				@Service
				class MyService {
				}

				interface MyApi

				annotation class MyAnnotation

				object MySingleton
				""";

		CompilationUnit cu = build(source).compilationUnit();

		assertThat(cu.getPackage().getName().getFullyQualifiedName()).isEqualTo("com.example.demo");
		assertThat(text(source, cu.getPackage())).isEqualTo("package com.example.demo");
		assertThat(text(source, cu.getPackage().getName())).isEqualTo("com.example.demo");
		assertThat(cu.getStartPosition()).isEqualTo(0);
		assertThat(cu.getLength()).isEqualTo(source.length());

		assertThat(cu.types()).hasSize(4);

		TypeDeclaration service = (TypeDeclaration) cu.types().get(0);
		assertThat(service.getName().getIdentifier()).isEqualTo("MyService");
		assertThat(service.isInterface()).isFalse();
		assertThat(text(source, service)).isEqualTo("@Service\nclass MyService {\n}");
		assertThat(text(source, service.getName())).isEqualTo("MyService");

		TypeDeclaration api = (TypeDeclaration) cu.types().get(1);
		assertThat(api.getName().getIdentifier()).isEqualTo("MyApi");
		assertThat(api.isInterface()).isTrue();

		assertThat(cu.types().get(2)).isInstanceOf(AnnotationTypeDeclaration.class);
		assertThat(((AbstractTypeDeclaration) cu.types().get(2)).getName().getIdentifier()).isEqualTo("MyAnnotation");

		assertThat(((TypeDeclaration) cu.types().get(3)).getName().getIdentifier()).isEqualTo("MySingleton");
	}

	@Test
	void fileWithoutPackage() {
		CompilationUnit cu = build("class A").compilationUnit();

		assertThat(cu.getPackage()).isNull();
		assertThat(cu.types()).hasSize(1);
	}

	@Test
	void enumClassesAreLeftOut() {
		CompilationUnit cu = build("""
				package a

				enum class E { ONE, TWO }
				class C
				""").compilationUnit();

		assertThat(cu.types()).hasSize(1);
		assertThat(((TypeDeclaration) cu.types().get(0)).getName().getIdentifier()).isEqualTo("C");
	}

	@Test
	void nestedTypesAndCompanionObjects() {
		String source = """
				package a

				class Outer {
					class Inner
					companion object
				}
				""";

		CompilationUnit cu = build(source).compilationUnit();

		TypeDeclaration outer = (TypeDeclaration) cu.types().get(0);
		assertThat(outer.getTypes()).extracting(t -> t.getName().getIdentifier()).containsExactly("Inner", "Companion");
		assertThat(text(source, outer.getTypes()[0])).isEqualTo("class Inner");
	}

	@Test
	void annotationsOfAllKinds() {
		String source = """
				package a

				@Marker
				@Single("path")
				@Normal(value = "x", names = ["a", "b"], type = Foo::class, count = 3, flag = false, ref = Constants.X)
				class A
				""";

		CompilationUnit cu = build(source).compilationUnit();
		List<Annotation> annotations = new ArrayList<>(ASTUtils.getAnnotations((TypeDeclaration) cu.types().get(0)));

		assertThat(annotations).hasSize(3);

		assertThat(annotations.get(0)).isInstanceOf(MarkerAnnotation.class);
		assertThat(annotations.get(0).getTypeName().getFullyQualifiedName()).isEqualTo("Marker");
		assertThat(text(source, annotations.get(0))).isEqualTo("@Marker");

		assertThat(annotations.get(1)).isInstanceOf(SingleMemberAnnotation.class);
		SingleMemberAnnotation single = (SingleMemberAnnotation) annotations.get(1);
		assertThat(ASTUtils.getExpressionValueAsString(single.getValue())).isEqualTo("path");
		assertThat(text(source, single.getValue())).isEqualTo("\"path\"");

		assertThat(annotations.get(2)).isInstanceOf(NormalAnnotation.class);
		assertThat(ASTUtils.getAttribute(annotations.get(2), "value").map(ASTUtils::getExpressionValueAsString)).hasValue("x");
		assertThat(ASTUtils.getAttribute(annotations.get(2), "names").map(e -> ASTUtils.getExpressionValueAsArray(e, a -> {}))).hasValueSatisfying(
				values -> assertThat(values).containsExactly("a", "b"));
		assertThat(ASTUtils.getAttribute(annotations.get(2), "count").map(e -> text(source, e))).hasValue("3");
		assertThat(ASTUtils.getAttribute(annotations.get(2), "type").map(e -> text(source, e))).hasValue("Foo::class");
		assertThat(ASTUtils.getAttribute(annotations.get(2), "ref").map(e -> text(source, e))).hasValue("Constants.X");
	}

	@Test
	void qualifiedAnnotationNames() {
		String source = """
				package a

				@org.springframework.stereotype.Service
				class A
				""";

		CompilationUnit cu = build(source).compilationUnit();
		Annotation annotation = ASTUtils.getAnnotations((TypeDeclaration) cu.types().get(0)).iterator().next();

		assertThat(annotation.getTypeName().getFullyQualifiedName()).isEqualTo("org.springframework.stereotype.Service");
		assertThat(text(source, annotation.getTypeName())).isEqualTo("org.springframework.stereotype.Service");
	}

	@Test
	void methods() {
		String source = """
				package a

				class A {
					fun plain() {}

					@GetMapping("/x")
					fun annotated(name: String) {}
				}
				""";

		CompilationUnit cu = build(source).compilationUnit();
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);

		assertThat(type.getMethods()).extracting(m -> m.getName().getIdentifier()).containsExactly("plain", "annotated");
		MethodDeclaration annotated = type.getMethods()[1];
		assertThat(text(source, annotated.getName())).isEqualTo("annotated");
		assertThat(text(source, annotated)).startsWith("@GetMapping(\"/x\")").endsWith("fun annotated(name: String) {}");
		assertThat(ASTUtils.getAnnotations(annotated)).hasSize(1);
		assertThat(ASTUtils.getAnnotations(type.getMethods()[0])).isEmpty();
	}

	@Test
	void namesThatAreNotValidJavaIdentifiers() {
		CompilationUnit cu = build("""
				package a

				class A {
					fun `should do something`() {}
					fun native() {}
				}
				""").compilationUnit();

		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);

		assertThat(type.getMethods()).extracting(m -> m.getName().getIdentifier()).containsExactly("should_do_something", "native_");
	}

	@Test
	void theNodesHaveJdtBindings() {
		KotlinDomBuilder.Result result = build("""
				package a

				class A
				""");

		assertThat(result.bindings()).isInstanceOf(KotlinNodeBindings.class);
		assertThat(((TypeDeclaration) result.compilationUnit().types().get(0)).resolveBinding().getQualifiedName()).isEqualTo("a.A");
	}

	@Test
	void bindingsOfTypesAnnotationsAndMethods() {
		CompilationUnit cu = build("""
				package com.example

				import org.springframework.web.bind.annotation.GetMapping
				import org.springframework.web.bind.annotation.RestController

				@RestController
				class Greeter {

					@GetMapping("/hello")
					fun hello(name: String, times: Int, tags: List<String>) {}
				}
				""").compilationUnit();

		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);

		ITypeBinding resolvedType = type.resolveBinding();
		assertThat(resolvedType.getQualifiedName()).isEqualTo("com.example.Greeter");
		assertThat(cu.getPackage().resolveBinding().getName()).isEqualTo("com.example");

		Annotation annotation = ASTUtils.getAnnotations(type).iterator().next();
		IAnnotationBinding resolvedAnnotation = annotation.resolveAnnotationBinding();
		assertThat(resolvedAnnotation.getAnnotationType().getQualifiedName()).isEqualTo("org.springframework.web.bind.annotation.RestController");
		assertThat(resolvedType.getAnnotations()).extracting(a -> a.getAnnotationType()).contains(resolvedAnnotation.getAnnotationType());

		MethodDeclaration method = type.getMethods()[0];
		assertThat(ASTUtils.getMethodSignature(method, true)).isEqualTo("com.example.Greeter.hello(java.lang.String, I, java.util.List) : V");
		assertThat(ASTUtils.getMethodSignature(method, false)).isEqualTo("Greeter.hello(String, int, List<String>) : void");
		assertThat(ASTUtils.getAnnotations(method).iterator().next().resolveTypeBinding().getQualifiedName())
				.isEqualTo("org.springframework.web.bind.annotation.GetMapping");
	}

	@Test
	void nodesThatWereNotBuiltFromSourceHaveNoBindings() {
		CompilationUnit cu = build("""
				package a

				class A
				""").compilationUnit();

		TypeDeclaration other = cu.getAST().newTypeDeclaration();

		assertThat(other.resolveBinding()).isNull();
		assertThat(cu.getAST().newMethodDeclaration().resolveBinding()).isNull();
		assertThat(cu.getAST().newMarkerAnnotation().resolveAnnotationBinding()).isNull();
	}

	@Test
	void methodSignaturesAreTheSameAsForTheEquivalentJavaCode() throws Exception {
		String kotlin = """
				package test

				class Example {
					fun mixed(name: String, count: Int, boxed: Int?, numbers: List<Int>, map: Map<String, List<String>>, names: Array<String>, flags: BooleanArray, any: Any) {}
					fun withReturn(): String = ""
					fun unit(): Unit {}
				}
				""";
		String java = """
				package test;

				import java.util.List;
				import java.util.Map;

				public class Example {
					public void mixed(String name, int count, Integer boxed, List<Integer> numbers, Map<String, List<String>> map, String[] names, boolean[] flags, Object any) {}
					public String withReturn() { return ""; }
					public void unit() {}
				}
				""";

		TypeDeclaration kotlinType = (TypeDeclaration) build(kotlin).compilationUnit().types().get(0);

		String uri = Paths.get(project.getLocationUri()).resolve("src/main/java/test/Example.java").toUri().toASCIIString();
		CompilationUnit javaCu = CompilationUnitCache.parse2(java.toCharArray(), uri, "Example.java", project);
		TypeDeclaration javaType = (TypeDeclaration) javaCu.types().get(0);

		assertThat(kotlinType.getMethods()).hasSameSizeAs(javaType.getMethods());
		for (int i = 0; i < javaType.getMethods().length; i++) {
			MethodDeclaration kotlinMethod = kotlinType.getMethods()[i];
			MethodDeclaration javaMethod = javaType.getMethods()[i];

			assertThat(ASTUtils.getMethodSignature(kotlinMethod, true)).isEqualTo(ASTUtils.getMethodSignature(javaMethod, true));
			assertThat(ASTUtils.getMethodSignature(kotlinMethod, false)).isEqualTo(ASTUtils.getMethodSignature(javaMethod, false));
		}
	}

	@Test
	void everyNodeLiesInsideItsParent() {
		String source = """
				package a

				import org.springframework.web.bind.annotation.*

				@RestController
				@RequestMapping(value = ["/a", "/b"], produces = ["x"])
				class A {
					@GetMapping("/x")
					fun x() {}
					class Nested {
						@PostMapping
						fun y() {}
					}
				}
				""";

		CompilationUnit cu = build(source).compilationUnit();

		cu.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				assertThat(node.getStartPosition()).as(node.getClass().getSimpleName()).isGreaterThanOrEqualTo(0);
				ASTNode parent = node.getParent();
				if (parent != null) {
					assertThat(node.getStartPosition()).as(node.getClass().getSimpleName()).isGreaterThanOrEqualTo(parent.getStartPosition());
					assertThat(node.getStartPosition() + node.getLength()).as(node.getClass().getSimpleName())
							.isLessThanOrEqualTo(parent.getStartPosition() + parent.getLength());
				}
			}
		});
	}

	@Test
	void constantsAreFoldedIntoLiteralsAtTheirPlace() {
		String source = """
				package a

				import org.springframework.web.bind.annotation.RequestMapping
				import org.springframework.web.bind.annotation.RequestMethod

				object C { const val PATH = "/c" }

				@RequestMapping(path = [C.PATH + "/x", "/t${C.PATH}"], method = [RequestMethod.GET], name = missing)
				class A
				""";

		CompilationUnit cu = build(source).compilationUnit();
		Annotation annotation = ASTUtils.getAnnotations((TypeDeclaration) cu.types().get(1)).iterator().next();

		var path = ASTUtils.getAttribute(annotation, "path").get();
		assertThat(path).isInstanceOf(org.eclipse.jdt.core.dom.ArrayInitializer.class);
		List<?> elements = ((org.eclipse.jdt.core.dom.ArrayInitializer) path).expressions();
		assertThat(elements).allSatisfy(e -> assertThat(e).isInstanceOf(org.eclipse.jdt.core.dom.StringLiteral.class));
		assertThat(ASTUtils.getExpressionValueAsArray(path, dependency -> {})).containsExactly("/c/x", "/t/c");
		assertThat(text(source, (ASTNode) elements.get(0))).isEqualTo("C.PATH + \"/x\"");
		assertThat(text(source, (ASTNode) elements.get(1))).isEqualTo("\"/t${C.PATH}\"");

		// an enum constant stays a name, and so does a constant that is not known
		var method = ASTUtils.getAttribute(annotation, "method").get();
		assertThat(ASTUtils.getExpressionValueAsArray(method, dependency -> {})).containsExactly("GET");
		assertThat(ASTUtils.getAttribute(annotation, "name").get()).isInstanceOf(org.eclipse.jdt.core.dom.SimpleName.class);
	}

	@Test
	void classLiteralsAreResolved() {
		CompilationUnit cu = build("""
				package a

				import org.springframework.context.annotation.Import

				@Import(value = [String::class, Nested::class])
				class A {
					class Nested
				}
				""").compilationUnit();

		List<org.eclipse.jdt.core.dom.TypeLiteral> literals = find(cu, org.eclipse.jdt.core.dom.TypeLiteral.class);

		assertThat(literals).hasSize(2);
		assertThat(literals.get(0).getType().resolveBinding().getQualifiedName()).isEqualTo("java.lang.String");
		assertThat(literals.get(1).getType().resolveBinding()).isNotNull();
	}

}
