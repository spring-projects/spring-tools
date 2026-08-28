/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.bosh;

import java.net.URI;

import org.springframework.ide.vscode.commons.util.ValueParseException;
import org.springframework.ide.vscode.commons.util.ValueParser;

import com.google.common.collect.ImmutableList;

public class BoshValueParsers {
	
	public static ValueParser url(String... _schemes) {
		return new ValueParser() {

			private final ImmutableList<String> validSchemes = ImmutableList.copyOf(_schemes);

			@Override
			public Object parse(String s) throws Exception {
				URI uri = new URI(s);
				String scheme = uri.getScheme();
				if (scheme == null) {
					throw new ValueParseException(message());
				} else if (!validSchemes.contains(scheme.toLowerCase())) {
					int start = s.indexOf(scheme);
					if (start >= 0) {
						int end = start + scheme.length();
						throw new ValueParseException(message(), start, end);
					} else {
						// Trouble finding exact location of underlined region so underline whole url
						throw new ValueParseException(message());
					}
				}
				return uri;
			}

			private String message() {
				return "Url scheme must be one of "+validSchemes;
			}
		};
	}

	public static final ValueParser INTEGER_OR_RANGE = new ValueParser() {

		private int findDash(String s) throws ValueParseException {
			int firstDash = s.indexOf('-');
			if (firstDash < 0) {
				return firstDash; //no dash... and that's okay!
			}
			int secondDash = s.indexOf('-', firstDash+1);
			if (secondDash >= 0) {
				//Only one dash is expected!
				throw new ValueParseException("Should be either a Integer, or a range (of the form '<integer>-<integer>')");
			}
			return firstDash;
		}

		@Override
		public Object parse(String s) throws Exception {
			int dash = findDash(s);
			if (dash >= 0) {
				int low, high;
				//range
				try {
					low = Integer.parseInt(s.substring(0, dash));
				} catch (Exception e) {
					throw new ValueParseException("Should be a Integer", 0, dash);
				}
				try {
					high = Integer.parseInt(s.substring(dash+1));
				} catch (Exception e) {
					throw new ValueParseException("Should be a Integer", dash+1, s.length());
				}
				if (low > high) {
					throw new ValueParseException(low + " should be smaller than "+high);
				}
			} else {
				//integer
				try {
					return Integer.parseInt(s);
				} catch (Exception e) {
					throw new ValueParseException("Should be either a Integer, or a range (of the form '<integer>-<integer>')");
				}
			}
			return s;
		}
	};


}
