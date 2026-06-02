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
import org.springframework.ide.vscode.boot.validation.generations.MavenMetadata;
import org.springframework.ide.vscode.boot.validation.generations.MavenMetadataProvider;
import org.springframework.ide.vscode.boot.validation.generations.SortedVersions;
import org.springframework.ide.vscode.boot.validation.generations.SpringProjectsProvider;
import org.springframework.ide.vscode.boot.validation.generations.json.Generation;
import org.springframework.ide.vscode.boot.validation.generations.json.Release;
import org.springframework.ide.vscode.boot.validation.generations.json.Release.Status;
import org.springframework.ide.vscode.boot.validation.generations.json.ResolvedSpringProject;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.stereotype.Component;

/**
 * @author Martin Lippert
 */
@Component
public class SpringVersionsAndGenerations {

	private static final Logger log = LoggerFactory.getLogger(SpringVersionsAndGenerations.class);

	private final SpringProjectsProvider provider;
	private final MavenMetadataProvider mavenMetadataProvider;
	private final JavaProjectFinder projectFinder;

	public SpringVersionsAndGenerations(SpringProjectsProvider provider, MavenMetadataProvider mavenMetadataProvider, JavaProjectFinder projectFinder) {
		this.provider = provider;
		this.mavenMetadataProvider = mavenMetadataProvider;
		this.projectFinder = projectFinder;
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

	public record BootVersionsFromMavenRepo(
			String currentVersion,
			String latestPatchVersion,
			String latestMinorVersion,
			String latestMajorVersion) {}

	@Tool(description = """
			Returns the latest available Spring Boot versions from the Maven repositories configured in the project's build file (pom.xml).
			Unlike getLatestReleaseInformation (which queries the Spring IO API), this reflects what is actually resolvable from the project's own configured Maven repositories, including private or enterprise repos.
			latestPatchVersion, latestMinorVersion, and latestMajorVersion are the newest available versions newer than currentVersion for each respective level (null means already up-to-date at that level).
			Only works for Maven (pom.xml) projects; returns null for Gradle projects or when Maven metadata cannot be fetched.
			""")
	public BootVersionsFromMavenRepo getLatestBootVersionsFromMavenRepo(
			@ToolParam(description = "IDE project name from getProjectList().projectName (case-insensitive match)")
			String projectName) {

		try {
			Optional<? extends IJavaProject> found = projectFinder.all().stream()
					.filter(p -> p.getElementName().equalsIgnoreCase(projectName))
					.findFirst();

			if (found.isEmpty()) {
				log.warn("project not found for Maven version lookup: {}", projectName);
				return null;
			}

			IJavaProject project = found.get();
			Version current = SpringProjectUtil.getSpringBootVersion(project);
			if (current == null) {
				log.warn("no Spring Boot version found in project: {}", projectName);
				return null;
			}

			MavenMetadata metadata = mavenMetadataProvider.getMetadata(project, "org.springframework.boot", "spring-boot");
			if (metadata == null) {
				return null;
			}

			SortedVersions versions = metadata.getReleaseVersions();
			return new BootVersionsFromMavenRepo(
					current.toString(),
					versions.getNewerLatestPatchRelease(current).map(Version::toString).orElse(null),
					versions.getNewerLatestMinorRelease(current).map(Version::toString).orElse(null),
					versions.getNewerLatestMajorRelease(current).map(Version::toString).orElse(null));
		}
		catch (Exception e) {
			log.error("error fetching Maven Boot versions for project: {}", projectName, e);
			return null;
		}
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
