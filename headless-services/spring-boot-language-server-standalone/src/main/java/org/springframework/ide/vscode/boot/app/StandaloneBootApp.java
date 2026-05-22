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
package org.springframework.ide.vscode.boot.app;

import org.springframework.boot.SpringApplication;
import org.springframework.ide.vscode.commons.languageserver.LanguageServerRunner;

import reactor.core.publisher.Hooks;

/**
 * Entry point for the standalone Spring Boot Language Server.
 *
 * <p>Runs without a JDT Language Server by registering two Spring configuration
 * sources: the standard {@link BootLanguageServerBootApp} (all language server beans)
 * plus {@link StandaloneProjectServiceConfig} (the Maven/Gradle-backed
 * {@code JavaProjectsService}). The latter's presence on the classpath also suppresses
 * the JDT-LS-backed bean in {@link BootLanguageServerBootApp} via
 * {@code @ConditionalOnMissingClass}.
 */
public class StandaloneBootApp {

	public static void main(String[] args) throws Exception {
		Hooks.onOperatorDebug();
		System.setProperty(LanguageServerRunner.SYSPROP_LANGUAGESERVER_NAME, "boot-language-server"); //makes it easy to recognize language server processes - and set this as early as possible
		new SpringApplication(BootLanguageServerBootApp.class, StandaloneProjectServiceConfig.class).run(args);
	}

}
