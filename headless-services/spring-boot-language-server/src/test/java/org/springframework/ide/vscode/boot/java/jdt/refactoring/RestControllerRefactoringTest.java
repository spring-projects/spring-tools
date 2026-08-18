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
 * Unit tests for {@link RestControllerRefactoring}.
 */
class RestControllerRefactoringTest {

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
		new RestControllerRefactoring(offsets).apply(rewrite, cu);
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

				import org.springframework.stereotype.Controller;
				import org.springframework.web.bind.annotation.ResponseBody;

				@Controller
				@ResponseBody
				class GreetingController {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class GreetingController"));

		assertEquals("""
				package com.example;

				import org.springframework.web.bind.annotation.RestController;

				@RestController
				class GreetingController {
				}
				""", result);
	}

	@Test
	void lineCommentIsNotRemoved() throws Exception {
		String source = """
				package com.example;

				import org.springframework.stereotype.Controller;
				import org.springframework.web.bind.annotation.ResponseBody;

				/**
				 * class comment
				 */
				// line comment
				@Controller
				@ResponseBody
				class GreetingController {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class GreetingController"));

		assertEquals("""
				package com.example;

				import org.springframework.web.bind.annotation.RestController;

				/**
				 * class comment
				 */
				// line comment
				@RestController
				class GreetingController {
				}
				""", result);
	}

	@Test
	void beanNameValueIsCarriedOver() throws Exception {
		String source = """
				package com.example;

				import org.springframework.stereotype.Controller;
				import org.springframework.web.bind.annotation.ResponseBody;

				@Controller("greetingController")
				@ResponseBody
				class GreetingController {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class GreetingController"));

		assertEquals("""
				package com.example;

				import org.springframework.web.bind.annotation.RestController;

				@RestController("greetingController")
				class GreetingController {
				}
				""", result);
	}

	@Test
	void beanNameValueAttributeIsCarriedOver() throws Exception {
		String source = """
				package com.example;

				import org.springframework.stereotype.Controller;
				import org.springframework.web.bind.annotation.ResponseBody;

				@Controller(value = "greetingController")
				@ResponseBody
				class GreetingController {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class GreetingController"));

		assertEquals("""
				package com.example;

				import org.springframework.web.bind.annotation.RestController;

				@RestController(value = "greetingController")
				class GreetingController {
				}
				""", result);
	}

	@Test
	void missingResponseBodyIsLeftUnchanged() throws Exception {
		String source = """
				package com.example;

				import org.springframework.stereotype.Controller;

				@Controller
				class GreetingController {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class GreetingController"));

		assertEquals(source, result);
	}

	@Test
	void missingControllerIsLeftUnchanged() throws Exception {
		String source = """
				package com.example;

				import org.springframework.web.bind.annotation.ResponseBody;

				@ResponseBody
				class GreetingController {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class GreetingController"));

		assertEquals(source, result);
	}

	@Test
	void otherAnnotationsArePreserved() throws Exception {
		String source = """
				package com.example;

				import org.springframework.stereotype.Controller;
				import org.springframework.web.bind.annotation.ResponseBody;

				@Deprecated
				@Controller
				@ResponseBody
				class GreetingController {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class GreetingController"));

		assertEquals("""
				package com.example;

				import org.springframework.web.bind.annotation.RestController;

				@Deprecated
				@RestController
				class GreetingController {
				}
				""", result);
	}

	@Test
	void convertsAllGivenOffsetsInOneFile() throws Exception {
		String source = """
				package com.example;

				import org.springframework.stereotype.Controller;
				import org.springframework.web.bind.annotation.ResponseBody;

				@Controller
				@ResponseBody
				class GreetingController {
				}

				@Controller
				@ResponseBody
				class FarewellController {
				}
				""";

		String result = applyRefactoring(source,
				offsetOf(source, "class GreetingController"),
				offsetOf(source, "class FarewellController"));

		assertEquals("""
				package com.example;

				import org.springframework.web.bind.annotation.RestController;

				@RestController
				class GreetingController {
				}

				@RestController
				class FarewellController {
				}
				""", result);
	}

}
