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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.kotlin.jdt.KotlinJdtBindings;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedAnnotation;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedType;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinPsiReader;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax;
import org.springframework.ide.vscode.commons.java.IAnnotation;
import org.springframework.ide.vscode.commons.java.IType;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;

/**
 * @author Martin Lippert
 */
public class KotlinTypeResolverTest {

	private static final KotlinPsiReader reader = new KotlinPsiReader();

	private MavenJavaProject project;
	private KotlinDeclarationIndex declarations;
	private KotlinTypeResolver resolver;

	@AfterAll
	static void tearDown() {
		reader.dispose();
	}

	@BeforeEach
	void setup() throws Exception {
		project = ProjectsHarness.INSTANCE.mavenProjectAlreadyBuilt("test-stereotypes-support");
		declarations = new KotlinDeclarationIndex();
		resolver = new KotlinTypeResolver(project.getIndex(), declarations);
	}

	/** parses the source and registers its classes in the declarations of the project */
	private KotlinSyntax.File file(String name, String source) {
		KotlinSyntax.File file = reader.read(name + ".kt", source);
		declarations.put(name, file);
		resolver.clearCaches();
		return file;
	}

	private ResolvedType type(String binaryName) {
		KotlinSourceClass source = declarations.find(binaryName);
		assertThat(source).as(binaryName).isNotNull();
		return resolver.sourceType(source);
	}

	private List<String> annotationNames(ResolvedType type) {
		return type.getAnnotations().stream().map(a -> a.getAnnotationType().getQualifiedName()).toList();
	}

	/** the JDT binding that the indexers see for the type */
	private ITypeBinding jdt(ResolvedType type) {
		return new KotlinJdtBindings(resolver).type(type);
	}

	private List<String> metaAnnotationNames(ResolvedType type) {
		List<String> result = new ArrayList<>();
		for (Iterator<IAnnotationBinding> itr = new AnnotationHierarchies().iterator(jdt(type)); itr.hasNext();) {
			result.add(itr.next().getAnnotationType().getQualifiedName());
		}
		return result;
	}

	@Test
	void namesOfSourceTypes() {
		file("A", """
				package com.example

				class Outer {
					class Inner {
						class Deeper
					}
				}
				""");

		ResolvedType outer = type("com.example.Outer");
		assertThat(outer.getName()).isEqualTo("Outer");
		assertThat(outer.getQualifiedName()).isEqualTo("com.example.Outer");
		assertThat(outer.getBinaryName()).isEqualTo("com.example.Outer");
		assertThat(outer.getKey()).isEqualTo("Lcom/example/Outer;");

		ResolvedType deeper = type("com.example.Outer$Inner$Deeper");
		assertThat(deeper.getName()).isEqualTo("Deeper");
		assertThat(deeper.getQualifiedName()).isEqualTo("com.example.Outer.Inner.Deeper");
		assertThat(deeper.getBinaryName()).isEqualTo("com.example.Outer$Inner$Deeper");
		assertThat(deeper.getKey()).isEqualTo("Lcom/example/Outer$Inner$Deeper;");
	}

	@Test
	void kindsOfSourceTypes() {
		file("A", """
				package a

				class C
				interface I
				annotation class Ann
				""");

		assertThat(type("a.C").isInterface()).isFalse();
		assertThat(type("a.C").isAnnotation()).isFalse();
		assertThat(type("a.I").isInterface()).isTrue();
		assertThat(type("a.I").isAnnotation()).isFalse();
		assertThat(type("a.Ann").isAnnotation()).isTrue();
		assertThat(type("a.Ann").isInterface()).isTrue(); // as for JDT bindings, annotation types are interfaces, too
	}

	@Test
	void annotationsFromExplicitImports() {
		file("A", """
				package a

				import org.springframework.stereotype.Service

				@Service
				class MyService
				""");

		assertThat(annotationNames(type("a.MyService"))).containsExactly("org.springframework.stereotype.Service");
	}

