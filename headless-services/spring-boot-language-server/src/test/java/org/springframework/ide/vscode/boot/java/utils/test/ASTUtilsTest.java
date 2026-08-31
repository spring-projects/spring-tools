/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils.AnnotationAttribute;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaParserUtils;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;

public class ASTUtilsTest {
	
	private List<Path> createdFiles = new ArrayList<>();
	
	private final String projectName = "test-spring-validations";

	private MavenJavaProject project;

	private Path mySimpleMain;
	private Path myComponent;
	private Path myConcatenatedComponent;
	private Path myConcatenatedConfig;

	
	@BeforeEach
	void setup() throws Exception {
		this.project = ProjectsHarness.INSTANCE.mavenProject(projectName);
		createTestFiles();
	}

	@AfterEach
	void tearDown() {
		clearTestFiles();
	}
	
	@Test
	void testTypeHierarchyIteratorSimpleClass() throws Exception {
		runTestsAgainstTypeDeclaration(mySimpleMain, (type) -> {
			Iterator<ITypeBinding> iter = ASTUtils.getHierarchyTypesBreadthFirstIterator(type.resolveBinding());
			assertNotNull(iter);

			assertEquals("test.MySimpleMain", iter.next().getQualifiedName());
			assertEquals("java.lang.Object", iter.next().getQualifiedName());
			assertFalse(iter.hasNext());
		});
	}
	
	@Test
	void testSupertypesForSimpleClass() throws Exception {
		runTestsAgainstTypeDeclaration(mySimpleMain, (type) -> {
			Set<String> supertypes = ASTUtils.findSupertypes(type.resolveBinding());

			assertEquals(1, supertypes.size());
			assertTrue(supertypes.contains("java.lang.Object"));
		});
	}

	@Test
	void testIsAnyTypeInHierarchyForSimpleClass() throws Exception {
		runTestsAgainstTypeDeclaration(mySimpleMain, (type) -> {
			assertTrue(ASTUtils.isAnyTypeInHierarchy(type.resolveBinding(), List.of("java.lang.Object")));
			assertTrue(ASTUtils.isAnyTypeInHierarchy(type.resolveBinding(), List.of("java.lang.Object", "java.io.Serializable")));
			assertFalse(ASTUtils.isAnyTypeInHierarchy(type.resolveBinding(), List.of("java.io.Serializable")));
			assertFalse(ASTUtils.isAnyTypeInHierarchy(type.resolveBinding(), List.of()));
			
			assertTrue(ASTUtils.isAnyTypeInHierarchy(type.resolveBinding(), List.of("test.MySimpleMain")));
		});
	}

	@Test
	void testAreAllTypesInHierarchyForSimpleClass() throws Exception {
		runTestsAgainstTypeDeclaration(mySimpleMain, (type) -> {
			assertTrue(ASTUtils.areAllTypesInHierarchy(type.resolveBinding(), List.of("java.lang.Object")));
			assertFalse(ASTUtils.areAllTypesInHierarchy(type.resolveBinding(), List.of("java.lang.Object", "java.io.Serializable")));
			assertTrue(ASTUtils.areAllTypesInHierarchy(type.resolveBinding(), List.of()));

			assertTrue(ASTUtils.areAllTypesInHierarchy(type.resolveBinding(), List.of("test.MySimpleMain")));
		});
	}

	@Test
	void testTypeHierarchyIteratorWithSuperclassesAndInterfaces() throws Exception {
		runTestsAgainstTypeDeclaration(myComponent, (type) -> {
			Iterator<ITypeBinding> iter = ASTUtils.getHierarchyTypesBreadthFirstIterator(type.resolveBinding());
			assertNotNull(iter);

			assertEquals("test.MyComponent", iter.next().getQualifiedName());
			assertEquals("test.MyInterface", iter.next().getQualifiedName());
			assertEquals("test.MySuperclass", iter.next().getQualifiedName());
			assertEquals("test.MySuperInterface", iter.next().getQualifiedName());
			assertEquals("test.MySuperclassInterface", iter.next().getQualifiedName());
			assertEquals("java.lang.Object", iter.next().getQualifiedName());
			assertFalse(iter.hasNext());
		});
	}
	
