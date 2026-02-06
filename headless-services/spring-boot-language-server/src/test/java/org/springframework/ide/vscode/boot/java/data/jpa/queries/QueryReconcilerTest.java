/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data.jpa.queries;

import java.io.File;

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
public class QueryReconcilerTest {

	@Autowired
	private BootLanguageServerHarness harness;
	@Autowired
	private JavaProjectFinder projectFinder;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/boot-mysql/").toURI());

		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();
		
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

	@Test
	void noErrorsInPropsFile() throws Exception {
		String source = """
				query1=SELECT ptype FROM PetType ptype ORDER BY ptype.name
				""";
		String docUri = directory.toPath().resolve("src/main/resources/jpa-named-queries.properties").toUri()
				.toString();
		Editor editor = harness.newEditor(LanguageId.JPA_QUERY_PROPERTIES, source, docUri);
		editor.assertProblems();
	}
	
	@Test
	void errorsInPropsFile() throws Exception {
		String source = """
				query1=SELECTX ptype FROM PetType ptype ORDER BY ptype.name
				""";
		String docUri = directory.toPath().resolve("src/main/resources/jpa-named-queries.properties").toUri()
				.toString();
		Editor editor = harness.newEditor(LanguageId.JPA_QUERY_PROPERTIES, source, docUri);
		editor.assertProblems("SELECTX|HQL: mismatched input 'SELECTX'");
	}
	
	@Test
	void noErrorsForHqlInPropsFile() throws Exception {
		String source = """
				query1=SELECT DISTINCT owner FROM Owner owner left join  owner.pets WHERE owner.lastName LIKE :lastName%
				""";
		String docUri = directory.toPath().resolve("src/main/resources/jpa-named-queries.properties").toUri()
				.toString();
		Editor editor = harness.newEditor(LanguageId.JPA_QUERY_PROPERTIES, source, docUri);
		editor.assertProblems();
	}

	@Test
	void errorsForJpqlInPropsFile() throws Exception {
		directory = new File(ProjectsHarness.class.getResource("/test-projects/super-property-nav-sample/").toURI());
		String projectDir = directory.toURI().toString();
		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		
		String source = """
				query1=SELECT DISTINCT owner FROM Owner owner left join  owner.pets WHERE owner.lastName LIKE :lastName%
				""";
		String docUri = directory.toPath().resolve("src/main/resources/jpa-named-queries.properties").toUri()
				.toString();
		Editor editor = harness.newEditor(LanguageId.JPA_QUERY_PROPERTIES, source, docUri);
		editor.assertProblems("WHERE|JPQL: no viable alternative");
	}
	
