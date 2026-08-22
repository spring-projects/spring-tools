/*******************************************************************************
 * Copyright (c) 2022, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.rewrite;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.openrewrite.Recipe;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;
import org.springframework.ide.vscode.commons.rewrite.maven.LightUpgradeDependencyVersion;
import org.springframework.ide.vscode.commons.util.Assert;

import com.google.gson.JsonElement;

/**
 * LSP command wiring for {@link LightUpgradeDependencyVersion}: bumps a Spring Boot
 * project to a newer patch version (same major.minor), applied via
 * {@link RewriteRecipeRepository#applyToBuildFiles(Recipe, String, String, boolean)} -
 * which, for this recipe, parses the project's pom.xml as plain XML rather than as an
 * OpenRewrite Maven model, so there's no ancestor/BOM-import resolution and no network.
 */
public class SpringBootPatchUpgrade {

	public static final String CMD_UPGRADE_SPRING_BOOT_PATCH = "sts/upgrade/spring-boot-patch";

	public SpringBootPatchUpgrade(SimpleLanguageServer server, RewriteRecipeRepository recipeRepo, JavaProjectFinder projectFinder) {
		server.onCommand(CMD_UPGRADE_SPRING_BOOT_PATCH, params -> {
            List<?> args = params.getArguments();
            if (args == null || args.size() < 2) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("Command requires project URI and target Spring Boot version"));
            }
			String uri = ((JsonElement) args.get(0)).getAsString();
			Assert.isLegal(uri != null, "Project URI parameter must not be 'null'");
			Version targetVersion = Version.parse(((JsonElement) args.get(1)).getAsString());
			Assert.isLegal(targetVersion != null, "Target Spring Boot version must not be 'null'");
            boolean askForPreview = args.size() > 2 ? ((JsonElement) args.get(2)).getAsBoolean() : false;

            IJavaProject project = projectFinder.find(new TextDocumentIdentifier(uri)).orElse(null);
			Assert.isLegal(project != null, "No Spring Boot project found for uri: " + uri);

			Assert.isLegal(ProjectBuild.MAVEN_PROJECT_TYPE.equals(project.getProjectBuild().getType()), "Only Maven projects supported");

			Version version = SpringProjectUtil.getDependencyVersionByName(project, SpringProjectUtil.SPRING_BOOT);

			Assert.isLegal(
					version.compareTo(targetVersion) < 0,
					"Cannot upgrade Spring Boot Project '" + project.getElementName() + "' because its version '"
							+ version.toMajorMinorVersionStr() + "' is newer or same as the target version '"
							+ targetVersion.toMajorMinorVersionStr() + "'");

			Assert.isLegal(
					version.getMajor() == targetVersion.getMajor() && version.getMinor() == targetVersion.getMinor(),
					"Non patch version upgrades not supported!");

			Recipe recipe = new LightUpgradeDependencyVersion("org.springframework.boot", "*", targetVersion.toString());
			return recipeRepo.applyToBuildFiles(recipe, uri, UUID.randomUUID().toString(), askForPreview);
		});
	}

}