	@Test
	void testTypeHierarchyIteratorWithFullyQualifiedTypeNames() throws Exception {
		runTestsAgainstTypeDeclaration(myComponent, (type) -> {
			Iterator<String> iter = ASTUtils.getHierarchyTypesFqNamesBreadthFirstIterator(type.resolveBinding());
			assertNotNull(iter);

			assertEquals("test.MyComponent", iter.next());
			assertEquals("test.MyInterface", iter.next());
			assertEquals("test.MySuperclass", iter.next());
			assertEquals("test.MySuperInterface", iter.next());
			assertEquals("test.MySuperclassInterface", iter.next());
			assertEquals("java.lang.Object", iter.next());
			assertFalse(iter.hasNext());
		});
	}
	
	@Test
	void testCircularTypeHierarchy() throws Exception {
		createFile(projectName, "test", "Start.java", """
		package test;
		public class Start extends Third {
		}
		""");

		createFile(projectName, "test", "Second.java", """
		package test;
		public class Second extends Start {
		}
		""");

		Path third = createFile(projectName, "test", "Third.java", """
		package test;
		public class Third extends Second {
		}
		""");
		
		runTestsAgainstTypeDeclaration(third, (type) -> {
			assertFalse(ASTUtils.isAnyTypeInHierarchy(type.resolveBinding(), List.of("java.io.Serializable")));
			assertTrue(ASTUtils.isAnyTypeInHierarchy(type.resolveBinding(), List.of("test.Start")));
			assertTrue(ASTUtils.areAllTypesInHierarchy(type.resolveBinding(), List.of("test.Start", "test.Second", "test.Third")));
		});

	}
	
	@Test
	void testInterfaceAppearsMultipleTimesInHierarchy() throws Exception {
		createFile(projectName, "test", "Start.java", """
		package test;
		public class Start implements TestInterface {
		}
		""");

		Path second = createFile(projectName, "test", "Second.java", """
		package test;
		public class Second extends Start implements TestInterface {
		}
		""");

		createFile(projectName, "test", "TestInterface.java", """
		package test;
		public interface TestInterface {
		}
		""");
		
		runTestsAgainstTypeDeclaration(second, (type) -> {
			Iterator<String> iter = ASTUtils.getHierarchyTypesFqNamesBreadthFirstIterator(type.resolveBinding());
			assertNotNull(iter);

			assertEquals("test.Second", iter.next());
			assertEquals("test.TestInterface", iter.next());
			assertEquals("test.Start", iter.next());
			assertEquals("test.TestInterface", iter.next());
			assertEquals("java.lang.Object", iter.next());
			assertFalse(iter.hasNext());
		});

	}
	
	private void runTestsAgainstTypeDeclaration(Path file, Consumer<TypeDeclaration> test) throws Exception {
		SpringIndexerJavaParserUtils.createParser(this.project, new AnnotationHierarchies(), true).createASTs(new String[] { file.toFile().toString() }, null, new String[0], new FileASTRequestor() {
			@Override
			public void acceptAST(String sourceFilePath, CompilationUnit cu) {
				cu.accept(new ASTVisitor() {

					@Override
					public boolean visit(TypeDeclaration type) {
						test.accept(type);
						return super.visit(type);
					}
					
				});
			}	
		}, null);
	}

