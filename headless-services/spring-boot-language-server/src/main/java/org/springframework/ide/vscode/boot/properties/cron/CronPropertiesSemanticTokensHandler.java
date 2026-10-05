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
package org.springframework.ide.vscode.boot.properties.cron;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.DocumentFilter;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.cron.CronSemanticTokens;
import org.springframework.ide.vscode.boot.properties.cron.CronProperties.CronValue;
import org.springframework.ide.vscode.commons.languageserver.semantic.tokens.SemanticTokenData;
import org.springframework.ide.vscode.commons.languageserver.semantic.tokens.SemanticTokensHandler;
import org.springframework.ide.vscode.commons.languageserver.semantic.tokens.TextMateScopes;
import org.springframework.ide.vscode.commons.languageserver.util.LspClient;
import org.springframework.ide.vscode.commons.languageserver.util.LspClient.Client;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.Region;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.commons.yaml.ast.YamlASTProvider;

/**
 * CRON syntax highlighting for values of Spring Boot properties that hold CRON expressions in
 * <code>application.properties</code> and <code>application.yml</code> files.
 *
 * @author Alex Boyko
 */
public class CronPropertiesSemanticTokensHandler implements SemanticTokensHandler {

	private static final Logger log = LoggerFactory.getLogger(CronPropertiesSemanticTokensHandler.class);

	private final CronSemanticTokens cronTokens;
	private final YamlASTProvider yamlParser;
	private final Client client;

	public CronPropertiesSemanticTokensHandler(CronSemanticTokens cronTokens, YamlASTProvider yamlParser) {
		this(cronTokens, yamlParser, LspClient.currentClient());
	}

	CronPropertiesSemanticTokensHandler(CronSemanticTokens cronTokens, YamlASTProvider yamlParser, Client client) {
		this.cronTokens = cronTokens;
		this.yamlParser = yamlParser;
		this.client = client;
	}

	/**
	 * LSP4E looks up the color of a token type as a TextMate scope, hence Eclipse needs scopes, see {@link TextMateScopes}.
	 */
	private String tokenType(String type) {
		return client == Client.ECLIPSE ? TextMateScopes.scopeFor(type) : type;
	}

	@Override
	public SemanticTokensWithRegistrationOptions getCapability() {
		SemanticTokensWithRegistrationOptions capabilities = new SemanticTokensWithRegistrationOptions();
		capabilities.setDocumentSelector(List.of(
				new DocumentFilter(LanguageId.BOOT_PROPERTIES.getId(), null, null),
				new DocumentFilter(LanguageId.BOOT_PROPERTIES_YAML.getId(), null, null)));
		capabilities.setFull(true);
		capabilities.setLegend(new SemanticTokensLegend(cronTokens.getTokenTypes().stream().map(this::tokenType).toList(), cronTokens.getTypeModifiers()));
		return capabilities;
	}

	@Override
	public List<SemanticTokenData> semanticTokensFull(TextDocument doc, CancelChecker cancelChecker) {
		if (doc != null) {
			try {
				List<SemanticTokenData> data = new ArrayList<>();
				for (CronValue cron : CronProperties.findValues(doc, yamlParser)) {
					for (SemanticTokenData td : cronTokens.computeTokens(cron.text())) {
						data.add(new SemanticTokenData(new Region(td.range().getOffset() + cron.offset(), td.range().getLength()),
								tokenType(td.type()), td.modifiers()));
					}
				}
				return data;
			} catch (Exception e) {
				log.error("", e);
			}
		}
		return SemanticTokensHandler.super.semanticTokensFull(doc, cancelChecker);
	}

}
