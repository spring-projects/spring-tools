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

import java.util.Set;
import java.util.regex.Pattern;

/**
 * The rules for reading the values of annotation arguments that the readers that work on the raw
 * syntax tree of a parser share (the PSI reader gets the same things from the PSI classes), so
 * what is a reference, which calls create arrays, and what an escape sequence means is the same
 * for all of them.
 *
 * @author Martin Lippert
 */
final class KotlinValueSyntax {

	private KotlinValueSyntax() {
	}

	/** an expression that is just a name, which is written in dots: a reference to a constant or an enum entry */
	static final Pattern DOTTED_NAME = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_]*(\\.[\\p{L}_][\\p{L}\\p{N}_]*)*");

	/** the functions that create an array, as a value of an annotation argument */
	static final Set<String> ARRAY_FUNCTIONS = Set.of("arrayOf", "intArrayOf", "longArrayOf", "shortArrayOf", "byteArrayOf",
			"charArrayOf", "booleanArrayOf", "floatArrayOf", "doubleArrayOf", "listOf", "setOf");

	/** the text of a string without the escape sequences: {@code \t}, {@code \$}, {@code A} and so on */
	static String unescape(String string) {
		if (string.indexOf('\\') < 0) {
			return string;
		}

		StringBuilder result = new StringBuilder(string.length());
		for (int i = 0; i < string.length(); i++) {
			char c = string.charAt(i);
			if (c != '\\' || i + 1 >= string.length()) {
				result.append(c);
				continue;
			}

			char escaped = string.charAt(++i);
			switch (escaped) {
			case 't' -> result.append('\t');
			case 'b' -> result.append('\b');
			case 'n' -> result.append('\n');
			case 'r' -> result.append('\r');
			case 'u' -> {
				if (i + 4 < string.length() && string.substring(i + 1, i + 5).matches("[0-9a-fA-F]{4}")) {
					result.append((char) Integer.parseInt(string.substring(i + 1, i + 5), 16));
					i += 4;
				}
				else {
					result.append("\\u");
				}
			}
			case '\'', '"', '\\', '$' -> result.append(escaped);
			default -> result.append('\\').append(escaped);
			}
		}
		return result.toString();
	}

	/** a name without the backticks around it */
	static String unquote(String name) {
		return name.length() > 1 && name.startsWith("`") && name.endsWith("`") ? name.substring(1, name.length() - 1) : name;
	}

}
