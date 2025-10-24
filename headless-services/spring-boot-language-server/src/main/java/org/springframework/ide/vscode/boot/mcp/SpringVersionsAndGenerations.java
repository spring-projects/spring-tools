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
package org.springframework.ide.vscode.boot.mcp;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ide.vscode.boot.validation.generations.SpringProjectsProvider;
import org.springframework.ide.vscode.boot.validation.generations.json.Generation;
import org.springframework.ide.vscode.boot.validation.generations.json.Release;
import org.springframework.ide.vscode.boot.validation.generations.json.Release.Status;
import org.springframework.ide.vscode.boot.validation.generations.json.ResolvedSpringProject;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.stereotype.Component;

/**
 * @author Martin Lippert
 */
@Component
public class SpringVersionsAndGenerations {

	private static final Logger log = LoggerFactory.getLogger(SpringVersionsAndGenerations.class);

	private final SpringProjectsProvider provider;

	public SpringVersionsAndGenerations(SpringProjectsProvider provider) {
		this.provider = provider;
	}

	public record ReleaseInformation(String projectName, String version, String endOfOssSupport, String endOfCommercialSupport) {}

	@Tool(description = "This function provides information about the latest release of a specific Spring project, including the version number and release date")
	public ReleaseInformation getLatestReleaseInformation(
			@ToolParam(description = "the technical name of the Spring project, e.g. \"spring-boot\" for Spring Boot or \"spring-data-jpa\" for Spring Data JPA") String projectName) {
		
		try {
			ResolvedSpringProject info = provider.getProject(projectName);
			
			if (info != null) {
			
				List<Release> releasesDetails = info.getReleasesDetails();
				
				Optional<Release> latestRelease = releasesDetails.stream()
					.filter(release -> Status.GENERAL_AVAILABILITY.equals(release.getStatus()))
					.filter(release -> release.isCurrent())
					.findAny();
				
				if (latestRelease.isPresent()) {
	
					List<Generation> generations = info.getGenerations();
					Generation generation = findCorrespondingGeneration(generations, latestRelease.get().getVersion());
					
					if (generation != null) {
						return new ReleaseInformation(
								projectName,
								latestRelease.get().getVersion().toString(),
								generation.getOssSupportEndDate(),
								generation.getCommercialSupportEndDate()
						);
					}
				}
			}
		}
		catch (Exception e) {
			log.error("error finding project release information for project: " + projectName, e);
		}
		
		return null;
	}

	private Generation findCorrespondingGeneration(List<Generation> generations, Version version) throws Exception {
		for (Generation gen : generations) {
			Version genVersion = SpringProjectUtil.getVersionFromGeneration(gen.getName());

			if (genVersion.getMajor() == version.getMajor()
					&& genVersion.getMinor() == version.getMinor()) {
				return gen;
			}
		}
		return null;
	}

}
