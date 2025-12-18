/*******************************************************************************
 * Copyright (c) 202, 2025 Broadcom, Inc.
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

import org.eclipse.lsp4j.Range;
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
public class DataQueryParameterDefinitionProviderTest {

	@Autowired BootLanguageServerHarness harness;

	private ProjectsHarness projects = ProjectsHarness.INSTANCE;
	
	private MavenJavaProject jp;
		
	@BeforeEach
	public void setup() throws Exception {
		jp =  projects.mavenProject("boot-mysql");
		harness.useProject(jp);
	}
	
	@Test
	void parameterNameDefinition() throws Exception {
		String source = """
		package my.package
		
		import org.springframework.data.jpa.repository.Query;
		
		public interface OwnerRepository {
		
			@Query("SELECT DISTINCT owner FROM Owner owner left join  owner.pets WHERE owner.lastName LIKE :lastName%")
			Object findByLastName(@Param("lastName") String lastName);
		}
		""";
		
		Editor editor = harness.newEditor(LanguageId.JAVA, source, Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/OwnerRepository.java").toUri().toASCIIString());
		
		Range expectedRange = editor.rangeOf("String lastName", "lastName");
		Range highlightRange = editor.rangeOf(":lastName%", "lastName");
		editor.assertGotoDefinition(highlightRange.getStart(), expectedRange, highlightRange);
		
	}

	@Test
	void parameterOrdinalDefinition() throws Exception {
		String source = """
		package my.package
		
		import org.springframework.data.jpa.repository.Query;
		
		public interface OwnerRepository {
		
			@Query("SELECT DISTINCT owner FROM Owner owner left join  owner.pets WHERE owner.lastName LIKE :1%")
			Object findByLastName(@Param("lastName") String lastName);
		}
		""";
		
		Editor editor = harness.newEditor(LanguageId.JAVA, source, Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/OwnerRepository.java").toUri().toASCIIString());
		
		Range expectedRange = editor.rangeOf("String lastName", "lastName");
		Range highlightRange = editor.rangeOf(":1", "1");
		editor.assertGotoDefinition(highlightRange.getStart(), expectedRange, highlightRange);
		
	}
	
	@Test
	void parameterNameDefinitionJdbc() throws Exception {
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
		
		Range expectedRange = editor.rangeOf("String lastName", "lastName");
		Range highlightRange = editor.rangeOf(":lastName", "lastName");
		editor.assertGotoDefinition(highlightRange.getStart(), expectedRange, highlightRange);
		
	}
	
	@Test
	void parameterOrdinalDefinitionJdbc() throws Exception {
		String source = """
		package my.package
		
		import org.springframework.data.jdbc.repository.query.Query;
		import org.springframework.data.repository.query.Param;
		
		public interface EmployeeRepository {
		
			@Query("SELECT * FROM owner WHERE last_name LIKE concat(?1,'%')")
			void findByLastName(@Param("lastName") String lastName);
		}
		""";
		
		Editor editor = harness.newEditor(LanguageId.JAVA, source, Paths.get(jp.getLocationUri()).resolve("src/main/resource/my/package/EmployeeRepository.java").toUri().toASCIIString());
		
		Range expectedRange = editor.rangeOf("String lastName", "lastName");
		Range highlightRange = editor.rangeOf("?1", "1");
		editor.assertGotoDefinition(highlightRange.getStart(), expectedRange, highlightRange);
		
	}


}
