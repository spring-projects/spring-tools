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
package org.springframework.ide.vscode.boot.properties.reconcile;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ide.vscode.commons.util.EnumValueParser;
import org.springframework.ide.vscode.commons.util.StringUtil;

public class BootEnumValueParser extends EnumValueParser {
	
	private Set<String> canonicalValues;
	
	private static String getCanonicalName(String name) {
		StringBuilder canonicalName = new StringBuilder(name.length());
		name.chars()
			.filter(Character::isLetterOrDigit)
			.map(Character::toLowerCase)
			.forEach((c) -> canonicalName.append((char) c));
		return canonicalName.toString();
	}

	public BootEnumValueParser(String typeName, String[] values) {
		super(typeName, values);
		this.canonicalValues = Arrays.stream(values).map(BootEnumValueParser::getCanonicalName).collect(Collectors.toSet());
	}

	@Override
	public Object parse(String str) throws Exception {
		// IMPORTANT: check the text FIRST before fetching values
		// from the hints provider, as the hints provider may be expensive when
		// resolving values
		if (!StringUtil.hasText(str)) {
			throw errorOnBlank(createBlankTextErrorMessage());
		}

		// If values is not fully known then just assume the str is acceptable.
		if (canonicalValues.contains(getCanonicalName(str))) {
			return str;
		} else {
			throw errorOnParse(createErrorMessage(str, getAllKnownValues()));
		}
	}

}