	@Test
	void annotationsFromStarImportsAndFullyQualified() {
		file("A", """
				package a

				import org.springframework.web.bind.annotation.*

				@RestController
				@org.springframework.stereotype.Service
				class MyController
				""");

		assertThat(annotationNames(type("a.MyController"))).containsExactly("org.springframework.web.bind.annotation.RestController",
				"org.springframework.stereotype.Service");
	}

	@Test
	void annotationsThatCannotBeResolvedAreLeftOut() {
		file("A", """
				package a

				@DoesNotExist
				@org.springframework.stereotype.Service
				class MyService
				""");

		assertThat(annotationNames(type("a.MyService"))).containsExactly("org.springframework.stereotype.Service");
	}

	@Test
	void importAliasReplacesTheOriginalName() {
		file("A", """
				package a

				import org.springframework.stereotype.Service as Svc

				@Svc
				class MyService
				""");

		assertThat(annotationNames(type("a.MyService"))).containsExactly("org.springframework.stereotype.Service");
	}

	@Test
	void metaAnnotationsOfAnnotationsFromTheClasspath() {
		file("A", """
				package a

				import org.springframework.web.bind.annotation.RestController

				@RestController
				class MyController
				""");

		assertThat(metaAnnotationNames(type("a.MyController"))).contains("org.springframework.web.bind.annotation.RestController",
				"org.springframework.stereotype.Controller", "org.springframework.stereotype.Component");
	}

	@Test
	void metaAnnotationsDoNotIncludeAnnotationsOfTheMembersOfAnnotations() {
		file("A", """
				package a

				import org.springframework.web.bind.annotation.GetMapping

				class MyController {
					@GetMapping
					fun hello() {}
				}
				""");

		// @AliasFor sits on the members of GetMapping, not on GetMapping itself
		ResolvedType controller = type("a.MyController");
		assertThat(controller.getAnnotations()).isEmpty();

		KotlinSourceClass source = declarations.find("a.MyController");
		ResolvedAnnotation getMapping = resolver.resolveAnnotation(source.declaration().functions().get(0).annotations().get(0), source.file(), source);
		List<String> hierarchy = new ArrayList<>();
		for (Iterator<IAnnotationBinding> itr = new AnnotationHierarchies().iterator(new KotlinJdtBindings(resolver).annotation(getMapping)); itr.hasNext();) {
			hierarchy.add(itr.next().getAnnotationType().getQualifiedName());
		}
		assertThat(hierarchy).contains("org.springframework.web.bind.annotation.GetMapping", "org.springframework.web.bind.annotation.RequestMapping");
		assertThat(hierarchy).doesNotContain("org.springframework.core.annotation.AliasFor");
	}

	@Test
	void declaredAnnotationsOfClasspathTypesLeaveOutTheOnesOfTheirMembers() {
		IType type = project.getIndex().findType("org.springframework.web.bind.annotation.RestController");

		List<String> all = type.getAnnotations().map(IAnnotation::fqName).toList();
		List<String> declared = type.getDeclaredAnnotations().map(IAnnotation::fqName).toList();

		// the Jandex based type reports the annotations of the methods of the annotation type as well
		assertThat(all).contains("org.springframework.core.annotation.AliasFor");
		assertThat(declared).doesNotContain("org.springframework.core.annotation.AliasFor");
		assertThat(declared).contains("org.springframework.stereotype.Controller", "org.springframework.web.bind.annotation.ResponseBody");
	}

	@Test
	void annotationsDeclaredInKotlinSourcesOfOtherFiles() {
		file("Stereo", """
				package a.custom

				import org.springframework.stereotype.Component

				@Component
				annotation class MyStereotype
				""");
		file("A", """
				package a

				import a.custom.MyStereotype

				@MyStereotype
				class MyBean
				""");

		assertThat(annotationNames(type("a.MyBean"))).containsExactly("a.custom.MyStereotype");
		assertThat(metaAnnotationNames(type("a.MyBean"))).startsWith("a.custom.MyStereotype", "org.springframework.stereotype.Component");
	}

	@Test
	void classesOfTheSamePackageNeedNoImport() {
		file("Stereo", """
				package a

				import org.springframework.stereotype.Component

				@Component
				annotation class MyStereotype
				""");
		file("A", """
				package a

				@MyStereotype
				class MyBean
				""");

		assertThat(annotationNames(type("a.MyBean"))).containsExactly("a.MyStereotype");
	}

