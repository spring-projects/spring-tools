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
 * Unit tests for {@link ExtractRequestMappingParentPathRefactoring}.
 */
class ExtractRequestMappingParentPathRefactoringTest {

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

	private static String applyRefactoring(String source, int classOffset, String methodPathPrefix, String classAnnotationPath, int... methodOffsets) throws Exception {
		CompilationUnit cu = parseSource(source);
		ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
		new ExtractRequestMappingParentPathRefactoring(classOffset, methodPathPrefix, classAnnotationPath, methodOffsets).apply(rewrite, cu);
		Document doc = new Document(source);
		TextEdit edit = rewrite.rewriteAST(doc, defaultFormatterOptions());
		edit.apply(doc);
		return doc.get();
	}

	private static int offsetOf(String source, String substring) {
		return source.indexOf(substring);
	}

	@Test
	void singleMemberAnnotationsAreStripped() throws Exception {
		String source = """
				package com.example;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				class FooController {

					@GetMapping("/foo/a")
					String a() {
						return "a";
					}

					@GetMapping("/foo/b")
					String b() {
						return "b";
					}

				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class FooController"), "/foo", "/foo",
				offsetOf(source, "String a()"), offsetOf(source, "String b()"));

		assertEquals("""
				package com.example;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RequestMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RequestMapping("/foo")
				@RestController
				class FooController {

					@GetMapping("/a")
					String a() {
						return "a";
					}

					@GetMapping("/b")
					String b() {
						return "b";
					}

				}
				""", result);
	}

	@Test
	void exactMatchBecomesMarkerAnnotation() throws Exception {
		String source = """
				package com.example;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				class FooController {

					@GetMapping("/foo")
					String a() {
						return "a";
					}

					@GetMapping("/foo/b")
					String b() {
						return "b";
					}

				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class FooController"), "/foo", "/foo",
				offsetOf(source, "String a()"), offsetOf(source, "String b()"));

		assertEquals("""
				package com.example;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RequestMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RequestMapping("/foo")
				@RestController
				class FooController {

					@GetMapping
					String a() {
						return "a";
					}

					@GetMapping("/b")
					String b() {
						return "b";
					}

				}
				""", result);
	}

	@Test
	void normalAnnotationPathAttributeIsUpdatedAndOtherAttributesPreserved() throws Exception {
		String source = """
				package com.example;

				import org.springframework.web.bind.annotation.RequestMapping;
				import org.springframework.web.bind.annotation.RequestMethod;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				class FooController {

					@RequestMapping(value = "/foo/a", method = RequestMethod.GET)
					String a() {
						return "a";
					}

					@RequestMapping(value = "/foo/b", method = RequestMethod.POST)
					String b() {
						return "b";
					}

				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class FooController"), "/foo", "/foo",
				offsetOf(source, "String a()"), offsetOf(source, "String b()"));

		assertEquals("""
				package com.example;

				import org.springframework.web.bind.annotation.RequestMapping;
				import org.springframework.web.bind.annotation.RequestMethod;
				import org.springframework.web.bind.annotation.RestController;

				@RequestMapping("/foo")
				@RestController
				class FooController {

					@RequestMapping(value = "/a", method = RequestMethod.GET)
					String a() {
						return "a";
					}

					@RequestMapping(value = "/b", method = RequestMethod.POST)
					String b() {
						return "b";
					}

				}
				""", result);
	}

	@Test
	void normalAnnotationExactMatchRemovesValuePairButKeepsOthers() throws Exception {
		String source = """
				package com.example;

				import org.springframework.web.bind.annotation.RequestMapping;
				import org.springframework.web.bind.annotation.RequestMethod;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				class FooController {

					@RequestMapping(value = "/foo", method = RequestMethod.GET)
					String a() {
						return "a";
					}

					@RequestMapping(value = "/foo/b", method = RequestMethod.POST)
					String b() {
						return "b";
					}

				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class FooController"), "/foo", "/foo",
				offsetOf(source, "String a()"), offsetOf(source, "String b()"));

		assertEquals("""
				package com.example;

				import org.springframework.web.bind.annotation.RequestMapping;
				import org.springframework.web.bind.annotation.RequestMethod;
				import org.springframework.web.bind.annotation.RestController;

				@RequestMapping("/foo")
				@RestController
				class FooController {

					@RequestMapping(method = RequestMethod.GET)
					String a() {
						return "a";
					}

					@RequestMapping(value = "/b", method = RequestMethod.POST)
					String b() {
						return "b";
					}

				}
				""", result);
	}

	@Test
	void unresolvableMethodIsLeftUnchanged() throws Exception {
		String source = """
				package com.example;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				class FooController {

					@GetMapping("/foo/a")
					String a() {
						return "a";
					}

				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class FooController"), "/foo", "/foo",
				offsetOf(source, "String a()"), 0);

		assertEquals("""
				package com.example;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RequestMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RequestMapping("/foo")
				@RestController
				class FooController {

					@GetMapping("/a")
					String a() {
						return "a";
					}

				}
				""", result);
	}

	@Test
	void mergesIntoExistingSingleMemberClassMapping() throws Exception {
		String source = """
				package com.example;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RequestMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				@RequestMapping("/foo")
				class FooController {

					@GetMapping("/bar/a")
					String a() {
						return "a";
					}

					@GetMapping("/bar/b")
					String b() {
						return "b";
					}

				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class FooController"), "/bar", "/foo/bar",
				offsetOf(source, "String a()"), offsetOf(source, "String b()"));

		assertEquals("""
				package com.example;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RequestMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				@RequestMapping("/foo/bar")
				class FooController {

					@GetMapping("/a")
					String a() {
						return "a";
					}

					@GetMapping("/b")
					String b() {
						return "b";
					}

				}
				""", result);
	}

	@Test
	void mergesIntoExistingNormalAnnotationClassMappingPreservingOtherAttributes() throws Exception {
		String source = """
				package com.example;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RequestMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				@RequestMapping(value = "/foo", produces = "application/json")
				class FooController {

					@GetMapping("/bar/a")
					String a() {
						return "a";
					}

					@GetMapping("/bar/b")
					String b() {
						return "b";
					}

				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class FooController"), "/bar", "/foo/bar",
				offsetOf(source, "String a()"), offsetOf(source, "String b()"));

		assertEquals("""
				package com.example;

				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.RequestMapping;
				import org.springframework.web.bind.annotation.RestController;

				@RestController
				@RequestMapping(value = "/foo/bar", produces = "application/json")
				class FooController {

					@GetMapping("/a")
					String a() {
						return "a";
					}

					@GetMapping("/b")
					String b() {
						return "b";
					}

				}
				""", result);
	}

}
