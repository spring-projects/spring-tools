/*******************************************************************************
 * Copyright (c) 2017, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.rewrite.java;

import java.util.ArrayList;
import java.util.List;

import org.openrewrite.NlsRewrite.Description;
import org.openrewrite.NlsRewrite.DisplayName;
import org.openrewrite.Option;
import org.openrewrite.Recipe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Udayani V
 */
public class InjectBeanCompletionRecipe extends Recipe {

	@Override
	public @DisplayName String getDisplayName() {
		return "Inject bean completions";
	}

	@Override
	public @Description String getDescription() {
		return "Automates the injection of a specified bean into Spring components by adding the necessary field and import, creating the constructor if it doesn't exist, and injecting the bean as a constructor parameter.";
	}
	
	@Option(description = "Fully qualified type of the injected the bean")
	String fullyQualifiedName;

	@Option(description = "Name of the field for the injected bean")
	String fieldName;

	@Option(description = "Name of the bean class that is going to have an injected bean via constructor parameter")
	String classFqName;
	
	@JsonCreator
	public InjectBeanCompletionRecipe(@JsonProperty("fullyQualifiedName") String fullyQualifiedName,
			@JsonProperty("fieldName") String fieldName,
			@JsonProperty("classFqName") String classFqName) {
		this.fullyQualifiedName = fullyQualifiedName;
		this.fieldName = fieldName;
		this.classFqName = classFqName;
    }
	
	@Override
    public List<Recipe> getRecipeList() {
		List<Recipe> list = new ArrayList<>();
		list.add(new AddFieldRecipe(fullyQualifiedName, classFqName, fieldName));
		list.add(new ConstructorInjectionRecipe(fullyQualifiedName, fieldName, classFqName));
		return list;
    }

}
