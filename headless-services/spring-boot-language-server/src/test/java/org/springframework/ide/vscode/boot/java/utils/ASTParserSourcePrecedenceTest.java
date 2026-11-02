/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.stereotypes.JarFixtureBuilder;

/**
 * The project's own output folder is on the classpath the indexer parses with, next to its source
 * folders - see {@link SpringIndexerJavaParserUtils#createParser}. A type of the project resolves
 * from its source all the same, never from its compiled class: whether the project has been built,
 * or what a build plugin added to the bytecode, doesn't change what the index has. The indexers rely
 * on that, taking what build plugins add into account for every type that comes from source.
 *
 * @author Martin Lippert
 */
public class ASTParserSourcePrecedenceTest {

	@TempDir
	Path tempDir;

	private Path sources;
	private Path classes;

	@BeforeEach
	void setup() throws Exception {
		sources = Files.createDirectories(tempDir.resolve("src/main/java"));
		Files.createDirectories(sources.resolve("p"));
		Files.writeString(sources.resolve("p/Super.java"), "package p; public class Super { public void fromSource() {} }");
		Files.writeString(sources.resolve("p/Sub.java"), "package p; public class Sub extends Super { }");

		// a different, stale build of the same type, in the output folder
		classes = JarFixtureBuilder.compileAll(Files.createDirectories(tempDir.resolve("target")),
				Map.of("p.Super", "package p; @Deprecated public class Super { public void fromBytecode() {} }"));
	}

	private ASTParserCleanupEnabled parser() {
		// the JDK's system library, which a project's classpath brings along otherwise
		String jrt = Path.of(System.getProperty("java.home"), "lib", "jrt-fs.jar").toString();
		return new ASTParserCleanupEnabled(new String[] {jrt, classes.toString()}, new String[] {sources.toString()}, "21",
				new AnnotationHierarchies(), false);
	}

	@Test
	void batchOfFilesWithoutTheType() throws Exception {
		List<ITypeBinding> superclasses = new ArrayList<>();

		parser().createASTs(new String[] {sources.resolve("p/Sub.java").toString()}, null, new String[0], new FileASTRequestor() {
			@Override
			public void acceptAST(String sourceFilePath, CompilationUnit cu) {
				superclasses.add(((TypeDeclaration) cu.types().get(0)).resolveBinding().getSuperclass());
			}
		}, null);

		assertEquals(1, superclasses.size());
		assertResolvedFromSource(superclasses.get(0));
	}

	@Test
	void singleFile() throws Exception {
		ASTParserCleanupEnabled parser = parser();
		parser.setUnitName("Sub.java");
		parser.setSource(Files.readString(sources.resolve("p/Sub.java")).toCharArray());

		CompilationUnit cu = (CompilationUnit) parser.createAST(null);

		assertResolvedFromSource(((TypeDeclaration) cu.types().get(0)).resolveBinding().getSuperclass());
	}

	private static void assertResolvedFromSource(ITypeBinding superclass) {
		assertNotNull(superclass);
		assertEquals("p.Super", superclass.getQualifiedName());
		assertTrue(superclass.isFromSource());

		List<String> methods = Arrays.stream(superclass.getDeclaredMethods())
				.filter(method -> !method.isConstructor())
				.map(IMethodBinding::getName)
				.toList();
		assertEquals(List.of("fromSource"), methods);
		assertEquals(0, superclass.getAnnotations().length);
	}

}
