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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Paths;
import java.util.List;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.HoverTestConf;
import org.springframework.ide.vscode.boot.java.reconcilers.CompositeASTVisitor;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.commons.languageserver.semantic.tokens.SemanticTokenData;
import org.springframework.ide.vscode.commons.languageserver.util.Settings;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.util.Collector;
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
	
	@Autowired JdtDataQuerySemanticTokensProvider provider;
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
	
	private List<SemanticTokenData> computeTokens(CompilationUnit cu) {
		Collector<SemanticTokenData> collector = new Collector<>();
		CompositeASTVisitor visitor = new CompositeASTVisitor();
		visitor.add(provider.getTokensComputer(jp, null, cu, collector));
		cu.accept(visitor);
		return collector.get();
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
		CompilationUnit cu = CompilationUnitCache.parse2(source.toCharArray(), uri, "OwnerRepository.java", jp);
        
        assertThat(cu).isNotNull();
        
        List<SemanticTokenData> tokens = computeTokens(cu);
        
        SemanticTokenData token = tokens.get(0);
        assertThat(token).isEqualTo(new SemanticTokenData(125, 131, "keyword", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("SELECT");
        
        token = tokens.get(1);
        assertThat(token).isEqualTo(new SemanticTokenData(132, 140, "keyword", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("DISTINCT");
        
        token = tokens.get(2);
        assertThat(token).isEqualTo(new SemanticTokenData(141, 146, "variable", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("owner");
        
        token = tokens.get(3);
        assertThat(token).isEqualTo(new SemanticTokenData(147, 151, "keyword", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("FROM");

        token = tokens.get(4);
        assertThat(token).isEqualTo(new SemanticTokenData(152, 157, "class", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("Owner");

        token = tokens.get(5);
        assertThat(token).isEqualTo(new SemanticTokenData(158, 163, "variable", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("owner");
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
		CompilationUnit cu = CompilationUnitCache.parse2(source.toCharArray(), uri, "OwnerRepository.java", jp);
        
        assertThat(cu).isNotNull();
        
        List<SemanticTokenData> tokens = computeTokens(cu);
        
        SemanticTokenData token = tokens.get(0);
        assertThat(token).isEqualTo(new SemanticTokenData(128, 134, "keyword", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("SELECT");
        
        token = tokens.get(1);
        assertThat(token).isEqualTo(new SemanticTokenData(135, 143, "keyword", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("DISTINCT");
        
        token = tokens.get(2);
        assertThat(token).isEqualTo(new SemanticTokenData(144, 149, "variable", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("owner");
        
        token = tokens.get(3);
        assertThat(token).isEqualTo(new SemanticTokenData(150, 154, "keyword", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("FROM");

        token = tokens.get(4);
        assertThat(token).isEqualTo(new SemanticTokenData(155, 160, "class", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("Owner");

        token = tokens.get(5);
        assertThat(token).isEqualTo(new SemanticTokenData(161, 166, "variable", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("owner");
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
		CompilationUnit cu = CompilationUnitCache.parse2(source.toCharArray(), uri, "OwnerRepository.java", jp);
        
        assertThat(cu).isNotNull();
        
        List<SemanticTokenData> tokens = computeTokens(cu);
        
        SemanticTokenData token = tokens.get(0);
        assertThat(token).isEqualTo(new SemanticTokenData(176, 182, "keyword", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("SELECT");
        
        token = tokens.get(1);
        assertThat(token).isEqualTo(new SemanticTokenData(183, 191, "keyword", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("DISTINCT");
        
        token = tokens.get(2);
        assertThat(token).isEqualTo(new SemanticTokenData(192, 197, "variable", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("owner");
        
        token = tokens.get(3);
        assertThat(token).isEqualTo(new SemanticTokenData(198, 202, "keyword", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("FROM");

        token = tokens.get(4);
        assertThat(token).isEqualTo(new SemanticTokenData(203, 208, "class", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("Owner");

        token = tokens.get(5);
        assertThat(token).isEqualTo(new SemanticTokenData(209, 214, "variable", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("owner");
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
		CompilationUnit cu = CompilationUnitCache.parse2(source.toCharArray(), uri, "OwnerRepository.java", jp);
        
        assertThat(cu).isNotNull();
        
        List<SemanticTokenData> tokens = computeTokens(cu);
        
        SemanticTokenData token = tokens.get(0);
        assertThat(token).isEqualTo(new SemanticTokenData(181, 187, "keyword", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("SELECT");
        
        token = tokens.get(1);
        assertThat(token).isEqualTo(new SemanticTokenData(188, 196, "keyword", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("DISTINCT");
        
        token = tokens.get(2);
        assertThat(token).isEqualTo(new SemanticTokenData(197, 202, "variable", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("owner");
        
        token = tokens.get(3);
        assertThat(token).isEqualTo(new SemanticTokenData(203, 207, "keyword", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("FROM");

        token = tokens.get(4);
        assertThat(token).isEqualTo(new SemanticTokenData(208, 213, "class", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("Owner");

        token = tokens.get(5);
        assertThat(token).isEqualTo(new SemanticTokenData(214, 219, "variable", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("owner");
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
		CompilationUnit cu = CompilationUnitCache.parse2(source.toCharArray(), uri, "OwnerRepository.java", jp);
        
        assertThat(cu).isNotNull();
        
        List<SemanticTokenData> tokens = computeTokens(cu);
        
        SemanticTokenData token = tokens.get(0);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("SELECT");
        assertThat(token).isEqualTo(new SemanticTokenData(128, 134, "keyword", new String[0]));
        
        token = tokens.get(1);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("*");
        assertThat(token).isEqualTo(new SemanticTokenData(135, 136, "operator", new String[0]));
        
        token = tokens.get(2);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("FROM");
        assertThat(token).isEqualTo(new SemanticTokenData(137, 141, "keyword", new String[0]));
        
        token = tokens.get(3);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("USERS");
        assertThat(token).isEqualTo(new SemanticTokenData(142, 147, "variable", new String[0]));
        
        token = tokens.get(4);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("u");
        assertThat(token).isEqualTo(new SemanticTokenData(148, 149, "variable", new String[0]));
        
        token = tokens.get(5);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("WHERE");
        assertThat(token).isEqualTo(new SemanticTokenData(150, 155, "keyword", new String[0]));
        
        token = tokens.get(6);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("u");
        assertThat(token).isEqualTo(new SemanticTokenData(156, 157, "variable", new String[0]));
        
        token = tokens.get(7);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo(".");
        assertThat(token).isEqualTo(new SemanticTokenData(157, 158, "operator", new String[0]));
        
        token = tokens.get(8);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("status");
        assertThat(token).isEqualTo(new SemanticTokenData(158, 164, "property", new String[0]));

        token = tokens.get(9);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("=");
        assertThat(token).isEqualTo(new SemanticTokenData(165, 166, "operator", new String[0]));

        token = tokens.get(10);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("1");
        assertThat(token).isEqualTo(new SemanticTokenData(167, 168, "number", new String[0]));
        
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
		CompilationUnit cu = CompilationUnitCache.parse2(source.toCharArray(), uri, "OwnerRepository.java", jp);
        
        assertThat(cu).isNotNull();
        
        List<SemanticTokenData> tokens = computeTokens(cu);
        
        SemanticTokenData token = tokens.get(0);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("SELECT");
        assertThat(token).isEqualTo(new SemanticTokenData(140, 146, "keyword", new String[0]));
        
        token = tokens.get(1);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("*");
        assertThat(token).isEqualTo(new SemanticTokenData(147, 148, "operator", new String[0]));
        
        token = tokens.get(2);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("FROM");
        assertThat(token).isEqualTo(new SemanticTokenData(149, 153, "keyword", new String[0]));
        
        token = tokens.get(3);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("USERS");
        assertThat(token).isEqualTo(new SemanticTokenData(154, 159, "variable", new String[0]));
        
        token = tokens.get(4);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("u");
        assertThat(token).isEqualTo(new SemanticTokenData(160, 161, "variable", new String[0]));
        
        token = tokens.get(5);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("WHERE");
        assertThat(token).isEqualTo(new SemanticTokenData(162, 167, "keyword", new String[0]));
        
        token = tokens.get(6);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("u");
        assertThat(token).isEqualTo(new SemanticTokenData(168, 169, "variable", new String[0]));
        
        token = tokens.get(7);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo(".");
        assertThat(token).isEqualTo(new SemanticTokenData(169, 170, "operator", new String[0]));
        
        token = tokens.get(8);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("status");
        assertThat(token).isEqualTo(new SemanticTokenData(170, 176, "property", new String[0]));

        token = tokens.get(9);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("=");
        assertThat(token).isEqualTo(new SemanticTokenData(177, 178, "operator", new String[0]));

        token = tokens.get(10);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("1");
        assertThat(token).isEqualTo(new SemanticTokenData(179, 180, "number", new String[0]));
        
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
		CompilationUnit cu = CompilationUnitCache.parse2(source.toCharArray(), uri, "OwnerRepository.java", jp);
        
        assertThat(cu).isNotNull();
        
        List<SemanticTokenData> tokens = computeTokens(cu);
        
        SemanticTokenData token = tokens.get(0);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("SELECT");
        assertThat(token).isEqualTo(new SemanticTokenData(132, 138, "keyword", new String[0]));
        
        token = tokens.get(1);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("*");
        assertThat(token).isEqualTo(new SemanticTokenData(140, 141, "operator", new String[0]));
        
        token = tokens.get(2);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("FROM");
        assertThat(token).isEqualTo(new SemanticTokenData(142, 146, "keyword", new String[0]));
        
        token = tokens.get(3);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("USERS");
        assertThat(token).isEqualTo(new SemanticTokenData(147, 152, "variable", new String[0]));
        
        token = tokens.get(4);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("u");
        assertThat(token).isEqualTo(new SemanticTokenData(153, 154, "variable", new String[0]));
        
        token = tokens.get(5);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("WHERE");
        assertThat(token).isEqualTo(new SemanticTokenData(155, 160, "keyword", new String[0]));
        
        token = tokens.get(6);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("u");
        assertThat(token).isEqualTo(new SemanticTokenData(161, 162, "variable", new String[0]));
        
        token = tokens.get(7);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo(".");
        assertThat(token).isEqualTo(new SemanticTokenData(162, 163, "operator", new String[0]));
        
        token = tokens.get(8);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("status");
        assertThat(token).isEqualTo(new SemanticTokenData(163, 169, "property", new String[0]));

        token = tokens.get(9);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("=");
        assertThat(token).isEqualTo(new SemanticTokenData(170, 171, "operator", new String[0]));

        token = tokens.get(10);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("1");
        assertThat(token).isEqualTo(new SemanticTokenData(172, 173, "number", new String[0]));
        
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
		CompilationUnit cu = CompilationUnitCache.parse2(source.toCharArray(), uri, "OwnerRepository.java", jp);
        
        assertThat(cu).isNotNull();
        
        List<SemanticTokenData> tokens = computeTokens(cu);
        
        SemanticTokenData token = tokens.get(0);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("SELECT");
        assertThat(token).isEqualTo(new SemanticTokenData(128, 134, "keyword", new String[0]));
        
        token = tokens.get(1);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("*");
        assertThat(token).isEqualTo(new SemanticTokenData(135, 136, "operator", new String[0]));
        
        token = tokens.get(2);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("FROM");
        assertThat(token).isEqualTo(new SemanticTokenData(137, 141, "keyword", new String[0]));
        
        token = tokens.get(3);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("USERS");
        assertThat(token).isEqualTo(new SemanticTokenData(142, 147, "variable", new String[0]));
        
        token = tokens.get(4);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("u");
        assertThat(token).isEqualTo(new SemanticTokenData(148, 149, "variable", new String[0]));
        
        token = tokens.get(5);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("WHERE");
        assertThat(token).isEqualTo(new SemanticTokenData(150, 155, "keyword", new String[0]));
        
        token = tokens.get(6);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("u");
        assertThat(token).isEqualTo(new SemanticTokenData(156, 157, "variable", new String[0]));
        
        token = tokens.get(7);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo(".");
        assertThat(token).isEqualTo(new SemanticTokenData(157, 158, "operator", new String[0]));
        
        token = tokens.get(8);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("status");
        assertThat(token).isEqualTo(new SemanticTokenData(158, 164, "property", new String[0]));

        token = tokens.get(9);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("=");
        assertThat(token).isEqualTo(new SemanticTokenData(165, 166, "operator", new String[0]));

        token = tokens.get(10);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("?");
        assertThat(token).isEqualTo(new SemanticTokenData(167, 168, "operator", new String[0]));
        
        token = tokens.get(11);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("#{");
        assertThat(token).isEqualTo(new SemanticTokenData(168, 170, "operator", new String[0]));
        
        token = tokens.get(12);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("status");
        assertThat(token).isEqualTo(new SemanticTokenData(170, 176, "variable", new String[0]));

        token = tokens.get(13);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("}");
        assertThat(token).isEqualTo(new SemanticTokenData(176, 177, "operator", new String[0]));
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
		CompilationUnit cu = CompilationUnitCache.parse2(source.toCharArray(), uri, "EmployeeRepository.java", jp);
        
        assertThat(cu).isNotNull();
        
        List<SemanticTokenData> tokens = computeTokens(cu);
        
        SemanticTokenData token = tokens.get(0);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("SELECT");
        SemanticTokenData expected = SemanticTokenData.builder("keyword").withOffset(186).withText("SELECT").build();
        assertThat(token).isEqualTo(expected);
        
        token = tokens.get(1);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("*");
        expected = SemanticTokenData.builder("operator").withPrevious(expected).space().withText("*").build();
        assertThat(token).isEqualTo(expected);
        
        token = tokens.get(2);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("FROM");
        expected = SemanticTokenData.builder("keyword").withPrevious(expected).space().withText("FROM").build();
        assertThat(token).isEqualTo(expected);
        
        token = tokens.get(3);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("owner");
        expected = SemanticTokenData.builder("type").withPrevious(expected).space().withText("owner").build();
        assertThat(token).isEqualTo(expected);
        
        token = tokens.get(4);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("WHERE");
        expected = SemanticTokenData.builder("keyword").withPrevious(expected).space().withText("WHERE").build();
        assertThat(token).isEqualTo(expected);
        
        token = tokens.get(5);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("last_name");
        expected = SemanticTokenData.builder("variable").withPrevious(expected).space().withText("last_name").build();
        assertThat(token).isEqualTo(expected);
        
        token = tokens.get(6);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("LIKE");
        expected = SemanticTokenData.builder("keyword").withPrevious(expected).space().withText("LIKE").build();
        assertThat(token).isEqualTo(expected);
        
        token = tokens.get(7);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("concat");
        expected = SemanticTokenData.builder("keyword").withPrevious(expected).space().withText("concat").build();
        assertThat(token).isEqualTo(expected);
        
        token = tokens.get(8);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("(");
        expected = SemanticTokenData.builder("operator").withPrevious(expected).withText("(").build();
        assertThat(token).isEqualTo(expected);

        token = tokens.get(9);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo(":");
        expected = SemanticTokenData.builder("operator").withPrevious(expected).withText(":").build();
        assertThat(token).isEqualTo(expected);

        token = tokens.get(10);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("lastName");
        expected = SemanticTokenData.builder("parameter").withPrevious(expected).withText("lastName").build();
        assertThat(token).isEqualTo(expected);
        
        token = tokens.get(11);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo(",");
        expected = SemanticTokenData.builder("operator").withPrevious(expected).withText(",").build();
        assertThat(token).isEqualTo(expected);
        
        token = tokens.get(12);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("'%'");
        expected = SemanticTokenData.builder("string").withPrevious(expected).withText("'%'").build();
        assertThat(token).isEqualTo(expected);


        token = tokens.get(13);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo(")");
        expected = SemanticTokenData.builder("operator").withPrevious(expected).withText(")").build();
        assertThat(token).isEqualTo(expected);
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
		CompilationUnit cu = CompilationUnitCache.parse2(source.toCharArray(), uri, "EmployeeRepository.java", jp);
        
        assertThat(cu).isNotNull();
        
        List<SemanticTokenData> tokens = computeTokens(cu);
        
        SemanticTokenData token = tokens.get(0);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("SELECT");
        SemanticTokenData expected = SemanticTokenData.builder("keyword").withOffset(194).withText("SELECT").build();
        assertThat(token).isEqualTo(expected);
        
        token = tokens.get(1);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("*");
        expected = SemanticTokenData.builder("operator").withPrevious(expected).space().withText("*").build();
        assertThat(token).isEqualTo(expected);
        
        token = tokens.get(2);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("FROM");
        expected = SemanticTokenData.builder("keyword").withPrevious(expected).space().withText("FROM").build();
        assertThat(token).isEqualTo(expected);
        
        token = tokens.get(3);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("owner");
        expected = SemanticTokenData.builder("type").withPrevious(expected).space().withText("owner").build();
        assertThat(token).isEqualTo(expected);
        
        token = tokens.get(4);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("WHERE");
        expected = SemanticTokenData.builder("keyword").withPrevious(expected).space().withText("WHERE").build();
        assertThat(token).isEqualTo(expected);
        
        token = tokens.get(5);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("last_name");
        expected = SemanticTokenData.builder("variable").withPrevious(expected).space().withText("last_name").build();
        assertThat(token).isEqualTo(expected);
        
        token = tokens.get(6);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("LIKE");
        expected = SemanticTokenData.builder("keyword").withPrevious(expected).space().withText("LIKE").build();
        assertThat(token).isEqualTo(expected);
        
        token = tokens.get(7);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("concat");
        expected = SemanticTokenData.builder("keyword").withPrevious(expected).space().withText("concat").build();
        assertThat(token).isEqualTo(expected);
        
        token = tokens.get(8);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("(");
        expected = SemanticTokenData.builder("operator").withPrevious(expected).withText("(").build();
        assertThat(token).isEqualTo(expected);

        token = tokens.get(9);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo(":");
        expected = SemanticTokenData.builder("operator").withPrevious(expected).withText(":").build();
        assertThat(token).isEqualTo(expected);

        token = tokens.get(10);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("lastName");
        expected = SemanticTokenData.builder("parameter").withPrevious(expected).withText("lastName").build();
        assertThat(token).isEqualTo(expected);
        
        token = tokens.get(11);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo(",");
        expected = SemanticTokenData.builder("operator").withPrevious(expected).withText(",").build();
        assertThat(token).isEqualTo(expected);
        
        token = tokens.get(12);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("'%'");
        expected = SemanticTokenData.builder("string").withPrevious(expected).withText("'%'").build();
        assertThat(token).isEqualTo(expected);


        token = tokens.get(13);
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo(")");
        expected = SemanticTokenData.builder("operator").withPrevious(expected).withText(")").build();
        assertThat(token).isEqualTo(expected);
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
		CompilationUnit cu = CompilationUnitCache.parse2(source.toCharArray(), uri, "OwnerRepository.java", jp);
        
        assertThat(cu).isNotNull();
        
        List<SemanticTokenData> tokens = computeTokens(cu);
        
        SemanticTokenData token = tokens.get(0);
        assertThat(token).isEqualTo(new SemanticTokenData(101, 107, "keyword", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("SELECT");
        
        token = tokens.get(1);
        assertThat(token).isEqualTo(new SemanticTokenData(108, 116, "keyword", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("DISTINCT");
        
        token = tokens.get(2);
        assertThat(token).isEqualTo(new SemanticTokenData(117, 122, "variable", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("owner");
        
        token = tokens.get(3);
        assertThat(token).isEqualTo(new SemanticTokenData(123, 127, "keyword", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("FROM");

        token = tokens.get(4);
        assertThat(token).isEqualTo(new SemanticTokenData(128, 133, "class", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("Owner");

        token = tokens.get(5);
        assertThat(token).isEqualTo(new SemanticTokenData(134, 139, "variable", new String[0]));
        assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("owner");
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
		CompilationUnit cu = CompilationUnitCache.parse2(source.toCharArray(), uri, "OwnerRepository.java", jp);

		assertThat(cu).isNotNull();

		List<SemanticTokenData> tokens = computeTokens(cu);

		SemanticTokenData token = tokens.get(0);
		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("SELECT");
		assertThat(token).isEqualTo(new SemanticTokenData(128, 134, "keyword", new String[0]));

		token = tokens.get(1);
		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("DIS");
		assertThat(token).isEqualTo(new SemanticTokenData(142, 145, "keyword", new String[0]));

		token = tokens.get(2);
		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("TINCT");
		assertThat(token).isEqualTo(new SemanticTokenData(152, 157, "keyword", new String[0]));

		token = tokens.get(3);
		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("test");
		assertThat(token).isEqualTo(new SemanticTokenData(165, 169, "variable", new String[0]));

		token = tokens.get(4);
		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("FROM");
		assertThat(token).isEqualTo(new SemanticTokenData(170, 174, "keyword", new String[0]));

		token = tokens.get(5);
		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("Te");
		assertThat(token).isEqualTo(new SemanticTokenData(175, 177, "variable", new String[0]));

		token = tokens.get(6);
		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("st");
		assertThat(token).isEqualTo(new SemanticTokenData(184, 186, "variable", new String[0]));

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
		CompilationUnit cu = CompilationUnitCache.parse2(source.toCharArray(), uri, "OwnerRepository.java", jp);

		assertThat(cu).isNotNull();

		List<SemanticTokenData> tokens = computeTokens(cu);

		SemanticTokenData token = tokens.get(0);
		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("SELECT");
		assertThat(token).isEqualTo(new SemanticTokenData(171, 177, "keyword", new String[0]));

		token = tokens.get(1);
		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("DIS");
		assertThat(token).isEqualTo(new SemanticTokenData(185, 188, "keyword", new String[0]));

		token = tokens.get(2);
		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("TINCT");
		assertThat(token).isEqualTo(new SemanticTokenData(195, 200, "keyword", new String[0]));

//		token = tokens.get(3);
//		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("test");
//		assertThat(token).isEqualTo(new SemanticTokenData(165, 169, "variable", new String[0]));
//
//		token = tokens.get(4);
//		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("FROM");
//		assertThat(token).isEqualTo(new SemanticTokenData(170, 174, "keyword", new String[0]));
//
//		token = tokens.get(5);
//		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("Te");
//		assertThat(token).isEqualTo(new SemanticTokenData(175, 177, "variable", new String[0]));

		token = tokens.get(3);
		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("st");
		assertThat(token).isEqualTo(new SemanticTokenData(213, 215, "variable", new String[0]));

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
		CompilationUnit cu = CompilationUnitCache.parse2(source.toCharArray(), uri, "OwnerRepository.java", jp);

		assertThat(cu).isNotNull();

		List<SemanticTokenData> tokens = computeTokens(cu);

		SemanticTokenData token = tokens.get(0);
		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("SELECT");
		assertThat(token).isEqualTo(new SemanticTokenData(194, 200, "keyword", new String[0]));

		token = tokens.get(1);
		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("DIS");
		assertThat(token).isEqualTo(new SemanticTokenData(208, 211, "keyword", new String[0]));

		token = tokens.get(2);
		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("TINCT");
		assertThat(token).isEqualTo(new SemanticTokenData(218, 223, "keyword", new String[0]));

//		token = tokens.get(3);
//		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("test");
//		assertThat(token).isEqualTo(new SemanticTokenData(165, 169, "variable", new String[0]));
//
//		token = tokens.get(4);
//		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("FROM");
//		assertThat(token).isEqualTo(new SemanticTokenData(170, 174, "keyword", new String[0]));
//
//		token = tokens.get(5);
//		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("Te");
//		assertThat(token).isEqualTo(new SemanticTokenData(175, 177, "variable", new String[0]));

		token = tokens.get(3);
		assertThat(source.substring(token.getStart(), token.getEnd())).isEqualTo("st");
		assertThat(token).isEqualTo(new SemanticTokenData(238, 240, "variable", new String[0]));

	}
}
