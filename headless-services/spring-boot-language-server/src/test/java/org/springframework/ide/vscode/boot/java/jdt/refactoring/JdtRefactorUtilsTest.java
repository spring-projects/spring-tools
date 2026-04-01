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
package org.springframework.ide.vscode.boot.java.jdt.refactoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jface.text.Document;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * Unit tests for {@link JdtRefactorUtils}.
 */
class JdtRefactorUtilsTest {

	// ========== helpers ==========

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
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		return (CompilationUnit) parser.createAST(null);
	}

	private static String applyAddImport(String source, String fqn) throws Exception {
		CompilationUnit cu = parseSource(source);
		ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
		JdtRefactorUtils.addImport(rewrite, cu.getAST(), cu,
				new ClassType(JdtRefactorUtils.extractPackageName(fqn), JdtRefactorUtils.extractSimpleName(fqn)));
		Document doc = new Document(source);
		TextEdit edit = rewrite.rewriteAST(doc, defaultFormatterOptions());
		edit.apply(doc);
		return doc.get();
	}

	// ========== extractSimpleName ==========

	@Test
	void extractSimpleName_fullyQualified() {
		assertEquals("Foo", JdtRefactorUtils.extractSimpleName("org.example.Foo"));
	}

	@Test
	void extractSimpleName_simpleName() {
		assertEquals("Foo", JdtRefactorUtils.extractSimpleName("Foo"));
	}

	@Test
	void extractSimpleName_singleDot() {
		assertEquals("Foo", JdtRefactorUtils.extractSimpleName("a.Foo"));
	}

	// ========== extractPackageName ==========

	@Test
	void extractPackageName_fullyQualified() {
		assertEquals("org.example", JdtRefactorUtils.extractPackageName("org.example.Foo"));
	}

	@Test
	void extractPackageName_simpleName_returnsEmpty() {
		assertEquals("", JdtRefactorUtils.extractPackageName("Foo"));
	}

	@Test
	void extractPackageName_singleDot() {
		assertEquals("a", JdtRefactorUtils.extractPackageName("a.Foo"));
	}

	// ========== escapeForTextBlock ==========

	@Test
	void escapeForTextBlock_plainText_unchanged() {
		String raw = "SELECT o FROM Office o WHERE o.name = :name";
		assertEquals(raw, JdtRefactorUtils.escapeForTextBlock(raw));
	}

	@Test
	void escapeForTextBlock_emptyString_unchanged() {
		assertEquals("", JdtRefactorUtils.escapeForTextBlock(""));
	}

	@Test
	void escapeForTextBlock_singleQuotesWithoutBackslash_unchanged() {
		assertEquals("SELECT * FROM t WHERE x = 'it''s'",
				JdtRefactorUtils.escapeForTextBlock("SELECT * FROM t WHERE x = 'it''s'"));
	}

	/**
	 * Regression test for the LIKE escape-character bug:
	 * the AOT-generated query {@code ... ESCAPE '\'} (single backslash at runtime)
	 * must become {@code ... ESCAPE '\\'} (doubled) in the text block source so that
	 * the Java compiler reproduces the single backslash at runtime.
	 * <p>
	 * Without doubling, {@code \'} in a text block is a valid Java escape sequence
	 * for {@code '}, silently dropping the backslash and producing an invalid query.
	 */
	@Test
	void escapeForTextBlock_backslashEscapeChar_doubled() {
		// Raw runtime value: ...ESCAPE '\'  (one backslash)
		String raw = "SELECT o FROM Office o WHERE UPPER(o.name) LIKE UPPER(:name) ESCAPE '\\'";

		// Expected text-block-safe value: ...ESCAPE '\\'  (two backslashes)
		String expected = "SELECT o FROM Office o WHERE UPPER(o.name) LIKE UPPER(:name) ESCAPE '\\\\'";

		assertEquals(expected, JdtRefactorUtils.escapeForTextBlock(raw));
	}

	@Test
	void escapeForTextBlock_multipleBackslashes_allDoubled() {
		// Raw: a\b\c  →  escaped: a\\b\\c
		assertEquals("a\\\\b\\\\c", JdtRefactorUtils.escapeForTextBlock("a\\b\\c"));
	}

	@Test
	void escapeForTextBlock_consecutiveBackslashes_allDoubled() {
		// Raw: \\  (two backslashes)  →  escaped: \\\\  (four backslashes)
		assertEquals("\\\\\\\\", JdtRefactorUtils.escapeForTextBlock("\\\\"));
	}

	// ========== addImport ==========

	@Test
	void addImport_typeNotPresent_importAdded() throws Exception {
		String source = """
				package com.example;

				class Foo {
				}
				""";

		String result = applyAddImport(source, "org.springframework.data.domain.Sort");

		assertEquals("""
				package com.example;

				import org.springframework.data.domain.Sort;

				class Foo {
				}
				""", result);
	}

	@Test
	void addImport_javaLangType_notAdded() throws Exception {
		String source = """
				package com.example;

				class Foo {
				}
				""";

		String result = applyAddImport(source, "java.lang.String");

		assertEquals(source, result);
	}

	@Test
	void addImport_defaultPackageType_notAdded() throws Exception {
		String source = """
				package com.example;

				class Foo {
				}
				""";

		String result = applyAddImport(source, "MyClass");

		assertEquals(source, result);
	}

	@Test
	void addImport_samePackageAsCompilationUnit_notAdded() throws Exception {
		String source = """
				package com.example;

				class Foo {
				}
				""";

		String result = applyAddImport(source, "com.example.Bar");

		assertEquals(source, result);
	}

	@Test
	void addImport_exactImportAlreadyPresent_noDuplicate() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.domain.Sort;

				class Foo {
				}
				""";

		String result = applyAddImport(source, "org.springframework.data.domain.Sort");

		assertEquals(source, result);
	}

	@Test
	void addImport_wildcardImportCoversPackage_notAdded() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.domain.*;

				class Foo {
				}
				""";

		String result = applyAddImport(source, "org.springframework.data.domain.Sort");

		assertEquals(source, result);
	}

	@Test
	void addImport_insertedBeforeExistingImportInSortedOrder() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.domain.Sort;

				class Foo {
				}
				""";

		String result = applyAddImport(source, "com.google.common.collect.ImmutableList");

		assertEquals("""
				package com.example;

				import com.google.common.collect.ImmutableList;
				import org.springframework.data.domain.Sort;

				class Foo {
				}
				""", result);
	}

	@Test
	void addImport_insertedAfterAllExistingImportsInSortedOrder() throws Exception {
		String source = """
				package com.example;

				import com.google.common.collect.ImmutableList;

				class Foo {
				}
				""";

		String result = applyAddImport(source, "org.springframework.data.domain.Sort");

		assertEquals("""
				package com.example;

				import com.google.common.collect.ImmutableList;
				import org.springframework.data.domain.Sort;

				class Foo {
				}
				""", result);
	}

	// ========== toLspTextDocumentEdit ==========

	private static final String DOC_URI = "file:///test.java";
	private static final int DOC_VERSION = 3;

	// Document: "Hello World\nSecond Line\n"
	//            0123456789 10 11 12...
	// line 0: "Hello World\n"  (chars 0-11)
	// line 1: "Second Line\n"  (chars 12-23)

	@Test
	void toLspTextDocumentEdit_setsUriAndVersion() throws Exception {
		TextDocument doc = new TextDocument(DOC_URI, LanguageId.JAVA, DOC_VERSION, "Hello");
		InsertEdit jdtEdit = new InsertEdit(0, "X");

		TextDocumentEdit result = JdtRefactorUtils.toLspTextDocumentEdit(jdtEdit, doc);

		assertEquals(DOC_URI, result.getTextDocument().getUri());
		assertEquals(DOC_VERSION, result.getTextDocument().getVersion());
	}

	@Test
	void toLspTextDocumentEdit_insertEdit_convertedToZeroLengthRange() throws Exception {
		TextDocument doc = new TextDocument(DOC_URI, LanguageId.JAVA, 0, "Hello World\nSecond Line\n");
		InsertEdit jdtEdit = new InsertEdit(6, "Beautiful ");

		TextDocumentEdit result = JdtRefactorUtils.toLspTextDocumentEdit(jdtEdit, doc);

		assertEquals(1, result.getEdits().size());
		var lspEdit = result.getEdits().get(0).getLeft();
		assertEquals(0, lspEdit.getRange().getStart().getLine());
		assertEquals(6, lspEdit.getRange().getStart().getCharacter());
		assertEquals(0, lspEdit.getRange().getEnd().getLine());
		assertEquals(6, lspEdit.getRange().getEnd().getCharacter());
		assertEquals("Beautiful ", lspEdit.getNewText());
	}

	@Test
	void toLspTextDocumentEdit_replaceEdit_convertedToNonEmptyRange() throws Exception {
		TextDocument doc = new TextDocument(DOC_URI, LanguageId.JAVA, 0, "Hello World\nSecond Line\n");
		ReplaceEdit jdtEdit = new ReplaceEdit(6, 5, "Universe");

		TextDocumentEdit result = JdtRefactorUtils.toLspTextDocumentEdit(jdtEdit, doc);

		assertEquals(1, result.getEdits().size());
		var lspEdit = result.getEdits().get(0).getLeft();
		assertEquals(0, lspEdit.getRange().getStart().getLine());
		assertEquals(6, lspEdit.getRange().getStart().getCharacter());
		assertEquals(0, lspEdit.getRange().getEnd().getLine());
		assertEquals(11, lspEdit.getRange().getEnd().getCharacter());
		assertEquals("Universe", lspEdit.getNewText());
	}

	@Test
	void toLspTextDocumentEdit_deleteEdit_convertedToEmptyNewText() throws Exception {
		TextDocument doc = new TextDocument(DOC_URI, LanguageId.JAVA, 0, "Hello World\nSecond Line\n");
		DeleteEdit jdtEdit = new DeleteEdit(5, 6);  // delete " World"

		TextDocumentEdit result = JdtRefactorUtils.toLspTextDocumentEdit(jdtEdit, doc);

		assertEquals(1, result.getEdits().size());
		var lspEdit = result.getEdits().get(0).getLeft();
		assertEquals(0, lspEdit.getRange().getStart().getLine());
		assertEquals(5, lspEdit.getRange().getStart().getCharacter());
		assertEquals(0, lspEdit.getRange().getEnd().getLine());
		assertEquals(11, lspEdit.getRange().getEnd().getCharacter());
		assertEquals("", lspEdit.getNewText());
	}

	@Test
	void toLspTextDocumentEdit_multiChildEdit_allChildrenConverted() throws Exception {
		TextDocument doc = new TextDocument(DOC_URI, LanguageId.JAVA, 0, "Hello World\nSecond Line\n");

		MultiTextEdit parent = new MultiTextEdit();
		parent.addChild(new InsertEdit(0, ">>> "));
		parent.addChild(new ReplaceEdit(6, 5, "Universe"));
		parent.addChild(new DeleteEdit(12, 6));  // delete "Second"

		TextDocumentEdit result = JdtRefactorUtils.toLspTextDocumentEdit(parent, doc);

		assertEquals(3, result.getEdits().size());
		assertEquals(">>> ",    result.getEdits().get(0).getLeft().getNewText());
		assertEquals("Universe", result.getEdits().get(1).getLeft().getNewText());
		assertEquals("",         result.getEdits().get(2).getLeft().getNewText());
	}

	@Test
	void toLspTextDocumentEdit_crossLineEdit_correctLineAndCharacter() throws Exception {
		TextDocument doc = new TextDocument(DOC_URI, LanguageId.JAVA, 0, "Hello World\nSecond Line\n");
		// offset 12 = start of line 1 ('S' of "Second")
		InsertEdit jdtEdit = new InsertEdit(12, ">>>");

		TextDocumentEdit result = JdtRefactorUtils.toLspTextDocumentEdit(jdtEdit, doc);

		assertTrue(result.getEdits().size() > 0);
		var lspEdit = result.getEdits().get(0).getLeft();
		assertEquals(1, lspEdit.getRange().getStart().getLine());
		assertEquals(0, lspEdit.getRange().getStart().getCharacter());
	}

}
