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

import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.HoverTestConf;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(HoverTestConf.class)
public class JdtQueryDocHighlightsProviderTest {
	
	@Autowired BootLanguageServerHarness harness;

	private ProjectsHarness projects = ProjectsHarness.INSTANCE;
	
	private MavenJavaProject jp;
		
	@BeforeEach
	public void setup() throws Exception {
		jp =  projects.mavenProject("boot-mysql");
		harness.useProject(jp);
	}
	
	@Test
	void parameterName() throws Exception {
		String source = """
		package my.package
		
		import org.springframework.data.jpa.repository.Query;
		
		public interface OwnerRepository {
		
			@Query("SELECT DISTINCT owner FROM Owner owner left join  owner.pets WHERE owner.lastName LIKE :lastName%")
			Object findByLastName(@Param("lastName") String lastName);
		}
		""";
		
		Editor editor = harness.newEditor(LanguageId.JAVA, source, Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/OwnerRepository.java").toUri().toASCIIString());
		
		editor.assertDocumentHighlights(":lastName", new DocumentHighlight(editor.rangeOf("String lastName", "lastName"), DocumentHighlightKind.Write));
		
	}

	@Test
	void parameterNameJdbc() throws Exception {
		String source = """
		package my.package
		
		import org.springframework.data.jdbc.repository.query.Query;
		import org.springframework.data.repository.query.Param;
		
		public interface EmployeeRepository {
		
			@Query("SELECT * FROM owner WHERE last_name LIKE concat(:lastName,'%')")
			void findByLastName(@Param("lastName") String lastName);
		}
		""";
		
		Editor editor = harness.newEditor(LanguageId.JAVA, source, Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/EmployeeRepository.java").toUri().toASCIIString());
		
		editor.assertDocumentHighlights(":lastName", new DocumentHighlight(editor.rangeOf("String lastName", "lastName"), DocumentHighlightKind.Write));
		
	}
	
	@Test
	void parameterOrdinal() throws Exception {
		String source = """
		package my.package
		
		import org.springframework.data.jpa.repository.Query;
		
		public interface OwnerRepository {
		
			@Query("SELECT DISTINCT owner FROM Owner owner left join  owner.pets WHERE owner.lastName LIKE ?1%")
			Object findByLastName(@Param("lastName") String lastName);
		}
		""";
		
		Editor editor = harness.newEditor(LanguageId.JAVA, source, Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/OwnerRepository.java").toUri().toASCIIString());
		
		editor.assertDocumentHighlights("?1", new DocumentHighlight(editor.rangeOf("String lastName", "lastName"), DocumentHighlightKind.Write));
		
	}

	@Test
	void noOrdinalMatch() throws Exception {
		String source = """
		package my.package
		
		import org.springframework.data.jpa.repository.Query;
		
		public interface OwnerRepository {
		
			@Query("SELECT DISTINCT owner FROM Owner owner left join  owner.pets WHERE owner.lastName LIKE ?2%")
			Object findByLastName(@Param("lastName") String lastName);
		}
		""";
		
		Editor editor = harness.newEditor(LanguageId.JAVA, source, Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/OwnerRepository.java").toUri().toASCIIString());
		
		editor.assertDocumentHighlights("?1");
		
	}

	@Test
	void noParameterNameMatch() throws Exception {
		String source = """
		package my.package
		
		import org.springframework.data.jpa.repository.Query;
		
		public interface OwnerRepository {
		
			@Query("SELECT DISTINCT owner FROM Owner owner left join  owner.pets WHERE owner.lastName LIKE :name%")
			Object findByLastName(@Param("lastName") String lastName);
		}
		""";
		
		Editor editor = harness.newEditor(LanguageId.JAVA, source, Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/OwnerRepository.java").toUri().toASCIIString());
		
		editor.assertDocumentHighlights(":name");
		
	}
}
