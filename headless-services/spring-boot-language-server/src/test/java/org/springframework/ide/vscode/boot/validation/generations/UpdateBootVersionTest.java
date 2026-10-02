/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.validation.generations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.java.rewrite.SpringBootPatchUpgrade;
import org.springframework.ide.vscode.boot.validation.generations.json.Generation;
import org.springframework.ide.vscode.boot.validation.generations.json.ResolvedSpringProject;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IProjectBuild;
import org.springframework.ide.vscode.commons.languageserver.reconcile.DiagnosticSeverityProvider;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;

/**
 * @author Broadcom
 */
public class UpdateBootVersionTest {

	@Test
	void offersSeparateQuickfixesForOssAndEnterprisePatches() throws Exception {
		Diagnostic diagnostic = validatePatch(Map.of(
				"oss", "3.4.12",
				"enterprise", "3.4.16"
		), "3.4.10");

		assertEquals("Newer patch version of Spring Boot available: 3.4.16 (Enterprise), 3.4.12 (OSS)",
				diagnostic.getMessage().getLeft());

		@SuppressWarnings("unchecked")
		List<CodeAction> actions = (List<CodeAction>) diagnostic.getData();
		assertEquals(3, actions.size());
		assertTrue(actions.get(0).getTitle().contains("3.4.16") && actions.get(0).getTitle().contains("Enterprise"));
		assertTrue(actions.get(1).getTitle().contains("3.4.12") && actions.get(1).getTitle().contains("OSS"));
		assertTrue(actions.get(2).getTitle().contains("Release Notes") && actions.get(2).getTitle().contains("3.4.12"));
	}

	@Test
	void ignoresEnterprisePatchNotNewerThanCurrentVersion() throws Exception {
		// enterprise patch is behind the project's current version - only the oss upgrade is offered
		Diagnostic diagnostic = validatePatch(Map.of(
				"oss", "3.4.12",
				"enterprise", "3.4.9"
		), "3.4.10");

		assertEquals("Newer patch version of Spring Boot available: 3.4.12 (OSS)", diagnostic.getMessage().getLeft());

		@SuppressWarnings("unchecked")
		List<CodeAction> actions = (List<CodeAction>) diagnostic.getData();
		assertEquals(2, actions.size());
		assertTrue(actions.get(0).getTitle().contains("3.4.12") && actions.get(0).getTitle().contains("OSS"));
		assertTrue(actions.get(1).getTitle().contains("Release Notes"));
	}

	@Test
	void noPatchDiagnosticWhenAlreadyOnLatestPatch() throws Exception {
		Generation generation = mock(Generation.class);
		when(generation.getLatestPatchByType()).thenReturn(toVersionMap(Map.of("oss", "3.4.10")));

		ResolvedSpringProject bootProject = mock(ResolvedSpringProject.class);
		when(bootProject.getLatestPatchVersions()).thenReturn(List.of(Version.parse("3.4.10")));
		when(bootProject.findGeneration(any())).thenReturn(Optional.of(generation));

		SpringProjectsProvider provider = mock(SpringProjectsProvider.class);
		when(provider.getProject(anyString())).thenReturn(bootProject);

		UpdateBootVersion validator = newValidator(provider);
		Collection<Diagnostic> diagnostics = validator.validate(mockMavenProject(), Version.parse("3.4.10"));

		assertEquals(0, diagnostics.size());
	}

	private Diagnostic validatePatch(Map<String, String> latestPatchByType, String currentVersion) throws Exception {
		Generation generation = mock(Generation.class);
		when(generation.getLatestPatchByType()).thenReturn(toVersionMap(latestPatchByType));

		ResolvedSpringProject bootProject = mock(ResolvedSpringProject.class);
		when(bootProject.getLatestPatchVersions()).thenReturn(List.of(Version.parse(currentVersion)));
		when(bootProject.findGeneration(any())).thenReturn(Optional.of(generation));

		SpringProjectsProvider provider = mock(SpringProjectsProvider.class);
		when(provider.getProject(anyString())).thenReturn(bootProject);

		UpdateBootVersion validator = newValidator(provider);
		Collection<Diagnostic> diagnostics = validator.validate(mockMavenProject(), Version.parse(currentVersion));

		assertEquals(1, diagnostics.size());
		return diagnostics.iterator().next();
	}

	private static Map<String, Version> toVersionMap(Map<String, String> raw) {
		Map<String, Version> result = new java.util.LinkedHashMap<>();
		raw.forEach((k, v) -> result.put(k, Version.parse(v)));
		return result;
	}

	private UpdateBootVersion newValidator(SpringProjectsProvider provider) {
		DiagnosticSeverityProvider severityProvider = mock(DiagnosticSeverityProvider.class);
		when(severityProvider.getDiagnosticSeverity(any(ProblemType.class))).thenReturn(DiagnosticSeverity.Warning);

		BootJavaConfig config = mock(BootJavaConfig.class);
		when(config.isUseProjectBuildFileForVersionValidation()).thenReturn(true);

		MavenMetadataProvider mavenMetadataProvider = mock(MavenMetadataProvider.class);
		// no Maven metadata available -> forces the generations-based fallback path

		Optional<SpringBootPatchUpgrade> bootUpgradeOpt = Optional.of(mock(SpringBootPatchUpgrade.class));

		return new UpdateBootVersion(severityProvider, bootUpgradeOpt, provider, mavenMetadataProvider, config);
	}

	private IJavaProject mockMavenProject() {
		IJavaProject jp = mock(IJavaProject.class);
		when(jp.getProjectBuild()).thenReturn(IProjectBuild.create(ProjectBuild.MAVEN_PROJECT_TYPE, null));
		when(jp.getElementName()).thenReturn("test-project");
		when(jp.getLocationUri()).thenReturn(java.net.URI.create("file:///test-project"));
		return jp;
	}

}
