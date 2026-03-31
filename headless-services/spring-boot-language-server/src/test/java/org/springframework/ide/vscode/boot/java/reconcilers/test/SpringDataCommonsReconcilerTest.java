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
package org.springframework.ide.vscode.boot.java.reconcilers.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.CodeAction;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Tests for {@link org.springframework.ide.vscode.boot.java.reconcilers.SpringDataCommonsReconciler}.
 * <p>
 * Covers Sort.by and Sort.Order string property references with domain type
 * resolution from repository calls (Tier 1) and contextual block scanning (Tier 2).
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class SpringDataCommonsReconcilerTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;

	private IJavaProject testProject;

	@BeforeEach
	public void setup() throws Exception {
		testProject = ProjectsHarness.INSTANCE.mavenProject("test-spring-data-typesafe");
		harness.useProject(testProject);
		harness.intialize(null);
		harness.changeConfiguration("{\"boot-java\": {\"validation\": {\"java\": { \"reconcilers\": true}}}}");

		projectFinder.find(new TextDocumentIdentifier(testProject.getLocationUri().toASCIIString())).get();
	}

	// ========== Sort.by — direct repository call (Tier 1) ==========

	@Test
	void sortBy_singleProperty_repositoryCall() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.domain.Sort;

				class TestClass {
					private CustomerRepository repository;

					void test() {
						repository.findAll(Sort.by("firstName"));
					}
				}
				""", docUri);

		editor.assertProblems(
				"\"firstName\"|Non type-safe property reference for domain type 'Customer'"
		);
	}

	@Test
	void sortBy_multipleProperties_repositoryCall() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.domain.Sort;

				class TestClass {
					private CustomerRepository repository;

					void test() {
						repository.findAll(Sort.by("firstName", "lastName"));
					}
				}
				""", docUri);

		// Varargs: single warning spanning both string literals
		editor.assertProblems(
				"\"firstName\", \"lastName\"|Non type-safe property reference for domain type 'Customer'"
		);
	}

	@Test
	void sortOrderDesc_repositoryCall() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.domain.Sort;

				class TestClass {
					private CustomerRepository repository;

					void test() {
						repository.findAll(Sort.by(Sort.Order.desc("lastName")));
					}
				}
				""", docUri);

		editor.assertProblems(
				"\"lastName\"|Non type-safe property reference for domain type 'Customer'"
		);
	}

	@Test
	void sortUnsorted_noProblems() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.domain.Sort;

				class TestClass {
					private CustomerRepository repository;

					void test() {
						repository.findAll(Sort.unsorted());
					}
				}
				""", docUri);

		editor.assertProblems();
	}

	// ========== Tier 2: Contextual resolution from enclosing block ==========

	@Test
	void contextual_sortVariable_repositoryInSameBlock() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.domain.Sort;

				class TestClass {
					private CustomerRepository repository;

					void test() {
						Sort sort = Sort.by("firstName");
						repository.findAll(sort);
					}
				}
				""", docUri);

		editor.assertProblems(
				"\"firstName\"|Non type-safe property reference for domain type 'Customer'"
		);
	}

	@Test
	void contextual_noCandidates_genericMessage() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.domain.Sort;

				class TestClass {
					void test() {
						Sort sort = Sort.by("firstName");
					}
				}
				""", docUri);

		editor.assertProblems(
				"\"firstName\"|Non type-safe property reference"
		);
	}

	@Test
	void contextual_propertyNotOnDomainType_genericMessage() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.domain.Sort;

				class TestClass {
					private CustomerRepository repository;

					void test() {
						Sort sort = Sort.by("somethingUnknown");
						repository.findAll(sort);
					}
				}
				""", docUri);

		// No quick fix possible for "somethingUnknown" so domain type is omitted from the message
		editor.assertProblems(
				"\"somethingUnknown\"|Non type-safe property reference"
		);
	}

	// ========== No string property reference — no problems ==========

	@Test
	void noStringPropertyReference_noProblems() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				class TestClass {
					void test() {
						String s = "hello";
					}
				}
				""", docUri);

		editor.assertProblems();
	}

	// ========== Quick fix tests ==========

	@Test
	void quickfix_exactMatch_singleProperty() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.domain.Sort;

				class TestClass {
					private CustomerRepository repository;

					void test() {
						repository.findAll(Sort.by("firstName"));
					}
				}
				""", docUri);

		Diagnostic problem = editor.assertProblem("\"firstName\"");
		List<CodeAction> actions = editor.getCodeActions(problem);
		assertEquals(1, actions.size());
		assertEquals("Replace with Customer::getFirstName", actions.get(0).getLabel());

		actions.get(0).perform();
		assertEquals("""
				package demo;

				import org.springframework.data.domain.Sort;

				class TestClass {
					private CustomerRepository repository;

					void test() {
						repository.findAll(Sort.by(Customer::getFirstName));
					}
				}
				""", editor.getRawText());
	}

	@Test
	void quickfix_fuzzy_singleSegment() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.domain.Sort;

				class TestClass {
					private CustomerRepository repository;

					void test() {
						repository.findAll(Sort.by("firstNam"));
					}
				}
				""", docUri);

		Diagnostic problem = editor.assertProblem("\"firstNam\"");
		List<CodeAction> actions = editor.getCodeActions(problem);
		assertFalse(actions.isEmpty(), "Should have fuzzy match quick fixes");

		CodeAction firstNameFix = actions.stream()
				.filter(a -> a.getLabel().contains("Customer::getFirstName"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Should suggest firstName as fuzzy match"));

		firstNameFix.perform();
		assertEquals("""
				package demo;

				import org.springframework.data.domain.Sort;

				class TestClass {
					private CustomerRepository repository;

					void test() {
						repository.findAll(Sort.by(Customer::getFirstName));
					}
				}
				""", editor.getRawText());
	}

	@Test
	void quickfix_multipleProperties_singleFix() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.domain.Sort;

				class TestClass {
					private CustomerRepository repository;

					void test() {
						repository.findAll(Sort.by("firstName", "lastName"));
					}
				}
				""", docUri);

		Diagnostic problem = editor.assertProblem("\"firstName\", \"lastName\"");
		List<CodeAction> actions = editor.getCodeActions(problem);
		assertEquals(1, actions.size());
		assertEquals("Replace with type-safe property references", actions.get(0).getLabel());

		actions.get(0).perform();
		assertEquals("""
				package demo;

				import org.springframework.data.domain.Sort;

				class TestClass {
					private CustomerRepository repository;

					void test() {
						repository.findAll(Sort.by(Customer::getFirstName, Customer::getLastName));
					}
				}
				""", editor.getRawText());
	}

	@Test
	void quickfix_noDomainType_noQuickFixes() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.domain.Sort;

				class TestClass {
					void test() {
						Sort sort = Sort.by("something");
					}
				}
				""", docUri);

		Diagnostic problem = editor.assertProblem("\"something\"");
		List<CodeAction> actions = editor.getCodeActions(problem);
		assertTrue(actions.isEmpty(), "No quick fixes when no domain type is known");
	}

	// ========== Fix all in file ==========

	@Test
	void fixAll_multipleExactMatches_singleFixAllQuickFix() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.domain.Sort;

				class TestClass {
					private CustomerRepository repository;

					void test() {
						repository.findAll(Sort.by("firstName"));
						repository.findAll(Sort.by(Sort.Order.desc("lastName")));
					}
				}
				""", docUri);

		List<Diagnostic> problems = editor.assertProblems(
				"\"firstName\"|Non type-safe property reference for domain type 'Customer'",
				"\"lastName\"|Non type-safe property reference for domain type 'Customer'"
		);

		// Each problem should have 2 quick fixes: individual + fix all
		List<CodeAction> actions0 = editor.getCodeActions(problems.get(0));
		assertEquals(2, actions0.size());
		assertEquals("Replace with Customer::getFirstName", actions0.get(0).getLabel());
		assertEquals("Replace all with type-safe property references in file", actions0.get(1).getLabel());

		List<CodeAction> actions1 = editor.getCodeActions(problems.get(1));
		assertEquals(2, actions1.size());
		assertEquals("Replace with Customer::getLastName", actions1.get(0).getLabel());
		assertEquals("Replace all with type-safe property references in file", actions1.get(1).getLabel());

		// Apply "fix all" from the first problem — replaces both
		actions0.get(1).perform();
		assertEquals("""
				package demo;

				import org.springframework.data.domain.Sort;

				class TestClass {
					private CustomerRepository repository;

					void test() {
						repository.findAll(Sort.by(Customer::getFirstName));
						repository.findAll(Sort.by(Sort.Order.desc(Customer::getLastName)));
					}
				}
				""", editor.getRawText());
	}

	@Test
	void fixAll_mixedExactAndNonMatching_onlyOnFixableProblems() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.domain.Sort;

				class TestClass {
					private CustomerRepository repository;

					void test() {
						repository.findAll(Sort.by("firstName"));
						Sort sort = Sort.by("unknownProperty");
						repository.findAll(sort);
					}
				}
				""", docUri);

		List<Diagnostic> problems = editor.assertProblems(
				"\"firstName\"|Non type-safe property reference for domain type 'Customer'",
				"\"unknownProperty\"|Non type-safe property reference"
		);

		// First problem (exact match): only individual fix, no "fix all"
		// because there is only 1 exact-match descriptor in the file
		List<CodeAction> actions0 = editor.getCodeActions(problems.get(0));
		assertEquals(1, actions0.size());
		assertEquals("Replace with Customer::getFirstName", actions0.get(0).getLabel());

		// Second problem (no match): no quick fixes at all
		List<CodeAction> actions1 = editor.getCodeActions(problems.get(1));
		assertTrue(actions1.isEmpty(), "No quick fixes for non-matching property");
	}

	private String docUri(String fileName) {
		Path javaFile = Paths.get(testProject.getLocationUri()).resolve("src/main/java/demo/" + fileName);
		return javaFile.toUri().toASCIIString();
	}

}
