/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.concourse;

import java.util.function.Function;

import org.springframework.ide.vscode.commons.util.RegexpParser;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.ValueParseException;
import org.springframework.ide.vscode.commons.util.ValueParser;
import org.springframework.ide.vscode.commons.util.ValueParsers;
import org.springframework.ide.vscode.commons.yaml.schema.DynamicSchemaContext;
import org.springframework.ide.vscode.commons.yaml.schema.SchemaContextAware;

import com.google.common.collect.Multiset;

/**
 * Methods and constants to create/get parsers for some atomic types
 * used in manifest yml schema.
 *
 * @author Kris De Volder
 */
public class ConcourseValueParsers {

//	public static final SchemaContextAware<ValueParser> resourceTypeName(ConcourseModel models) {
//		return (dc) -> {
//			return new EnumValueParser("ResourceType Name", models.getResourceTypeNames(dc.getDocument())) {
//				@Override
//				protected String createErrorMessage(String value, Collection<String> validValues) {
//					return "The '"+value+"' Resource Type does not exist. Existing resource types: "+validValues;
//				}
//			};
//		};
//	};

	public static final SchemaContextAware<ValueParser> resourceNameDef(ConcourseModel models) {
		return acceptOnlyUniqueNames(models::getResourceNames, "resource name");
	}

	public static final SchemaContextAware<ValueParser> jobNameDef(ConcourseModel models) {
		return acceptOnlyUniqueNames(models::getJobNames, "job name");
	}

	public static SchemaContextAware<ValueParser> resourceTypeNameDef(ConcourseModel models) {
		return acceptOnlyUniqueNames(dc -> models.getResourceTypeNames(dc, false), "resource-type name");
	}

	public static SchemaContextAware<ValueParser> acceptOnlyUniqueNames(
			Function<DynamicSchemaContext, Multiset<String>> getDefinedNameCounts,
			String typeName
	) {
		return acceptOnlyUniqueNames(getDefinedNameCounts, typeName, false);
	}

	public static SchemaContextAware<ValueParser> acceptOnlyUniqueNames(
			Function<DynamicSchemaContext, Multiset<String>> getDefinedNameCounts,
			String typeName,
			boolean allowEmptyName
	) {
		return (dc) -> {
			return (String input) -> {
				if (!allowEmptyName && !StringUtil.hasText(input)) {
					throw new ValueParseException("'"+typeName +"' should not be blank");
				}
				Multiset<String> resourceNames = getDefinedNameCounts.apply(dc);
				if (resourceNames.count(input) <= 1) {
					//okay
					return input;
				}
				throw new ValueParseException("Duplicate "+typeName+" '"+input+"'");
			};
		};
	};

	public static ValueParser DURATION = new RegexpParser(
			"^(([0-9]+(.[0-9]+)?)(ns|us|µs|ms|s|h|m))+$",
			"Duration",
			" A duration string is a sequence of decimal numbers, each with "
					+ "optional fraction and a unit suffix, such as '300ms', '1.5h' or"
					+ " '2h45m'. Valid time units are 'ns', 'us' (or 'µs'), 'ms', 's', "
			+ "'m', 'h'."
	);

	public static final ValueParser TIME_OF_DAY = new RegexpParser(
			createTimeRegexp(),
			"Time",
			"Supported time formats are: 3:04 PM, 3PM, 3 PM, 15:04, and 1504. "
			+ "Deprecation: an offset may be appended, e.g. +0700 or -0400, but "
			+ "you should use location instead."
	);

	public static final ValueParser IDENTIFIER = new RegexpParser(
			"(\\d|[\\p{L}&&[^\\p{Lu}]]|\\-|_|\\.)*",
			"Identifier", 
			"An identifier is a string value comprised of Unicode lowercase letters, Decimal numbers, hyphens (`-`)" +
			" underscores (`_`) and periods (`.`)");

	public static final ValueParser MAX_IN_FLIGHT_OR_ALL = (str) -> {
		if (str.equals("all")) {
			return str;
		}
		try {
			return ValueParsers.STRICTLY_POS_INTEGER.parse(str);
		} catch (Exception e ) {
			throw new IllegalArgumentException("Expecting either 'all' or an integer greater than 0");
		}
	};

	private static String createTimeRegexp() {
		String hours = "([0-2]?[0-9])";
		String minutes = "([0-6][0-9])";
		String time = hours+"((:"+minutes+")|"+minutes+")?";
		String pm = "(\\s?[AP]M)?";
		String zone = "(\\s(\\+|\\-)[0-9][0-9][0-9][0-9])?";

		return time + pm + zone;
	}

}
