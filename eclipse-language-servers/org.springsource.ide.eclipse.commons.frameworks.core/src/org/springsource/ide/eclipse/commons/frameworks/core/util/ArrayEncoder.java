/*******************************************************************************
 * Copyright (c) 2012 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.frameworks.core.util;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilty methods for encoding / decoding an array of Strings into a single String.
 *
 * @author Kris De Volder
 */
public class ArrayEncoder {

	private static final char ESCAPE_CHAR = '\\';
	private static final char TERMINATOR_CHAR = ';';
	private static final int BUFFER_SIZE = 1024;

	/**
	 * Decode an array of Strings encoded as a single String by StringUtils encodeArray.
	 */
	public static String[] decode(String encoded) {
		if (encoded == null) {
			return null;
		}
		// Encoding: String are terminated by a special terminator char. If the original String contains a separator
		// character or an escape character it is escaped by preceding it with an escape character.

		// Note: we use terminator char rather than a separator between Strings.
		// This is because with a true separator, it will be impossible to distinguish between the encoding of an empty array
		// and the encoding of an array with a single empty String.

		// Examples: (terminator = ';' escape = '\\')

		// new String[0]       == encode == > ""
		// new String[] { "" } == encode == > ";"
		// new String[] { "Hello", "World" } == encode == > "Hello;World;"

		StringReader in = new StringReader(encoded);
		List<String> strings = new ArrayList<String>();
		StringBuffer currentString = new StringBuffer();
		try {
			for (int _c = in.read(); _c >= 0; _c = in.read()) {
				char c = (char)_c;
				if (c == TERMINATOR_CHAR) {
					strings.add(currentString.toString());
					currentString = new StringBuffer();
				} else if (c == ESCAPE_CHAR) {
					c = (char)in.read();
					currentString.append(c);
				} else {
					currentString.append(c);
				}
			}
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		return strings.toArray(new String[strings.size()]);
	}

	public static String encode(String[] strings) {
		StringWriter out = new StringWriter(BUFFER_SIZE);
		for (String string : strings) {
			for (int i = 0; i < string.length(); i++) {
				char c = string.charAt(i);
				if (c == ESCAPE_CHAR || c == TERMINATOR_CHAR) {
					out.write(ESCAPE_CHAR);
				}
				out.write(c);
			}
			out.write(TERMINATOR_CHAR);
		};
		return out.toString();
	}

}