	private void createTestFiles() throws Exception {
		this.mySimpleMain = createFile(projectName, "test", "MySimpleMain.java", """
		package test;
		public class MySimpleMain {
		}
		""");

		createFile(projectName, "test", "MySuperclass.java", """
		package test;
		public class MySuperclass implements MySuperclassInterface {
		}
		""");

		createFile(projectName, "test", "MySuperclassInterface.java", """
		package test;
		public interface MySuperclassInterface {
		}
		""");
		
		createFile(projectName, "test", "MyInterface.java", """
		package test;
		public interface MyInterface extends MySuperInterface {
		}
		""");
		
		createFile(projectName, "test", "MySuperInterface.java", """
		package test;
		public interface MySuperInterface {
		}
		""");

		this.myComponent = createFile(projectName, "test", "MyComponent.java", """
		package test;
		import org.springframework.boot.autoconfigure.SpringBootApplication;
		
		@SpringBootApplication
		public class MyComponent extends MySuperclass implements MyInterface {
		}
		""");

		createFile(projectName, "test", "MyConstants.java", """
		package test;
		public class MyConstants {
			public static final String SUFFIX = "Suffix";
		}
		""");

		this.myConcatenatedComponent = createFile(projectName, "test", MY_CONCATENATED_COMPONENT_NAME, MY_CONCATENATED_COMPONENT);
		this.myConcatenatedConfig = createFile(projectName, "test", MY_CONCATENATED_CONFIG_NAME, MY_CONCATENATED_CONFIG);
	}
	
	private Path createFile(String projectName, String packageName, String name, String content) throws Exception {
		Path projectPath = Paths.get(getClass().getResource("/test-projects/" + projectName).toURI());
		Path filePath = projectPath.resolve("src/main/java").resolve(packageName.replace('.', '/')).resolve(name);
		Files.createDirectories(filePath.getParent());
		createdFiles.add(Files.createFile(filePath));
		Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
		return filePath;
	}
	
