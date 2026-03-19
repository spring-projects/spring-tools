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
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.CodeAction;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class SpringDataStringPropertyReferenceReconcilerTest {

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

	// ========== Scenario 1: Repository method call ==========

	@Test
	void scenario1_sortBy_singleProperty_repositoryCall() throws Exception {
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
	void scenario1_sortBy_multipleProperties_repositoryCall() throws Exception {
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

		editor.assertProblems(
				"\"firstName\"|Non type-safe property reference for domain type 'Customer'",
				"\"lastName\"|Non type-safe property reference for domain type 'Customer'"
		);
	}

	@Test
	void scenario1_sortOrderDesc_repositoryCall() throws Exception {
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
	void scenario1_sortUnsorted_noProblems() throws Exception {
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

	// ========== Scenario 2: Fluent Template API — query(X.class).matching(where(...)) ==========

	@Test
	void scenario2_fluentQueryApi_criteriaWhere() throws Exception {
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
	void scenario2_fluentUpdateApi_criteriaWhere() throws Exception {
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
	void scenario2_fluentQueryApi_withAsProjection() throws Exception {
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

	// ========== Scenario 3: Template find/findOne/findAll with Class parameter ==========

	@Test
	void scenario3_templateFind_withClassParam() throws Exception {
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

	// ========== Scenario 4: Aggregation — newAggregation(X.class, ...) ==========

	@Test
	void scenario4_aggregation_withClassParam() throws Exception {
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

	// ========== Scenario 5: Template update(entity, options) ==========

	@Test
	void scenario5_templateUpdateWithEntity() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.cassandra.core.CassandraOperations;
				import org.springframework.data.cassandra.core.UpdateOptions;
				import org.springframework.data.cassandra.core.query.Criteria;

				class TestClass {
					private CassandraOperations operations;

					void test() {
						Customer person = new Customer();
						operations.update(person,
							UpdateOptions.builder()
								.ifCondition(Criteria.where("firstName"))
								.build());
					}
				}
				""", docUri);

		editor.assertProblems(
				"\"firstName\"|Non type-safe property reference for domain type 'Customer'"
		);
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
	void contextual_propertyMatchesNoCandidates_genericMessage() throws Exception {
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
	void contextual_propertyNotOnDomainType_stillShowsDomainType() throws Exception {
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

		editor.assertProblems(
				"\"somethingUnknown\"|Non type-safe property reference for domain type 'Customer'"
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
	void quickfix_fuzzy_perSegmentInChain() throws Exception {
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
		assertEquals(1, actions0.size());
		assertEquals("Replace with Customer::getFirstName", actions0.get(0).getLabel());

		List<CodeAction> actions1 = editor.getCodeActions(problems.get(1));
		assertEquals(1, actions1.size());
		assertEquals("Replace with Customer::getLastName", actions1.get(0).getLabel());
	}

	private String docUri(String fileName) {
		Path javaFile = Paths.get(testProject.getLocationUri()).resolve("src/main/java/demo/" + fileName);
		return javaFile.toUri().toASCIIString();
	}

}
