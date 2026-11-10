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
package org.springframework.ide.vscode.boot.kotlin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.java.handlers.SpringComponentIndexer;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexer;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerProvider;
import org.springframework.ide.vscode.boot.java.utils.SymbolHandler;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.stereotype.Component;

/**
 * Adds the {@link SpringIndexerKotlin} to the symbol index. The indexer is always there, but only
 * does something while the Kotlin support is switched on in the settings.
 *
 * @author Martin Lippert
 */
@Component
public class KotlinIndexerProvider implements SpringIndexerProvider {

	@Autowired BootJavaConfig config;
	@Autowired SpringComponentIndexer[] componentIndexers;
	@Autowired SimpleLanguageServer server;

	@Override
	public SpringIndexer createIndexer(SymbolHandler symbolHandler) {
		// the very same indexers as for Java files: the Kotlin AST resolves to JDT bindings
		return new SpringIndexerKotlin(symbolHandler, componentIndexers, config::isKotlinSupportEnabled, config::isScanJavaTestSourcesEnabled,
				() -> server.getWorkspaceService().getFileObserver());
	}

}
