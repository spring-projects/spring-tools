/*******************************************************************************
 * Copyright (c) 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.jdt.refactoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RemoveAnnotationRefactoring}.
 */
class RemoveAnnotationRefactoringTest {

	private static CompilationUnit parseSource(String source) {
		ASTParser parser = ASTParser.newParser(AST.JLS25);
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setEnvironment(new String[0], new String[0], null, true);
		parser.setUnitName("Test.java");
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		return (CompilationUnit) parser.createAST(null);
	}

	private static String applyRefactoring(String source, int... offsets) throws Exception {
		CompilationUnit cu = parseSource(source);
		ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
		new RemoveAnnotationRefactoring(offsets).apply(rewrite, cu);
		Document doc = new Document(source);
		TextEdit edit = rewrite.rewriteAST(doc, JavaCore.getOptions());
		edit.apply(doc);
		return doc.get();
	}

	private static int offsetOf(String source, String substring) {
		return source.indexOf(substring);
	}

	@Test
	void removeMarkerAnnotationFromConstructorAndImport() throws Exception {
		String source = """
				package com.example;

				import java.lang.annotation.Documented;

				class MyService {
					@Documented
					MyService() {}
				}
				""";

		// Offset inside the annotation name
		String result = applyRefactoring(source, offsetOf(source, "@Documented") + 2);

		assertEquals("""
				package com.example;

				class MyService {
					MyService() {}
				}
				""", result);
	}

	@Test
	void removeMarkerAnnotationButKeepImportIfUsed() throws Exception {
		String source = """
				package com.example;

				import java.lang.annotation.Documented;

				class MyService {
					@Documented
					MyService() {}
					
					@Documented
					MyService(int x) {}
				}
				""";

		// Remove only the first annotation
		String result = applyRefactoring(source, offsetOf(source, "@Documented") + 2);

		assertEquals("""
				package com.example;

				import java.lang.annotation.Documented;

				class MyService {
					MyService() {}
					
					@Documented
					MyService(int x) {}
				}
				""", result);
	}

	@Test
	void removeMarkerAnnotationFromTypeDeclaration() throws Exception {
		String source = """
				package com.example;

				import java.lang.annotation.Documented;
				import java.io.Serializable;

				@Documented
				interface PersonRepo extends Serializable {}
				""";

		// Offset at the start of the annotation name
		String result = applyRefactoring(source, offsetOf(source, "@Documented") + 1);

		assertEquals("""
				package com.example;

				import java.io.Serializable;

				interface PersonRepo extends Serializable {}
				""", result);
	}

	@Test
	void removeBatchAnnotations() throws Exception {
		String source = """
				package com.example;

				import java.lang.annotation.Documented;
				import java.io.Serializable;

				@Documented
				interface Repo1 extends Serializable {}

				@Documented
				interface Repo2 extends Serializable {}
				""";

		String result = applyRefactoring(source,
				offsetOf(source, "@Documented\ninterface Repo1") + 1,
				offsetOf(source, "@Documented\ninterface Repo2") + 2);

		assertEquals("""
				package com.example;

				import java.io.Serializable;

				interface Repo1 extends Serializable {}

				interface Repo2 extends Serializable {}
				""", result);
	}

	@Test
	void ignoreOffsetOutsideAnnotationName() throws Exception {
		String source = """
				package com.example;

				import java.lang.annotation.Documented;

				@Documented
				interface Repo1 {}
				""";

		// Offset inside the parentheses, which is outside the name node
		String result = applyRefactoring(source, offsetOf(source, "Repo1"));

		// Should not modify the source
		assertEquals(source, result);
	}

}
