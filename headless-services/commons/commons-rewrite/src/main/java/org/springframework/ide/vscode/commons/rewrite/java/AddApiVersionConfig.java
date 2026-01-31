/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.rewrite.java;

import java.util.List;

import org.openrewrite.NlsRewrite.Description;
import org.openrewrite.NlsRewrite.DisplayName;
import org.openrewrite.config.CompositeRecipe;
import org.springframework.ide.vscode.commons.rewrite.java.AddApiVersioningConfigurationCall.ConfigType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AddApiVersionConfig extends CompositeRecipe {

	@JsonCreator
	public AddApiVersionConfig(
			@JsonProperty("filePath") String filePath,
			@JsonProperty("pkgName") String pkgName,
			@JsonProperty("isFlux") boolean isFlux,
			@JsonProperty("configType") ConfigType configType,
			@JsonProperty("value") String value) {
		super(List.of(
				new AddWebConfigurerBean(filePath, pkgName, isFlux),
				new AddApiVersioningConfigMethod(filePath),
				new AddApiVersioningConfigurationCall(filePath, configType, value)
		));
	}

	@Override
	public @DisplayName String getDisplayName() {
		return "Add API versioning configuration to Web Configurer";
	}

	@Override
	public @Description String getDescription() {
		return "Creates or updates a Web Configurer class with API versioning configuration. "
				+ "This composite recipe performs three steps: "
				+ "1) Creates a WebMvcConfigurer or WebFluxConfigurer @Configuration class if it doesn't exist, "
				+ "2) Adds the configureApiVersioning method if not present, and "
				+ "3) Adds the appropriate versioning configuration call (useRequestHeader, useQueryParam, usePathSegment, or useMediaTypeParameter).";
	}

}
