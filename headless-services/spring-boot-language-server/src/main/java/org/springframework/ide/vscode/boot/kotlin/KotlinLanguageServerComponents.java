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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.app.FeatureFlag;
import org.springframework.ide.vscode.boot.app.FeatureFlagGuards;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.commons.languageserver.composable.LanguageServerComponents;
import org.springframework.ide.vscode.commons.languageserver.util.DocumentSymbolHandler;
import org.springframework.ide.vscode.commons.util.text.LanguageId;

/**
 * The editor features for Kotlin source files. For now this is the outline: the symbols of what
 * the {@link SpringIndexerKotlin} indexed in the file, the way the Java components offer them for
 * Java files.
 * <p>
 * Kotlin is deliberately not one of the languages of the Java components: their hovers,
 * reconcilers, code actions etc. work on the text of the document as Java source.
 * <p>
 * The language a client reports for a Kotlin file depends on the extensions that are installed:
 * {@code kotlin} if one of them defines the language, {@code plaintext} if none does (VSCode with
 * only the Java extension of Red Hat, for example). So both are taken, and the file name decides.
 *
 * @author Martin Lippert
 */
public class KotlinLanguageServerComponents implements LanguageServerComponents {

	private final SpringSymbolIndex springIndex;
	private final BootJavaConfig config;

	public KotlinLanguageServerComponents(SpringSymbolIndex springIndex, BootJavaConfig config) {
		this.springIndex = springIndex;
		this.config = config;
	}

	@Override
	public Set<LanguageId> getInterestingLanguages() {
		return Set.of(LanguageId.KOTLIN, LanguageId.PLAINTEXT);
	}

	@Override
	public Optional<DocumentSymbolHandler> getDocumentSymbolProvider() {
		DocumentSymbolHandler documentSymbols = params -> config.isKotlinSupportEnabled() && isKotlinFile(params.getTextDocument().getUri())
				? springIndex.getDocumentSymbols(params.getTextDocument().getUri())
				: List.of();
		return Optional.of(FeatureFlagGuards.onlyWhen(config, FeatureFlag.DOCUMENT_SYMBOLS, documentSymbols));
	}

	private static boolean isKotlinFile(String uri) {
		return uri != null && uri.endsWith(".kt");
	}

}