	@Test
	void nestedClassesAreFoundFromInsideAndOutside() {
		file("A", """
				package a

				import org.springframework.stereotype.Component

				class Outer {

					@Component
					annotation class Marker

					@Marker
					class Inner

					fun usesInner(inner: Inner, other: Outer.Inner) {}
				}

				@Outer.Marker
				class Outside
				""");

		assertThat(annotationNames(type("a.Outer$Inner"))).containsExactly("a.Outer.Marker");
		assertThat(annotationNames(type("a.Outside"))).containsExactly("a.Outer.Marker");

		KotlinSourceClass outer = declarations.find("a.Outer");
		KotlinSyntax.Function function = outer.declaration().functions().get(0);
		assertThat(resolver.resolveTypeReference(function.parameters().get(0).type(), outer.file(), outer).getBinaryName()).isEqualTo("a.Outer$Inner");
		assertThat(resolver.resolveTypeReference(function.parameters().get(1).type(), outer.file(), outer).getBinaryName()).isEqualTo("a.Outer$Inner");
	}

	@Test
	void superclassAndInterfaces() {
		file("A", """
				package a

				import java.io.Serializable

				open class Base
				interface Marker
				class Impl : Base(), Marker, Serializable
				""");

		ResolvedType impl = type("a.Impl");

		assertThat(impl.getSuperclass().getQualifiedName()).isEqualTo("a.Base");
		assertThat(impl.getInterfaces()).extracting(ResolvedType::getQualifiedName).containsExactly("a.Marker", "java.io.Serializable");
		assertThat(ASTUtils.findSupertypes(jdt(impl))).contains("a.Base", "a.Marker", "java.io.Serializable", "java.lang.Object");
	}

	@Test
	void defaultSupertypes() {
		file("A", """
				package a

				class Plain
				interface I
				annotation class Ann
				""");

		assertThat(type("a.Plain").getSuperclass().getQualifiedName()).isEqualTo("java.lang.Object");
		assertThat(type("a.Plain").getInterfaces()).isEmpty();
		assertThat(type("a.I").getSuperclass()).isNull();
		assertThat(type("a.Ann").getSuperclass()).isNull();
		assertThat(type("a.Ann").getInterfaces()).extracting(ResolvedType::getQualifiedName).containsExactly("java.lang.annotation.Annotation");
	}

	@Test
	void supertypesThatAreKotlinAliasesOfJavaClasses() {
		file("A", """
				package a

				class MyException : Exception()
				""");

		assertThat(type("a.MyException").getSuperclass().getQualifiedName()).isEqualTo("java.lang.Exception");
		assertThat(ASTUtils.findSupertypes(jdt(type("a.MyException")))).contains("java.lang.Exception", "java.lang.Throwable", "java.lang.Object");
	}

	@Test
	void supertypesAreFoundThroughTheClasspathToo() {
		file("A", """
				package a

				import org.springframework.web.bind.annotation.RestController

				@RestController
				open class Base

				class Sub : Base()
				""");

		// the annotations of the supertypes are the ones of the type hierarchy
		List<String> annotationsInHierarchy = new ArrayList<>();
		for (Iterator<ITypeBinding> itr = ASTUtils.getHierarchyTypesBreadthFirstIterator(jdt(type("a.Sub"))); itr.hasNext();) {
			for (IAnnotationBinding a : itr.next().getAnnotations()) {
				annotationsInHierarchy.add(a.getAnnotationType().getQualifiedName());
			}
		}
		assertThat(annotationsInHierarchy).containsExactly("org.springframework.web.bind.annotation.RestController");
	}

	@Test
	void cyclicHierarchiesAreCut() {
		file("A", """
				package a

				open class A : B()
				open class B : A()
				""");

		List<String> names = new ArrayList<>();
		for (Iterator<ITypeBinding> itr = ASTUtils.getHierarchyTypesBreadthFirstIterator(jdt(type("a.A"))); itr.hasNext();) {
			names.add(itr.next().getQualifiedName());
		}

		// as with JDT, a supertype that closes the cycle is left out, so the JDT based helpers that
		// walk the hierarchy without guarding against cycles terminate
		assertThat(names).containsExactly("a.A", "java.lang.Object");
	}

