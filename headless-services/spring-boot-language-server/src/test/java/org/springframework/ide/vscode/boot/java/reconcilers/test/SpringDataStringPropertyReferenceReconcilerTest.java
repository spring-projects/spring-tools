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

import java.io.File;
import java.nio.file.Path;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
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

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
		harness.changeConfiguration("{\"boot-java\": {\"validation\": {\"java\": { \"reconcilers\": true}}}}");

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-data-typesafe/").toURI());

		String projectDir = directory.toURI().toString();
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();
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

	// ========== Domain type cannot be determined ==========

	@Test
	void noDomainTypeContext_genericMessage() throws Exception {
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

	private String docUri(String fileName) {
		Path javaFile = directory.toPath().resolve("src/main/java/demo/" + fileName);
		return javaFile.toUri().toASCIIString();
	}

}
