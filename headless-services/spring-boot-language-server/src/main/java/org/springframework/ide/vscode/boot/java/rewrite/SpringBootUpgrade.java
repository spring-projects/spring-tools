/*******************************************************************************
 * Copyright (c) 2022, 2024 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.rewrite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.openrewrite.Recipe;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.maven.UpgradeDependencyVersion;
import org.openrewrite.maven.UpgradeParentVersion;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.util.Assert;

import com.google.gson.JsonElement;

public class SpringBootUpgrade {
	
	final public static String CMD_UPGRADE_SPRING_BOOT = "sts/upgrade/spring-boot";

	private final Map<String, String> versionsToRecipeId = new HashMap<>();
	{
		versionsToRecipeId.put("2.0", "org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_0");
		versionsToRecipeId.put("2.1", "org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_1");
		versionsToRecipeId.put("2.2", "org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_2");
		versionsToRecipeId.put("2.3", "org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_3");
		versionsToRecipeId.put("2.4", "org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_4");
		versionsToRecipeId.put("2.5", "org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_5");
		versionsToRecipeId.put("2.6", "org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_6");
		versionsToRecipeId.put("2.7", "org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_7");
		versionsToRecipeId.put("3.0", "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_0");
		versionsToRecipeId.put("3.1", "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_1");
		versionsToRecipeId.put("3.2", "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_2");
		versionsToRecipeId.put("3.3", "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_3");
		versionsToRecipeId.put("3.4", "org.springframework.ide.vscode.rewrite.boot3.UpgradeSpringBoot_3_4");
		versionsToRecipeId.put("3.5", "org.springframework.ide.vscode.rewrite.boot3.UpgradeSpringBoot_3_5");
	}
	
	public SpringBootUpgrade(SimpleLanguageServer server, RewriteRecipeRepository recipeRepo, JavaProjectFinder projectFinder) {
		server.onCommand(CMD_UPGRADE_SPRING_BOOT, params -> {
			String uri = ((JsonElement) params.getArguments().get(0)).getAsString();
			Assert.isLegal(uri != null, "Project URI parameter must not be 'null'");
			Version targetVersion = Version.parse(((JsonElement) params.getArguments().get(1)).getAsString());
			Assert.isLegal(targetVersion != null, "Target Spring Boot version must not be 'null'");
			boolean askForPreview = params.getArguments().size() > 2 ? ((JsonElement) params.getArguments().get(2)).getAsBoolean() : false;
			
			IJavaProject project = projectFinder.find(new TextDocumentIdentifier(uri)).orElse(null);
			Assert.isLegal(project != null, "No Spring Boot project found for uri: " + uri);
			
			Version version = SpringProjectUtil.getDependencyVersion(project, SpringProjectUtil.SPRING_BOOT);
			
			// Version upgrade is not supposed to work for patch version. Only for the major and minor versions.
			
			Assert.isLegal(
					version.compareTo(targetVersion) < 0,
					"Cannot upgrade Spring Boot Project '" + project.getElementName() + "' because its version '"
							+ version.toMajorMinorVersionStr() + "' is newer or same as the target version '"
							+ targetVersion.toMajorMinorVersionStr() + "'");
			
			return recipeRepo.recipes().thenComposeAsync(recipes -> recipeRepo.apply(
					createUpgradeRecipe(recipes, version, targetVersion),
					uri,
					UUID.randomUUID().toString(),
					askForPreview
			));
		});
	}
	
	static List<String> createRecipeIdsChain(int major, int minor, int targetMajor, int targetMinor, Map<String, String> versionToRecipeId) {
		List<String> ids = new ArrayList<>();
		for (int currentMajor = major, currentMinor = minor; targetMajor >  currentMajor || (targetMajor == currentMajor && currentMinor <= targetMinor);) {
			String recipeId = versionToRecipeId.get(createVersionString(currentMajor, currentMinor));
			if (recipeId == null) {
				currentMajor++;
				currentMinor = 0;
			} else {
				ids.add(recipeId);
				currentMinor++;
			}
		}
		return ids;
	}
	
	private Recipe createUpgradeRecipe(Map<String, Recipe> recipes, Version version, Version targetVersion) {
		Recipe recipe = new DeclarativeRecipe("upgrade-spring-boot", "Upgrade Spring Boot from " + version + " to " + targetVersion,
				"", Collections.emptySet(), null, null, false, Collections.emptyList());
		
		if (version.getMajor() == targetVersion.getMajor() && version.getMinor() == targetVersion.getMinor()) {
			// patch version upgrade - treat as pom versions only upgrade
			recipe.getRecipeList().add(new UpgradeDependencyVersion("org.springframework.boot", "*", version.getMajor() + "." + version.getMinor() + ".x", null, null, null));
			recipe.getRecipeList().add(new UpgradeParentVersion("org.springframework.boot", "spring-boot-starter-parent", version.getMajor() + "." + version.getMinor() + ".x", null, null));
		} else /*if (version.getMajor() == targetVersion.getMajor())*/ {
			List<String> recipedIds = createRecipeIdsChain(version.getMajor(), version.getMinor() + 1, targetVersion.getMajor(), targetVersion.getMinor(), versionsToRecipeId);
			if (!recipedIds.isEmpty()) {
				String recipeId = recipedIds.get(recipedIds.size() - 1);
				Optional.ofNullable(recipes.get(recipeId)).ifPresent(r -> recipe.getRecipeList().add(r));
			}
		}

		if (recipe.getRecipeList().isEmpty()) {
			throw new IllegalStateException("No upgrade recipes found!");
		} else if (recipe.getRecipeList().size() == 1) {
			return recipe.getRecipeList().get(0);
		} else {
			return recipe;
		}
	}
	
	private static String createVersionString(int major, int minor) {
		StringBuilder sb = new StringBuilder();
		sb.append(major);
		sb.append('.');
		sb.append(minor);
		return sb.toString();
	}
	
	static String nearestAvailableMinorVersion(Version v, Set<String> availableVersions) {
		for (int major = v.getMajor(), minor = v.getMinor(); minor >= 0; minor--) {
			String versionStr = createVersionString(major, minor);
			if (availableVersions.contains(versionStr)) {
				return versionStr;
			}
		}
		return null;
	}
	
	public Optional<String> getNearestAvailableMinorVersion(Version v) {
		return Optional.ofNullable(nearestAvailableMinorVersion(v, versionsToRecipeId.keySet()));
	}
	
}
