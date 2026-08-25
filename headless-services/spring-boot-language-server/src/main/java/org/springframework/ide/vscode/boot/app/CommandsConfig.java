/*******************************************************************************
 * Copyright (c) 2024, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.app;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.Misc;
import org.springframework.ide.vscode.boot.java.commands.SpringIndexCommands;
import org.springframework.ide.vscode.boot.java.commands.StructureViewProvider;
import org.springframework.ide.vscode.boot.java.commands.WorkspaceBootExecutableProjects;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeCatalogRegistry;
import org.springframework.ide.vscode.boot.modulith.ModulithService;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;

@Configuration(proxyBeanMethods = false)
public class CommandsConfig {
	
	@Bean WorkspaceBootExecutableProjects workspaceBootProjects(SimpleLanguageServer server, JavaProjectFinder projectFinder, SpringSymbolIndex symbolIndex) {
		return new WorkspaceBootExecutableProjects(server, projectFinder, symbolIndex);
	}
	
	@Bean
	StructureViewProvider structureViewProvider(SpringMetamodelIndex symbolIndex, ModulithService modulithService,
			StereotypeCatalogRegistry stereotypeCatalogRegistry, SourceLinks sourceLinks) {
		return new StructureViewProvider(symbolIndex, modulithService, stereotypeCatalogRegistry, sourceLinks);
	}

	@Bean
	SpringIndexCommands springIndexCommands(SimpleLanguageServer server, JavaProjectFinder projectFinder,
			SpringMetamodelIndex symbolIndex, StructureViewProvider structureViewProvider) {
		return new SpringIndexCommands(server, symbolIndex, projectFinder, structureViewProvider);
	}
	
	@Bean
	Misc misc(SimpleLanguageServer server) {
		return new Misc(server);
	}
	
}
