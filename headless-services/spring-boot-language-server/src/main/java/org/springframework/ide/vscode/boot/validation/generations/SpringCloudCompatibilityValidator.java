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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.Diagnostic;
import org.springframework.ide.vscode.boot.validation.generations.json.Generation;
import org.springframework.ide.vscode.boot.validation.generations.json.ResolvedSpringProject;
import org.springframework.ide.vscode.boot.validation.generations.preferences.VersionValidationProblemType;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.reconcile.DiagnosticSeverityProvider;

import com.google.common.collect.ImmutableList;

/**
 * Validator that checks whether the Spring Boot version used in a project
 * is compatible with the Spring Cloud version used in the same project.
 * 
 * @author Martin Lippert
 */
public class SpringCloudCompatibilityValidator extends AbstractDiagnosticValidator {

	// use linked hash map to maintain a specific order of the entries, so that
	// "spring-cloud-commons" is always used first
	// since "spring-could-commons" is the most common case
	private static final Map<String, String> PROJECTS_TO_IDENTIFICATION_ARTIFACT = Collections.unmodifiableMap(
			new LinkedHashMap<>(Map.ofEntries(
					Map.entry("spring-cloud-commons", "spring-cloud-commons"),
					Map.entry("spring-cloud-function", "spring-cloud-function-core"),
					Map.entry("spring-cloud-task", "spring-cloud-task-core")
			)));
	
	private SpringProjectsProvider provider;

	public SpringCloudCompatibilityValidator(DiagnosticSeverityProvider diagnosticSeverityProvider, SpringProjectsProvider provider) {
		super(diagnosticSeverityProvider);
		this.provider = provider;
	}

	@Override
	public Collection<Diagnostic> validate(IJavaProject javaProject, Version bootVersion) throws Exception {
		ImmutableList.Builder<Diagnostic> b = ImmutableList.builder();
		
		Generation springCloudGeneration = identifySpringCloudVersion(javaProject);
		if (springCloudGeneration == null) {
			return b.build();
		}
		
		// Get the linked generations (which includes supported Spring Boot versions)
		Map<String, String[]> linkedGenerations = springCloudGeneration.getLinkedGenerations();
		if (linkedGenerations == null || !linkedGenerations.containsKey(SpringProjectUtil.SPRING_BOOT)) {
			return b.build();
		}
		
		String[] supportedBootGenerations = linkedGenerations.get(SpringProjectUtil.SPRING_BOOT);
		if (supportedBootGenerations == null || supportedBootGenerations.length == 0) {
			return b.build();
		}
		
		// Check if the current Spring Boot version matches any of the supported generations
		String bootGenerationName = bootVersion.getMajor() + "." + bootVersion.getMinor() + ".x";
		boolean isCompatible = Arrays.stream(supportedBootGenerations)
				.anyMatch(gen -> gen.equals(bootGenerationName));
		
		// Only create a diagnostic if the versions are incompatible
		if (!isCompatible) {
			StringBuilder message = new StringBuilder();
			message.append("Spring Cloud ");
			message.append(springCloudGeneration.getName());
			message.append(" is not compatible with Spring Boot ");
			message.append(bootVersion.toString());
			message.append(". Supported Spring Boot versions: ");
			message.append(String.join(", ", supportedBootGenerations));
			Diagnostic d = createDiagnostic(VersionValidationProblemType.SPRING_CLOUD_INCOMPATIBLE_BOOT_VERSION, message.toString());
			if (d != null) {
				b.add(d);
			}
		}
		
		return b.build();
	}
	
	private Generation identifySpringCloudVersion(IJavaProject javaProject) throws Exception {
		Generation generation = getSpringCloudGeneration(javaProject);
		if (generation == null) {
			return null;
		}
		
		String[] linkedCloudVersions = generation.getLinkedGenerations().get("spring-cloud");
		if (linkedCloudVersions == null || linkedCloudVersions.length > 1) {
			return null;
		}
		
		String springCloudGeneration = linkedCloudVersions[0];
		ResolvedSpringProject cloudProject = provider.getProject("spring-cloud");
		List<Generation> cloudGenerations = cloudProject.getGenerations();
		for (Generation cloudGeneration : cloudGenerations) {
			if (cloudGeneration.getName().equals(springCloudGeneration)) {
				return cloudGeneration;
			}
		}
		
		return null;
	}

	private Generation getSpringCloudGeneration(IJavaProject javaProject) throws Exception {
		for (String cloudProject : PROJECTS_TO_IDENTIFICATION_ARTIFACT.keySet()) {
			ResolvedSpringProject project = provider.getProject(cloudProject);
			if (project != null) {
				Generation generation = GenerationsValidator.getGenerationForJavaProject(javaProject, project, PROJECTS_TO_IDENTIFICATION_ARTIFACT.get(cloudProject));
				if (generation != null) {
					return generation;
				}
			}
		}
		
		return null;
	}

	@Override
	public boolean isEnabled() {
		return isEnabled(
				VersionValidationProblemType.SPRING_CLOUD_INCOMPATIBLE_BOOT_VERSION
		);
	}

}
