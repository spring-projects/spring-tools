/*******************************************************************************
 * Copyright (c) 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.docker.runtarget;

public class CommandUtil {
	
	public static String escape(String[] command) {
		StringBuilder buf = new StringBuilder();
		boolean first = true;
		for (String piece : command) {
			if (!first) {
				buf.append(" ");
			}
			for (int i = 0; i < piece.length(); i++) {
				char c = piece.charAt(i);
				if (needsEscape(c)) {
					buf.append("\\");
				}
				buf.append(c);
			}
			first = false;
		}
		return buf.toString();
	}

	private static boolean needsEscape(char c) {
		return Character.isWhitespace(c) || c == '"' || c == '\\';
	}
}
