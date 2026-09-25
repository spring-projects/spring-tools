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
package org.springframework.ide.vscode.boot.java.data.jpa.queries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;

import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.CodeAction;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class SqlDialectQuickFixProviderTest {

	@Autowired
	private BootLanguageServerHarness harness;
	@Autowired
	private JavaProjectFinder projectFinder;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
		harness.changeConfiguration("""
				{
				"spring-boot": {
					"ls": {
						"problem": {
							"data-query": {
								"SQL_SYNTAX": "ERROR"
							}
						}
					}
				}
			}
			""");
	}

	private Diagnostic findProblem(Editor editor, QueryProblemType type) throws Exception {
		return editor.reconcile().stream()
				.filter(d -> d.getCode() != null && d.getCode().isLeft() && type.getCode().equals(d.getCode().getLeft()))
				.findFirst()
				.orElse(null);
	}

	private static List<CodeAction> dialectActionsOnly(List<CodeAction> actions) {
		return actions.stream()
				.filter(ca -> ca.getLabel() != null && ca.getLabel().startsWith("Set SQL dialect to "))
				.toList();
	}

	@Test
	void syntaxErrorAloneOffersNoDialectSwitchFixWhenUnambiguous() throws Exception {
		// boot-mysql: unambiguous, resolves to MySQL with no override. A syntax error here is
		// presumably a real mistake in the query, not a dialect problem, so nothing is offered.
		directory = new File(ProjectsHarness.class.getResource("/test-projects/boot-mysql/").toURI());
		String projectDir = directory.toURI().toString();
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		String source = """
				package example.demo;

				import org.springframework.data.jdbc.repository.query.Query;
				import org.springframework.data.repository.CrudRepository;

				public interface OwnerRepository extends CrudRepository<Object, Integer> {

					@Query("SELECTX * FROM owner WHERE last_name = :lastName")
					List<Object> findByLastName(String lastName);

				}
				""";
		String docUri = directory.toPath().resolve("src/main/java/example/demo/OwnerRepository.java").toUri()
				.toString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, docUri);

		Diagnostic syntaxError = findProblem(editor, QueryProblemType.SQL_SYNTAX);
		assertTrue(syntaxError != null, "Expected a SQL_SYNTAX diagnostic");

		List<CodeAction> dialectActions = dialectActionsOnly(editor.getCodeActions(syntaxError));
		assertEquals(0, dialectActions.size());
	}

	@Test
	void syntaxErrorOffersEveryOtherApplicableDialectWhenClasspathAmbiguousAndAutoSelected() throws Exception {
		// boot-mariadb-postgresql: both MySQL/MariaDB and PostgreSQL drivers present, no
		// override (so "auto" is the current selection), so a real syntax error (misdetected
		// against the wrong of the two candidate grammars) should let the user pick between
		// the dialects actually found - "auto" itself is excluded since it's already selected.
		directory = new File(ProjectsHarness.class.getResource("/test-projects/boot-mariadb-postgresql/").toURI());
		String projectDir = directory.toURI().toString();
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		String source = """
				package example.demo;

				import org.springframework.data.jdbc.repository.query.Query;
				import org.springframework.data.repository.CrudRepository;

				public interface MachineRepository extends CrudRepository<Object, Integer> {

					@Query("SELECTX * FROM machine")
					List<Object> findAll();

				}
				""";
		String docUri = directory.toPath().resolve("src/main/java/example/demo/MachineRepository.java").toUri()
				.toString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, docUri);

		Diagnostic syntaxError = findProblem(editor, QueryProblemType.SQL_SYNTAX);
		assertTrue(syntaxError != null, "Expected a SQL_SYNTAX diagnostic");

		List<CodeAction> dialectActions = dialectActionsOnly(editor.getCodeActions(syntaxError));
		assertEquals(2, dialectActions.size());
		assertEquals("Set SQL dialect to MySQL", dialectActions.get(0).getLabel());
		assertEquals("Set SQL dialect to PostgreSQL", dialectActions.get(1).getLabel());
	}

	@Test
	void syntaxErrorOffersRemainingApplicableDialectPlusAutoWhenClasspathAmbiguousAndOneIsSelected() throws Exception {
		// Same ambiguous classpath, but the user has explicitly forced MySQL - so the
		// candidates are the other applicable dialect (PostgreSQL) plus "auto", excluding
		// MySQL itself since it's already selected.
		directory = new File(ProjectsHarness.class.getResource("/test-projects/boot-mariadb-postgresql/").toURI());
		String projectDir = directory.toURI().toString();
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();
		harness.changeConfiguration("""
				{
				"spring-boot": {
					"ls": {
						"problem": {
							"data-query": {
								"SQL_SYNTAX": "ERROR"
							}
						},
						"problem-parameters": {
							"data-query": {
								"sql-dialect": "mysql"
							}
						}
					}
				}
			}
			""");

		String source = """
				package example.demo;

				import org.springframework.data.jdbc.repository.query.Query;
				import org.springframework.data.repository.CrudRepository;

				public interface MachineRepository extends CrudRepository<Object, Integer> {

					@Query("SELECTX * FROM machine")
					List<Object> findAll();

				}
				""";
		String docUri = directory.toPath().resolve("src/main/java/example/demo/MachineRepository.java").toUri()
				.toString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, docUri);

		Diagnostic syntaxError = findProblem(editor, QueryProblemType.SQL_SYNTAX);
		assertTrue(syntaxError != null, "Expected a SQL_SYNTAX diagnostic");

		List<CodeAction> dialectActions = dialectActionsOnly(editor.getCodeActions(syntaxError));
		assertEquals(2, dialectActions.size());
		assertEquals("Set SQL dialect to PostgreSQL", dialectActions.get(0).getLabel());
		assertEquals("Set SQL dialect to Auto", dialectActions.get(1).getLabel());
	}

	@Test
	void syntaxErrorOffersOnlyAutoWhenOverrideMismatchesUnambiguousClasspath() throws Exception {
		// boot-mariadb-h2 unambiguously resolves to MySQL, but the user has explicitly forced
		// PostgreSQL, so the override itself is used for reconciliation (a genuine syntax error
		// against PostgreSQL grammar is raised), and the only offered fix is reverting to auto.
		directory = new File(ProjectsHarness.class.getResource("/test-projects/boot-mariadb-h2/").toURI());
		String projectDir = directory.toURI().toString();
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();
		harness.changeConfiguration("""
				{
				"spring-boot": {
					"ls": {
						"problem": {
							"data-query": {
								"SQL_SYNTAX": "ERROR"
							}
						},
						"problem-parameters": {
							"data-query": {
								"sql-dialect": "postgresql"
							}
						}
					}
				}
			}
			""");

		String source = """
				package example.demo;

				import org.springframework.data.jdbc.repository.query.Query;
				import org.springframework.data.repository.CrudRepository;

				public interface OwnerRepository extends CrudRepository<Object, Integer> {

					@Query("SELECTX * FROM owner")
					List<Object> findAll();

				}
				""";
		String docUri = directory.toPath().resolve("src/main/java/example/demo/OwnerRepository.java").toUri()
				.toString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, docUri);

		Diagnostic syntaxError = findProblem(editor, QueryProblemType.SQL_SYNTAX);
		assertTrue(syntaxError != null, "Expected a SQL_SYNTAX diagnostic");

		List<CodeAction> dialectActions = dialectActionsOnly(editor.getCodeActions(syntaxError));
		assertEquals(1, dialectActions.size());
		assertEquals("Set SQL dialect to Auto", dialectActions.get(0).getLabel());

		Command command = dialectActions.get(0).getCommand();
		assertEquals(SqlDialectQuickFixProvider.SET_CONFIGURATION_COMMAND_ID, command.getCommand());
		assertEquals(List.of("spring-boot.ls.problem-parameters.data-query.sql-dialect", "auto"), command.getArguments());
	}

}
