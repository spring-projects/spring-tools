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

import java.util.List;
import java.util.function.Function;

import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;
import org.springframework.ide.vscode.commons.languageserver.semantic.tokens.SemanticTokenData;
import org.springframework.ide.vscode.commons.util.text.Region;

class SemanticTokensStreamProcessor extends AbstractSemanticTokensDataStreamProcessor<String, SemanticTokenData>{

	protected SemanticTokensStreamProcessor(Function<Position, Integer> offsetMapper) {
		super(offsetMapper, Function.<String>identity());
	}

	@Override
	protected @Nullable SemanticTokenData createTokenData(@Nullable String tokenType, int offset, int length,
			List<String> tokenModifiers) {
		return new SemanticTokenData(new Region(offset, length), tokenType, tokenModifiers.toArray(new String[tokenModifiers.size()]));
	}

}
