/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom, Inc.
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
import org.springframework.core.env.Environment;
import org.springframework.ide.vscode.boot.jdt.ls.JavaProjectsService;
import org.springframework.ide.vscode.boot.mcp.FileChangesMcpTools;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectChangeNotifier;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.util.FileChangeNotifier;

/**
 * Spring configuration that provides a {@link JavaProjectsService} backed by Maven and
 * Gradle project caches, requiring no JDT Language Server, plus the MCP file-change tools and
 * the {@link StandaloneFileWatcher} that keeps the index in sync with the disk in MCP-only mode.
 *
 * <p>Registered explicitly as a second Spring source in {@link StandaloneBootApp#main},
 * so it is discovered regardless of classpath scanning boundaries. The presence of
 * {@link LegacyJavaProjectsService} on the classpath causes
 * {@link BootLanguageServerBootApp} to skip its JDT-LS-backed bean via
 * {@code @ConditionalOnMissingClass}.
 */
@Configuration(proxyBeanMethods = false)
public class StandaloneProjectServiceConfig {
	
	@Bean
	LegacyJavaProjectsService javaProjectsService(SimpleLanguageServer server) {
		return new LegacyJavaProjectsService(server);
	}
	
	@Bean
	FileChangesMcpTools fileChangesMcpTools(FileChangeNotifier fileChangeNotifier, ProjectChangeNotifier projectChangeNotifier, SimpleLanguageServer server) {
		return new FileChangesMcpTools(fileChangeNotifier, projectChangeNotifier, server);
	}

	@Bean
	StandaloneFileWatcher standaloneFileWatcher(Environment environment, FileChangeNotifier fileChangeNotifier, LegacyJavaProjectsService projectsService) {
		return new StandaloneFileWatcher(environment, fileChangeNotifier, projectsService);
	}

}
