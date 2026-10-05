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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.cron.CronSemanticTokens;
import org.springframework.ide.vscode.commons.languageserver.semantic.tokens.SemanticTokenData;
import org.springframework.ide.vscode.commons.languageserver.util.LspClient.Client;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.commons.yaml.ast.YamlParser;

/**
 * @author Alex Boyko
 */
public class CronPropertiesSemanticTokensHandlerTest {

	private final CronPropertiesSemanticTokensHandler handler = new CronPropertiesSemanticTokensHandler(new CronSemanticTokens(), new YamlParser());

	private List<SemanticTokenData> tokens(LanguageId language, String text) {
		return handler.semanticTokensFull(new TextDocument("file:///test", language, 0, text), null);
	}

	/** Offsets of the tokens, expressed as the text they cover */
	private List<String> covered(String text, List<SemanticTokenData> tokens) {
		return tokens.stream().map(t -> text.substring(t.range().getOffset(), t.range().getOffset() + t.range().getLength())).toList();
	}

	@Test
	void properties() {
		String text = "server.port=8080\nspring.integration.poller.cron =   0 */30 * ? * *\nother.cron=0 0 * * * *\n";
		List<SemanticTokenData> tokens = tokens(LanguageId.BOOT_PROPERTIES, text);
		assertThat(covered(text, tokens)).containsExactly("0", "*", "/", "30", "*", "?", "*", "*");
	}

	@Test
	void propertiesRelaxedNameAndMacro() {
		String text = "spring.session.jdbc.cleanupCron=@daily";
		assertThat(covered(text, tokens(LanguageId.BOOT_PROPERTIES, text))).containsExactly("@daily");
	}

	@Test
	void propertiesPlaceholderIsIgnored() {
		assertThat(tokens(LanguageId.BOOT_PROPERTIES, "spring.session.jdbc.cleanup-cron=${my.cron}")).isEmpty();
	}

	@Test
	void yaml() {
		String text = """
				spring:
				  integration:
				    poller:
				      cron: 0 */30 * ? * *
				  session:
				    jdbc:
				      cleanup-cron: "@daily"
				    data:
				      redis:
				        cleanupCron: '0 0 0 * * *'
				server:
				  cron: 0 0 0 * * *
				""";
		assertThat(covered(text, tokens(LanguageId.BOOT_PROPERTIES_YAML, text)))
			.containsExactly("0", "*", "/", "30", "*", "?", "*", "*", "@daily", "0", "0", "0", "*", "*", "*");
	}

	@Test
	void eclipseClientGetsTextMateScopes() {
		CronPropertiesSemanticTokensHandler eclipse = new CronPropertiesSemanticTokensHandler(new CronSemanticTokens(), new YamlParser(), Client.ECLIPSE);
		String text = "spring.session.jdbc.cleanup-cron=0 */5 * * * MON";

		List<SemanticTokenData> tokens = eclipse.semanticTokensFull(new TextDocument("file:///test", LanguageId.BOOT_PROPERTIES, 0, text), null);
		assertThat(tokens).extracting(SemanticTokenData::type).containsOnly("constant.numeric", "keyword.operator", "entity.name.type.enum");

		// Every type used by a token is in the legend, otherwise the tokens cannot be encoded
		assertThat(eclipse.getCapability().getLegend().getTokenTypes()).containsAll(tokens.stream().map(SemanticTokenData::type).toList());
		assertThat(eclipse.getCapability().getLegend().getTokenTypes()).doesNotContain("operator", "number", "enum");

		// Other clients keep the standard types
		assertThat(handler.getCapability().getLegend().getTokenTypes()).contains("operator", "number", "enum");
		assertThat(handler.semanticTokensFull(new TextDocument("file:///test", LanguageId.BOOT_PROPERTIES, 0, text), null))
				.extracting(SemanticTokenData::type).containsOnly("number", "operator", "enum");
	}

}
