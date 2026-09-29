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
package org.springframework.ide.vscode.boot.java.stereotypes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The expected strings are what JDT's {@code ITypeBinding.getName()}, {@code getQualifiedName()} and
 * {@code getBinaryName()} return for the very same method, as recorded by running JDT on this source.
 * Type variables' binary names are the one deliberate exception (see {@link JdtStyleTypeNames}).
 *
 * @author Martin Lippert
 */
public class JdtStyleTypeNamesTest {

	@TempDir
	Path tempDir;

	@Test
	void rendersTypesExactlyAsJdtDoes() throws Exception {
		MethodInfo method = method("""
				package p;
				public class A {
					public static class Inner {}
					public <T> java.util.Map<String,Integer> m(java.util.List<? extends Number> l, int[] arr, String[][] s, A.Inner i, T t,
							java.util.List<?> w, java.util.List<? super Integer> su, int x) { return null; }
				}
				""");

		List<Type> parameters = method.parameterTypes();

		assertEquals("Map<String,Integer>", JdtStyleTypeNames.name(method.returnType()));
		assertEquals("java.util.Map<java.lang.String,java.lang.Integer>", JdtStyleTypeNames.qualifiedName(method.returnType()));
		assertEquals("java.util.Map", JdtStyleTypeNames.binaryName(method.returnType()));

		assertNames(parameters.get(0), "List<? extends Number>", "java.util.List<? extends java.lang.Number>", "java.util.List");
		assertNames(parameters.get(1), "int[]", "int[]", "[I");
		assertNames(parameters.get(2), "String[][]", "java.lang.String[][]", "[[Ljava.lang.String;");
		assertNames(parameters.get(3), "Inner", "p.A.Inner", "p.A$Inner");
		assertEquals("T", JdtStyleTypeNames.name(parameters.get(4)));
		assertEquals("T", JdtStyleTypeNames.qualifiedName(parameters.get(4)));
		assertNames(parameters.get(5), "List<?>", "java.util.List<?>", "java.util.List");
		assertNames(parameters.get(6), "List<? super Integer>", "java.util.List<? super java.lang.Integer>", "java.util.List");
		assertNames(parameters.get(7), "int", "int", "I");
	}

	private static void assertNames(Type type, String name, String qualifiedName, String binaryName) {
		assertEquals(name, JdtStyleTypeNames.name(type));
		assertEquals(qualifiedName, JdtStyleTypeNames.qualifiedName(type));
		assertEquals(binaryName, JdtStyleTypeNames.binaryName(type));
	}

	private MethodInfo method(String source) throws Exception {
		var jar = JarFixtureBuilder.buildJar(tempDir, "types", Map.of("p.A", source), Map.of());
		Indexer indexer = new Indexer();
		JarStereotypeScanner.indexInto(indexer, jar);
		Index index = indexer.complete();
		return index.getClassByName(DotName.createSimple("p.A")).firstMethod("m");
	}

}
