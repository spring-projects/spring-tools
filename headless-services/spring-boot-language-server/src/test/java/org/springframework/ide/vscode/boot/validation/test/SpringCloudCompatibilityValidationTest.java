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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.app.RestTemplateFactory;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.HoverTestConf;
import org.springframework.ide.vscode.boot.validation.generations.SpringCloudCompatibilityValidator;
import org.springframework.ide.vscode.boot.validation.generations.SpringIoProjectsProvider;
import org.springframework.ide.vscode.boot.validation.generations.SpringProjectsProvider;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.reconcile.DiagnosticSeverityProvider;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(HoverTestConf.class)
public class SpringCloudCompatibilityValidationTest {
	
	@Autowired private BootLanguageServerHarness harness;
	@Autowired private RestTemplateFactory restTemplateFactory;
	@Autowired private BootJavaConfig config;
	@Autowired private DiagnosticSeverityProvider severityProvider;

	@Test
	void testIncompatibleSpringBootVersionWithSpringCloud() throws Exception {
        SpringProjectsProvider projectsProvider = new SpringIoProjectsProvider(config, restTemplateFactory, harness.getServer().getProgressService(), harness.getServer().getMessageService(), -1);
		IJavaProject javaProject = createMockJavaProject("4.3.0");
		
		SpringCloudCompatibilityValidator validator = new SpringCloudCompatibilityValidator(severityProvider, projectsProvider);
		Collection<Diagnostic> diagnostics = validator.validate(javaProject, Version.parse("4.0.1"));
		
		assertNotNull(diagnostics);
		assertEquals(1, diagnostics.size());
		Diagnostic diagnostic = diagnostics.iterator().next();
		assertEquals("Spring Cloud 2025.0.x is not compatible with Spring Boot 4.0.1. Supported Spring Boot versions: 3.5.x", diagnostic.getMessage());
		assertEquals(DiagnosticSeverity.Warning, diagnostic.getSeverity());
	}

	@Test
	void testCompatibleSpringBootVersionWithSpringCloud() throws Exception {
        SpringProjectsProvider projectsProvider = new SpringIoProjectsProvider(config, restTemplateFactory, harness.getServer().getProgressService(), harness.getServer().getMessageService(), -1);
		IJavaProject javaProject = createMockJavaProject("5.0.0");
		
		SpringCloudCompatibilityValidator validator = new SpringCloudCompatibilityValidator(severityProvider, projectsProvider);
		Collection<Diagnostic> diagnostics = validator.validate(javaProject, Version.parse("4.0.1"));
		
		// Assert - should have NO diagnostics when versions are compatible
		assertNotNull(diagnostics);
		assertEquals(0, diagnostics.size(), "No diagnostic should be created for compatible versions");
	}

	@Test
	void testNoSpringCloudDependency() throws Exception {
        SpringProjectsProvider projectsProvider = new SpringIoProjectsProvider(config, restTemplateFactory, harness.getServer().getProgressService(), harness.getServer().getMessageService(), -1);
		IJavaProject javaProject = createMockJavaProject(null);
		
		SpringCloudCompatibilityValidator validator = new SpringCloudCompatibilityValidator(severityProvider, projectsProvider);
		Collection<Diagnostic> diagnostics = validator.validate(javaProject, Version.parse("4.0.1"));

		// Assert - should have no diagnostics when Spring Cloud is not present
		assertNotNull(diagnostics);
		assertEquals(0, diagnostics.size(), "No diagnostic should be created when Spring Cloud is not used");
	}

	private IJavaProject createMockJavaProject(String cloudCommonsVersion) throws Exception {
		IJavaProject project = mock(IJavaProject.class);
		IClasspath classpath = mock(IClasspath.class);
		when(project.getClasspath()).thenReturn(classpath);
		
		List<CPE> classpathEntries = new java.util.ArrayList<>();
		
		// Mock finding Spring Cloud dependency
		// Note: SpringProjectUtil.getDependencyVersion looks for "spring-cloud" in the path
		if (cloudCommonsVersion != null) {
			CPE cloudCpe = createCPE("binary", "spring-cloud-commons", "/path/to/.m2/repository/org/springframework/cloud/spring-cloud-commons/" + cloudCommonsVersion + "/spring-cloud-commons-" + cloudCommonsVersion + ".jar", cloudCommonsVersion);
			classpathEntries.add(cloudCpe);
			when(classpath.findBinaryLibraryByName("spring-cloud-commons")).thenReturn(Optional.of(cloudCpe));
		}
		else {
			when(classpath.findBinaryLibraryByName("spring-cloud-commons")).thenReturn(Optional.empty());
		}
		
		when(classpath.getClasspathEntries()).thenReturn(classpathEntries);
		
		return project;
	}

	private CPE createCPE(String kind, String name, String path, String version) {
		CPE cpe = mock(CPE.class);
		when(cpe.getKind()).thenReturn(kind);
		when(cpe.getPath()).thenReturn(path);
		when(cpe.getVersion()).thenReturn(Version.parse(version));
		when(cpe.isSystem()).thenReturn(false);
		when(cpe.getName()).thenReturn(name);
		return cpe;
	}

}
