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

import java.util.List;
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
import org.springframework.ide.vscode.boot.java.jdt.refactoring.AddAnnotationRefactoring.Attribute;

/**
 * Unit tests for {@link AddAnnotationRefactoring}.
 * <p>
 * Pure JDT-level: source text in, apply edit, assert result.
 * No Spring context, LSP harness, or mock beans required.
 * <p>
 * Note: these tests parse without binding resolution. Method matching therefore
 * falls back to name + parameter count only (the declaring-class and
 * parameter-type checks are skipped when bindings are unavailable).
 */
class AddAnnotationRefactoringTest {

	private static final String JPA_QUERY_FQN = "org.springframework.data.jpa.repository.Query";
	private static final String DECLARING_CLASS = "com.example.OrderRepository";

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

	private static String applyRefactoring(String source, String methodName, List<String> paramTypes,
			List<Attribute> attrs) throws Exception {
		return applyRefactoring(source, JPA_QUERY_FQN, DECLARING_CLASS, methodName, paramTypes, attrs);
	}

	private static String applyRefactoring(String source, String annotationFqn, String declaringClass,
			String methodName, List<String> paramTypes, List<Attribute> attrs) throws Exception {
		CompilationUnit cu = parseSource(source);
		ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
		new AddAnnotationRefactoring(annotationFqn, declaringClass, methodName, paramTypes, attrs).apply(rewrite, cu);
		Document doc = new Document(source);
		TextEdit edit = rewrite.rewriteAST(doc, defaultFormatterOptions());
		edit.apply(doc);
		return doc.get();
	}

	// ========== No existing annotation ==========

	@Test
	void noAnnotation_singleValueAttribute_addsSingleMemberAnnotation() throws Exception {
		String source = """
				package com.example;

				interface OrderRepository {
					List<Order> findByStatus(String status);
				}
				""";

		String result = applyRefactoring(source, "findByStatus", List.of("String"),
				List.of(new Attribute("value", "\"SELECT o FROM Order o WHERE o.status = :status\"")));

		assertEquals("""
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query("SELECT o FROM Order o WHERE o.status = :status")
					List<Order> findByStatus(String status);
				}
				""", result);
	}

	@Test
	void noAnnotation_multipleAttributes_addsNormalAnnotation() throws Exception {
		String source = """
				package com.example;

				interface OrderRepository {
					List<Order> findByStatus(String status);
				}
				""";

		String result = applyRefactoring(source, "findByStatus", List.of("String"),
				List.of(
						new Attribute("value", "\"SELECT o FROM Order o WHERE o.status = :status\""),
						new Attribute("countQuery", "\"SELECT COUNT(o) FROM Order o WHERE o.status = :status\"")));

		assertEquals("""
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query(value = "SELECT o FROM Order o WHERE o.status = :status", countQuery = "SELECT COUNT(o) FROM Order o WHERE o.status = :status")
					List<Order> findByStatus(String status);
				}
				""", result);
	}

