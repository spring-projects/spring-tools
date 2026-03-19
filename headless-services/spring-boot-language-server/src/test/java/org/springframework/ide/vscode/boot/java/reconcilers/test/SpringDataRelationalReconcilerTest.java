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
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.CodeAction;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Tests for {@link org.springframework.ide.vscode.boot.java.reconcilers.SpringDataRelationalReconciler}.
 * <p>
 * Covers relational Criteria.where and Update.update/set string property references.
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class SpringDataRelationalReconcilerTest {

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
	void criteriaWhere_repositoryContext() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.relational.core.query.Criteria;

				class TestClass {
					private CustomerRepository repository;

					void test() {
						Criteria c = Criteria.where("firstName");
						repository.findAll(null);
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

				import org.springframework.data.relational.core.query.Criteria;

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

	// ========== Update.update / Update.set ==========

	@Test
	void updateUpdate_repositoryContext() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.relational.core.query.Update;

				class TestClass {
					private CustomerRepository repository;

					void test() {
						Update u = Update.update("firstName", "John");
						repository.findAll(null);
					}
				}
				""", docUri);

		editor.assertProblems(
				"\"firstName\"|Non type-safe property reference for domain type 'Customer'"
		);
	}

	@Test
	void updateSet_repositoryContext() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.relational.core.query.Update;

				class TestClass {
					private CustomerRepository repository;

					void test() {
						Update u = Update.update("firstName", "John").set("lastName", "Doe");
						repository.findAll(null);
					}
				}
				""", docUri);

		editor.assertProblems(
				"\"firstName\"|Non type-safe property reference for domain type 'Customer'",
				"\"lastName\"|Non type-safe property reference for domain type 'Customer'"
		);
	}

	// ========== Quick fix ==========

	@Test
	void quickfix_criteriaWhere_exactMatch() throws Exception {
		String docUri = docUri("TestClass.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package demo;

				import org.springframework.data.relational.core.query.Criteria;

				class TestClass {
					private CustomerRepository repository;

					void test() {
						Criteria c = Criteria.where("firstName");
						repository.findAll(null);
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

				import org.springframework.data.relational.core.query.Criteria;

				class TestClass {
					private CustomerRepository repository;

					void test() {
						Criteria c = Criteria.where(Customer::getFirstName);
						repository.findAll(null);
					}
				}
				""", editor.getRawText());
	}

	private String docUri(String fileName) {
		Path javaFile = Paths.get(testProject.getLocationUri()).resolve("src/main/java/demo/" + fileName);
		return javaFile.toUri().toASCIIString();
	}

}
