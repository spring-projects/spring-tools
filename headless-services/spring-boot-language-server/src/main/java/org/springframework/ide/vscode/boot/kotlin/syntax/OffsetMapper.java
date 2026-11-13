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
package org.springframework.ide.vscode.boot.kotlin.syntax;

import java.util.ArrayList;
import java.util.List;

/**
 * The parsers of the Kotlin compiler work on text with '\n' as the only line separator. This normalizes the text and
 * maps offsets in the normalized text back to the original text.
 */
final class OffsetMapper {

	private final String normalized;
	private final int[] removedCarriageReturns; // positions in the normalized text where a '\r' was dropped in front of

	OffsetMapper(String original) {
		if (original.indexOf('\r') < 0) {
			this.normalized = original;
			this.removedCarriageReturns = new int[0];
			return;
		}

		StringBuilder result = new StringBuilder(original.length());
		List<Integer> removed = new ArrayList<>();
		for (int i = 0; i < original.length(); i++) {
			char c = original.charAt(i);
			if (c == '\r') {
				if (i + 1 < original.length() && original.charAt(i + 1) == '\n') {
					removed.add(result.length());
				}
				else {
					result.append('\n');
				}
			}
			else {
				result.append(c);
			}
		}
		this.normalized = result.toString();
		this.removedCarriageReturns = removed.stream().mapToInt(Integer::intValue).toArray();
	}

	String normalizedText() {
		return normalized;
	}

	/** the offset in the original text of the character that is at the given offset in the normalized text */
	int originalStart(int offset) {
		int shift = 0;
		while (shift < removedCarriageReturns.length && removedCarriageReturns[shift] <= offset) {
			shift++;
		}
		return offset + shift;
	}

	/**
	 * The offset in the original text of the end of a range in the normalized text (the offset
	 * of the first character that is not part of the range any more). A carriage return that
	 * was dropped in front of that character belongs to the end of the line, not to the range.
	 */
	int originalEnd(int offset) {
		int shift = 0;
		while (shift < removedCarriageReturns.length && removedCarriageReturns[shift] < offset) {
			shift++;
		}
		return offset + shift;
	}
}
