/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Boot4JavaProblemType;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.WebApiVersionSyntaxReconciler;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement.ConfigType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;

public class WebApiVersionSyntaxReconcilerTest extends BaseReconcilerTest {

	@Override
	protected String getFolder() {
		return "webapiversioning";
	}

	@Override
	protected String getProjectName() {
		return "sf7-validation";
	}

	@Override
	protected JdtAstReconciler getReconciler() {
		return new WebApiVersionSyntaxReconciler(null);
	}

	@BeforeEach
	void setup() throws Exception {
		super.setup();
	}
	
	@AfterEach
	void tearDown() throws Exception {
		super.tearDown();
	}
	
	@Test
	void parseDefaultStandardVersionWithoutErrors() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.web.bind.annotation.RestController;
				import org.springframework.web.bind.annotation.RequestMapping;
				
				@RestController
				@RequestMapping(path = "mypath", version = "1.2.3")
				public class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			WebConfigIndexElement webConfig = new WebConfigIndexElement.Builder(ConfigType.WEB_CONFIG)
					.versionStrategy("version-strategy-configured", new Range(new Position(1, 1), new Position(1, 4)))
					.buildFor(null);
			springIndex.updateElements(getProjectName(), "soneURI", new SpringIndexElement[] {webConfig});

			WebApiVersionSyntaxReconciler r = new WebApiVersionSyntaxReconciler(springIndex);
			return r;
		}, "A.java", source, false);
		
		assertEquals(0, problems.size());
	}

	@Test
	void parseWithConfiguredDefaultStandardVersionWithoutErrors() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.web.bind.annotation.RestController;
				import org.springframework.web.bind.annotation.RequestMapping;
				
				@RestController
				@RequestMapping(path = "mypath", version = "1.2.3")
				public class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			WebConfigIndexElement webConfig = new WebConfigIndexElement.Builder(ConfigType.WEB_CONFIG)
					.versionStrategy("version-strategy-configured", new Range(new Position(1, 1), new Position(1, 4)))
					.versionParser(WebConfigIndexElement.DEFAULT_VERSION_PARSER)
					.buildFor(null);
			springIndex.updateElements(getProjectName(), "soneURI", new SpringIndexElement[] {webConfig});

			WebApiVersionSyntaxReconciler r = new WebApiVersionSyntaxReconciler(springIndex);
			return r;
		}, "A.java", source, false);
		
		assertEquals(0, problems.size());
	}

	@Test
	void parserShowsErrorForNotParseableVersionNumberWhenStandardVersionParserIsUsed() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.web.bind.annotation.RestController;
				import org.springframework.web.bind.annotation.RequestMapping;
				
				@RestController
				@RequestMapping(path = "mypath", version = "a.b.c")
				public class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			WebConfigIndexElement webConfig = new WebConfigIndexElement.Builder(ConfigType.WEB_CONFIG)
					.versionStrategy("version-strategy-configured", new Range(new Position(1, 1), new Position(1, 4)))
					.buildFor(null);
			springIndex.updateElements(getProjectName(), "soneURI", new SpringIndexElement[] {webConfig});

			WebApiVersionSyntaxReconciler r = new WebApiVersionSyntaxReconciler(springIndex);
			return r;
		}, "A.java", source, false);
		
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		
		assertEquals(Boot4JavaProblemType.API_VERSION_SYNTAX_ERROR, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("\"a.b.c\"", markedStr);

		assertEquals(0, problem.getQuickfixes().size());
	}

	@Test
	void parserShowsErrorForNotParseableVersionNumberWhenStandardVersionParserIsConfigured() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.web.bind.annotation.RestController;
				import org.springframework.web.bind.annotation.RequestMapping;
				
				@RestController
				@RequestMapping(path = "mypath", version = "a.b.c")
				public class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			WebConfigIndexElement webConfig = new WebConfigIndexElement.Builder(ConfigType.WEB_CONFIG)
					.versionStrategy("version-strategy-configured", new Range(new Position(1, 1), new Position(1, 4)))
					.versionParser(WebConfigIndexElement.DEFAULT_VERSION_PARSER)
					.buildFor(null);
			springIndex.updateElements(getProjectName(), "soneURI", new SpringIndexElement[] {webConfig});

			WebApiVersionSyntaxReconciler r = new WebApiVersionSyntaxReconciler(springIndex);
			return r;
		}, "A.java", source, false);
		
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		
		assertEquals(Boot4JavaProblemType.API_VERSION_SYNTAX_ERROR, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("\"a.b.c\"", markedStr);

		assertEquals(0, problem.getQuickfixes().size());
	}

	@Test
	void parserShowsNoErrorForNotParseableVersionNumberWhenSpecificParserIsUsed() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.web.bind.annotation.RestController;
				import org.springframework.web.bind.annotation.RequestMapping;
				
				@RestController
				@RequestMapping(path = "mypath", version = "1.2.3")
				public class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			WebConfigIndexElement webConfig = new WebConfigIndexElement.Builder(ConfigType.WEB_CONFIG)
					.versionStrategy("version-strategy-configured", new Range(new Position(1, 1), new Position(1, 4)))
					.versionParser("some.random.VersionParser")
					.buildFor(null);
			springIndex.updateElements(getProjectName(), "soneURI", new SpringIndexElement[] {webConfig});

			WebApiVersionSyntaxReconciler r = new WebApiVersionSyntaxReconciler(springIndex);
			return r;
		}, "A.java", source, false);
		
		assertEquals(0, problems.size());
	}

}
