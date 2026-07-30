/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.core.util;

/**
 * Simple name generator implementation
 *
 * @author Alex Boyko
 * @author Kris De Volder
 */
public class NameGenerator {

	private static final String delimiter = "-";
	private static final String DEFAULT_PREFIX = "demo";

	private String prefix;
	private int number = 0;
	private boolean plainPrefixAvailable;

	public NameGenerator(String previousName) {
		if (previousName == null || previousName.trim().isEmpty()) {
			previousName = DEFAULT_PREFIX;
			plainPrefixAvailable = true;
		}
		prefix = previousName.replace(' ', '-'); //See: https://github.com/spring-projects/spring-ide/issues/230
		int d = previousName.lastIndexOf(delimiter);
		if (d >= 0) {
			try {
				String numString = previousName.substring(d+1);
				number = Integer.parseInt(numString);
				prefix = previousName.substring(0, d);
			} catch (NumberFormatException e) {
				//ignore
			}
		}
	}

	public String generateNext() {
		if (plainPrefixAvailable) {
			plainPrefixAvailable = false;
			return prefix;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(prefix);
		sb.append(delimiter);
		sb.append(++number);
		return sb.toString();
	}

}
