/*******************************************************************************
 * Copyright (c) 2022, 2023 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.rewrite;

//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertNotNull;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//
//import java.time.Duration;
//
//import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
//import org.junit.jupiter.api.Test;
//import org.openrewrite.Recipe;
//import org.openrewrite.config.DeclarativeRecipe;
//import org.openrewrite.config.Environment;
//import org.openrewrite.config.RecipeDescriptor;
//import org.openrewrite.java.dependencies.UpgradeDependencyVersion;
//import org.springframework.ide.vscode.commons.rewrite.LoadUtils.DurationTypeConverter;
//
//import com.google.gson.Gson;
//import com.google.gson.GsonBuilder;

@Disabled
public class LoadUtilsTest {
	
//	private static Environment env;
//	
//	private static Gson serializationGson = new GsonBuilder()
//			.registerTypeAdapter(Duration.class, new DurationTypeConverter())
//			.create();
//	
//	@BeforeAll
//	public static void setupAll() {
//		env = Environment.builder().scanRuntimeClasspath().build();
//	}
//	
//	@SuppressWarnings("unchecked")
//	@Test
//	public void createRecipeTest() throws Exception {
//		RecipeDescriptor recipeDescriptor = env.listRecipeDescriptors().stream().filter(d -> "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_0".equals(d.getName())).findFirst().orElse(null);		
//		assertNotNull(recipeDescriptor);
//		
//		Recipe r = LoadUtils.createRecipe(recipeDescriptor, id -> {
//			try {
//				return (Class<Recipe>) Class.forName(id);
//			} catch (ClassNotFoundException e) {
//				return null;
//			}
//		});
//		
//		assertTrue(r instanceof DeclarativeRecipe);
//		assertEquals("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_0", r.getName());
//		assertTrue(r.getDescription().startsWith("Migrate applications to the latest Spring Boot 3.0 release."));
//		assertEquals("Migrate to Spring Boot 3.0", r.getDisplayName());
//		assertTrue(r.getRecipeList().size() >= 12);
//		
////		Recipe pomRecipe = r.getRecipeList().get(2);
////		assertTrue(pomRecipe instanceof DeclarativeRecipe);
////		assertEquals("org.openrewrite.java.spring.boot3.MavenPomUpgrade", pomRecipe.getName());
////		assertEquals("Upgrade Maven POM to Spring Boot 3.0 from prior 2.x version.", pomRecipe.getDescription());
////		assertEquals("Upgrade Maven POM to Spring Boot 3.0 from 2.x", pomRecipe.getDisplayName());
////		assertTrue(pomRecipe.getRecipeList().size() >= 3);
//		
//		UpgradeDependencyVersion upgradeDependencyRecipe = r.getRecipeList().stream().filter(UpgradeDependencyVersion.class::isInstance).map(UpgradeDependencyVersion.class::cast).findFirst().get();
//		assertEquals("org.openrewrite.java.dependencies.UpgradeDependencyVersion", upgradeDependencyRecipe.getName());
//		assertEquals("Upgrade Gradle or Maven dependency versions", upgradeDependencyRecipe.getDisplayName());
//		assertTrue(upgradeDependencyRecipe.getNewVersion().startsWith("3.0."));
//		assertEquals("org.springframework.boot", upgradeDependencyRecipe.getGroupId());
//		assertEquals("*", upgradeDependencyRecipe.getArtifactId());
//	}
//	
//	@SuppressWarnings("unchecked")
//	public void deserializeFromJson() throws Exception {
//		Recipe r = env.listRecipes().stream().filter(d -> "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_0".equals(d.getName())).findFirst().orElse(null);
//		RecipeDescriptor recipeDescriptor = r.getDescriptor();		
//		assertNotNull(recipeDescriptor);
//		
//		String json = serializationGson.toJson(recipeDescriptor);
//		RecipeDescriptor deserialized = serializationGson.fromJson(json, RecipeDescriptor.class);
//		assertNotNull(deserialized);
//		
//		r = LoadUtils.createRecipe(deserialized, id -> {
//			try {
//				return (Class<Recipe>) Class.forName(id);
//			} catch (ClassNotFoundException e) {
//				return null;
//			}
//		});
//		
//		assertTrue(r instanceof DeclarativeRecipe);
//		assertEquals("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_0", r.getName());
//		assertEquals(
//				"Migrate applications to the latest Spring Boot 3.0 release. This recipe will modify an application's build files, make changes to deprecated/preferred APIs, and migrate configuration settings that have changes between versions. This recipe will also chain additional framework migrations (Spring Framework, Spring Data, etc) that are required as part of the migration to Spring Boot 2.7.\n"
//				+ ""
//						+ "",
//				r.getDescription());
//		assertEquals("Migrate to Spring Boot 3.0", r.getDisplayName());
//		assertEquals(9, r.getRecipeList().size());
//		
//		Recipe pomRecipe = r.getRecipeList().get(0);
//		assertTrue(pomRecipe instanceof DeclarativeRecipe);
//		assertEquals("org.openrewrite.java.dependencies.UpgradeDependencyVersion", pomRecipe.getName());
//		assertEquals("Upgrade Maven POM to Spring Boot 3.0 from prior 2.x version.", pomRecipe.getDescription());
//		assertEquals("Upgrade Maven POM to Spring Boot 3.0 from 2.x", pomRecipe.getDisplayName());
//		assertTrue(pomRecipe.getRecipeList().size() >= 3);
//		
//		UpgradeDependencyVersion upgradeDependencyRecipe = pomRecipe.getRecipeList().stream().filter(UpgradeDependencyVersion.class::isInstance).map(UpgradeDependencyVersion.class::cast).findFirst().get();
//		assertEquals("org.openrewrite.maven.UpgradeDependencyVersion", upgradeDependencyRecipe.getName());
//		assertEquals("Upgrade Maven dependency version", upgradeDependencyRecipe.getDisplayName());
//		assertEquals(0, upgradeDependencyRecipe.getRecipeList().size());
//		assertTrue(upgradeDependencyRecipe.getNewVersion().startsWith("3.0."));
//		assertEquals("org.springframework.boot", upgradeDependencyRecipe.getGroupId());
//		assertEquals("*", upgradeDependencyRecipe.getArtifactId());
//	}
}
