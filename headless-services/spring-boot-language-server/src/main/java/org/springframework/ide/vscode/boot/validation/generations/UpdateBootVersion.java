/*******************************************************************************
 * Copyright (c) 2023, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.validation.generations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.app.BootLanguageServerInitializer;
import org.springframework.ide.vscode.boot.java.rewrite.SpringBootPatchUpgrade;
import org.springframework.ide.vscode.boot.validation.generations.json.Generation;
import org.springframework.ide.vscode.boot.validation.generations.json.ResolvedSpringProject;
import org.springframework.ide.vscode.boot.validation.generations.preferences.VersionValidationProblemType;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.reconcile.DiagnosticSeverityProvider;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;

import com.google.common.collect.ImmutableList;

public class UpdateBootVersion extends AbstractDiagnosticValidator {
	
	private static final Logger log = LoggerFactory.getLogger(UpdateBootVersion.class);
	
	private static final String RELEASE_NOTES_URL_PREFIX = "https://github.com/spring-projects/spring-boot/releases/tag/v";

	private Optional<SpringBootPatchUpgrade> bootUpgradeOpt;

	private SpringProjectsProvider springProjectsProvider;
	private MavenMetadataProvider mavenMetadataProvider;
	private BootJavaConfig bootJavaConfig;

	public UpdateBootVersion(DiagnosticSeverityProvider diagnosticSeverityProvider, Optional<SpringBootPatchUpgrade> bootUpgradeOpt, SpringProjectsProvider springProjectsProvider, MavenMetadataProvider mavenMetadataProvider, BootJavaConfig bootJavaConfig) {
		super(diagnosticSeverityProvider);
		this.bootUpgradeOpt = bootUpgradeOpt;
		this.springProjectsProvider = springProjectsProvider;
		this.mavenMetadataProvider = mavenMetadataProvider;
		this.bootJavaConfig = bootJavaConfig;
	}

	@Override
	public Collection<Diagnostic> validate(IJavaProject javaProject, Version javaProjectVersion) throws Exception {
		long start = System.currentTimeMillis();
		try {
			SortedVersions versions = null;
			Map<String, Version> patchCandidatesByType;

			if (bootJavaConfig.isUseProjectBuildFileForVersionValidation() && ProjectBuild.MAVEN_PROJECT_TYPE.equals(javaProject.getProjectBuild().getType())) {
				try {
					MavenMetadata metadata = mavenMetadataProvider.getMetadata(javaProject, "org.springframework.boot", "spring-boot");
					if (metadata != null) {
						versions = metadata.getReleaseVersions();
					}
				} catch (Exception e) {
					// Logged in provider, fallback will happen below
				}
			}

			Optional<Generation> currentGeneration = Optional.empty();
			if (versions == null) {
				ResolvedSpringProject bootProject = springProjectsProvider.getProject(SpringProjectUtil.SPRING_BOOT);
				versions = new SortedVersions(bootProject.getLatestPatchVersions());
				currentGeneration = bootProject.findGeneration(javaProjectVersion);
			}

			if (currentGeneration.isPresent()) {
				patchCandidatesByType = currentGeneration.get().getLatestPatchByType();
			} else {
				patchCandidatesByType = versions.getNewerLatestPatchRelease(javaProjectVersion)
						.map(latest -> Map.of("", latest))
						.orElse(Map.of());
			}

			ImmutableList.Builder<Diagnostic> builder = ImmutableList.builder();

			versions.getNewerLatestMajorRelease(javaProjectVersion)
					.flatMap(latest -> validateMajorVersion(javaProject, javaProjectVersion, latest))
					.ifPresent(builder::add);

			versions.getNewerLatestMinorRelease(javaProjectVersion)
					.flatMap(latest -> validateMinorVersion(javaProject, javaProjectVersion, latest))
					.ifPresent(builder::add);

			validatePatchVersions(javaProject, javaProjectVersion, patchCandidatesByType)
					.ifPresent(builder::add);

			return builder.build();
		} finally {
			log.info("boot major/minor/patch version validation for `%s` took: %d".formatted(javaProject.getElementName(), System.currentTimeMillis() - start));
		}
	}
	
	private boolean canProvideQuickfix(IJavaProject jp) {
		return ProjectBuild.MAVEN_PROJECT_TYPE.equals(jp.getProjectBuild().getType());
	}
	
	private Optional<Diagnostic> validateMajorVersion(IJavaProject javaProject, Version javaProjectVersion, Version latest) {
		List<CodeAction> actions = new ArrayList<>(1);

		actions.add(openReleaseNotesCodeAction(latest));

		return Optional.ofNullable(createDiagnostic(actions, VersionValidationProblemType.UPDATE_LATEST_MAJOR_VERSION, "Newer major version of Spring Boot available: %s".formatted(latest.toString()).toString()));
	}

	private Optional<Diagnostic> validateMinorVersion(IJavaProject javaProject, Version javaProjectVersion, Version latest) {
		List<CodeAction> actions = new ArrayList<>(1);

		actions.add(openReleaseNotesCodeAction(latest));

		return Optional.ofNullable(createDiagnostic(actions, VersionValidationProblemType.UPDATE_LATEST_MINOR_VERSION, "Newer minor version of Spring Boot available: %s".formatted(latest.toString())));
	}

	/**
	 * One quickfix per candidate newer than the current version (e.g. oss + enterprise).
	 */
	private Optional<Diagnostic> validatePatchVersions(IJavaProject javaProject, Version javaProjectVersion, Map<String, Version> candidatesByType) {
		List<Map.Entry<String, Version>> newerCandidates = candidatesByType.entrySet().stream()
				.filter(e -> e.getValue().compareTo(javaProjectVersion) > 0)
				.sorted(Map.Entry.<String, Version>comparingByValue().reversed())
				.collect(Collectors.toList());

		if (newerCandidates.isEmpty()) {
			return Optional.empty();
		}

		List<CodeAction> actions = new ArrayList<>();
		for (Map.Entry<String, Version> candidate : newerCandidates) {
			String type = candidate.getKey();
			Version latest = candidate.getValue();
			String qualifier = type.isEmpty() ? "Maven dependency version changes only"
					: VersionValidationUtils.patchTypeLabel(type) + ", Maven dependency version changes only";

			if (canProvideQuickfix(javaProject)) {
				bootUpgradeOpt.map(bu -> {
					CodeAction c = new CodeAction();
					c.setKind(CodeActionKind.QuickFix);
					c.setTitle("Upgrade to Spring Boot " + latest.toString() + " (" + qualifier + ")");
					String commandId = SpringBootPatchUpgrade.CMD_UPGRADE_SPRING_BOOT_PATCH;
					c.setCommand(new Command("Upgrade to Version " + latest.toString(), commandId,
							ImmutableList.of(javaProject.getLocationUri().toASCIIString(), latest.toString(), false)));
					return c;
				}).ifPresent(actions::add);
			}

			// Release notes for commercial-only patches aren't publicly published, so skip those.
			if (!"enterprise".equals(type)) {
				actions.add(openReleaseNotesCodeAction(latest));
			}
		}

		String message = newerCandidates.size() == 1 && newerCandidates.get(0).getKey().isEmpty()
				? "Newer patch version of Spring Boot available: %s".formatted(newerCandidates.get(0).getValue())
				: newerCandidates.stream()
						.map(e -> "%s (%s)".formatted(e.getValue(), VersionValidationUtils.patchTypeLabel(e.getKey())))
						.collect(Collectors.joining(", ", "Newer patch version of Spring Boot available: ", ""));

		return Optional.ofNullable(createDiagnostic(actions, VersionValidationProblemType.UPDATE_LATEST_PATCH_VERSION, message));
	}

	private static CodeAction openReleaseNotesCodeAction(Version version) {
		CodeAction releaseNoteLink = new CodeAction();
		releaseNoteLink.setKind(CodeActionKind.QuickFix);
		releaseNoteLink.setTitle("Open Release Notes for Spring Boot " + version.toString());
		ShowDocumentParams showDocumentParams = new ShowDocumentParams(RELEASE_NOTES_URL_PREFIX + version.toString());
		showDocumentParams.setExternal(true);
		showDocumentParams.setTakeFocus(true);
		showDocumentParams.setSelection(new Range());
		releaseNoteLink.setCommand(new Command("Release Notes for Spring Boot " + version.toString(), BootLanguageServerInitializer.CMD_SHOW_DOC,
				ImmutableList.of(showDocumentParams)));
		return releaseNoteLink;
	}

	@Override
	public boolean isEnabled() {
		return isEnabled(
				VersionValidationProblemType.UPDATE_LATEST_PATCH_VERSION,
				VersionValidationProblemType.UPDATE_LATEST_MINOR_VERSION,
				VersionValidationProblemType.UPDATE_LATEST_MAJOR_VERSION
		);
	}

}