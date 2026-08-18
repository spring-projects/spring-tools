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
import org.springframework.ide.vscode.boot.java.Annotations;

/**
 * Unit tests for {@link ReplaceScopeAnnotationRefactoring}.
 */
class ReplaceScopeAnnotationRefactoringTest {

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

	private static String applyRefactoring(String source, int declarationOffset, String targetAnnotationFqn, String proxyModeConstant) throws Exception {
		CompilationUnit cu = parseSource(source);
		ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
		new ReplaceScopeAnnotationRefactoring(declarationOffset, targetAnnotationFqn, proxyModeConstant).apply(rewrite, cu);
		Document doc = new Document(source);
		TextEdit edit = rewrite.rewriteAST(doc, defaultFormatterOptions());
		edit.apply(doc);
		return doc.get();
	}

	private static int offsetOf(String source, String substring) {
		return source.indexOf(substring);
	}

	@Test
	void bareReplacementBecomesMarkerAnnotation() throws Exception {
		String source = """
				package com.example;

				import org.springframework.context.annotation.Scope;
				import org.springframework.stereotype.Component;

				@Component
				@Scope("request")
				class FooBean {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "@Component"), Annotations.SPRING_REQUEST_SCOPE, null);

		assertEquals("""
				package com.example;

				import org.springframework.stereotype.Component;
				import org.springframework.web.context.annotation.RequestScope;

				@Component
				@RequestScope
				class FooBean {
				}
				""", result);
	}

	@Test
	void proxyModeIsCarriedOverExplicitlyWhenPreserving() throws Exception {
		String source = """
				package com.example;

				import org.springframework.context.annotation.Scope;
				import org.springframework.stereotype.Component;

				@Component
				@Scope("session")
				class FooBean {
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "@Component"), Annotations.SPRING_SESSION_SCOPE, "DEFAULT");

		assertEquals("""
				package com.example;

				import org.springframework.context.annotation.ScopedProxyMode;
				import org.springframework.stereotype.Component;
				import org.springframework.web.context.annotation.SessionScope;

				@Component
				@SessionScope(proxyMode = ScopedProxyMode.DEFAULT)
				class FooBean {
				}
				""", result);
	}

	@Test
	void applicationScopeBareReplacement() throws Exception {
		String source = """
				package com.example;

				import org.springframework.context.annotation.Bean;
				import org.springframework.context.annotation.Configuration;
				import org.springframework.context.annotation.Scope;

				@Configuration
				class FooConfig {

					@Bean
					@Scope("application")
					FooBean fooBean() {
						return new FooBean();
					}

				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "@Bean"), Annotations.SPRING_APPLICATION_SCOPE, null);

		assertEquals("""
				package com.example;

				import org.springframework.context.annotation.Bean;
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.context.annotation.ApplicationScope;

				@Configuration
				class FooConfig {

					@Bean
					@ApplicationScope
					FooBean fooBean() {
						return new FooBean();
					}

				}
				""", result);
	}

}
