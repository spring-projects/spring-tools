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
package org.springframework.ide.vscode.commons.languageserver.semantic.tokens;

import static org.assertj.core.api.Assertions.assertThat;


import org.junit.jupiter.api.Test;

/**
 * @author Alex Boyko
 */
public class TextMateScopesTest {

	@Test
	void standardTypesMapToVsCodeDefaultScopes() {
		assertThat(TextMateScopes.scopeFor("operator")).isEqualTo("keyword.operator");
		assertThat(TextMateScopes.scopeFor("number")).isEqualTo("constant.numeric");
		assertThat(TextMateScopes.scopeFor("keyword")).isEqualTo("keyword.control");
		assertThat(TextMateScopes.scopeFor("method")).isEqualTo("entity.name.function.member");
		assertThat(TextMateScopes.scopeFor("macro")).isEqualTo("entity.name.function.preprocessor");
		assertThat(TextMateScopes.scopeFor("enumMember")).isEqualTo("variable.other.enummember");
		assertThat(TextMateScopes.scopeFor("string")).isEqualTo("string");
		assertThat(TextMateScopes.scopeFor("comment")).isEqualTo("comment");
	}

	@Test
	void unknownTypesAreLeftAsIs() {
		assertThat(TextMateScopes.scopeFor("my-custom-type")).isEqualTo("my-custom-type");
		assertThat(TextMateScopes.scopeFor("keyword.operator")).isEqualTo("keyword.operator");
	}

}