	private void clearTestFiles() {
		for (Iterator<Path> itr = createdFiles.iterator(); itr.hasNext();) {
			Path path = itr.next();
			try {
				Files.delete(path);
				itr.remove();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	// -----------------------------------------------------------------------
	// ASTUtils.bodyDeclarationAnchorOffset — method declaration cases
	// -----------------------------------------------------------------------

	@Test
	void anchorIsReturnTypeWhenMethodHasJavadocButNoAnnotations() {
		String source = """
				package p;

				import java.util.List;

				public interface Repo {
					/**
					 * Some javadoc.
					 */
					List<String> findCustomers(String id);
				}
				""";
		CompilationUnit cu = parseSource(source);
		MethodDeclaration method = firstMethod(cu);
		assertNotNull(method.getJavadoc());

		int anchor = ASTUtils.bodyDeclarationAnchorOffset(method);
		int javadocEnd = method.getJavadoc().getStartPosition() + method.getJavadoc().getLength();
		assertTrue(anchor >= javadocEnd, "code lens start must be at or after the closing */");
		assertEquals(method.getReturnType2().getStartPosition(), anchor);
	}

	@SuppressWarnings("unchecked")
	@Test
	void anchorIsFirstAnnotationWhenMethodJavadocPrecedesAnnotations() {
		String source = """
				package p;

				import java.util.List;

				public interface Repo {
					/**
					 * Some javadoc.
					 */
					@Deprecated
					List<String> findCustomers(String id);
				}
				""";
		CompilationUnit cu = parseSource(source);
		MethodDeclaration method = firstMethod(cu);
		assertNotNull(method.getJavadoc());

		int anchor = ASTUtils.bodyDeclarationAnchorOffset(method);
		int javadocEnd = method.getJavadoc().getStartPosition() + method.getJavadoc().getLength();
		assertTrue(anchor >= javadocEnd, "code lens start must be at or after the closing */");

		List<IExtendedModifier> modifiers = method.modifiers();
		IExtendedModifier first = modifiers.get(0);
		assertTrue(first instanceof ASTNode, "first modifier should be an AST node");
		assertEquals(((ASTNode) first).getStartPosition(), anchor);
	}

	@Test
	void anchorMatchesReturnTypeWhenMethodHasNoJavadoc() {
		String source = """
				package p;

				import java.util.List;

				public interface Repo {
					List<String> findCustomers(String id);
				}
				""";
		CompilationUnit cu = parseSource(source);
		MethodDeclaration method = firstMethod(cu);
		assertNull(method.getJavadoc());

		int anchor = ASTUtils.bodyDeclarationAnchorOffset(method);
		assertEquals(method.getReturnType2().getStartPosition(), anchor);
	}

	// -----------------------------------------------------------------------
	// ASTUtils.bodyDeclarationAnchorOffset — type declaration cases
	// -----------------------------------------------------------------------

	@SuppressWarnings("unchecked")
	@Test
	void typeAnchorIsFirstAnnotationWhenJavadocPrecedesAnnotations() {
		String source = """
				package p;

				/**
				 * Some javadoc.
				 */
				@SuppressWarnings("all")
				public class MyController {
				}
				""";
		CompilationUnit cu = parseSource(source);
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
		assertNotNull(type.getJavadoc());

		int anchor = ASTUtils.bodyDeclarationAnchorOffset(type);
		int javadocEnd = type.getJavadoc().getStartPosition() + type.getJavadoc().getLength();
		assertTrue(anchor >= javadocEnd, "code lens start must be at or after the closing */");

		List<IExtendedModifier> modifiers = type.modifiers();
		IExtendedModifier first = modifiers.get(0);
		assertTrue(first instanceof ASTNode, "first modifier should be an AST node");
		assertEquals(((ASTNode) first).getStartPosition(), anchor);
	}

	@SuppressWarnings("unchecked")
	@Test
	void typeAnchorIsFirstModifierKeywordWhenNoAnnotation() {
		String source = """
				package p;

				/**
				 * Some javadoc.
				 */
				public class MyController {
				}
				""";
		CompilationUnit cu = parseSource(source);
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
		assertNotNull(type.getJavadoc());

		int anchor = ASTUtils.bodyDeclarationAnchorOffset(type);
		int javadocEnd = type.getJavadoc().getStartPosition() + type.getJavadoc().getLength();
		assertTrue(anchor >= javadocEnd, "code lens start must be at or after the closing */");

		List<IExtendedModifier> modifiers = type.modifiers();
		IExtendedModifier first = modifiers.get(0);
		assertTrue(first instanceof ASTNode, "first modifier should be an AST node");
		assertEquals(((ASTNode) first).getStartPosition(), anchor);
	}

	@Test
	void typeAnchorFallsBackToTypeNameWhenNoModifiers() {
		String source = """
				package p;

				class PackagePrivate {
				}
				""";
		CompilationUnit cu = parseSource(source);
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
		assertNull(type.getJavadoc());
		assertTrue(type.modifiers().isEmpty());

		int anchor = ASTUtils.bodyDeclarationAnchorOffset(type);
		assertEquals(type.getName().getStartPosition(), anchor);
	}

	// -----------------------------------------------------------------------
	// concatenated annotation attribute values (GH-1486)
	// -----------------------------------------------------------------------

	private static final String MY_CONCATENATED_COMPONENT_NAME = "MyConcatenatedComponent.java";

	private static final String MY_CONCATENATED_COMPONENT = """
			package test;
			import org.springframework.stereotype.Component;

			@Component("special" + "Name" + MyConstants.SUFFIX)
			public class MyConcatenatedComponent {
			}
			""";

	private static final String MY_CONCATENATED_CONFIG_NAME = "MyConcatenatedConfig.java";

	private static final String MY_CONCATENATED_CONFIG = """
			package test;
			import org.springframework.context.annotation.Bean;
			import org.springframework.context.annotation.Configuration;

			@Configuration
			public class MyConcatenatedConfig {

				@Bean(name = { "one" + "Bean", "two" + MyConstants.SUFFIX })
				public String myBean() {
					return "bean";
				}

				@Bean("plainBean")
				public String myPlainBean() {
					return "plain";
				}

			}
			""";

	@Test
	void expressionValueResolvesConcatenationOfLiteralsAndConstants() throws Exception {
		runTestsAgainstCompilationUnit(myConcatenatedComponent, MY_CONCATENATED_COMPONENT, (cu, offsets) -> {
			// cursor in the middle of the first of three concatenated operands
			Expression value = ASTUtils.getAttributeValueExpressionAt(offsets.nodeAt("\"special\""));

			assertTrue(value instanceof InfixExpression, "expected the whole concatenation, got " + value.getClass().getSimpleName());
			assertEquals("specialNameSuffix", ASTUtils.getExpressionValueAsString(value));
		});
	}

	@Test
	void expressionValueDependenciesIncludeConstantDeclaringType() throws Exception {
		runTestsAgainstCompilationUnit(myConcatenatedComponent, MY_CONCATENATED_COMPONENT, (cu, offsets) -> {
			Expression value = ASTUtils.getAttributeValueExpressionAt(offsets.nodeAt("MyConstants.SUFFIX"));

			List<String> dependencies = new ArrayList<>();
			assertEquals("specialNameSuffix", ASTUtils.getExpressionValueAsString(value, dep -> dependencies.add(dep.getQualifiedName())));
			assertEquals(List.of("test.MyConstants"), dependencies);
		});
	}

	@Test
	void attributeValueExpressionIsResolvedFromAnyOperandOfTheConcatenation() throws Exception {
		runTestsAgainstCompilationUnit(myConcatenatedComponent, MY_CONCATENATED_COMPONENT, (cu, offsets) -> {
			Expression fromFirst = ASTUtils.getAttributeValueExpressionAt(offsets.nodeAt("\"special\""));
			Expression fromSecond = ASTUtils.getAttributeValueExpressionAt(offsets.nodeAt("\"Name\""));
			Expression fromConstant = ASTUtils.getAttributeValueExpressionAt(offsets.nodeAt("MyConstants.SUFFIX"));

			assertSame(fromFirst, fromSecond);
			assertSame(fromFirst, fromConstant);
		});
	}

	@Test
	void attributeValueExpressionIsNullOutsideOfAnnotations() throws Exception {
		runTestsAgainstCompilationUnit(myConcatenatedConfig, MY_CONCATENATED_CONFIG, (cu, offsets) -> {
			assertNull(ASTUtils.getAttributeValueExpressionAt(offsets.nodeAt("MyConcatenatedConfig {")));
		});
	}

	@Test
	void resolveAnnotationAttributeAtSingleMemberAnnotation() throws Exception {
		runTestsAgainstCompilationUnit(myConcatenatedComponent, MY_CONCATENATED_COMPONENT, (cu, offsets) -> {
			AnnotationAttribute attribute = ASTUtils.resolveAnnotationAttributeAt(offsets.nodeAt("\"Name\"")).get();

			assertEquals("org.springframework.stereotype.Component", attribute.annotationType());
			assertEquals("value", attribute.attributeName());
			assertEquals("specialNameSuffix", attribute.value());
		});
	}

	@Test
	void resolveAnnotationAttributeAtArrayElementOfNamedAttribute() throws Exception {
		runTestsAgainstCompilationUnit(myConcatenatedConfig, MY_CONCATENATED_CONFIG, (cu, offsets) -> {
			AnnotationAttribute first = ASTUtils.resolveAnnotationAttributeAt(offsets.nodeAt("\"one\"")).get();
			assertEquals("org.springframework.context.annotation.Bean", first.annotationType());
			assertEquals("name", first.attributeName());
			assertEquals("oneBean", first.value());

			// the second array element must resolve independently of the first
			AnnotationAttribute second = ASTUtils.resolveAnnotationAttributeAt(offsets.nodeAt("\"two\"")).get();
			assertEquals("name", second.attributeName());
			assertEquals("twoSuffix", second.value());
		});
	}

	@Test
	void resolveAnnotationAttributeAtIsEmptyOutsideOfAnnotations() throws Exception {
		runTestsAgainstCompilationUnit(myConcatenatedConfig, MY_CONCATENATED_CONFIG, (cu, offsets) -> {
			assertTrue(ASTUtils.resolveAnnotationAttributeAt(offsets.nodeAt("MyConcatenatedConfig {")).isEmpty());
		});
	}

	@Test
	void valueRegionStripsQuotesForLiteralsButNotForConcatenations() throws Exception {
		runTestsAgainstCompilationUnit(myConcatenatedConfig, MY_CONCATENATED_CONFIG, (cu, offsets) -> {
			TextDocument doc = new TextDocument("file:///MyConcatenatedConfig.java", LanguageId.JAVA, 0, MY_CONCATENATED_CONFIG);

			Expression concatenation = ASTUtils.getAttributeValueExpressionAt(offsets.nodeAt("\"one\""));
			assertEquals("\"one\" + \"Bean\"", ASTUtils.valueRegion(doc, concatenation).toString());

			Expression literal = ASTUtils.getAttributeValueExpressionAt(offsets.nodeAt("\"plainBean\""));
			assertEquals("plainBean", ASTUtils.valueRegion(doc, literal).toString());
		});
	}

	@Test
	void getFirstStringResolvesConcatenationsInsideArrays() throws Exception {
		runTestsAgainstCompilationUnit(myConcatenatedConfig, MY_CONCATENATED_CONFIG, (cu, offsets) -> {
			Annotation beanAnnotation = ASTUtils.resolveAnnotationAttributeAt(offsets.nodeAt("\"one\"")).get().annotation();

			// the whole "name" attribute value, i.e. the array initializer
			Expression arrayValue = ASTUtils.getAttribute(beanAnnotation, "name").get();
			assertEquals("oneBean", ASTUtils.getFirstString(arrayValue).get());
		});
	}

	@Test
	void getFirstStringResolvesPlainConcatenations() throws Exception {
		runTestsAgainstCompilationUnit(myConcatenatedComponent, MY_CONCATENATED_COMPONENT, (cu, offsets) -> {
			Annotation componentAnnotation = ASTUtils.resolveAnnotationAttributeAt(offsets.nodeAt("\"Name\"")).get().annotation();

			Expression value = ASTUtils.getAttribute(componentAnnotation, "value").get();
			assertEquals("specialNameSuffix", ASTUtils.getFirstString(value).get());
		});
	}

	/**
	 * Locates AST nodes the way the language server does at runtime: by the offset of a marker in
	 * the source text, resolved through {@link NodeFinder}.
	 */
	private record NodeLocator(CompilationUnit cu, String source) {

		ASTNode nodeAt(String marker) {
			int index = source.indexOf(marker);
			assertTrue(index >= 0, "marker not found in source: " + marker);
			// aim at the middle of the marker so we land inside the node, not on its boundary
			return NodeFinder.perform(cu, index + marker.length() / 2, 0);
		}
	}

	private void runTestsAgainstCompilationUnit(Path file, String source, BiConsumer<CompilationUnit, NodeLocator> test) throws Exception {
		SpringIndexerJavaParserUtils.createParser(this.project, new AnnotationHierarchies(), true)
			.createASTs(new String[] { file.toFile().toString() }, null, new String[0], new FileASTRequestor() {
				@Override
				public void acceptAST(String sourceFilePath, CompilationUnit cu) {
					test.accept(cu, new NodeLocator(cu, source));
				}
			}, null);
	}

	private static CompilationUnit parseSource(String source) {
		ASTParser parser = ASTParser.newParser(AST.JLS25);
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(false);
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		return (CompilationUnit) parser.createAST(null);
	}

	private static MethodDeclaration firstMethod(CompilationUnit cu) {
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration[] methods = type.getMethods();
		assertTrue(methods.length > 0, "fixture should declare at least one method");
		return methods[0];
	}

}
