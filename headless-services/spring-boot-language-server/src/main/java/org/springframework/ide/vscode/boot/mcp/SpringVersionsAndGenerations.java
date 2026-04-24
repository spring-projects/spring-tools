/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom
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

	@Tool(description = """
			Returns the latest general-availability (current) release for a Spring portfolio project plus OSS and commercial support end dates for that generation.
			Data is resolved from the language server's Spring projects metadata (same source used for validations), not a live per-call HAL dump.
			Use getReleases when you need every release record; use getGenerations when you need all generation windows from the live Spring IO API; use getUpcomingReleases for scheduled upcoming releases from the calendar API.
			""")
	public ReleaseInformation getLatestReleaseInformation(
			@ToolParam(description = """
					Spring project technical id, e.g. "spring-boot", "spring-framework", "spring-data-jpa" (not the IDE workspace project name).
					""")
			String projectName) {
		
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
