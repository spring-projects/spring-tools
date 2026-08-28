/*******************************************************************************
 * Copyright (c) 2017 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.core.util;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtil {

	public static boolean hasText(String name) {
		return name != null && !name.trim().equals("");
	}

	public static String trim(String s) {
		if (s != null) {
			return s.trim();
		}
		return null;
	}

	public static String trimEnd(String s) {
		if (s != null) {
			return s.replaceAll("\\s+\\z", "");
		}
		return null;
	}

	public static int commonPrefixLength(CharSequence s, CharSequence t) {
		int shortestStringLen = Math.min(s.length(), t.length());
		for (int i = 0; i < shortestStringLen; i++) {
			if (s.charAt(i) != t.charAt(i)) {
				return i;
			}
		}
		//no difference found upto entire length of shortest string.
		return shortestStringLen;
	}

	/**
	 * @return longest string which is a prefix of both argument Strings.
	 */
	public static String commonPrefix(CharSequence s, CharSequence t) {
		int len = commonPrefixLength(s, t);
		if (len > 0) {
			return s.subSequence(0,len).toString();
		}
		return "";
	}

	public static String removePrefix(String s, String prefix) {
		if (s.startsWith(prefix)) {
			return s.substring(prefix.length());
		} else {
			return s;
		}
	}

	public static String camelCaseToHyphens(String value) {
		Matcher matcher = CAMEL_CASE_PATTERN.matcher(value);
		StringBuffer result = new StringBuffer();
		int start = 0;
		while (matcher.find()) {
			result.append(value.substring(start, matcher.start()));
			result.append(matcher.group(1));
			result.append('-');
			result.append(matcher.group(2).toLowerCase());
			start = matcher.end();
		}
		result.append(value.substring(start));
		return result.toString();
	}

	private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("([^A-Z-])([A-Z])");

	public static String arrayToCommaDelimitedString(Object[] array) {
		return collectionToCommaDelimitedString(Arrays.asList(array));
	}

	public static String collectionToCommaDelimitedString(Collection<?> items) {
		StringBuilder buf = new StringBuilder();
		boolean first = true;
		for (Object item : items) {
			if (!first) {
				buf.append(",");
			}
			buf.append(item);
			first = false;
		}
		return buf.toString();
	}

	public static String upperCaseToHyphens(String v) {
		if (v != null) {
			return v.toLowerCase().replace('_', '-');
		}
		return null;
	}

	public static String hyphensToUpperCase(String v) {
		if (v != null) {
			return v.toUpperCase().replace('-', '_');
		}
		return null;
	}

	public static String hyphensToCamelCase(String propName, boolean startWithUpperCase) {
		String [] parts = propName.split("-");
		if (startWithUpperCase) {
			parts[0] = upCaseFirstChar(parts[0]);
		}
		StringBuilder camelCased = new StringBuilder(parts[0]);
		for (int i = 1; i < parts.length; i++) {
			camelCased.append(upCaseFirstChar(parts[i]));
		}
		return camelCased.toString();
	}


	public static String upCaseFirstChar(String string) {
		if (StringUtil.hasText(string)) {
			return Character.toUpperCase(string.charAt(0)) + string.substring(1);
		}
		return "";
	}

	public static String datestamp() {
		Date d = new Date();
		SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd");
		return f.format(d);
	}
}