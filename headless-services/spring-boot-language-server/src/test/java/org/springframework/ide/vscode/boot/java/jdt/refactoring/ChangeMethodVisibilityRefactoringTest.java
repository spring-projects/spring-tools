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
package org.springframework.ide.vscode.boot.java.jdt.refactoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.ChangeMethodVisibilityRefactoring.Visibility;

/**
 * Unit tests for {@link ChangeMethodVisibilityRefactoring}.
 */
class ChangeMethodVisibilityRefactoringTest {

	private static Map<String, String> defaultFormatterOptions() {
		Map<String, String> options = JavaCore.getOptions();
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.TAB);
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
		return options;
	}

	private static CompilationUnit parseSource(String source) {
		ASTParser parser = ASTParser.newParser(AST.JLS25);
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(false);

		Map<String, String> options = JavaCore.getOptions();
		String apiLevel = JavaCore.VERSION_21;
		JavaCore.setComplianceOptions(apiLevel, options);
		parser.setCompilerOptions(options);

		return (CompilationUnit) parser.createAST(null);
	}

	private static String applyRefactoring(String source, Visibility visibility, int... offsets)
			throws Exception {
		CompilationUnit cu = parseSource(source);
		ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
		new ChangeMethodVisibilityRefactoring(visibility, offsets).apply(rewrite, cu);
		Document doc = new Document(source);
		TextEdit edit = rewrite.rewriteAST(doc, defaultFormatterOptions());
		edit.apply(doc);
		return doc.get();
	}

	private static int offsetOf(String source, String substring) {
		return source.indexOf(substring);
	}

	@Test
	void publicToPackagePrivate_offsetInsideMethodName() throws Exception {
		String source = """
				package com.example;

				class TestClass {
					public void test() {
					}
				}
				""";

		// Offset inside the method name "test"
		String result = applyRefactoring(source, Visibility.PACKAGE_PRIVATE, offsetOf(source, "test") + 1);

		assertEquals("""
				package com.example;

				class TestClass {
					void test() {
					}
				}
				""", result);
	}

	@Test
	void ignoreOffsetOutsideMethodName() throws Exception {
		String source = """
				package com.example;

				class TestClass {
					public void test() {
					}
				}
				""";

		// Offset inside the return type "void"
		String result = applyRefactoring(source, Visibility.PACKAGE_PRIVATE, offsetOf(source, "void"));

		// Should not modify the source
		assertEquals(source, result);
	}

	@Test
	void privateToPublic() throws Exception {
		String source = """
				package com.example;

				class TestClass {
					private void test() {
					}
				}
				""";

		String result = applyRefactoring(source, Visibility.PUBLIC, offsetOf(source, "test") + 1);

		assertEquals("""
				package com.example;

				class TestClass {
					public void test() {
					}
				}
				""", result);
	}

	@Test
	void packagePrivateToProtected() throws Exception {
		String source = """
				package com.example;

				class TestClass {
					void test() {
					}
				}
				""";

		String result = applyRefactoring(source, Visibility.PROTECTED, offsetOf(source, "test") + 1);

		assertEquals("""
				package com.example;

				class TestClass {
					protected void test() {
					}
				}
				""", result);
	}

	@Test
	void publicToPrivate_withAnnotations() throws Exception {
		String source = """
				package com.example;

				class TestClass {
					@Override
					public void test() {
					}
				}
				""";

		String result = applyRefactoring(source, Visibility.PRIVATE, offsetOf(source, "test") + 1);

		assertEquals("""
				package com.example;

				class TestClass {
					@Override
					private void test() {
					}
				}
				""", result);
	}

	@Test
	void batchReplacement() throws Exception {
		String source = """
				package com.example;

				class TestClass {
					public void test1() {
					}
					
					protected void test2() {
					}
				}
				""";

		String result = applyRefactoring(source, Visibility.PACKAGE_PRIVATE, 
				offsetOf(source, "test1") + 1,
				offsetOf(source, "test2") + 1);

		assertEquals("""
				package com.example;

				class TestClass {
					void test1() {
					}
					
					void test2() {
					}
				}
				""", result);
	}

}
