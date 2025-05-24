/*******************************************************************************
 * Copyright (c) 2014-2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.util;

import java.util.Collection;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableSet;

/**
 * Parser for checking a 'Enum' style values.
 *
 * @author Kris De Volder
 */
public class EnumValueParser implements ValueParser {

	private String typeName;

	private Supplier<PartialCollection<String>> values;
    private final boolean longRunning;


	public EnumValueParser(String typeName, String... values) {
		this(typeName, ImmutableSet.copyOf(values));
	}

	public EnumValueParser(String typeName, Collection<String> values) {
		this(typeName, false /* not long running by default */, provider(values));
	}

	private static <T> Supplier<PartialCollection<T>> provider(Collection<T> values) {
		return () -> PartialCollection.compute(() -> values);
	}
	
	private static <T> Supplier<PartialCollection<T>> supplier(Callable<Collection<T>> values) {
		return () -> PartialCollection.compute(() -> values.call());
	}

	public EnumValueParser(String typeName, boolean longRunning, Callable<Collection<String>> values) {
		this(typeName, longRunning, supplier(values));
	}

	public EnumValueParser(String typeName, boolean longRunning, Supplier<PartialCollection<String>> values) {
		this.typeName = typeName;
		this.values = values;
		this.longRunning = longRunning;
	}

	public EnumValueParser(String name, PartialCollection<String> values) {
		this(name, false /* not long running by default */, () -> values);
	}

	@Override
	public Object parse(String str) throws Exception {
		// IMPORTANT: check the text FIRST before fetching values
		// from the hints provider, as the hints provider may be expensive when
		// resolving values
		if (!StringUtil.hasText(str)) {
			throw errorOnBlank(createBlankTextErrorMessage());
		}

		PartialCollection<String> values = this.values.get();

		// If values is not fully known then just assume the str is acceptable.
		if (values == null || !values.isComplete() || hasMatchingValue(str, values.getElements())) {
			return str;
		} else {
			throw errorOnParse(createErrorMessage(str, values.getElements()));
		}
	}

	protected boolean hasMatchingValue(String str, Collection<String> values) {
		return values.contains(str);
	}

	protected String createBlankTextErrorMessage() {
		return "'" + typeName + "'" + " cannot be blank.";
	}

	protected String createErrorMessage(String parseString, Collection<String> values) {
		return "'" + parseString + "' is an unknown '" + typeName + "'. Valid values are: " + new TreeSet<>(values);
	}

	protected Exception errorOnParse(String message) {
		return new ValueParseException(message);
	}

	protected Exception errorOnBlank(String message) {
		return new ValueParseException(message);
	}
	
	public boolean longRunning() {
		return this.longRunning ;
	}
}
