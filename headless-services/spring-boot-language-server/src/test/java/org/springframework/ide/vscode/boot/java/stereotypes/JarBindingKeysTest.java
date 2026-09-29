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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The keys {@link JarBindingKeys} builds from a JAR's bytecode are the keys JDT's own bindings
 * have for the same elements compiled from source - what the IDE's Java tooling resolves.
 *
 * @author Martin Lippert
 */
public class JarBindingKeysTest {

	@TempDir
	Path tempDir;

	private static final Map<String, String> SOURCES = Map.of(
			"p.A", """
					package p;
					import java.util.*;
					public class A<E> implements Comparable<A<E>> {
						private String name;
						private List<String> names;
						private int[][] grid;
						private Map<String, ? super Integer> lower;
						public A(String name, int... more) {}
						public A() {}
						public void plain(String s, int i, long[] ls, String[][] matrix) {}
						public List<? extends Number> generic(Map<String, List<Integer>> m, List<?> any) { return null; }
						public <T extends Number> T typed(T t, Class<T> c) { return t; }
						public <K extends Comparable<K>, V> Map<K, V> twoTypeParameters(K k) { return null; }
						public E ofClassVar(E e) throws java.io.IOException, InterruptedException { return e; }
						public int compareTo(A<E> o) { return 0; }
						public static class Nested { public Nested(Nested other) {} public void in(Nested n) {} }
						public class Inner { public Inner(String s) {} }
					}
					""",
			"p.I", "package p; public interface I<T> { T get(); default void run(boolean b, char c, double d, float f, byte y, short s) {} }");

	@Test
	void keysAreTheOnesJdtHasForTheSameSource() throws Exception {
		Set<String> fromJdt = jdtKeys();

		File jar = JarFixtureBuilder.buildJar(tempDir, "keys", SOURCES, Map.of());
		Indexer indexer = new Indexer();
		Set<DotName> classes = JarStereotypeScanner.indexInto(indexer, jar);
		Index index = indexer.complete();

		Set<String> fromJar = new TreeSet<>();
		for (DotName name : classes) {
			ClassInfo classInfo = index.getClassByName(name);
			fromJar.add(JarBindingKeys.of(classInfo));
			for (MethodInfo method : JarStereotypeScanner.sourceLevelMethodsOf(classInfo)) {
				fromJar.add(JarBindingKeys.of(method));
			}
			for (FieldInfo field : classInfo.fields()) {
				if (!field.isSynthetic()) {
					fromJar.add(JarBindingKeys.of(field));
				}
			}
		}

		// every class above declares its constructors: no implicit one that only bytecode has
		assertEquals(withoutWildcardFieldTypes(namedConstructors(fromJdt)), withoutWildcardFieldTypes(fromJar));
		assertEquals(Set.of("Lp/A;.lower)"), onlyWildcardFieldNames(fromJar));
	}

	/**
	 * JDT's keys with each constructor named after its class, as {@link JarBindingKeys} names it -
	 * JDT's own nameless form is not resolvable against a class file.
	 */
	private static Set<String> namedConstructors(Set<String> keys) {
		Set<String> result = new TreeSet<>();
		for (String key : keys) {
			int nameless = key.indexOf(";.(");
			if (nameless < 0) {
				result.add(key);
				continue;
			}
			String type = key.substring(1, nameless);
			String simpleName = type.substring(Math.max(type.lastIndexOf('/'), type.lastIndexOf('$')) + 1);
			result.add(key.substring(0, nameless + 2) + simpleName + key.substring(nameless + 2));
		}
		return result;
	}

	/**
	 * A field is looked up by its name alone, and JDT renders a wildcard in a field's type in its
	 * own way ({@code Ljava/util/Map;{1}-Ljava/lang/Integer;}) - so for those only the name is
	 * compared.
	 */
	private static Set<String> withoutWildcardFieldTypes(Set<String> keys) {
		Set<String> result = new TreeSet<>();
		for (String key : keys) {
			result.add(isWildcardField(key) ? key.substring(0, key.indexOf(')') + 1) : key);
		}
		return result;
	}

	private static Set<String> onlyWildcardFieldNames(Set<String> keys) {
		Set<String> result = new TreeSet<>();
		keys.stream().filter(JarBindingKeysTest::isWildcardField).forEach(key -> result.add(key.substring(0, key.indexOf(')') + 1)));
		return result;
	}

	private static boolean isWildcardField(String key) {
		return !key.contains("(") && key.contains(")") && (key.contains("-") || key.contains("+") || key.contains("*"));
	}

	/**
	 * The keys of JDT's bindings for every type, method and field declared in {@link #SOURCES} -
	 * types by their erasure, which is how a class is looked up.
	 */
	private Set<String> jdtKeys() throws Exception {
		Path src = tempDir.resolve("src");
		String[] files = new String[SOURCES.size()];
		int i = 0;
		for (var source : SOURCES.entrySet()) {
			Path file = src.resolve(source.getKey().replace('.', '/') + ".java");
			Files.createDirectories(file.getParent());
			Files.writeString(file, source.getValue());
			files[i++] = file.toString();
		}

		ASTParser parser = ASTParser.newParser(AST.JLS25);
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setEnvironment(new String[0], new String[] { src.toString() }, null, true);

		Set<String> keys = new TreeSet<>();
		parser.createASTs(files, null, new String[0], new FileASTRequestor() {
			@Override
			public void acceptAST(String sourceFilePath, CompilationUnit ast) {
				ast.accept(new ASTVisitor() {
					@Override
					public boolean visit(TypeDeclaration node) {
						// the raw type's key, as a class is looked up (JavaData.toBindingKey)
						keys.add("L" + node.resolveBinding().getBinaryName().replace('.', '/') + ";");
						return true;
					}

					@Override
					public boolean visit(MethodDeclaration node) {
						keys.add(node.resolveBinding().getKey());
						return true;
					}

					@Override
					public boolean visit(FieldDeclaration node) {
						for (Object fragment : node.fragments()) {
							keys.add(((VariableDeclarationFragment) fragment).resolveBinding().getKey());
						}
						return true;
					}
				});
			}
		}, null);
		return keys;
	}

}
