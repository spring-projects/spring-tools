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
package org.springframework.ide.vscode.boot.kotlin.jdt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.kotlin.ast.KotlinDomBuilder;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinDeclarationIndex;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypeResolver;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinPsiReader;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;

/**
 * The JDT bridge: an AST built from Kotlin, with the JDT binding resolver installed, answers the
 * plain JDT {@code resolveBinding()} calls.
 *
 * @author Martin Lippert
 */
public class KotlinJdtBridgeTest {

	private static final KotlinPsiReader reader = new KotlinPsiReader();

	private static final String SOURCE = """
			package example.bridge

			import org.springframework.stereotype.Service
			import org.springframework.web.bind.annotation.RequestMapping
			import org.springframework.web.bind.annotation.RequestMethod
			import org.springframework.web.bind.annotation.RestController

			interface Greeter

			open class Base

			@Service
			class GreeterService : Base(), Greeter {

				@RequestMapping(path = ["/a", "/b"], method = [RequestMethod.GET], consumes = ["text/plain"])
				fun greet(name: String, times: Int): String = name

				class Nested
			}

			@RestController
			class Controller {
				@RequestMapping("/x")
				fun handle() {}
			}
			""";

	private MavenJavaProject project;

	@AfterAll
	static void tearDown() {
		reader.dispose();
	}

	@BeforeEach
	void setup() throws Exception {
		project = ProjectsHarness.INSTANCE.mavenProjectAlreadyBuilt("test-stereotypes-support");
	}

	private CompilationUnit build(String source) {
		KotlinSyntax.File file = reader.read("Test.kt", source);
		KotlinDeclarationIndex declarations = new KotlinDeclarationIndex();
		declarations.put("Test.kt", file);
		return new KotlinDomBuilder().build(file, new KotlinTypeResolver(project.getIndex(), declarations)).compilationUnit();
	}

	private static TypeDeclaration type(CompilationUnit cu, String name) {
		return ((List<?>) cu.types()).stream().map(TypeDeclaration.class::cast)
				.filter(type -> type.getName().getIdentifier().equals(name)).findFirst().orElseThrow();
	}

	@Test
	void theNodesResolveThroughJdtOnly() {
		CompilationUnit cu = build(SOURCE);

		// every AST gets a resolver of its own, the resolver class is defined only once
		CompilationUnit other = build("class Other");
		assertThat(((TypeDeclaration) other.types().get(0)).resolveBinding().getQualifiedName()).isEqualTo("Other");
		assertThat(type(cu, "Controller").resolveBinding().getQualifiedName()).isEqualTo("example.bridge.Controller");
	}

	@Test
	void typeBindings() {
		CompilationUnit cu = build(SOURCE);
		ITypeBinding service = type(cu, "GreeterService").resolveBinding();

		assertThat(service).isNotNull();
		assertThat(service.getQualifiedName()).isEqualTo("example.bridge.GreeterService");
		assertThat(service.getBinaryName()).isEqualTo("example.bridge.GreeterService");
		assertThat(service.getKey()).isEqualTo("Lexample/bridge/GreeterService;");
		assertThat(service.getPackage().getName()).isEqualTo("example.bridge");
		assertThat(service.isClass()).isTrue();
		assertThat(service.isFromSource()).isTrue();
		assertThat(service.getSuperclass().getQualifiedName()).isEqualTo("example.bridge.Base");
		assertThat(Arrays.stream(service.getInterfaces()).map(ITypeBinding::getQualifiedName)).containsExactly("example.bridge.Greeter");
		assertThat(service.getSuperclass().getSuperclass().getQualifiedName()).isEqualTo("java.lang.Object");

		// the same type is the same binding, as with JDT
		assertThat(type(cu, "GreeterService").resolveBinding()).isSameAs(service);

		assertThat(service.getDeclaringClass()).isNull();
		ITypeBinding nested = type(cu, "GreeterService").getTypes()[0].resolveBinding();
		assertThat(nested.getBinaryName()).isEqualTo("example.bridge.GreeterService$Nested");
		assertThat(nested.getQualifiedName()).isEqualTo("example.bridge.GreeterService.Nested");
		assertThat(nested.getDeclaringClass()).isSameAs(service);
		assertThat(nested.isMember()).isTrue();

		// classpath types, through Jandex
		assertThat(service.isSubTypeCompatible(service.getInterfaces()[0])).isTrue();
		assertThat(service.isSubTypeCompatible(type(cu, "Controller").resolveBinding())).isFalse();
	}

