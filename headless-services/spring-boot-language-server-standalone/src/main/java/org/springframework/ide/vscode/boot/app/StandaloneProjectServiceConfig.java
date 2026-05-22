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
import org.springframework.ide.vscode.boot.jdt.ls.JavaProjectsService;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;

/**
 * Spring configuration that provides a {@link JavaProjectsService} backed by Maven and
 * Gradle project caches, requiring no JDT Language Server.
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
	JavaProjectsService javaProjectsService(SimpleLanguageServer server) {
		return new LegacyJavaProjectsService(server);
	}

}
