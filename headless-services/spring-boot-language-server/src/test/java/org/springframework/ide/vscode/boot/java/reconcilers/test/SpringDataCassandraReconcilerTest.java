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
 * Tests for {@link org.springframework.ide.vscode.boot.java.reconcilers.SpringDataCassandraReconciler}.
 * <p>
 * Covers Cassandra Criteria.where, Update.set and template update-with-entity
 * string property references.
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class SpringDataCassandraReconcilerTest {

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

	// ========== Criteria.where ==========

	@Test
	void criteriaWhere_templateUpdateWithEntity() throws Exception {
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

	@Test
	void criteriaWhere_noDomainType() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.cassandra.core.query.Criteria;

				class TestClass {
					void test() {
						Criteria c = Criteria.where("firstName");
					}
				}
				""", docUri);

		editor.assertProblems(
				"\"firstName\"|Non type-safe property reference"
		);
	}

	// ========== Update.set ==========

	@Test
	void updateSet_templateContext() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.cassandra.core.CassandraOperations;
				import org.springframework.data.cassandra.core.query.Criteria;
				import org.springframework.data.cassandra.core.query.Query;
				import org.springframework.data.cassandra.core.query.Update;

				class TestClass {
					private CassandraOperations operations;

					void test() {
						operations.update(
							Query.query(Criteria.where("firstName")),
							Update.empty().set("lastName", "Doe"),
							Customer.class);
					}
				}
				""", docUri);

		editor.assertProblems(
				"\"firstName\"|Non type-safe property reference for domain type 'Customer'",
				"\"lastName\"|Non type-safe property reference for domain type 'Customer'"
		);
	}

	// ========== Columns.from ==========

	@Test
	void columnsFrom_multipleProperties() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.cassandra.core.CassandraOperations;
				import org.springframework.data.cassandra.core.query.Columns;
				import org.springframework.data.cassandra.core.query.Query;

				class TestClass {
					private CassandraOperations operations;

					void test() {
						operations.select(
							Query.empty().columns(Columns.from("firstName", "lastName")),
							Customer.class);
					}
				}
				""", docUri);

		// Varargs: single warning spanning both string literals
		editor.assertProblems(
				"\"firstName\", \"lastName\"|Non type-safe property reference for domain type 'Customer'"
		);
	}

	@Test
	void columnsFrom_noDomainType() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.cassandra.core.query.Columns;

				class TestClass {
					void test() {
						Columns c = Columns.from("firstName");
					}
				}
				""", docUri);

		editor.assertProblems(
				"\"firstName\"|Non type-safe property reference"
		);
	}

	// ========== Quick fix ==========

	@Test
	void quickfix_criteriaWhere_exactMatch() throws Exception {
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

		Diagnostic problem = editor.assertProblem("\"firstName\"");
		List<CodeAction> actions = editor.getCodeActions(problem);
		assertEquals(1, actions.size());
		assertEquals("Replace with Customer::getFirstName", actions.get(0).getLabel());

		actions.get(0).perform();
		assertEquals("""
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
								.ifCondition(Criteria.where(Customer::getFirstName))
								.build());
					}
				}
				""", editor.getRawText());
	}

	private String docUri(String fileName) {
		Path javaFile = Paths.get(testProject.getLocationUri()).resolve("src/main/java/demo/" + fileName);
		return javaFile.toUri().toASCIIString();
	}

}
