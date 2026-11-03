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
package org.springframework.ide.vscode.boot.validation.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.validation.generations.GenerationsValidator;
import org.springframework.ide.vscode.boot.validation.generations.SpringCloudCompatibilityValidator;
import org.springframework.ide.vscode.boot.validation.generations.SpringProjectsProvider;
import org.springframework.ide.vscode.boot.validation.generations.UpdateBootVersion;
import org.springframework.ide.vscode.boot.validation.generations.json.Generation;
import org.springframework.ide.vscode.boot.validation.generations.json.ResolvedSpringProject;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.reconcile.DiagnosticSeverityProvider;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;

/**
 * Verifies that the version validators cope with a {@link SpringProjectsProvider}
 * that returns <code>null</code> (e.g. spring.io unavailable or project unknown).
 */
class NullProjectsProviderValidationTest {

	private final SpringProjectsProvider nullProvider = slug -> null;
	private final DiagnosticSeverityProvider severityProvider = mock(DiagnosticSeverityProvider.class);

	@Test
	void generationsValidatorHandlesNullBootProject() throws Exception {
		GenerationsValidator validator = new GenerationsValidator(severityProvider, nullProvider);

		Collection<Diagnostic> diagnostics = validator.validate(mock(IJavaProject.class), Version.parse("3.5.0"));

		assertNotNull(diagnostics);
		assertTrue(diagnostics.isEmpty());
	}

	@Test
	void updateBootVersionHandlesNullBootProject() throws Exception {
		BootJavaConfig config = mock(BootJavaConfig.class);
		when(config.isUseProjectBuildFileForVersionValidation()).thenReturn(false);
		UpdateBootVersion validator = new UpdateBootVersion(severityProvider, Optional.empty(), nullProvider, null, config);

		Collection<Diagnostic> diagnostics = validator.validate(mock(IJavaProject.class), Version.parse("3.5.0"));

		assertNotNull(diagnostics);
		assertTrue(diagnostics.isEmpty());
	}

	@Test
	void springCloudValidatorHandlesNullProviderResult() throws Exception {
		SpringCloudCompatibilityValidator validator = new SpringCloudCompatibilityValidator(severityProvider, nullProvider);

		Collection<Diagnostic> diagnostics = validator.validate(projectWithCloudCommons("4.3.0"), Version.parse("3.5.0"));

		assertNotNull(diagnostics);
		assertTrue(diagnostics.isEmpty());
	}

	@Test
	void springCloudValidatorHandlesMissingSpringCloudProject() throws Exception {
		// spring-cloud-commons is known and links to a spring-cloud generation, but the
		// "spring-cloud" project itself is not available
		Generation commonsGeneration = new Generation();
		commonsGeneration.setName("4.3.x");
		commonsGeneration.setLinkedGenerations(Map.of("spring-cloud", new String[] { "2025.0.x" }));

		ResolvedSpringProject commons = mock(ResolvedSpringProject.class);
		when(commons.getGenerations()).thenReturn(List.of(commonsGeneration));

		SpringProjectsProvider provider = slug -> "spring-cloud-commons".equals(slug) ? commons : null;
		SpringCloudCompatibilityValidator validator = new SpringCloudCompatibilityValidator(severityProvider, provider);

		Collection<Diagnostic> diagnostics = validator.validate(projectWithCloudCommons("4.3.0"), Version.parse("3.5.0"));

		assertNotNull(diagnostics);
		assertTrue(diagnostics.isEmpty());
	}

	private IJavaProject projectWithCloudCommons(String version) {
		CPE cpe = mock(CPE.class);
		when(cpe.getVersion()).thenReturn(version);

		IClasspath classpath = mock(IClasspath.class);
		when(classpath.findBinaryLibraryByName("spring-cloud-commons")).thenReturn(Optional.of(cpe));

		IJavaProject project = mock(IJavaProject.class);
		when(project.getClasspath()).thenReturn(classpath);
		return project;
	}

}
