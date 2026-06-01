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
import java.util.function.Consumer;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaParserUtils;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;

public class ASTUtilsTest {
	
	private List<Path> createdFiles = new ArrayList<>();
	
	private final String projectName = "test-spring-validations";

	private MavenJavaProject project;

	private Path mySimpleMain;
	private Path myComponent;

	
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
