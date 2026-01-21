/*******************************************************************************
 * Copyright (c) 2022, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.rewrite;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.openrewrite.Recipe;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.maven.UpgradeParentVersion;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;
import org.springframework.ide.vscode.commons.util.Assert;

import com.google.gson.JsonElement;

public class SpringBootUpgrade {
	
	final public static String CMD_UPGRADE_SPRING_BOOT = "sts/upgrade/spring-boot";

	public SpringBootUpgrade(SimpleLanguageServer server, RewriteRecipeRepository recipeRepo, JavaProjectFinder projectFinder) {
		server.onCommand(CMD_UPGRADE_SPRING_BOOT, params -> {
			String uri = ((JsonElement) params.getArguments().get(0)).getAsString();
			Assert.isLegal(uri != null, "Project URI parameter must not be 'null'");
			Version targetVersion = Version.parse(((JsonElement) params.getArguments().get(1)).getAsString());
			Assert.isLegal(targetVersion != null, "Target Spring Boot version must not be 'null'");
			boolean askForPreview = params.getArguments().size() > 2 ? ((JsonElement) params.getArguments().get(2)).getAsBoolean() : false;
			
			IJavaProject project = projectFinder.find(new TextDocumentIdentifier(uri)).orElse(null);
			Assert.isLegal(project != null, "No Spring Boot project found for uri: " + uri);
			
			Assert.isLegal(ProjectBuild.MAVEN_PROJECT_TYPE.equals(project.getProjectBuild().getType()), "Only Maven projects supported");
			
			Version version = SpringProjectUtil.getDependencyVersionByName(project, SpringProjectUtil.SPRING_BOOT);
			
			// Version upgrade is not supposed to work for patch version. Only for the major and minor versions.
			
			Assert.isLegal(
					version.compareTo(targetVersion) < 0,
					"Cannot upgrade Spring Boot Project '" + project.getElementName() + "' because its version '"
							+ version.toMajorMinorVersionStr() + "' is newer or same as the target version '"
							+ targetVersion.toMajorMinorVersionStr() + "'");
			
			Assert.isLegal(
					version.getMajor() == targetVersion.getMajor() && version.getMinor() == targetVersion.getMinor(),
					"Non patch version upgrades not supported!");
			
			return recipeRepo.applyToBuildFiles(createUpgradeRecipe(version, targetVersion), uri, UUID.randomUUID().toString(), askForPreview);
		});
	}
	
	private Recipe createUpgradeRecipe(Version version, Version targetVersion) {
		Recipe recipe = new DeclarativeRecipe("upgrade-spring-boot", "Upgrade Spring Boot from " + version + " to " + targetVersion,
				"", Collections.emptySet(), null, null, false, Collections.emptyList());
		
		if (version.getMajor() == targetVersion.getMajor() && version.getMinor() == targetVersion.getMinor()) {
			// patch version upgrade - treat as pom versions only upgrade
			recipe.getRecipeList().add(new org.openrewrite.maven.UpgradeDependencyVersion("org.springframework.boot", "*", version.getMajor() + "." + version.getMinor() + ".x", null, null, null));
			recipe.getRecipeList().add(new UpgradeParentVersion("org.springframework.boot", "spring-boot-starter-parent", version.getMajor() + "." + version.getMinor() + ".x", null, null));
		}

		if (recipe.getRecipeList().isEmpty()) {
			throw new IllegalStateException("No upgrade recipes found!");
		} else if (recipe.getRecipeList().size() == 1) {
			return recipe.getRecipeList().get(0);
		} else {
			return recipe;
		}
	}
	
	public Optional<String> getNearestAvailableMinorVersion(Version v) {
		return Optional.empty();
	}
	
}
