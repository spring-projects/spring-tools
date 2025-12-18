/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.languageserver.testharness;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.springframework.ide.vscode.commons.languageserver.semantic.tokens.SemanticTokenData;

/**
 * Assertion utilities for semantic tokens testing.
 */
public class SemanticTokensAssert {

	/**
	 * Asserts that the actual semantic tokens match the expected tokens.
	 * 
	 * @param source the source text from which tokens were extracted
	 * @param actual the actual list of semantic tokens
	 * @param expected the expected semantic tokens (text and type)
	 */
	public static void assertTokens(String source, List<SemanticTokenData> actual, ExpectedSemanticToken... expected) {
		assertThat(actual)
			.withFailMessage("Expected %d tokens but found %d", expected.length, actual.size())
			.hasSize(expected.length);
		
		for (int i = 0; i < expected.length; i++) {
			SemanticTokenData token = actual.get(i);
			ExpectedSemanticToken exp = expected[i];
			
			// Extract actual text from source using token offsets
			String actualText = source.substring(token.getStart(), token.getEnd());
			
			// Assert text matches
			assertThat(actualText)
				.withFailMessage("Token %d: expected text '%s' but was '%s'", i, exp.text(), actualText)
				.isEqualTo(exp.text());
			
			// Assert type matches
			assertThat(token.type())
				.withFailMessage("Token %d ('%s'): expected type '%s' but was '%s'", 
					i, actualText, exp.type(), token.type())
				.isEqualTo(exp.type());
			
			// Assert modifiers match (if specified)
			assertThat(token.modifiers())
				.withFailMessage("Token %d ('%s'): expected modifiers %s but was %s", 
					i, actualText, Arrays.toString(exp.modifiers()), Arrays.toString(token.modifiers()))
				.isEqualTo(exp.modifiers());
		}
	}

	/**
	 * Represents an expected semantic token for testing purposes.
	 * Contains the expected text, type, and optional modifiers.
	 */
	public record ExpectedSemanticToken(String text, String type, String[] modifiers) {
		public ExpectedSemanticToken(String text, String type) {
			this(text, type, new String[0]);
		}
	}
}

