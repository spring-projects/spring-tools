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
package org.springframework.ide.vscode.boot.java.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link DataRepositoryAotMetadataCodeLensProvider#methodHeaderAnchorOffset(MethodDeclaration)}:
 * code lens ranges must start after Javadoc and on the first modifier or return type so clients
 * render lenses between Javadoc and {@code @Query} (or other annotations).
 */
class DataRepositoryAotMetadataCodeLensProviderMethodHeaderAnchorTest {

	private static CompilationUnit parse(String source) {
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
		CompilationUnit cu = parse(source);
		MethodDeclaration method = firstMethod(cu);
		assertNotNull(method.getJavadoc());

		int anchor = DataRepositoryAotMetadataCodeLensProvider.methodHeaderAnchorOffset(method);
		int javadocEnd = method.getJavadoc().getStartPosition() + method.getJavadoc().getLength();
		assertTrue(anchor >= javadocEnd, "code lens start must be at or after the closing */");
		assertEquals(method.getReturnType2().getStartPosition(), anchor);
	}

	@Test
	void anchorIsFirstAnnotationWhenJavadocPrecedesAnnotations() {
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
		CompilationUnit cu = parse(source);
		MethodDeclaration method = firstMethod(cu);
		assertNotNull(method.getJavadoc());

		int anchor = DataRepositoryAotMetadataCodeLensProvider.methodHeaderAnchorOffset(method);
		int javadocEnd = method.getJavadoc().getStartPosition() + method.getJavadoc().getLength();
		assertTrue(anchor >= javadocEnd, "code lens start must be at or after the closing */");

		List<IExtendedModifier> modifiers = method.modifiers();
		IExtendedModifier first = modifiers.get(0);
		assertTrue(first instanceof ASTNode, "first modifier should be an AST node");
		assertEquals(((ASTNode) first).getStartPosition(), anchor);
	}

	@Test
	void anchorMatchesReturnTypeWhenNoJavadoc() {
		String source = """
				package p;

				import java.util.List;

				public interface Repo {
					List<String> findCustomers(String id);
				}
				""";
		CompilationUnit cu = parse(source);
		MethodDeclaration method = firstMethod(cu);
		assertNull(method.getJavadoc());

		int anchor = DataRepositoryAotMetadataCodeLensProvider.methodHeaderAnchorOffset(method);
		assertEquals(method.getReturnType2().getStartPosition(), anchor);
	}
}
