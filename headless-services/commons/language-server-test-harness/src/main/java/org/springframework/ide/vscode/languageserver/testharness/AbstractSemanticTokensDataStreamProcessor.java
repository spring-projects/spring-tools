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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.jspecify.annotations.Nullable;

abstract class AbstractSemanticTokensDataStreamProcessor<T, V> {

	private final Function<Position, Integer> offsetMapper;
	private final Function<String, @Nullable T> tokenTypeMapper;

	protected AbstractSemanticTokensDataStreamProcessor(Function<Position, Integer> offsetMapper,
			Function<String, @Nullable T> tokenTypeMapper) {
		this.offsetMapper = offsetMapper;
		this.tokenTypeMapper = tokenTypeMapper;
	}

	/**
	 * Get the IDE Tokens for the given data stream and tokens legend.
	 *
	 * @param dataStream
	 * @param semanticTokensLegend
	 */
	public final List<V> getTokensData(final List<Integer> dataStream,
			final SemanticTokensLegend semanticTokensLegend) {
		final var tokens = new ArrayList<V>(dataStream.size() / 5);

		int idx = 0;
		int prevLine = 0;
		int line = 0;
		int offset = 0;
		int length = 0;
		String tokenType = null;
		for (Integer data : dataStream) {
			switch (idx % 5) {
			case 0: // line
				line += data;
				break;
			case 1: // offset
				if (line == prevLine) {
					offset += data;
				} else {
					offset = offsetMapper.apply(new Position(line, data));
				}
				break;
			case 2: // length
				length = data;
				break;
			case 3: // token type
				tokenType = tokenType(data, semanticTokensLegend.getTokenTypes());
				break;
			case 4: // token modifier
				prevLine = line;
				@Nullable V token = createTokenData(tokenType == null ? null : tokenTypeMapper.apply(tokenType), offset, length, tokenModifiers(data, semanticTokensLegend.getTokenModifiers()));
				if (token != null) {
					tokens.add(token);
				}
				break;
			}
			idx++;
		}
		return tokens;
	}

	protected abstract @Nullable V createTokenData(@Nullable T tokenType, int offset, int length, List<String> tokenModifiers);

	private @Nullable String tokenType(final Integer data, final List<String> legend) {
		try {
			return legend.get(data);
		} catch (IndexOutOfBoundsException e) {
			return null; // no match
		}
	}

	private List<String> tokenModifiers(final Integer data, final List<String> legend) {
		if (data.intValue() == 0) {
			return Collections.emptyList();
		}
		final var bitSet = BitSet.valueOf(new long[] { data });
		final var tokenModifiers = new ArrayList<String>();
		for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
			try {
				tokenModifiers.add(legend.get(i));
			} catch (IndexOutOfBoundsException e) {
				// no match
			}
		}

		return tokenModifiers;
	}

}
