/*******************************************************************************
 * Copyright (c) 2024, 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data.jpa.queries;

import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.HoverTestConf;
import org.springframework.ide.vscode.commons.languageserver.util.Settings;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.languageserver.testharness.ExpectedSemanticToken;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(HoverTestConf.class)
public class JdtDataQuerySemanticTokensProviderTest {
	
	@Autowired BootLanguageServerHarness harness;

	private ProjectsHarness projects = ProjectsHarness.INSTANCE;
	
	private MavenJavaProject jp;
		
	@BeforeEach
	public void setup() throws Exception {
		jp =  projects.mavenProject("boot-mysql");
		harness.useProject(jp);

		String changedSettings = """
		{
			"boot-java": {
				"jpql": true
			}
		}	
		""";
		JsonElement settingsAsJson = new Gson().fromJson(changedSettings, JsonElement.class);
		harness.changeConfiguration(new Settings(settingsAsJson));
	}
	
	@Test
	void singleMemberAnnotation() throws Exception {
		String source = """
		package my.package
		
		import org.springframework.data.jpa.repository.Query;
		
		public interface OwnerRepository {
		
			@Query("SELECT DISTINCT owner FROM Owner owner")
			void findByLastName();
		}
		""";
        
        String uri = Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/OwnerRepository.java").toUri().toASCIIString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, uri);
		
		editor.assertSemanticTokensFull(
				new ExpectedSemanticToken("SELECT", "keyword"),
				new ExpectedSemanticToken("DISTINCT", "keyword"),
				new ExpectedSemanticToken("owner", "variable"),
				new ExpectedSemanticToken("FROM", "keyword"),
				new ExpectedSemanticToken("Owner", "class"),
				new ExpectedSemanticToken("owner", "variable")
		);
	}

	@Test
	void singleMemberAnnotationWithTextBlock() throws Exception {
		String source = """
		package my.package
		
		import org.springframework.data.jpa.repository.Query;
		
		public interface OwnerRepository {
		
			@Query(\"""
				SELECT DISTINCT owner FROM Owner owner
				\""")
			void findByLastName();
		}
		""";
        
        String uri = Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/OwnerRepository.java").toUri().toASCIIString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, uri);
		
		editor.assertSemanticTokensFull(
				new ExpectedSemanticToken("SELECT", "keyword"),
				new ExpectedSemanticToken("DISTINCT", "keyword"),
				new ExpectedSemanticToken("owner", "variable"),
				new ExpectedSemanticToken("FROM", "keyword"),
				new ExpectedSemanticToken("Owner", "class"),
				new ExpectedSemanticToken("owner", "variable")
		);
	}

	@Test
	void normalAnnotation() throws Exception {
		String source = """
		package my.package
		
		import org.springframework.data.jpa.repository.Query;
		
		public interface OwnerRepository {
		
			@Query(value = "SELECT DISTINCT owner FROM Owner owner")
			void findByLastName();
		}
		""";
        
        String uri = Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/OwnerRepository.java").toUri().toASCIIString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, uri);
		
		editor.assertSemanticTokensFull(
				new ExpectedSemanticToken("SELECT", "keyword"),
				new ExpectedSemanticToken("DISTINCT", "keyword"),
				new ExpectedSemanticToken("owner", "variable"),
				new ExpectedSemanticToken("FROM", "keyword"),
				new ExpectedSemanticToken("Owner", "class"),
				new ExpectedSemanticToken("owner", "variable")
		);
	}
	
	@Test
	void createQueryMethod() throws Exception {
		String source = """
		package my.package
		
		import jakarta.persistence.EntityManager;
		
		public interface OwnerRepository {
		
			default void findByLastName(EntityManager manager) {
				manager.createQuery("SELECT DISTINCT owner FROM Owner owner")
			}
		}
		""";
        
        String uri = Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/OwnerRepository.java").toUri().toASCIIString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, uri);
		
		editor.assertSemanticTokensFull(
				new ExpectedSemanticToken("SELECT", "keyword"),
				new ExpectedSemanticToken("DISTINCT", "keyword"),
				new ExpectedSemanticToken("owner", "variable"),
				new ExpectedSemanticToken("FROM", "keyword"),
				new ExpectedSemanticToken("Owner", "class"),
				new ExpectedSemanticToken("owner", "variable")
		);
	}

	@Test
	void createQueryMethodWithTextBlock() throws Exception {
		String source = """
		package my.package
		
		import jakarta.persistence.EntityManager;
		
		public interface OwnerRepository {
		
			default void findByLastName(EntityManager manager) {
				manager.createQuery(\"""
				SELECT DISTINCT owner FROM Owner owner
				\""")
			}
		}
		""";
        
        String uri = Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/OwnerRepository.java").toUri().toASCIIString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, uri);
		
		editor.assertSemanticTokensFull(
				new ExpectedSemanticToken("SELECT", "keyword"),
				new ExpectedSemanticToken("DISTINCT", "keyword"),
				new ExpectedSemanticToken("owner", "variable"),
				new ExpectedSemanticToken("FROM", "keyword"),
				new ExpectedSemanticToken("Owner", "class"),
				new ExpectedSemanticToken("owner", "variable")
		);
	}

	@Test
	void nativeQueryAttribute() throws Exception {
		String source = """
		package my.package
		
		import org.springframework.data.jpa.repository.Query;
		
		public interface OwnerRepository {
		
			@Query(value = "SELECT * FROM USERS u WHERE u.status = 1", nativeQuery = true)
			void findByLastName();
		}
		""";
        
        String uri = Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/OwnerRepository.java").toUri().toASCIIString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, uri);
		
		editor.assertSemanticTokensFull(
				new ExpectedSemanticToken("SELECT", "keyword"),
				new ExpectedSemanticToken("*", "operator"),
				new ExpectedSemanticToken("FROM", "keyword"),
				new ExpectedSemanticToken("USERS", "variable"),
				new ExpectedSemanticToken("u", "variable"),
				new ExpectedSemanticToken("WHERE", "keyword"),
				new ExpectedSemanticToken("u", "variable"),
				new ExpectedSemanticToken(".", "operator"),
				new ExpectedSemanticToken("status", "property"),
				new ExpectedSemanticToken("=", "operator"),
				new ExpectedSemanticToken("1", "number")
		);
	}
	
	@Test
	void nativeQuery_1() throws Exception {
		String source = """
		package my.package
		
		import org.springframework.data.jpa.repository.NativeQuery;
		
		public interface OwnerRepository {
		
			@NativeQuery(value = "SELECT * FROM USERS u WHERE u.status = 1")
			void findByLastName();
		}
		""";
        
        String uri = Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/OwnerRepository.java").toUri().toASCIIString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, uri);
		
		editor.assertSemanticTokensFull(
				new ExpectedSemanticToken("SELECT", "keyword"),
				new ExpectedSemanticToken("*", "operator"),
				new ExpectedSemanticToken("FROM", "keyword"),
				new ExpectedSemanticToken("USERS", "variable"),
				new ExpectedSemanticToken("u", "variable"),
				new ExpectedSemanticToken("WHERE", "keyword"),
				new ExpectedSemanticToken("u", "variable"),
				new ExpectedSemanticToken(".", "operator"),
				new ExpectedSemanticToken("status", "property"),
				new ExpectedSemanticToken("=", "operator"),
				new ExpectedSemanticToken("1", "number")
		);
	}

	@Test
	void nativeQuery_2() throws Exception {
		String source = """
		package my.package
		
		import org.springframework.data.jpa.repository.NativeQuery;
		
		public interface OwnerRepository {
		
			@NativeQuery("SELECT  * FROM USERS u WHERE u.status = 1")
			void findByLastName();
		}
		""";
        
        String uri = Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/OwnerRepository.java").toUri().toASCIIString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, uri);
		
		editor.assertSemanticTokensFull(
				new ExpectedSemanticToken("SELECT", "keyword"),
				new ExpectedSemanticToken("*", "operator"),
				new ExpectedSemanticToken("FROM", "keyword"),
				new ExpectedSemanticToken("USERS", "variable"),
				new ExpectedSemanticToken("u", "variable"),
				new ExpectedSemanticToken("WHERE", "keyword"),
				new ExpectedSemanticToken("u", "variable"),
				new ExpectedSemanticToken(".", "operator"),
				new ExpectedSemanticToken("status", "property"),
				new ExpectedSemanticToken("=", "operator"),
				new ExpectedSemanticToken("1", "number")
		);
	}
	
	@Test
	void nativeQueryWithSpel() throws Exception {
		String source = """
		package my.package
		
		import org.springframework.data.jpa.repository.Query;
		
		public interface OwnerRepository {
		
			@Query(value = "SELECT * FROM USERS u WHERE u.status = ?#{status}", nativeQuery = true)
			void findByLastName();
		}
		""";
        
        String uri = Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/OwnerRepository.java").toUri().toASCIIString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, uri);
		
		editor.assertSemanticTokensFull(
				new ExpectedSemanticToken("SELECT", "keyword"),
				new ExpectedSemanticToken("*", "operator"),
				new ExpectedSemanticToken("FROM", "keyword"),
				new ExpectedSemanticToken("USERS", "variable"),
				new ExpectedSemanticToken("u", "variable"),
				new ExpectedSemanticToken("WHERE", "keyword"),
				new ExpectedSemanticToken("u", "variable"),
				new ExpectedSemanticToken(".", "operator"),
				new ExpectedSemanticToken("status", "property"),
				new ExpectedSemanticToken("=", "operator"),
				new ExpectedSemanticToken("?", "operator"),
				new ExpectedSemanticToken("#{", "operator"),
				new ExpectedSemanticToken("status", "variable"),
				new ExpectedSemanticToken("}", "operator")
		);
	}
	
	@Test
	void jdbcSqlQuerySimpleAnnotation() throws Exception {
		String source = """
		package my.package
		
		import org.springframework.data.jdbc.repository.query.Query;
		import org.springframework.data.repository.query.Param;
		
		public interface EmployeeRepository {
		
			@Query("SELECT * FROM owner WHERE last_name LIKE concat(:lastName,'%')")
			void findByLastName(@Param("lastName") String lastName);
		}
		""";
        
        String uri = Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/EmployeeRepository.java").toUri().toASCIIString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, uri);
		
		editor.assertSemanticTokensFull(
				new ExpectedSemanticToken("SELECT", "keyword"),
				new ExpectedSemanticToken("*", "operator"),
				new ExpectedSemanticToken("FROM", "keyword"),
				new ExpectedSemanticToken("owner", "type"),
				new ExpectedSemanticToken("WHERE", "keyword"),
				new ExpectedSemanticToken("last_name", "variable"),
				new ExpectedSemanticToken("LIKE", "keyword"),
				new ExpectedSemanticToken("concat", "keyword"),
				new ExpectedSemanticToken("(", "operator"),
				new ExpectedSemanticToken(":", "operator"),
				new ExpectedSemanticToken("lastName", "parameter"),
				new ExpectedSemanticToken(",", "operator"),
				new ExpectedSemanticToken("'%'", "string"),
				new ExpectedSemanticToken(")", "operator")
		);
	}
	
	@Test
	void jdbcSqlQueryNormalAnnotation() throws Exception {
		String source = """
		package my.package
		
		import org.springframework.data.jdbc.repository.query.Query;
		import org.springframework.data.repository.query.Param;
		
		public interface EmployeeRepository {
		
			@Query(value = "SELECT * FROM owner WHERE last_name LIKE concat(:lastName,'%')")
			void findByLastName(@Param("lastName") String lastName);
		}
		""";
        
        String uri = Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/EmployeeRepository.java").toUri().toASCIIString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, uri);
		
		editor.assertSemanticTokensFull(
				new ExpectedSemanticToken("SELECT", "keyword"),
				new ExpectedSemanticToken("*", "operator"),
				new ExpectedSemanticToken("FROM", "keyword"),
				new ExpectedSemanticToken("owner", "type"),
				new ExpectedSemanticToken("WHERE", "keyword"),
				new ExpectedSemanticToken("last_name", "variable"),
				new ExpectedSemanticToken("LIKE", "keyword"),
				new ExpectedSemanticToken("concat", "keyword"),
				new ExpectedSemanticToken("(", "operator"),
				new ExpectedSemanticToken(":", "operator"),
				new ExpectedSemanticToken("lastName", "parameter"),
				new ExpectedSemanticToken(",", "operator"),
				new ExpectedSemanticToken("'%'", "string"),
				new ExpectedSemanticToken(")", "operator")
		);
	}
	
	@Test
	void namedQueryAnnotation() throws Exception {
		String source = """
		package my.package
		
		import jakarta.persistence.NamedQuery;

		@NamedQuery(name = " my_query", query = "SELECT DISTINCT owner FROM Owner owner")
		public interface OwnerRepository {
		}
		""";
        
        String uri = Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/OwnerRepository.java").toUri().toASCIIString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, uri);
		
		editor.assertSemanticTokensFull(
				new ExpectedSemanticToken("SELECT", "keyword"),
				new ExpectedSemanticToken("DISTINCT", "keyword"),
				new ExpectedSemanticToken("owner", "variable"),
				new ExpectedSemanticToken("FROM", "keyword"),
				new ExpectedSemanticToken("Owner", "class"),
				new ExpectedSemanticToken("owner", "variable")
		);
	}
	
	@Test
	void nativeConcatenatedStringQuery() throws Exception {
		String source = """
		package my.package
		
		import org.springframework.data.jpa.repository.Query;
		
		public interface OwnerRepository {
		
			@Query(value = "SELECT" +
				" DIS" + 
				"TINCT" + 
				" test FROM Te" +
				"st", nativeQuery = true) 
			void findByLastName();
		}
		""";

		String uri = Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/OwnerRepository.java").toUri()
				.toASCIIString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, uri);

		editor.assertSemanticTokensFull(
				new ExpectedSemanticToken("SELECT", "keyword"),
				new ExpectedSemanticToken("DIS", "keyword"),
				new ExpectedSemanticToken("TINCT", "keyword"),
				new ExpectedSemanticToken("test", "variable"),
				new ExpectedSemanticToken("FROM", "keyword"),
				new ExpectedSemanticToken("Te", "variable"),
				new ExpectedSemanticToken("st", "variable")
		);
	}

	@Test
	void concatenatedStringWithConstantQuery() throws Exception {
		String source = """
		package my.package
		
		import org.springframework.data.jpa.repository.Query;
		
		public interface OwnerRepository {
		
			static final String Q = " test FROM Te";
		
			@Query(value = "SELECT" +
				" DIS" + 
				"TINCT" + 
				Q +
				"st", nativeQuery = true) 
			void findByLastName();
		}
		""";

		String uri = Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/OwnerRepository.java").toUri()
				.toASCIIString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, uri);

		editor.assertSemanticTokensFull(
				new ExpectedSemanticToken("SELECT", "keyword"),
				new ExpectedSemanticToken("DIS", "keyword"),
				new ExpectedSemanticToken("TINCT", "keyword"),
				new ExpectedSemanticToken("st", "variable")
		);
	}

	@Test
	void concatenatedStringWithFieldAccessConstantQuery() throws Exception {
		String source = """
		package my.package
		
		import org.springframework.data.jpa.repository.Query;
		
		public interface OwnerRepository {
		
			static class P {
					static final String Q = " test FROM Te";
			}
		
			@Query(value = "SELECT" +
				" DIS" + 
				"TINCT" + 
				P.Q +
				"st", nativeQuery = true) 
			void findByLastName();
		}
		""";

		String uri = Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/OwnerRepository.java").toUri()
				.toASCIIString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, uri);

		editor.assertSemanticTokensFull(
				new ExpectedSemanticToken("SELECT", "keyword"),
				new ExpectedSemanticToken("DIS", "keyword"),
				new ExpectedSemanticToken("TINCT", "keyword"),
				new ExpectedSemanticToken("st", "variable")
		);
	}
}
