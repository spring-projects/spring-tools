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
package org.springframework.ide.vscode.boot.java.jdt.refactoring;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for normalizing Java type strings.
 * <p>
 * Provides methods to convert fully qualified type names that use {@code .} for
 * inner class boundaries into the canonical form using {@code $}.
 *
 * @author Alex Boyko
 */
public class NormalizeUtils {

	/**
	 * Matches a fully qualified name token: sequences of word characters, {@code .} and {@code $}.
	 * Everything else (e.g. {@code <}, {@code >}, {@code ,}, {@code ?}, {@code []},
	 * whitespace, {@code extends}, {@code super}) is left untouched.
	 */
	private static final Pattern NAME_TOKEN = Pattern.compile("[\\w.$]+");

	private NormalizeUtils() {
		// utility class
	}

	/**
	 * Normalize a type string so that inner class boundaries use {@code $} instead of {@code .}.
	 * <p>
	 * Applies a heuristic based on Java naming conventions: when two consecutive
	 * {@code .}-separated segments both start with an uppercase letter, the {@code .}
	 * between them is treated as an inner class boundary and replaced with {@code $}.
	 * <p>
	 * The method extracts each fully qualified name token from the type string
	 * (splitting around structural characters like {@code <}, {@code >}, {@code ,},
	 * and whitespace) and applies the heuristic independently to each token.
	 * Input that already uses {@code $} is returned unchanged.
	 *
	 * @param typeString the type string to normalize
	 * @return the normalized type string with {@code $} for inner class boundaries
	 */
	static String normalizeInnerClasses(String typeString) {
		if (typeString.isEmpty() || typeString.indexOf('$') >= 0) {
			return typeString;
		}

		Matcher matcher = NAME_TOKEN.matcher(typeString);
		StringBuilder result = new StringBuilder(typeString.length());

		while (matcher.find()) {
			matcher.appendReplacement(result, Matcher.quoteReplacement(applyUppercaseHeuristic(matcher.group())));
		}
		matcher.appendTail(result);

		return result.toString();
	}

	/**
	 * Apply the uppercase heuristic to a single name token (no type argument
	 * delimiters, commas, whitespace, or array brackets — just a dotted name,
	 * possibly with {@code $}).
	 * <p>
	 * Splits on {@code .}, finds the first segment starting with an uppercase letter
	 * (the outermost class), and converts subsequent {@code .} separators between
	 * uppercase-starting segments to {@code $}.
	 */
	private static String applyUppercaseHeuristic(String token) {
		String[] parts = token.split("\\.");
		if (parts.length <= 1) {
			return token;
		}

		boolean foundClass = false;
		StringBuilder sb = new StringBuilder(token.length());

		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				if (foundClass && !parts[i].isEmpty() && Character.isUpperCase(parts[i].charAt(0))) {
					sb.append('$');
				} else {
					sb.append('.');
				}
			}
			sb.append(parts[i]);
			if (!foundClass && !parts[i].isEmpty() && Character.isUpperCase(parts[i].charAt(0))) {
				foundClass = true;
			}
		}

		return sb.toString();
	}

}