	@Test
	void typesAsTheJvmSeesThem() {
		KotlinSyntax.File file = file("A", """
				package a

				class A {
					fun m(a: String, b: Int, c: Int?, d: Boolean, e: List<Int>, f: Map<String, List<String>>, g: Array<String>, h: IntArray, i: Any, j: Unit?, k: Foo) {}
				}
				""");
		KotlinSourceClass source = declarations.find("a.A");

		List<KotlinSyntax.Parameter> parameters = source.declaration().functions().get(0).parameters();
		List<ResolvedType> types = parameters.stream().map(p -> resolver.resolveTypeReference(p.type(), file, source)).toList();

		assertThat(types).extracting(ResolvedType::getQualifiedName).containsExactly("java.lang.String", "int", "java.lang.Integer", "boolean",
				"java.util.List<java.lang.Integer>", "java.util.Map<java.lang.String,java.util.List<java.lang.String>>", "java.lang.String[]", "int[]",
				"java.lang.Object", "kotlin.Unit", "Foo");
		assertThat(types).extracting(ResolvedType::getName).containsExactly("String", "int", "Integer", "boolean", "List<Integer>",
				"Map<String,List<String>>", "String[]", "int[]", "Object", "Unit", "Foo");
		assertThat(types).extracting(ResolvedType::getBinaryName).containsExactly("java.lang.String", "I", "java.lang.Integer", "Z",
				"java.util.List", "java.util.Map", "[Ljava.lang.String;", "[I", "java.lang.Object", "kotlin.Unit", "Foo");
		assertThat(types.get(4).isParameterizedType()).isTrue();
		assertThat(types.get(4).getErasure().getQualifiedName()).isEqualTo("java.util.List");
	}

	@Test
	void returnTypes() {
		KotlinSyntax.File file = file("A", """
				package a

				class A {
					fun none() {}
					fun unit(): Unit {}
					fun string(): String = ""
					fun nullableUnit(): Unit? = null
				}
				""");
		KotlinSourceClass source = declarations.find("a.A");

		List<ResolvedType> types = source.declaration().functions().stream()
				.map(f -> resolver.resolveReturnType(f.returnType(), file, source))
				.toList();

		assertThat(types).extracting(ResolvedType::getName).containsExactly("void", "void", "String", "Unit");
	}

	@Test
	void unresolvableNamesAreKeptAsWritten() {
		KotlinSyntax.File file = file("A", """
				package a

				class A {
					fun m(a: com.unknown.Thing, b: Unknown) {}
				}
				""");
		KotlinSourceClass source = declarations.find("a.A");

		List<KotlinSyntax.Parameter> parameters = source.declaration().functions().get(0).parameters();

		assertThat(resolver.resolveTypeReference(parameters.get(0).type(), file, source).getQualifiedName()).isEqualTo("com.unknown.Thing");
		assertThat(resolver.resolveTypeReference(parameters.get(0).type(), file, source).getName()).isEqualTo("Thing");
		assertThat(resolver.resolveTypeReference(parameters.get(1).type(), file, source).getQualifiedName()).isEqualTo("Unknown");
	}

	@Test
	void typesThatAreEqualAreTheSame() {
		file("A", """
				package a

				class A
				""");

		assertThat(type("a.A")).isEqualTo(type("a.A"));
		assertThat(type("a.A").hashCode()).isEqualTo(type("a.A").hashCode());
		assertThat(resolver.resolveBinaryName("java.lang.String")).isEqualTo(resolver.resolveBinaryName("java.lang.String"));
		assertThat(resolver.resolveBinaryName("java.lang.String")).isNotEqualTo(type("a.A"));
	}

	@Test
	void removedFilesAreNoLongerFound() {
		file("A", """
				package a

				class A
				""");
		assertThat(resolver.resolveBinaryName("a.A")).isNotNull();

		declarations.remove("A");
		resolver.clearCaches();

		assertThat(resolver.resolveBinaryName("a.A")).isNull();
	}

}
