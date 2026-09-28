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
import java.util.Map;

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
				.filter(ca -> ca.getLabel() != null && ca.getLabel().startsWith("Set SQL dialect for package "))
				.toList();
	}

	@Test
	void syntaxErrorAloneOffersNoDialectSwitchFixWhenUnambiguous() throws Exception {
		// Unambiguous, no override: a syntax error here is presumably a real mistake, not offered a fix.
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
		// Ambiguous classpath, no override: offer both dialects actually found, minus auto itself.
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
		assertEquals("Set SQL dialect for package 'example.demo' to MySQL", dialectActions.get(0).getLabel());
		assertEquals("Set SQL dialect for package 'example.demo' to PostgreSQL", dialectActions.get(1).getLabel());
	}

	@Test
	void syntaxErrorOffersRemainingApplicableDialectPlusAutoWhenClasspathAmbiguousAndOneIsSelected() throws Exception {
		// Same ambiguous classpath, MySQL forced: offer PostgreSQL plus auto, excluding MySQL itself.
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
								"sql-dialect-overrides": {
									"example.demo": "mysql"
								}
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
		assertEquals("Set SQL dialect for package 'example.demo' to PostgreSQL", dialectActions.get(0).getLabel());
		assertEquals("Set SQL dialect for package 'example.demo' to Auto", dialectActions.get(1).getLabel());

		// Merging: must preserve other packages' overrides, not replace the whole map.
		Command command = dialectActions.get(0).getCommand();
		assertEquals(SqlDialectQuickFixProvider.SET_CONFIGURATION_COMMAND_ID, command.getCommand());
		assertEquals("spring-boot.ls.problem-parameters.data-query.sql-dialect-overrides", command.getArguments().get(0));
		assertEquals(Map.of("example.demo", "postgresql"), command.getArguments().get(1));
	}

	@Test
	void syntaxErrorOffersOnlyAutoWhenOverrideMismatchesUnambiguousClasspath() throws Exception {
		// Unambiguous MySQL classpath, PostgreSQL forced: only "auto" is offered as a fix.
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
								"sql-dialect-overrides": {
									"example.demo": "postgresql",
									"com.other.unrelated": "mysql"
								}
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
		assertEquals("Set SQL dialect for package 'example.demo' to Auto", dialectActions.get(0).getLabel());

		// "Auto" just removes the entry, but the unrelated package's override must survive.
		Command command = dialectActions.get(0).getCommand();
		assertEquals(SqlDialectQuickFixProvider.SET_CONFIGURATION_COMMAND_ID, command.getCommand());
		assertEquals("spring-boot.ls.problem-parameters.data-query.sql-dialect-overrides", command.getArguments().get(0));
		assertEquals(Map.of("com.other.unrelated", "mysql"), command.getArguments().get(1));
	}

	@Test
	void pickingDialectAlreadyInheritedFromParentPackageRemovesTheOwnRedundantEntry() throws Exception {
		// Parent "example" overrides to PostgreSQL, child "example.demo" to MySQL. Picking
		// PostgreSQL for the child should drop its entry rather than duplicate the parent's.
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
								"sql-dialect-overrides": {
									"example": "postgresql",
									"example.demo": "mysql"
								}
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
		CodeAction toPostgres = dialectActions.stream()
				.filter(ca -> ca.getLabel().equals("Set SQL dialect for package 'example.demo' to PostgreSQL"))
				.findFirst().orElseThrow();

		Command command = toPostgres.getCommand();
		assertEquals(SqlDialectQuickFixProvider.SET_CONFIGURATION_COMMAND_ID, command.getCommand());
		assertEquals("spring-boot.ls.problem-parameters.data-query.sql-dialect-overrides", command.getArguments().get(0));
		assertEquals(Map.of("example", "postgresql"), command.getArguments().get(1));
	}

	@Test
	void pickingAutoWhenAnAncestorPackageHasARealOverrideWritesAnExplicitAutoEntry() throws Exception {
		// Both "example" and "example.demo" override to PostgreSQL (mismatching unambiguous
		// MySQL classpath): deleting the child's entry alone wouldn't opt out of the parent's
		// override, so picking "auto" must write an explicit "auto" entry instead.
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
								"sql-dialect-overrides": {
									"example": "postgresql",
									"example.demo": "postgresql"
								}
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
		assertEquals("Set SQL dialect for package 'example.demo' to Auto", dialectActions.get(0).getLabel());

		Command command = dialectActions.get(0).getCommand();
		assertEquals(SqlDialectQuickFixProvider.SET_CONFIGURATION_COMMAND_ID, command.getCommand());
		assertEquals("spring-boot.ls.problem-parameters.data-query.sql-dialect-overrides", command.getArguments().get(0));
		assertEquals(Map.of("example", "postgresql", "example.demo", "auto"), command.getArguments().get(1));
	}

	@Test
	void pickingAutoWhenNoAncestorPackageHasAnOverrideRemovesTheEntryEntirely() throws Exception {
		// "example.demo" overrides to PostgreSQL and has no ancestor override at all - picking
		// "auto" should just delete the entry, not write an explicit (and pointless) "auto".
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
								"sql-dialect-overrides": {
									"example.demo": "postgresql"
								}
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
		assertEquals("Set SQL dialect for package 'example.demo' to Auto", dialectActions.get(0).getLabel());

		Command command = dialectActions.get(0).getCommand();
		assertEquals(SqlDialectQuickFixProvider.SET_CONFIGURATION_COMMAND_ID, command.getCommand());
		assertEquals("spring-boot.ls.problem-parameters.data-query.sql-dialect-overrides", command.getArguments().get(0));
		assertEquals(Map.of(), command.getArguments().get(1));
	}

}
