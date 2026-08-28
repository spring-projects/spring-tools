/*******************************************************************************
 * Copyright (c) 2016-2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.boot.common;

import static org.springframework.ide.vscode.commons.util.StringUtil.camelCaseToHyphens;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.metadata.PropertyInfo;
import org.springframework.ide.vscode.boot.metadata.hints.HintProvider;
import org.springframework.ide.vscode.boot.metadata.hints.HintProviders;
import org.springframework.ide.vscode.boot.metadata.hints.StsValueHint;
import org.springframework.ide.vscode.boot.metadata.types.Type;
import org.springframework.ide.vscode.boot.metadata.types.TypeParser;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil.EnumCaseMode;
import org.springframework.ide.vscode.boot.properties.reconcile.PropertyNavigator;
import org.springframework.ide.vscode.commons.util.CollectionUtil;
import org.springframework.ide.vscode.commons.util.FuzzyMap;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment;

public class CommonLanguageTools {
	
	private static final Logger log = LoggerFactory.getLogger(CommonLanguageTools.class);


	public static final Pattern SPACES = Pattern.compile(
			"(\\s|\\\\\\s)*"
	);

	public static boolean isValuePrefixChar(char c) {
		return !Character.isWhitespace(c) && c != ',';
	}

	/**
	 * Determine the value type for a give propertyName.
	 */
	public static Type getValueType(FuzzyMap<PropertyInfo> index, TypeUtil typeUtil, String propertyName) {
		try {
			PropertyInfo prop = index.get(propertyName);
			if (prop != null) {
				return TypeParser.parse(prop.getType());
			} else {
				prop = CommonLanguageTools.findLongestValidProperty(index, propertyName);
				if (prop != null) {
					TextDocument doc = new TextDocument(null, LanguageId.PLAINTEXT);
					doc.setText(propertyName);
					PropertyNavigator navigator = new PropertyNavigator(doc, null, typeUtil, new DocumentRegion(doc, 0, doc.getLength()));
					return navigator.navigate(prop.getId().length(), TypeParser.parse(prop.getType()));
				}
			}
		} catch (Exception e) {
			log.error("", e);
		}
		return null;
	}

	public static Collection<StsValueHint> getValueHints(FuzzyMap<PropertyInfo> index, TypeUtil typeUtil, String query, String propertyName, EnumCaseMode caseMode) {
		Type type = getValueType(index, typeUtil, propertyName);
		if (typeUtil.isSequencable(type)) {
			//It is useful to provide content assist for the values in the list when entering a list
			type = TypeUtil.getDomainType(type);
		}
		List<StsValueHint> allHints = new ArrayList<>();
		{
			Collection<StsValueHint> hints = typeUtil.getHintValues(type, query, caseMode);
			if (CollectionUtil.hasElements(hints)) {
				allHints.addAll(hints);
			}
		}
		{
			PropertyInfo prop = index.findLongestCommonPrefixEntry(propertyName);
			if (prop != null) {
				HintProvider hintProvider = prop.getHints(typeUtil);
				if (prop.getId().length() < propertyName.length()) {
					//true prefix
					//TODO: properly process remaining portion of property name
					try {
						hintProvider = hintProvider.traverse(YamlPathSegment.valueAt(0));
					} catch (Exception e) {
						log.error("", e);
					}
				}
				if (!HintProviders.isNull(hintProvider)) {
					allHints.addAll(hintProvider.getValueHints(query));
				}
			}
		}
		return allHints;
	}

	/**
	 * Find the longest known property that is a prefix of the given name. Here prefix does not mean
	 * 'string prefix' but a prefix in the sense of treating '.' as a kind of separators. So
	 * 'prefix' is not allowed to end in the middle of a 'segment'.
	 */
	public static PropertyInfo findLongestValidProperty(FuzzyMap<PropertyInfo> index, String name) {
		int bracketPos = name.indexOf('[');
		int endPos = bracketPos >= 0 ? bracketPos : name.length();
		PropertyInfo prop = null;
		String prefix = null;
		while (endPos > 0 && prop == null) {
			prefix = name.substring(0, endPos);
			String canonicalPrefix = camelCaseToHyphens(prefix);
			prop = index.get(canonicalPrefix);
			if (prop == null) {
				endPos = name.lastIndexOf('.', endPos-1);
			}
		}
		if (prop != null) {
			//We should meet caller's expectation that matched properties returned by this method
			// match the names exactly even if we found them using relaxed name matching.
			return prop.withId(prefix);
		}
		return null;
	}

}
