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

/**
 * Unit tests for {@link ApplicationModuleListenerRefactoring}.
 */
class ApplicationModuleListenerRefactoringTest {

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
		new ApplicationModuleListenerRefactoring(offsets).apply(rewrite, cu);
		Document doc = new Document(source);
		TextEdit edit = rewrite.rewriteAST(doc, defaultFormatterOptions());
		edit.apply(doc);
		return doc.get();
	}

	private static int offsetOf(String source, String substring) {
		return source.indexOf(substring);
	}

	@Test
	void plainCombinationIsReplaced() throws Exception {
		String source = """
				package com.example;

				import org.springframework.scheduling.annotation.Async;
				import org.springframework.transaction.annotation.Transactional;
				import org.springframework.transaction.event.TransactionalEventListener;

				class OrderEventListener {

					@Async
					@Transactional
					@TransactionalEventListener
					void on(OrderCompleted event) {
					}

				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "void on"));

		assertEquals("""
				package com.example;

				import org.springframework.modulith.events.ApplicationModuleListener;

				class OrderEventListener {

					@ApplicationModuleListener
					void on(OrderCompleted event) {
					}

				}
				""", result);
	}

	@Test
	void lineCommentIsNotRemoved() throws Exception {
		String source = """
				package com.example;

				import org.springframework.scheduling.annotation.Async;
				import org.springframework.transaction.annotation.Transactional;
				import org.springframework.transaction.event.TransactionalEventListener;

				class OrderEventListener {

					/**
					 * method comment
					 */
					// line comment
					@Async
					@Transactional
					@TransactionalEventListener
					void on(OrderCompleted event) {
					}

				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "void on"));

		assertEquals("""
				package com.example;

				import org.springframework.modulith.events.ApplicationModuleListener;

				class OrderEventListener {

					/**
					 * method comment
					 */
					// line comment
					@ApplicationModuleListener
					void on(OrderCompleted event) {
					}

				}
				""", result);
	}

	@Test
	void lineCommentIsWrongIndentationDoesNotCauseTrouble() throws Exception {
		String source = """
				package com.example;

				import org.springframework.scheduling.annotation.Async;
				import org.springframework.transaction.annotation.Transactional;
				import org.springframework.transaction.event.TransactionalEventListener;

				class OrderEventListener {

					/**
					 * method comment
					 */
				// line comment
					@Async
					@Transactional
					@TransactionalEventListener
					void on(OrderCompleted event) {
					}

				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "void on"));

		assertEquals("""
				package com.example;

				import org.springframework.modulith.events.ApplicationModuleListener;

				class OrderEventListener {

					/**
					 * method comment
					 */
				// line comment
					@ApplicationModuleListener
					void on(OrderCompleted event) {
					}

				}
				""", result);
	}

	@Test
	void supportedAttributesAreCarriedOver() throws Exception {
		String source = """
				package com.example;

				import org.springframework.scheduling.annotation.Async;
				import org.springframework.transaction.annotation.Propagation;
				import org.springframework.transaction.annotation.Transactional;
				import org.springframework.transaction.event.TransactionalEventListener;

				class OrderEventListener {

					@Async
					@Transactional(readOnly = true, propagation = Propagation.REQUIRED)
					@TransactionalEventListener(id = "orderCompleted", condition = "#event.valid")
					void on(OrderCompleted event) {
					}

				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "void on"));

		assertEquals("""
				package com.example;

				import org.springframework.modulith.events.ApplicationModuleListener;
				import org.springframework.transaction.annotation.Propagation;

				class OrderEventListener {

					@ApplicationModuleListener(readOnlyTransaction = true, propagation = Propagation.REQUIRED, id = "orderCompleted", condition = "#event.valid")
					void on(OrderCompleted event) {
					}

				}
				""", result);
	}

	@Test
	void unsupportedTransactionalAttributeIsLeftUnchanged() throws Exception {
		String source = """
				package com.example;

				import org.springframework.scheduling.annotation.Async;
				import org.springframework.transaction.annotation.Transactional;
				import org.springframework.transaction.event.TransactionalEventListener;

				class OrderEventListener {

					@Async
					@Transactional(timeout = 5)
					@TransactionalEventListener
					void on(OrderCompleted event) {
					}

				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "void on"));

		assertEquals(source, result);
	}

	@Test
	void unsupportedEventListenerAttributeIsLeftUnchanged() throws Exception {
		String source = """
				package com.example;

				import org.springframework.scheduling.annotation.Async;
				import org.springframework.transaction.annotation.Transactional;
				import org.springframework.transaction.event.TransactionPhase;
				import org.springframework.transaction.event.TransactionalEventListener;

				class OrderEventListener {

					@Async
					@Transactional
					@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
					void on(OrderCompleted event) {
					}

				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "void on"));

		assertEquals(source, result);
	}

	@Test
	void unsupportedAsyncAttributeIsLeftUnchanged() throws Exception {
		String source = """
				package com.example;

				import org.springframework.scheduling.annotation.Async;
				import org.springframework.transaction.annotation.Transactional;
				import org.springframework.transaction.event.TransactionalEventListener;

				class OrderEventListener {

					@Async("orderExecutor")
					@Transactional
					@TransactionalEventListener
					void on(OrderCompleted event) {
					}

				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "void on"));

		assertEquals(source, result);
	}

	@Test
	void missingAnnotationIsLeftUnchanged() throws Exception {
		String source = """
				package com.example;

				import org.springframework.transaction.annotation.Transactional;
				import org.springframework.transaction.event.TransactionalEventListener;

				class OrderEventListener {

					@Transactional
					@TransactionalEventListener
					void on(OrderCompleted event) {
					}

				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "void on"));

		assertEquals(source, result);
	}

	@Test
	void otherAnnotationsArePreserved() throws Exception {
		String source = """
				package com.example;

				import org.springframework.scheduling.annotation.Async;
				import org.springframework.transaction.annotation.Transactional;
				import org.springframework.transaction.event.TransactionalEventListener;

				class OrderEventListener {

					@Deprecated
					@Async
					@Transactional
					@TransactionalEventListener
					void on(OrderCompleted event) {
					}

				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "void on"));

		assertEquals("""
				package com.example;

				import org.springframework.modulith.events.ApplicationModuleListener;

				class OrderEventListener {

					@Deprecated
					@ApplicationModuleListener
					void on(OrderCompleted event) {
					}

				}
				""", result);
	}

	@Test
	void convertsAllGivenOffsetsInOneFile() throws Exception {
		String source = """
				package com.example;

				import org.springframework.scheduling.annotation.Async;
				import org.springframework.transaction.annotation.Transactional;
				import org.springframework.transaction.event.TransactionalEventListener;

				class OrderEventListener {

					@Async
					@Transactional
					@TransactionalEventListener
					void onCompleted(OrderCompleted event) {
					}

					@Async
					@Transactional
					@TransactionalEventListener
					void onCancelled(OrderCancelled event) {
					}

				}
				""";

		String result = applyRefactoring(source,
				offsetOf(source, "void onCompleted"),
				offsetOf(source, "void onCancelled"));

		assertEquals("""
				package com.example;

				import org.springframework.modulith.events.ApplicationModuleListener;

				class OrderEventListener {

					@ApplicationModuleListener
					void onCompleted(OrderCompleted event) {
					}

					@ApplicationModuleListener
					void onCancelled(OrderCancelled event) {
					}

				}
				""", result);
	}

}
