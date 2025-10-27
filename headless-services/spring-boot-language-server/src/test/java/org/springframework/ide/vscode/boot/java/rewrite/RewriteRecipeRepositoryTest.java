/*******************************************************************************
 * Copyright (c) 2023 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.rewrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openrewrite.Recipe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.boot.java.rewrite.RewriteRecipeRepository.RecipeSelectionDescriptor;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
@Disabled
public class RewriteRecipeRepositoryTest {
	
	@Autowired RewriteRecipeRepository recipeRepo;
	
	@Test
	void listSubRecipe() throws Exception {
		List<Recipe> recipes = recipeRepo.getSubRecipes("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_1", List.of()).get();
		assertEquals(11, recipes.size());
		
		recipes = recipeRepo.getSubRecipes("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_1", List.of(0, 2)).get();
		assertEquals(22, recipes.size());
		
		recipes = recipeRepo.getSubRecipes("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_1",List.of(0, 345)).get();
		assertEquals(0, recipes.size());
			
		recipes = recipeRepo.getSubRecipes("Hohoho", List.of()).get();
		assertEquals(0, recipes.size());

	}
	
	@Test
	void listRootRecipes() throws Exception {
		List<Recipe> recipes = recipeRepo.getRootRecipes(r -> true).get();
		assertThat(recipes.size()).isGreaterThan(5);
		
		recipes = recipeRepo.getRootRecipes(r -> r.getName().startsWith("Hohoho")).get();
		assertEquals(0, recipes.size());
	}
	
	@Test
	void createRecipeFromSelectionDescriptor() throws Exception {
		RecipeSelectionDescriptor descriptor = new RecipeSelectionDescriptor(true, "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_1", new RecipeSelectionDescriptor[] {
				new RecipeSelectionDescriptor(true, "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_0", new RecipeSelectionDescriptor[] { // pick boot 3.0
						new RecipeSelectionDescriptor(true, "org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_7", null), // pick boot 2.7
						new RecipeSelectionDescriptor(false, "", null),
						new RecipeSelectionDescriptor(true, "org.openrewrite.java.migrate.UpgradeToJava17", new RecipeSelectionDescriptor[] { // pick Java 17
								new RecipeSelectionDescriptor(true, "", null), // java 11
								new RecipeSelectionDescriptor(true, "", null), // java 17
								new RecipeSelectionDescriptor(false, "", null),
								new RecipeSelectionDescriptor(false, "", null),
								new RecipeSelectionDescriptor(false, "", null),
								new RecipeSelectionDescriptor(false, "", null),
								new RecipeSelectionDescriptor(false, "", null),
								new RecipeSelectionDescriptor(true, "", null), // text blocks
								new RecipeSelectionDescriptor(false, "", null),
								new RecipeSelectionDescriptor(false, "", null),
								new RecipeSelectionDescriptor(false, "", null),
								new RecipeSelectionDescriptor(false, "", null),
								new RecipeSelectionDescriptor(false, "", null),
						}), 
						new RecipeSelectionDescriptor(false, "", null),
						new RecipeSelectionDescriptor(false, "", null),
						new RecipeSelectionDescriptor(false, "", null),
						new RecipeSelectionDescriptor(false, "", null),
						new RecipeSelectionDescriptor(false, "", null),
						new RecipeSelectionDescriptor(false, "", null),
						new RecipeSelectionDescriptor(false, "", null),
						new RecipeSelectionDescriptor(false, "", null),
						new RecipeSelectionDescriptor(false, "", null),
						new RecipeSelectionDescriptor(false, "", null),
						new RecipeSelectionDescriptor(false, "", null),
						new RecipeSelectionDescriptor(false, "", null),
						new RecipeSelectionDescriptor(false, "", null),
						new RecipeSelectionDescriptor(false, "", null),
						new RecipeSelectionDescriptor(false, "", null),
						new RecipeSelectionDescriptor(false, "", null),
						new RecipeSelectionDescriptor(false, "", null),
						new RecipeSelectionDescriptor(false, "", null),
				}),
				new RecipeSelectionDescriptor(false, "", null),
				new RecipeSelectionDescriptor(false, "", null),
				new RecipeSelectionDescriptor(false, "", null),
				new RecipeSelectionDescriptor(false, "", null),
				new RecipeSelectionDescriptor(true, "org.openrewrite.java.spring.security6.UpgradeSpringSecurity_6_1", null),
				new RecipeSelectionDescriptor(false, "", null),
				new RecipeSelectionDescriptor(false, "", null),
				new RecipeSelectionDescriptor(false, "", null)
		});
		
		Recipe boot31Recipes = recipeRepo.getRecipe("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_1").get().get();
		
		Recipe boot31 = recipeRepo.createRecipeFromSelection(boot31Recipes, descriptor.subselection());
		
		assertEquals(2, boot31.getRecipeList().size());
		
		Recipe security61 = boot31.getRecipeList().get(1);
		assertThat(security61.getRecipeList().size()).isGreaterThanOrEqualTo(5);
		
		Recipe boot30 = boot31.getRecipeList().get(0);
		assertEquals("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_0", boot30.getName());
		assertEquals(2, boot30.getRecipeList().size());
		
		Recipe boot27 = boot30.getRecipeList().get(0);
		assertThat(boot27.getRecipeList().size()).isGreaterThan(10);
		
		Recipe java17 = boot30.getRecipeList().get(1);
		assertThat(java17.getName()).isEqualTo("org.openrewrite.java.migrate.UpgradeToJava17");
		assertThat(java17.getRecipeList().size()).isEqualTo(3);
		
		assertThat(java17.getRecipeList().get(0).getName()).isEqualTo("org.openrewrite.java.migrate.Java8toJava11");
		assertThat(java17.getRecipeList().get(1).getName()).isEqualTo("org.openrewrite.java.migrate.UpgradeBuildToJava17");
		
	}

}
