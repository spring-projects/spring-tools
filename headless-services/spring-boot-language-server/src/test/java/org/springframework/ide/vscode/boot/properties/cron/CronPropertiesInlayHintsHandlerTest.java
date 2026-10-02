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

import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.cron.CronExpressionsInlayHintsProvider;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.commons.yaml.ast.YamlParser;

/**
 * @author Alex Boyko
 */
public class CronPropertiesInlayHintsHandlerTest {

	private static final String EVERY_HOUR = "0 0 * * * *";

	private List<InlayHint> hints(LanguageId language, String text, boolean enabled, Range range) {
		return new CronPropertiesInlayHintsHandler(() -> enabled, new YamlParser())
				.handle(new TextDocument("file:///test", language, 0, text), range, null);
	}

	private String label(InlayHint hint) {
		return hint.getLabel().getLeft();
	}

	@Test
	void properties() {
		String text = "server.port=8080\nspring.session.jdbc.cleanup-cron =   " + EVERY_HOUR + "  \nother.cron=0 0 * * * *\n";
		List<InlayHint> hints = hints(LanguageId.BOOT_PROPERTIES, text, true, null);
		assertThat(hints).hasSize(1);
		assertThat(label(hints.get(0))).isEqualTo(CronExpressionsInlayHintsProvider.describe(EVERY_HOUR).get());
		assertThat(label(hints.get(0))).containsIgnoringCase("hour");
		assertThat(hints.get(0).getPosition()).isEqualTo(new Position(1, "spring.session.jdbc.cleanup-cron =   ".length() + EVERY_HOUR.length()));
	}

	@Test
	void yaml() {
		String text = """
				spring:
				  integration:
				    poller:
				      cron: 0 0 * * * *
				  session:
				    jdbc:
				      cleanup-cron: "0 0 * * * *"
				""";
		List<InlayHint> hints = hints(LanguageId.BOOT_PROPERTIES_YAML, text, true, null);
		assertThat(hints).hasSize(2);
		assertThat(hints.get(0).getPosition()).isEqualTo(new Position(3, "      cron: ".length() + EVERY_HOUR.length()));
		// Quoted value: the hint is placed after the closing quote
		assertThat(hints.get(1).getPosition()).isEqualTo(new Position(6, "      cleanup-cron: ".length() + EVERY_HOUR.length() + 2));
	}

	@Test
	void invalidExpressionAndPlaceholderHaveNoHints() {
		String text = "spring.session.jdbc.cleanup-cron=0 0 25 * * *\nspring.integration.poller.cron=${my.cron}\n";
		assertThat(hints(LanguageId.BOOT_PROPERTIES, text, true, null)).isEmpty();
	}

	@Test
	void disabled() {
		assertThat(hints(LanguageId.BOOT_PROPERTIES, "spring.session.jdbc.cleanup-cron=" + EVERY_HOUR, false, null)).isEmpty();
	}

	@Test
	void requestedRangeIsRespected() {
		String text = "spring.session.jdbc.cleanup-cron=" + EVERY_HOUR + "\nspring.integration.poller.cron=" + EVERY_HOUR + "\n";
		List<InlayHint> hints = hints(LanguageId.BOOT_PROPERTIES, text, true, new Range(new Position(1, 0), new Position(2, 0)));
		assertThat(hints).hasSize(1);
		assertThat(hints.get(0).getPosition().getLine()).isEqualTo(1);
	}

}
