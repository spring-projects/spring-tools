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
package org.springframework.ide.vscode.boot.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.metadata.types.Type;
import org.springframework.ide.vscode.boot.metadata.types.TypeParser;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Alex Boyko
 */
public class ValuePrefixFinderTest {

	private static final Type CRON = new Type(TypeUtil.CRON_TYPE_NAME, null);
	private static final Type STRING = new Type("java.lang.String", null);

	private final ValuePrefixFinder finder = new ValuePrefixFinder();

	/**
	 * @param text key, separator and value, the cursor is at the end of the text
	 * @param keyLength length of the text before the value
	 */
	private String prefix(String text, int keyLength, Type type) {
		TextDocument doc = new TextDocument("file:///test", LanguageId.BOOT_PROPERTIES, 0, text);
		return finder.getPrefix(doc, text.length(), keyLength, type);
	}

	@Test
	void cronExpressionIsPrefixAsAWhole() {
		assertThat(prefix("a.cron=0 0 ", "a.cron=".length(), CRON)).isEqualTo("0 0 ");
		assertThat(prefix("a.cron=0 */5", "a.cron=".length(), CRON)).isEqualTo("0 */5");
		assertThat(prefix("a.cron=0 0 0 * * 6,0", "a.cron=".length(), CRON)).isEqualTo("0 0 0 * * 6,0");
		assertThat(prefix("a.cron=*/10 * ", "a.cron=".length(), CRON)).isEqualTo("*/10 * ");
		assertThat(prefix("a.cron=@daily", "a.cron=".length(), CRON)).isEqualTo("@daily");
	}

	@Test
	void quoteOfAQuotedCronValueIsNotPartOfThePrefix() {
		assertThat(prefix("a: \"0 0 ", "a: ".length(), CRON)).isEqualTo("0 0 ");
		assertThat(prefix("a: '0 */5", "a: ".length(), CRON)).isEqualTo("0 */5");
	}

	@Test
	void prefixIsTheLastWordWithoutAType() {
		assertThat(prefix("a=foo", "a=".length(), null)).isEqualTo("foo");
		assertThat(prefix("a=1,2", "a=".length(), null)).isEqualTo("2");
		assertThat(prefix("a=1, 2", "a=".length(), null)).isEqualTo("2");
		assertThat(prefix("a=hello wor", "a=".length(), null)).isEqualTo("wor");
		// Looks like a CRON expression, but the type is not known
		assertThat(prefix("a=0 0 ", "a=".length(), null)).isEmpty();
		assertThat(prefix("a=0 */5", "a=".length(), null)).isEqualTo("*/5");
	}

	@Test
	void prefixIsTheLastWordForOtherTypes() {
		assertThat(prefix("a=0 */5", "a=".length(), STRING)).isEqualTo("*/5");
		assertThat(prefix("a=dev, pr", "a=".length(), STRING)).isEqualTo("pr");
		assertThat(prefix("a=1, 2", "a=".length(), TypeParser.parse("java.util.List<java.lang.Integer>"))).isEqualTo("2");
	}

	@Test
	void emptyValue() {
		assertThat(prefix("a=", "a=".length(), CRON)).isEmpty();
		assertThat(prefix("a=", "a=".length(), null)).isEmpty();
		assertThat(prefix("a: ", "a: ".length(), CRON)).isEmpty();
	}

}
