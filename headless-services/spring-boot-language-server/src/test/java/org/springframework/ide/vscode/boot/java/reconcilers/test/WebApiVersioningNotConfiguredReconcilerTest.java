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
import org.springframework.ide.vscode.boot.java.reconcilers.ReconcilingIndex;
import org.springframework.ide.vscode.boot.java.reconcilers.WebApiVersioningReconciler;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement.ConfigType;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;

public class WebApiVersioningNotConfiguredReconcilerTest extends BaseReconcilerTest {

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
		return new WebApiVersioningReconciler(null);
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
	void controllerUsesVersioningWithoutVersioningConfigured() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.web.bind.annotation.RestController;
				import org.springframework.web.bind.annotation.RequestMapping;
				
				@RestController
				@RequestMapping(path = "mypath", version = "1")
				public class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			return new WebApiVersioningReconciler(springIndex);
		}, "A.java", source, false);
		
		assertEquals(1, problems.size());
		
		ReconcileProblem problem = problems.get(0);
		
		assertEquals(Boot4JavaProblemType.API_VERSIONING_NOT_CONFIGURED, problem.getType());
		
		String markedStr = source.substring(problem.getOffset(), problem.getOffset() + problem.getLength());
		assertEquals("version = \"1\"", markedStr);

		assertEquals(0, problem.getQuickfixes().size());
	}

	@Test
	void controllerUsesVersioningWithVersioningConfigured() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.web.bind.annotation.RestController;
				import org.springframework.web.bind.annotation.RequestMapping;
				
				@RestController
				@RequestMapping(path = "mypath", version = "1")
				public class A {
				}
				""";
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			
			WebConfigIndexElement webConfig = new WebConfigIndexElement.Builder(ConfigType.WEB_CONFIG)
					.versionStrategy("version-strategy-configured", new Range(new Position(1, 1), new Position(1, 4))).buildFor(null);
			springIndex.updateElements(getProjectName(), "soneURI", new SpringIndexElement[] {webConfig});

			return new WebApiVersioningReconciler(springIndex);
		}, "A.java", source, false);
		
		assertEquals(0, problems.size());
	}

	@Test
	void controllerUsesVersioningWithVersioningConfiguredViaProperties() throws Exception {
		String source = """
				package example.demo;
				
				import org.springframework.web.bind.annotation.RestController;
				import org.springframework.web.bind.annotation.RequestMapping;
				
				@RestController
				@RequestMapping(path = "mypath", version = "1")
				public class A {
				}
				""";
		
		WebConfigIndexElement webConfig = new WebConfigIndexElement.Builder(ConfigType.WEB_CONFIG)
				.versionStrategy("version-strategy-configured", new Range(new Position(1, 1), new Position(1, 4))).buildFor(null);

		ReconcilingIndex reconcilingIndex = new ReconcilingIndex() {
			@Override
			public List<WebConfigIndexElement> getWebConfigProperties(IJavaProject project) {
				return List.of(webConfig);
			}
		};
		
		List<ReconcileProblem> problems = reconcile(() -> {
			SpringMetamodelIndex springIndex = new SpringMetamodelIndex();
			return new WebApiVersioningReconciler(springIndex);
		}, "A.java", source, false, reconcilingIndex);
		
		assertEquals(0, problems.size());
	}

}