	@Test
	void noAnnotation_importAlreadyPresent_noDuplicateImport() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					List<Order> findByStatus(String status);
				}
				""";

		String result = applyRefactoring(source, "findByStatus", List.of("String"),
				List.of(new Attribute("value", "\"SELECT o FROM Order o WHERE o.status = :status\"")));

		assertEquals("""
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query("SELECT o FROM Order o WHERE o.status = :status")
					List<Order> findByStatus(String status);
				}
				""", result);
	}

	@Test
	void noAnnotation_textBlockValue_addsTextBlockAnnotation() throws Exception {
		String source = """
				package com.example;

				interface OrderRepository {
					List<Order> findAll();
				}
				""";

		String textBlockValue = "\"\"\"\n\t\t\tSELECT o\n\t\t\tFROM Order o\n\t\t\t\"\"\"";

		String result = applyRefactoring(source, "findAll", List.of(),
				List.of(new Attribute("value", textBlockValue)));

		assertEquals("""
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query(\"""
				\t\t\tSELECT o
				\t\t\tFROM Order o
				\t\t\t\""")
					List<Order> findAll();
				}
				""", result);
	}

	// ========== Existing marker annotation ==========

	@Test
	void existingMarkerAnnotation_emptyAttributeList_noChange() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query
					List<Order> findByStatus(String status);
				}
				""";

		String result = applyRefactoring(source, "findByStatus", List.of("String"), List.of());

		assertEquals(source, result);
	}

	@Test
	void existingMarkerAnnotation_replacedWithFullAnnotation() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query
					List<Order> findByStatus(String status);
				}
				""";

		String result = applyRefactoring(source, "findByStatus", List.of("String"),
				List.of(new Attribute("value", "\"SELECT o FROM Order o WHERE o.status = :status\"")));

		assertEquals("""
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query("SELECT o FROM Order o WHERE o.status = :status")
					List<Order> findByStatus(String status);
				}
				""", result);
	}

	// ========== Existing single-member annotation ==========

	@Test
	void existingSingleMemberAnnotation_newAttributeAdded_becomesNormalAnnotation() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query("SELECT o FROM Order o WHERE o.status = :status")
					List<Order> findByStatus(String status);
				}
				""";

		String result = applyRefactoring(source, "findByStatus", List.of("String"),
				List.of(new Attribute("countQuery", "\"SELECT COUNT(o) FROM Order o WHERE o.status = :status\"")));

		assertEquals("""
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query(value = "SELECT o FROM Order o WHERE o.status = :status", countQuery = "SELECT COUNT(o) FROM Order o WHERE o.status = :status")
					List<Order> findByStatus(String status);
				}
				""", result);
	}

	@Test
	void existingSingleMemberAnnotation_onlyValueAttributePassed_unchanged() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query("SELECT o FROM Order o WHERE o.status = :status")
					List<Order> findByStatus(String status);
				}
				""";

		// When only 'value' is passed there are no non-value attributes, so the
		// single-member annotation must stay as-is (no conversion to NormalAnnotation).
		String result = applyRefactoring(source, "findByStatus", List.of("String"),
				List.of(new Attribute("value", "\"NEW VALUE\"")));

		assertEquals(source, result);
	}

	@Test
	void existingSingleMemberAnnotation_valueAndNonValueAttributesPassed_existingValuePreservedNonValueAdded() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query("SELECT o FROM Order o WHERE o.status = :status")
					List<Order> findByStatus(String status);
				}
				""";

		// Passing a 'value' attribute alongside a non-value one: the caller-provided
		// 'value' must be discarded in favour of the value already in the annotation.
		String result = applyRefactoring(source, "findByStatus", List.of("String"),
				List.of(
						new Attribute("value", "\"NEW VALUE\""),
						new Attribute("countQuery", "\"SELECT COUNT(o) FROM Order o WHERE o.status = :status\"")));

		assertEquals("""
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query(value = "SELECT o FROM Order o WHERE o.status = :status", countQuery = "SELECT COUNT(o) FROM Order o WHERE o.status = :status")
					List<Order> findByStatus(String status);
				}
				""", result);
	}

	@Test
	void existingSingleMemberAnnotation_noNewAttributes_unchanged() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query("SELECT o FROM Order o WHERE o.status = :status")
					List<Order> findByStatus(String status);
				}
				""";

		String result = applyRefactoring(source, "findByStatus", List.of("String"), List.of());

		assertEquals(source, result);
	}

	// ========== Existing normal annotation ==========

	@Test
	void existingNormalAnnotation_multipleMissingAttributes_allAdded() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query(value = "SELECT o FROM Order o WHERE o.status = :status")
					List<Order> findByStatus(String status);
				}
				""";

		String result = applyRefactoring(source, "findByStatus", List.of("String"),
				List.of(
						new Attribute("value", "\"NEW VALUE\""),
						new Attribute("countQuery", "\"SELECT COUNT(o) FROM Order o WHERE o.status = :status\""),
						new Attribute("name", "\"findByStatus\"")));

		assertEquals("""
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query(value = "SELECT o FROM Order o WHERE o.status = :status", countQuery = "SELECT COUNT(o) FROM Order o WHERE o.status = :status", name = "findByStatus")
					List<Order> findByStatus(String status);
				}
				""", result);
	}

	@Test
	void existingNormalAnnotation_missingAttributeAdded() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query(value = "SELECT o FROM Order o WHERE o.status = :status")
					List<Order> findByStatus(String status);
				}
				""";

		String result = applyRefactoring(source, "findByStatus", List.of("String"),
				List.of(
						new Attribute("value", "\"NEW VALUE\""),
						new Attribute("countQuery", "\"SELECT COUNT(o) FROM Order o WHERE o.status = :status\"")));

		assertEquals("""
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query(value = "SELECT o FROM Order o WHERE o.status = :status", countQuery = "SELECT COUNT(o) FROM Order o WHERE o.status = :status")
					List<Order> findByStatus(String status);
				}
				""", result);
	}

	@Test
	void existingNormalAnnotation_allAttributesAlreadyPresent_noChange() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query(value = "SELECT o FROM Order o WHERE o.status = :status", countQuery = "SELECT COUNT(o)")
					List<Order> findByStatus(String status);
				}
				""";

		String result = applyRefactoring(source, "findByStatus", List.of("String"),
				List.of(
						new Attribute("value", "\"NEW\""),
						new Attribute("countQuery", "\"NEW\"")));

		assertEquals(source, result);
	}

	// ========== Method matching ==========

	@Test
	void methodNotFound_noChange() throws Exception {
		String source = """
				package com.example;

				interface OrderRepository {
					List<Order> findByStatus(String status);
				}
				""";

		String result = applyRefactoring(source, "findByName", List.of("String"),
				List.of(new Attribute("value", "\"SELECT o FROM Order o\"")));

		assertEquals(source, result);
	}

	@Test
	void multipleMethodsWithSameName_matchesByParamCount() throws Exception {
		String source = """
				package com.example;

				interface OrderRepository {
					List<Order> findByStatus(String status);
					List<Order> findByStatus(String status, String type);
				}
				""";

		String result = applyRefactoring(source, "findByStatus", List.of("String"),
				List.of(new Attribute("value", "\"SELECT o FROM Order o WHERE o.status = :status\"")));

		assertEquals("""
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query("SELECT o FROM Order o WHERE o.status = :status")
					List<Order> findByStatus(String status);
					List<Order> findByStatus(String status, String type);
				}
				""", result);
	}

	// ========== Text block with backslash (escape character bug) ==========

	/**
	 * Regression test: a query containing a backslash (e.g. {@code ESCAPE '\'} in JPQL)
	 * must be represented as {@code ESCAPE '\\'} inside a text block, because Java
	 * recognises {@code \'} as an escape sequence for {@code '} and silently drops the
	 * backslash otherwise.
	 * <p>
	 * {@code createAttributeList} is responsible for calling
	 * {@code DataRepositoryAotMetadataCodeLensProvider.escapeForTextBlock} before
	 * embedding the formatted value into the text block literal.
	 */
	@Test
	void noAnnotation_textBlockWithBackslash_backslashDoubledInSource() throws Exception {
		String source = """
				package com.example;

				interface OfficeRepository {
					List<Office> findByNameContainingIgnoreCase(String name);
				}
				""";

		// Simulates what createAttributeList produces when the raw runtime value
		// contains a literal backslash, e.g.:
		//   SELECT o FROM Office o WHERE UPPER(o.name) LIKE UPPER(:name) ESCAPE '\'
		// JdtRefactorUtils.escapeForTextBlock doubles it to \\ in the text block source.
		String textBlockValue = "\"\"\"\n\t\t\tSELECT o\n\t\t\tFROM Office o\n\t\t\tWHERE UPPER(o.name) LIKE UPPER(:name) ESCAPE '\\\\'\n\t\t\t\"\"\"";

		String result = applyRefactoring(source, "findByNameContainingIgnoreCase", List.of("String"),
				List.of(new Attribute("value", textBlockValue)));

		assertEquals("""
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OfficeRepository {
					@Query(\"""
				\t\t\tSELECT o
				\t\t\tFROM Office o
				\t\t\tWHERE UPPER(o.name) LIKE UPPER(:name) ESCAPE '\\\\'
				\t\t\t\""")
					List<Office> findByNameContainingIgnoreCase(String name);
				}
				""", result);
	}

	// ========== Annotation matched by simple name ==========

	@Test
	void existingAnnotationBySimpleName_treatedAsExisting() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query("SELECT o FROM Order o WHERE o.status = :status")
					List<Order> findByStatus(String status);
				}
				""";

		// Use FQN form of same annotation — should still find the existing @Query by simple name
		String result = applyRefactoring(source, JPA_QUERY_FQN, DECLARING_CLASS, "findByStatus", List.of("String"),
				List.of(new Attribute("countQuery", "\"SELECT COUNT(o)\"")));

		assertEquals("""
				package com.example;

				import org.springframework.data.jpa.repository.Query;

				interface OrderRepository {
					@Query(value = "SELECT o FROM Order o WHERE o.status = :status", countQuery = "SELECT COUNT(o)")
					List<Order> findByStatus(String status);
				}
				""", result);
	}

}
