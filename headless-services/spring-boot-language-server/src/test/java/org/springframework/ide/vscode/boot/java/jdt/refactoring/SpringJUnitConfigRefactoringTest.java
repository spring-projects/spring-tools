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
 * Unit tests for {@link SpringJUnitConfigRefactoring}.
 */
class SpringJUnitConfigRefactoringTest {

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
		new SpringJUnitConfigRefactoring(offsets).apply(rewrite, cu);
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

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.test.context.ContextConfiguration;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				@ExtendWith(SpringExtension.class)
				@ContextConfiguration
				class GreetingTests {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class GreetingTests"));

		assertEquals("""
				package com.example;

				import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

				@SpringJUnitConfig
				class GreetingTests {
				}
				""", result);
	}

	@Test
	void configurationClassesAreCarriedOverAsDefaultAttribute() throws Exception {
		String source = """
				package com.example;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.test.context.ContextConfiguration;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				@ExtendWith(SpringExtension.class)
				@ContextConfiguration(classes = TestConfig.class)
				class GreetingTests {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class GreetingTests"));

		assertEquals("""
				package com.example;

				import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

				@SpringJUnitConfig(TestConfig.class)
				class GreetingTests {
				}
				""", result);
	}

	@Test
	void multipleConfigurationClassesAreCarriedOverAsDefaultAttribute() throws Exception {
		String source = """
				package com.example;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.test.context.ContextConfiguration;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				@ExtendWith(SpringExtension.class)
				@ContextConfiguration(classes = { TestConfig.class, MoreConfig.class })
				class GreetingTests {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class GreetingTests"));

		assertEquals("""
				package com.example;

				import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

				@SpringJUnitConfig({ TestConfig.class, MoreConfig.class })
				class GreetingTests {
				}
				""", result);
	}

	@Test
	void defaultAttributeBecomesExplicitLocations() throws Exception {
		String source = """
				package com.example;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.test.context.ContextConfiguration;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				@ExtendWith(SpringExtension.class)
				@ContextConfiguration("/test-context.xml")
				class GreetingTests {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class GreetingTests"));

		assertEquals("""
				package com.example;

				import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

				@SpringJUnitConfig(locations = "/test-context.xml")
				class GreetingTests {
				}
				""", result);
	}

	@Test
	void valueAttributeBecomesExplicitLocations() throws Exception {
		String source = """
				package com.example;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.test.context.ContextConfiguration;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				@ExtendWith(SpringExtension.class)
				@ContextConfiguration(value = { "/a.xml", "/b.xml" })
				class GreetingTests {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class GreetingTests"));

		assertEquals("""
				package com.example;

				import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

				@SpringJUnitConfig(locations = { "/a.xml", "/b.xml" })
				class GreetingTests {
				}
				""", result);
	}

	@Test
	void otherAttributesAreCarriedOverUnchanged() throws Exception {
		String source = """
				package com.example;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.test.context.ContextConfiguration;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				@ExtendWith(SpringExtension.class)
				@ContextConfiguration(classes = TestConfig.class, inheritLocations = false, name = "parent")
				class GreetingTests {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class GreetingTests"));

		assertEquals("""
				package com.example;

				import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

				@SpringJUnitConfig(classes = TestConfig.class, inheritLocations = false, name = "parent")
				class GreetingTests {
				}
				""", result);
	}

	@Test
	void annotationOrderIsPreserved() throws Exception {
		String source = """
				package com.example;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.test.context.ContextConfiguration;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				@ContextConfiguration(classes = TestConfig.class)
				@ExtendWith(SpringExtension.class)
				class GreetingTests {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class GreetingTests"));

		assertEquals("""
				package com.example;

				import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

				@SpringJUnitConfig(TestConfig.class)
				class GreetingTests {
				}
				""", result);
	}

	@Test
	void lineCommentIsNotRemoved() throws Exception {
		String source = """
				package com.example;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.test.context.ContextConfiguration;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				/**
				 * class comment
				 */
				// line comment
				@ExtendWith(SpringExtension.class)
				@ContextConfiguration
				class GreetingTests {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class GreetingTests"));

		assertEquals("""
				package com.example;

				import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

				/**
				 * class comment
				 */
				// line comment
				@SpringJUnitConfig
				class GreetingTests {
				}
				""", result);
	}

	@Test
	void otherAnnotationsArePreserved() throws Exception {
		String source = """
				package com.example;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.test.context.ContextConfiguration;
				import org.springframework.test.context.ActiveProfiles;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				@ActiveProfiles("test")
				@ExtendWith(SpringExtension.class)
				@ContextConfiguration
				class GreetingTests {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class GreetingTests"));

		assertEquals("""
				package com.example;

				import org.springframework.test.context.ActiveProfiles;
				import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

				@ActiveProfiles("test")
				@SpringJUnitConfig
				class GreetingTests {
				}
				""", result);
	}

	@Test
	void furtherExtensionsAreLeftUnchanged() throws Exception {
		String source = """
				package com.example;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.test.context.ContextConfiguration;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				@ExtendWith({ SpringExtension.class, MockitoExtension.class })
				@ContextConfiguration
				class GreetingTests {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class GreetingTests"));

		assertEquals(source, result);
	}

	@Test
	void repeatedExtendWithKeepsTheOtherExtension() throws Exception {
		String source = """
				package com.example;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.test.context.ContextConfiguration;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				@ExtendWith(SpringExtension.class)
				@ExtendWith(MockitoExtension.class)
				@ContextConfiguration
				class GreetingTests {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class GreetingTests"));

		// the surviving '@ExtendWith' keeps its import in production; this bare-parser test has
		// no classpath, so the binding-based usage check in 'removeImports' cannot see it
		assertEquals("""
				package com.example;

				import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

				@SpringJUnitConfig
				@ExtendWith(MockitoExtension.class)
				class GreetingTests {
				}
				""", result);
	}

	@Test
	void missingContextConfigurationIsLeftUnchanged() throws Exception {
		String source = """
				package com.example;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				@ExtendWith(SpringExtension.class)
				class GreetingTests {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class GreetingTests"));

		assertEquals(source, result);
	}

	@Test
	void missingExtendWithIsLeftUnchanged() throws Exception {
		String source = """
				package com.example;

				import org.springframework.test.context.ContextConfiguration;

				@ContextConfiguration
				class GreetingTests {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "class GreetingTests"));

		assertEquals(source, result);
	}

	@Test
	void convertsAllGivenOffsetsInOneFile() throws Exception {
		String source = """
				package com.example;

				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.test.context.ContextConfiguration;
				import org.springframework.test.context.junit.jupiter.SpringExtension;

				@ExtendWith(SpringExtension.class)
				@ContextConfiguration
				class GreetingTests {
				}

				@ExtendWith(SpringExtension.class)
				@ContextConfiguration(classes = TestConfig.class)
				class FarewellTests {
				}
				""";

		String result = applyRefactoring(source,
				offsetOf(source, "class GreetingTests"),
				offsetOf(source, "class FarewellTests"));

		assertEquals("""
				package com.example;

				import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

				@SpringJUnitConfig
				class GreetingTests {
				}

				@SpringJUnitConfig(TestConfig.class)
				class FarewellTests {
				}
				""", result);
	}

}
