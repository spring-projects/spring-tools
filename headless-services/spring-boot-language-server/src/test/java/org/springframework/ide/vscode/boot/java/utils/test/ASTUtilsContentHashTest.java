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
package org.springframework.ide.vscode.boot.java.utils.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * Tests for {@link ASTUtils#contentHash(TextDocument, org.eclipse.jdt.core.dom.ASTNode)}, the hash
 * that lets the logical structure diff notice source changes that leave a tree node looking exactly
 * the same.
 *
 * @author Martin Lippert
 */
public class ASTUtilsContentHashTest {

	private static final String SOURCE = """
			package example;

			public class A {
				public String hello() {
					return "hello";
				}
			}
			""";

	@Test
	void identicalSourceProducesTheSameHash() {
		assertThat(methodHashOf(SOURCE)).isEqualTo(methodHashOf(SOURCE));
	}

	@Test
	void changedMethodBodyProducesADifferentHash() {
		String changed = SOURCE.replace("return \"hello\";", "return \"goodbye\";");

		assertThat(methodHashOf(changed)).isNotEqualTo(methodHashOf(SOURCE));
	}

	@Test
	void changeOutsideTheHashedNodeDoesNotAffectTheHash() {
		// the hash covers the method only, so an unrelated field added to the class around it must
		// not change it - otherwise every node in a file would change whenever the file does
		String changed = SOURCE.replace("public class A {", "public class A {\n\tprivate int counter;\n");

		assertThat(methodHashOf(changed)).isEqualTo(methodHashOf(SOURCE));
		assertThat(typeHashOf(changed)).isNotEqualTo(typeHashOf(SOURCE));
	}

	@Test
	void reformattingProducesADifferentHash() {
		// deliberate: the hash is over the raw source, so formatting counts as a change
		String changed = SOURCE.replace("\treturn \"hello\";", "        return \"hello\";");

		assertThat(methodHashOf(changed)).isNotEqualTo(methodHashOf(SOURCE));
	}

	@Test
	void excludedMembersDoNotCountTowardsTheEnclosingTypesHash() {
		// the method has a tree node of its own, so its changes are reported there - counting them
		// in the type's hash as well would light up the type for every edit inside any of its methods
		String changed = SOURCE.replace("return \"hello\";", "return \"goodbye\";");

		assertThat(typeHashExcludingMethodsOf(changed)).isEqualTo(typeHashExcludingMethodsOf(SOURCE));
		// ... while the plain type hash does react to it, which is exactly why the exclusion exists
		assertThat(typeHashOf(changed)).isNotEqualTo(typeHashOf(SOURCE));
	}

	@Test
	void whatIsLeftOfTheTypeStillCountsTowardsItsHash() {
		// a field has no node of its own, so the type's hash is the only place its change can show up
		String withField = SOURCE.replace("public class A {", "public class A {\n\tprivate int counter;\n");

		assertThat(typeHashExcludingMethodsOf(withField)).isNotEqualTo(typeHashExcludingMethodsOf(SOURCE));
	}

	@Test
	void excludingEverySpanLeavesAStableHashRatherThanBlowingUp() {
		TypeDeclaration type = typeDeclarationOf(SOURCE);

		assertThat(ASTUtils.contentHash(documentOf(SOURCE), type, List.of(type)))
				.isEqualTo(ASTUtils.contentHash(documentOf(SOURCE), type, List.of(type)));
	}

	@Test
	void hashIsShortEnoughToTravelWithEveryTreeNode() {
		assertThat(methodHashOf(SOURCE)).hasSize(12).matches("[0-9a-f]+");
	}

	@Test
	void nullNodeOrDocumentHashesToNull() {
		assertThat(ASTUtils.contentHash(null, typeDeclarationOf(SOURCE))).isNull();
		assertThat(ASTUtils.contentHash(documentOf(SOURCE), null)).isNull();
	}

	private static String typeHashOf(String source) {
		return ASTUtils.contentHash(documentOf(source), typeDeclarationOf(source));
	}

	private static String typeHashExcludingMethodsOf(String source) {
		TypeDeclaration type = typeDeclarationOf(source);
		return ASTUtils.contentHash(documentOf(source), type, List.of(type.getMethods()));
	}

	private static String methodHashOf(String source) {
		MethodDeclaration method = typeDeclarationOf(source).getMethods()[0];
		return ASTUtils.contentHash(documentOf(source), method);
	}

	private static TextDocument documentOf(String source) {
		return new TextDocument("file:///A.java", LanguageId.JAVA, 0, source);
	}

	private static TypeDeclaration typeDeclarationOf(String source) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		return (TypeDeclaration) cu.types().get(0);
	}

}
