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
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PreciseBeanTypeRefactoring}.
 */
class PreciseBeanTypeRefactoringTest {

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

	private static String applyRefactoring(String source, int methodOffset, String newReturnType, String oldReturnTypeErasureFqn) throws Exception {
		CompilationUnit cu = parseSource(source);
		ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
		new PreciseBeanTypeRefactoring(new PreciseBeanTypeRefactoring.Fix(methodOffset, newReturnType, oldReturnTypeErasureFqn)).apply(rewrite, cu);
		Document doc = new Document(source);
		TextEdit edit = rewrite.rewriteAST(doc, JavaCore.getOptions());
		edit.apply(doc);
		return doc.get();
	}

	private static int offsetOf(String source, String substring) {
		return source.indexOf(substring);
	}

	@Test
	void replacesReturnTypeAndDropsUnusedImport() throws Exception {
		String source = """
				import org.springframework.context.annotation.Bean;
				import java.util.List;
				import java.util.ArrayList;

				class A {
				    @Bean
				    List bean1() {
				        return new ArrayList();
				    }
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "List bean1()"), "java.util.ArrayList", "java.util.List");

		assertEquals("""
				import org.springframework.context.annotation.Bean;
				import java.util.ArrayList;

				class A {
				    @Bean
				    ArrayList bean1() {
				        return new ArrayList();
				    }
				}
				""", result);
	}

	@Test
	void keepsOldImportIfStillUsedElsewhere() throws Exception {
		String source = """
				import org.springframework.context.annotation.Bean;
				import java.util.List;
				import java.util.Stack;

				class A {
				    List other;

				    @Bean
				    List bean1() {
				        return new Stack();
				    }
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "List bean1()"), "java.util.Stack", "java.util.List");

		assertEquals("""
				import org.springframework.context.annotation.Bean;
				import java.util.List;
				import java.util.Stack;

				class A {
				    List other;

				    @Bean
				    Stack bean1() {
				        return new Stack();
				    }
				}
				""", result);
	}

	@Test
	void replacesParameterizedReturnTypeAndAddsImport() throws Exception {
		String source = """
				import org.springframework.context.annotation.Bean;
				import java.util.Collection;
				import java.util.List;

				class A {
				    @Bean
				    Collection<Integer> bean1() {
				        return List.of(5);
				    }
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "Collection<Integer> bean1()"),
				"java.util.List<java.lang.Integer>", "java.util.Collection");

		assertEquals("""
				import org.springframework.context.annotation.Bean;
				import java.util.List;

				class A {
				    @Bean
				    List<Integer> bean1() {
				        return List.of(5);
				    }
				}
				""", result);
	}

	@Test
	void ignoresOffsetOutsideMethodDeclaration() throws Exception {
		String source = """
				import org.springframework.context.annotation.Bean;

				class A {
				    int field;
				}
				""";

		String result = applyRefactoring(source, offsetOf(source, "int field"), "java.lang.String", null);

		assertEquals(source, result);
	}

	@Test
	void batchConstructorFixesEveryOffsetInOneApply() throws Exception {
		String source = """
				import org.springframework.context.annotation.Bean;
				import java.util.List;
				import java.util.ArrayList;
				import java.util.Stack;

				class A {
				    @Bean
				    List bean1() {
				        return new ArrayList();
				    }

				    @Bean
				    List bean2() {
				        return new Stack();
				    }
				}
				""";

		CompilationUnit cu = parseSource(source);
		ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
		new PreciseBeanTypeRefactoring(
				new PreciseBeanTypeRefactoring.Fix(offsetOf(source, "List bean1()"), "java.util.ArrayList", "java.util.List"),
				new PreciseBeanTypeRefactoring.Fix(offsetOf(source, "List bean2()"), "java.util.Stack", "java.util.List"))
			.apply(rewrite, cu);
		Document doc = new Document(source);
		TextEdit edit = rewrite.rewriteAST(doc, JavaCore.getOptions());
		edit.apply(doc);

		assertEquals("""
				import org.springframework.context.annotation.Bean;
				import java.util.ArrayList;
				import java.util.Stack;

				class A {
				    @Bean
				    ArrayList bean1() {
				        return new ArrayList();
				    }

				    @Bean
				    Stack bean2() {
				        return new Stack();
				    }
				}
				""", doc.get());
	}

}