	@Test
	void annotationsAndMetaAnnotationsWithTheJdtPathOfAnnotationHierarchies() {
		CompilationUnit cu = build(SOURCE);
		ITypeBinding service = type(cu, "GreeterService").resolveBinding();

		IAnnotationBinding[] annotations = service.getAnnotations();
		assertThat(annotations).hasSize(1);
		assertThat(annotations[0].getAnnotationType().getQualifiedName()).isEqualTo("org.springframework.stereotype.Service");

		// the meta-annotations come from the classpath
		AnnotationHierarchies hierarchies = new AnnotationHierarchies();
		assertThat(hierarchies.isAnnotatedWith((IBinding) service, Annotations.COMPONENT)).isTrue();
		assertThat(hierarchies.isAnnotatedWith((IBinding) type(cu, "Controller").resolveBinding(), Annotations.CONTROLLER)).isTrue();
		assertThat(hierarchies.isAnnotatedWith((IBinding) service, Annotations.CONTROLLER)).isFalse();

		// annotation nodes resolve as JDT expressions do
		Annotation node = (Annotation) type(cu, "GreeterService").modifiers().get(0);
		assertThat(node.resolveTypeBinding().getQualifiedName()).isEqualTo("org.springframework.stereotype.Service");
		assertThat(node.resolveAnnotationBinding().getAnnotationType()).isSameAs(annotations[0].getAnnotationType());
		assertThat(((ITypeBinding) node.getTypeName().resolveBinding()).getQualifiedName()).isEqualTo("org.springframework.stereotype.Service");
	}

	@Test
	void methodBindingsAndAttributeValues() {
		CompilationUnit cu = build(SOURCE);
		MethodDeclaration greet = type(cu, "GreeterService").getMethods()[0];
		IMethodBinding method = greet.resolveBinding();

		assertThat(method.getName()).isEqualTo("greet");
		assertThat(method.getDeclaringClass().getQualifiedName()).isEqualTo("example.bridge.GreeterService");
		assertThat(Arrays.stream(method.getParameterTypes()).map(ITypeBinding::getQualifiedName)).containsExactly("java.lang.String", "int");
		assertThat(method.getReturnType().getQualifiedName()).isEqualTo("java.lang.String");
		assertThat(method.getKey()).isEqualTo("Lexample/bridge/GreeterService;.greet(Ljava/lang/String;I)Ljava/lang/String;");

		assertThat(method.getAnnotations()).hasSize(1);
		IAnnotationBinding mapping = method.getAnnotations()[0];
		assertThat(mapping.getAnnotationType().getQualifiedName()).isEqualTo("org.springframework.web.bind.annotation.RequestMapping");

		IMemberValuePairBinding path = Arrays.stream(mapping.getDeclaredMemberValuePairs()).filter(p -> p.getName().equals("path")).findFirst().orElseThrow();
		assertThat((Object[]) path.getValue()).containsExactly("/a", "/b");

		IMemberValuePairBinding httpMethod = Arrays.stream(mapping.getDeclaredMemberValuePairs()).filter(p -> p.getName().equals("method")).findFirst().orElseThrow();
		Object[] methods = (Object[]) httpMethod.getValue();
		assertThat(methods).hasSize(1);
		assertThat(((IBinding) methods[0]).getName()).isEqualTo("GET");

		// what the existing JDT based helpers read from the annotation nodes
		Annotation node = (Annotation) greet.modifiers().get(0);
		assertThat(ASTUtils.getExpressionValueAsArray(ASTUtils.getAttribute(node, "path").get(), dependency -> {})).containsExactly("/a", "/b");
		assertThat(ASTUtils.getExpressionValueAsArray(ASTUtils.getAttribute(node, "method").get(), dependency -> {})).containsExactly("GET");

		// Unit is void
		assertThat(type(cu, "Controller").getMethods()[0].resolveBinding().getReturnType().getQualifiedName()).isEqualTo("void");
	}

}
