/*******************************************************************************
 * Copyright (c) 2023, 2025 VMware, Inc.
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
import java.util.Optional;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.springframework.ide.vscode.boot.app.BootLanguageServerInitializer;
import org.springframework.ide.vscode.boot.java.rewrite.SpringBootUpgrade;
import org.springframework.ide.vscode.boot.validation.generations.preferences.VersionValidationProblemType;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.reconcile.DiagnosticSeverityProvider;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;

import com.google.common.collect.ImmutableList;

public class UpdateBootVersion extends AbstractDiagnosticValidator {
	
	private static final String RELEASE_NOTES_URL_PREFIX = "https://github.com/spring-projects/spring-boot/releases/tag/v";

	private Optional<SpringBootUpgrade> bootUpgradeOpt;

	private SpringProjectsProvider springProjectsProvider;

	public UpdateBootVersion(DiagnosticSeverityProvider diagnosticSeverityProvider, Optional<SpringBootUpgrade> bootUpgradeOpt, SpringProjectsProvider springProjectsProvider) {
		super(diagnosticSeverityProvider);
		this.bootUpgradeOpt = bootUpgradeOpt;
		this.springProjectsProvider = springProjectsProvider;
	}

	@Override
	public Collection<Diagnostic> validate(IJavaProject javaProject, Version javaProjectVersion) throws Exception {
		List<Version> versions = springProjectsProvider.getProject(SpringProjectUtil.SPRING_BOOT).getReleases();
		ImmutableList.Builder<Diagnostic> builder = ImmutableList.builder();
		validateMajorVersion(javaProject, javaProjectVersion, versions).ifPresent(builder::add);
		validateMinorVersion(javaProject, javaProjectVersion, versions).ifPresent(builder::add);
		validatePatchVersion(javaProject, javaProjectVersion, versions).ifPresent(builder::add);
		return builder.build();
	}
	
	private boolean canProvideQuickfix(IJavaProject jp) {
		return ProjectBuild.MAVEN_PROJECT_TYPE.equals(jp.getProjectBuild().getType());
	}
	
	private Optional<Diagnostic> validateMajorVersion(IJavaProject javaProject, Version javaProjectVersion, List<Version> sortedBootVersions) {
		Version latest = VersionValidationUtils.getNewerLatestMajorRelease(sortedBootVersions, javaProjectVersion);

		if (latest != null) {
			VersionValidationProblemType problemType = VersionValidationProblemType.UPDATE_LATEST_MAJOR_VERSION;

			StringBuffer message = new StringBuffer();
			message.append("Newer major version of Spring Boot available: ");
			message.append(latest.toString());
			
			List<CodeAction> actions = new ArrayList<>(2);

			if (canProvideQuickfix(javaProject)) {
				bootUpgradeOpt.flatMap(bu -> bu.getNearestAvailableMinorVersion(latest)).map(targetVersion -> {
					Version upgradeVersion = Version.parse(targetVersion);
					if (javaProjectVersion.compareTo(upgradeVersion) >= 0) {
						return null;
					}
					CodeAction c = new CodeAction();
					c.setKind(CodeActionKind.QuickFix);
					c.setTitle("Upgrade to Spring Boot " + targetVersion + " (executes the full project conversion recipe from OpenRewrite)");
					String commandId = SpringBootUpgrade.CMD_UPGRADE_SPRING_BOOT;
					c.setCommand(new Command("Upgrade to Version " + targetVersion, commandId,
							ImmutableList.of(javaProject.getLocationUri().toASCIIString(), targetVersion, true)));
					return c;
				}).ifPresent(actions::add);
			}
			
			actions.add(openReleaseNotesCodeAction(latest));

			return Optional.ofNullable(createDiagnostic(actions, problemType, message.toString()));
		}
		return Optional.empty();
	}

	private Optional<Diagnostic> validateMinorVersion(IJavaProject javaProject, Version javaProjectVersion, List<Version> sortedBootVersions) {
		Version latest = VersionValidationUtils.getNewerLatestMinorRelease(sortedBootVersions, javaProjectVersion);

		if (latest != null) {
			VersionValidationProblemType problemType = VersionValidationProblemType.UPDATE_LATEST_MINOR_VERSION;

			StringBuffer message = new StringBuffer();
			message.append("Newer minor version of Spring Boot available: ");
			message.append(latest.toString());
			
			List<CodeAction> actions = new ArrayList<>(2);

			if (canProvideQuickfix(javaProject)) {
				bootUpgradeOpt.flatMap(bu -> bu.getNearestAvailableMinorVersion(latest)).map(targetVersion -> {
					Version upgradeVersion = Version.parse(targetVersion);
					if (javaProjectVersion.compareTo(upgradeVersion) >= 0) {
						return null;
					}
					CodeAction c = new CodeAction();
					c.setKind(CodeActionKind.QuickFix);
					c.setTitle("Upgrade to Spring Boot " + targetVersion + " (executes the full project conversion recipe from OpenRewrite)");
					String commandId = SpringBootUpgrade.CMD_UPGRADE_SPRING_BOOT;
					c.setCommand(new Command("Upgrade to Version " + targetVersion, commandId,
							ImmutableList.of(javaProject.getLocationUri().toASCIIString(), targetVersion, true)));
					return c;
				}).ifPresent(actions::add);
			}
						
			actions.add(openReleaseNotesCodeAction(latest));
			
			return Optional.ofNullable(createDiagnostic(actions, problemType, message.toString()));
		}
		return Optional.empty();
	}

	private Optional<Diagnostic> validatePatchVersion(IJavaProject javaProject, Version javaProjectVersion, List<Version> sortedBootVersions) {
		Version latest = VersionValidationUtils.getNewerLatestPatchRelease(sortedBootVersions, javaProjectVersion);

		if (latest != null) {
			VersionValidationProblemType problemType = VersionValidationProblemType.UPDATE_LATEST_PATCH_VERSION;

			StringBuffer message = new StringBuffer();
			message.append("Newer patch version of Spring Boot available: ");
			message.append(latest.toString());

			List<CodeAction> actions = new ArrayList<>(2);
			
			if (canProvideQuickfix(javaProject)) {
				bootUpgradeOpt.map(bu -> {
					CodeAction c = new CodeAction();
					c.setKind(CodeActionKind.QuickFix);
					c.setTitle("Upgrade to Spring Boot " + latest.toString() + " (Maven dependency version changes only)");
					String commandId = SpringBootUpgrade.CMD_UPGRADE_SPRING_BOOT;
					c.setCommand(new Command("Upgrade to Version " + latest.toString(), commandId,
							ImmutableList.of(javaProject.getLocationUri().toASCIIString(), latest.toString(), false)));
					return c;
				}).ifPresent(actions::add);
			}
			
			actions.add(openReleaseNotesCodeAction(latest));
			
			return Optional.ofNullable(createDiagnostic(actions, problemType, message.toString()));
		}
		return Optional.empty();
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