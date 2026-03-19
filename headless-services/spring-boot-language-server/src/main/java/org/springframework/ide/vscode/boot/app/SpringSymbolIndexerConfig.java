/*******************************************************************************
 * Copyright (c) 2019, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.app;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ide.vscode.boot.index.cache.IndexCache;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchyAwareLookup;
import org.springframework.ide.vscode.boot.java.beans.ComponentSymbolProvider;
import org.springframework.ide.vscode.boot.java.beans.ConfigurationPropertiesSymbolProvider;
import org.springframework.ide.vscode.boot.java.beans.FeignClientSymbolProvider;
import org.springframework.ide.vscode.boot.java.data.DataRepositoryAotMetadataService;
import org.springframework.ide.vscode.boot.java.data.DataRepositorySymbolProvider;
import org.springframework.ide.vscode.boot.java.handlers.SymbolProvider;
import org.springframework.ide.vscode.boot.java.requestmapping.HttpExchangeSymbolProvider;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypesIndexer;

@Configuration(proxyBeanMethods = false)
public class SpringSymbolIndexerConfig {

	@Bean
	AnnotationHierarchyAwareLookup<SymbolProvider> symbolProviders(IndexCache cache, DataRepositoryAotMetadataService repositoryMetadataService) {
		AnnotationHierarchyAwareLookup<SymbolProvider> providers = new AnnotationHierarchyAwareLookup<>();

		ComponentSymbolProvider componentSymbolProvider = new ComponentSymbolProvider();
		ConfigurationPropertiesSymbolProvider configPropsSymbolProvider = new ConfigurationPropertiesSymbolProvider();
		DataRepositorySymbolProvider dataRepositorySymbolProvider = new DataRepositorySymbolProvider(repositoryMetadataService);

//		providers.put(Annotations.COMPONENT, componentSymbolProvider);
//		providers.put(Annotations.NAMED_JAKARTA, componentSymbolProvider);
//		providers.put(Annotations.NAMED_JAVAX, componentSymbolProvider);
//		providers.put(Annotations.CONFIGURATION_PROPERTIES, configPropsSymbolProvider);

//		providers.put(Annotations.FEIGN_CLIENT, new FeignClientSymbolProvider());
//		providers.put(Annotations.HTTP_EXCHANGE, new HttpExchangeSymbolProvider());
		
//		providers.put(Annotations.JMOLECULES_STEREOTYPE, new StereotypesIndexer());

		return providers;
	}
}
