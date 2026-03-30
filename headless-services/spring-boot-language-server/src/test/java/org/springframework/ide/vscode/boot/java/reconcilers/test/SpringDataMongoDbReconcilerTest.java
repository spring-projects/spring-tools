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
 * Tests for {@link org.springframework.ide.vscode.boot.java.reconcilers.SpringDataMongoDbReconciler}.
 * <p>
 * Covers MongoDB Criteria.where, Update methods, fluent API, template find,
 * aggregation, and quick fixes.
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class SpringDataMongoDbReconcilerTest {

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

	// ========== Criteria.where — Fluent API (Tier 1, Pattern 2) ==========

	@Test
	void fluentQueryApi_criteriaWhere() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import static org.springframework.data.mongodb.core.query.Criteria.where;
				import static org.springframework.data.mongodb.core.query.Query.query;
				import org.springframework.data.mongodb.core.MongoOperations;

				class TestClass {
					private MongoOperations mongoOps;

					void test() {
						mongoOps.query(Customer.class)
							.matching(query(where("firstName")))
							.all();
					}
				}
				""", docUri);

		editor.assertProblems(
				"\"firstName\"|Non type-safe property reference for domain type 'Customer'"
		);
	}

	@Test
	void fluentUpdateApi_criteriaWhere() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import static org.springframework.data.mongodb.core.query.Criteria.where;
				import static org.springframework.data.mongodb.core.query.Query.query;
				import org.springframework.data.mongodb.core.MongoOperations;
				import org.springframework.data.mongodb.core.query.Update;

				class TestClass {
					private MongoOperations mongoOps;

					void test() {
						mongoOps.update(Customer.class)
							.matching(query(where("lastName")))
							.apply(new Update())
							.first();
					}
				}
				""", docUri);

		editor.assertProblems(
				"\"lastName\"|Non type-safe property reference for domain type 'Customer'"
		);
	}

	@Test
	void fluentQueryApi_withAsProjection() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import static org.springframework.data.mongodb.core.query.Criteria.where;
				import static org.springframework.data.mongodb.core.query.Query.query;
				import org.springframework.data.mongodb.core.MongoOperations;

				class TestClass {
					private MongoOperations mongoOps;

					void test() {
						mongoOps.query(Customer.class)
							.as(Order.class)
							.matching(query(where("orderDate")))
							.all();
					}
				}
				""", docUri);

		editor.assertProblems(
				"\"orderDate\"|Non type-safe property reference for domain type 'Order'"
		);
	}

	// ========== Template find/findAll with Class parameter (Pattern 3) ==========

	@Test
	void templateFind_withClassParam() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.mongodb.core.MongoTemplate;
				import org.springframework.data.mongodb.core.query.Criteria;
				import org.springframework.data.mongodb.core.query.Query;

				class TestClass {
					private MongoTemplate template;

					void test() {
						template.find(Query.query(Criteria.where("lastName")), Customer.class);
					}
				}
				""", docUri);

		editor.assertProblems(
				"\"lastName\"|Non type-safe property reference for domain type 'Customer'"
		);
	}

	// ========== Aggregation — newAggregation(X.class, ...) (Pattern 4) ==========

	@Test
	void aggregation_withClassParam() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
				import static org.springframework.data.mongodb.core.query.Criteria.where;

				class TestClass {
					void test() {
						newAggregation(Customer.class,
							match(where("firstName")));
					}
				}
				""", docUri);

		editor.assertProblems(
				"\"firstName\"|Non type-safe property reference for domain type 'Customer'"
		);
	}

	// ========== Field.include / Field.exclude (projections) ==========

	@Test
	void fieldInclude_singleProperty() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.mongodb.core.MongoTemplate;
				import org.springframework.data.mongodb.core.query.Query;

				class TestClass {
					private MongoTemplate template;

					void test() {
						Query q = new Query();
						q.fields().include("firstName");
						template.find(q, Customer.class);
					}
				}
				""", docUri);

		editor.assertProblems(
				"\"firstName\"|Non type-safe property reference for domain type 'Customer'"
		);
	}

	@Test
	void fieldExclude_singleProperty() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.mongodb.core.MongoTemplate;
				import org.springframework.data.mongodb.core.query.Query;

				class TestClass {
					private MongoTemplate template;

					void test() {
						Query q = new Query();
						q.fields().exclude("lastName");
						template.find(q, Customer.class);
					}
				}
				""", docUri);

		editor.assertProblems(
				"\"lastName\"|Non type-safe property reference for domain type 'Customer'"
		);
	}

	@Test
	void fieldInclude_multipleProperties() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.mongodb.core.MongoTemplate;
				import org.springframework.data.mongodb.core.query.Query;

				class TestClass {
					private MongoTemplate template;

					void test() {
						Query q = new Query();
						q.fields().include("firstName", "lastName");
						template.find(q, Customer.class);
					}
				}
				""", docUri);

		// Varargs: single warning spanning both string literals
		editor.assertProblems(
				"\"firstName\", \"lastName\"|Non type-safe property reference for domain type 'Customer'"
		);
	}

	// ========== Quick fix tests ==========

	@Test
	void quickfix_exactMatch_dottedChain() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import static org.springframework.data.mongodb.core.query.Criteria.where;
				import static org.springframework.data.mongodb.core.query.Query.query;
				import org.springframework.data.mongodb.core.MongoOperations;

				class TestClass {
					private MongoOperations mongoOps;

					void test() {
						mongoOps.query(Customer.class)
							.matching(query(where("address.country")))
							.all();
					}
				}
				""", docUri);

		Diagnostic problem = editor.assertProblem("\"address.country\"");
		List<CodeAction> actions = editor.getCodeActions(problem);
		assertEquals(1, actions.size());
		assertEquals(
				"Replace with PropertyPath.of(Customer::getAddress).then(Address::getCountry)",
				actions.get(0).getLabel());

		actions.get(0).perform();
		assertEquals("""
				package demo;

				import org.springframework.data.core.PropertyPath;
				import static org.springframework.data.mongodb.core.query.Criteria.where;
				import static org.springframework.data.mongodb.core.query.Query.query;
				import org.springframework.data.mongodb.core.MongoOperations;

				class TestClass {
					private MongoOperations mongoOps;

					void test() {
						mongoOps.query(Customer.class)
							.matching(query(where(PropertyPath.of(Customer::getAddress).then(Address::getCountry))))
							.all();
					}
				}
				""", editor.getRawText());
	}

	@Test
	void quickfix_fuzzy_dottedChain() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import static org.springframework.data.mongodb.core.query.Criteria.where;
				import static org.springframework.data.mongodb.core.query.Query.query;
				import org.springframework.data.mongodb.core.MongoOperations;

				class TestClass {
					private MongoOperations mongoOps;

					void test() {
						mongoOps.query(Customer.class)
							.matching(query(where("adress.country")))
							.all();
					}
				}
				""", docUri);

		Diagnostic problem = editor.assertProblem("\"adress.country\"");
		List<CodeAction> actions = editor.getCodeActions(problem);
		assertFalse(actions.isEmpty(), "Should have fuzzy match quick fixes for dotted chain");

		CodeAction chainFix = actions.stream()
				.filter(a -> a.getLabel().contains("PropertyPath.of(Customer::getAddress).then(Address::getCountry)"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Should suggest address.country as fuzzy match"));

		chainFix.perform();
		assertEquals("""
				package demo;

				import org.springframework.data.core.PropertyPath;
				import static org.springframework.data.mongodb.core.query.Criteria.where;
				import static org.springframework.data.mongodb.core.query.Query.query;
				import org.springframework.data.mongodb.core.MongoOperations;

				class TestClass {
					private MongoOperations mongoOps;

					void test() {
						mongoOps.query(Customer.class)
							.matching(query(where(PropertyPath.of(Customer::getAddress).then(Address::getCountry))))
							.all();
					}
				}
				""", editor.getRawText());
	}

	@Test
	void quickfix_exactDomainType_differentProperties() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import static org.springframework.data.mongodb.core.query.Criteria.where;
				import static org.springframework.data.mongodb.core.query.Query.query;
				import org.springframework.data.mongodb.core.MongoOperations;

				class TestClass {
					private MongoOperations mongoOps;

					void test() {
						mongoOps.query(Customer.class).matching(query(where("firstName"))).all();
						mongoOps.query(Customer.class).matching(query(where("lastName"))).all();
					}
				}
				""", docUri);

		List<Diagnostic> problems = editor.assertProblems(
				"\"firstName\"|Non type-safe property reference for domain type 'Customer'",
				"\"lastName\"|Non type-safe property reference for domain type 'Customer'"
		);

		List<CodeAction> actions0 = editor.getCodeActions(problems.get(0));
		assertEquals(2, actions0.size());
		assertEquals("Replace with Customer::getFirstName", actions0.get(0).getLabel());
		assertEquals("Replace all with type-safe property references in file", actions0.get(1).getLabel());

		List<CodeAction> actions1 = editor.getCodeActions(problems.get(1));
		assertEquals(2, actions1.size());
		assertEquals("Replace with Customer::getLastName", actions1.get(0).getLabel());
		assertEquals("Replace all with type-safe property references in file", actions1.get(1).getLabel());
	}

	private String docUri(String fileName) {
		Path javaFile = Paths.get(testProject.getLocationUri()).resolve("src/main/java/demo/" + fileName);
		return javaFile.toUri().toASCIIString();
	}

}