	@Test
	void noErrors() throws Exception {
		String source = """
				package example.demo;

				import org.springframework.data.jpa.repository.Query;
				import org.springframework.data.repository.Repository;

				public interface OwnerRepository extends Repository<Object, Integer> {

					@Query("SELECT ptype FROM PetType ptype ORDER BY ptype.name")
					List<Object> findPetTypes();

				}
				""";
		String docUri = directory.toPath().resolve("src/main/java/example/demo/OwnerRepository.java").toUri()
				.toString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, docUri);
		editor.assertProblems();
	}

	@Test
	void errorReported() throws Exception {
		String source = """
				package example.demo;

				import org.springframework.data.jpa.repository.Query;
				import org.springframework.data.repository.Repository;

				public interface OwnerRepository extends Repository<Object, Integer> {

					@Query("SELECTX ptype FROM PetType ptype ORDER BY ptype.name")
					List<Object> findPetTypes();

				}
				""";
		String docUri = directory.toPath().resolve("src/main/java/example/demo/OwnerRepository.java").toUri()
				.toString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, docUri);
		editor.assertProblems("SELECTX|HQL: mismatched input 'SELECTX'");
	}

	@Test
	void textBlock() throws Exception {
		String source = """
				package example.demo;

				import org.springframework.data.jpa.repository.Query;
				import org.springframework.data.repository.Repository;

				public interface OwnerRepository extends Repository<Object, Integer> {

					@Query(\"""
					SELECTX ptype FROM PetType ptype ORDER BY ptype.name
					\""")
					List<Object> findPetTypes();

				}
				""";
		String docUri = directory.toPath().resolve("src/main/java/example/demo/OwnerRepository.java").toUri()
				.toString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, docUri);
		editor.assertProblems("SELECTX|HQL: mismatched input 'SELECTX'");
	}
	
	@Test
	void normalAnnotation() throws Exception {
		String source = """
				package example.demo;

				import org.springframework.data.jpa.repository.Query;
				import org.springframework.data.repository.Repository;

				public interface OwnerRepository extends Repository<Object, Integer> {

					@Query(value = "SELECTX ptype FROM PetType ptype ORDER BY ptype.name", nativeQuery = false)
					List<Object> findPetTypes();

				}
				""";
		String docUri = directory.toPath().resolve("src/main/java/example/demo/OwnerRepository.java").toUri()
				.toString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, docUri);
		editor.assertProblems("SELECTX|HQL: mismatched input 'SELECTX'");
	}

	@Test
	void nativeMySqlAnnotation() throws Exception {
		String source = """
				package example.demo;

				import org.springframework.data.jpa.repository.Query;
				import org.springframework.data.repository.Repository;

				public interface OwnerRepository extends Repository<Object, Integer> {

					@Query(value = "SELECTX ptype FROM PetType ptype ORDER BY ptype.name", nativeQuery = true)
					List<Object> findPetTypes();

				}
				""";
		String docUri = directory.toPath().resolve("src/main/java/example/demo/OwnerRepository.java").toUri()
				.toString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, docUri);
		editor.assertProblems("SELECTX|MySQL: mismatched input 'SELECTX' expecting {'ALTER',");
	}
	
	@Test
	void nativePostgreSql() throws Exception {
		directory = new File(ProjectsHarness.class.getResource("/test-projects/boot-postgresql/").toURI());
		String projectDir = directory.toURI().toString();
		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		String source = """
				package example.demo;

				import org.springframework.data.jpa.repository.Query;
				import org.springframework.data.repository.Repository;

				public interface OwnerRepository extends Repository<Object, Integer> {

					@Query(value = "SELECTX ptype FROM PetType", nativeQuery = true)
					List<Object> findPetTypes();

				}
				""";
		String docUri = directory.toPath().resolve("src/main/java/example/demo/OwnerRepository.java").toUri()
				.toString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, docUri);
		editor.assertProblems("SELECTX|PostgreSQL: mismatched input 'SELECTX'");
	}
	
	@Test
	void noErrorForHql() throws Exception {
		String source = """
				package example.demo;

				import org.springframework.data.domain.Page;
				import org.springframework.data.domain.Pageable;
				import org.springframework.data.jpa.repository.Query;
				import org.springframework.data.repository.Repository;
				import org.springframework.data.repository.query.Param;

				public interface OwnerRepository extends Repository<Object, Integer> {

					@Query("SELECT DISTINCT owner FROM Owner owner left join  owner.pets WHERE owner.lastName LIKE :lastName% ")
					Page<Object> findByLastName(@Param("lastName") String lastName, Pageable pageable);

				}
				""";
		String docUri = directory.toPath().resolve("src/main/java/example/demo/OwnerRepository.java").toUri()
				.toString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, docUri);
		editor.assertProblems();
	}
	
	@Test
	void namedQueryAnnotation() throws Exception {
		String source = """
				package my.package
				
				import jakarta.persistence.NamedQuery;
		
				@NamedQuery(name = " my_query", query = "SELECTX ptype FROM PetType ptype ORDER BY ptype.name")
				public interface OwnerRepository {
				}
				""";
		String docUri = directory.toPath().resolve("src/main/java/example/demo/OwnerRepository.java").toUri()
				.toString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, docUri);
		editor.assertProblems("SELECTX|HQL: mismatched input 'SELECTX'");
	}

}
