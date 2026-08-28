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
package org.springframework.ide.vscode.commons.util;

/**
 * Constants and static methods to create generally useful value parsers.
 *
 * @author Kris De Volder
 */
public class ValueParsers {

	public static final ValueParser NE_STRING = (s) -> {
		if (StringUtil.hasText(s)) {
			return s;
		} else {
			throw new ValueParseException("String should not be empty");
		}
	};
	
	public static final ValueParser POS_INTEGER = integerRange(0, null);
	
	public static final ValueParser STRICTLY_POS_INTEGER = integerAtLeast(1);

	public static ValueParser integerAtLeast(final Integer lowerBound) {
		return integerRange(lowerBound, null);
	}

	public static ValueParser integerRange(final Integer lowerBound, final Integer upperBound) {
		Assert.isLegal(lowerBound == null || upperBound == null || lowerBound <= upperBound);
		return new ValueParser() {
			@Override
			public Object parse(String str) throws Exception {
				str = str.replace("_", ""); //Tolerate and ignore underscores in integers.
				int value = Integer.parseInt(str);
				if (lowerBound != null && value < lowerBound) {
					throw new NumberFormatException("Value must be at least "+lowerBound);
				}
				if (upperBound != null && value > upperBound) {
					throw new NumberFormatException("Value must be at most "+upperBound);
				}
				return value;
			}
		};
	}


}
