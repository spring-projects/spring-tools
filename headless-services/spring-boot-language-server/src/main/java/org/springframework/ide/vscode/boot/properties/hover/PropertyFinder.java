/*******************************************************************************
 * Copyright (c) 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.properties.hover;

import org.springframework.ide.vscode.boot.metadata.PropertyInfo;
import org.springframework.ide.vscode.boot.metadata.SpringPropertyIndex;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.FuzzyMap;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.java.properties.antlr.parser.AntlrParser;
import org.springframework.ide.vscode.java.properties.parser.ParseResults;
import org.springframework.ide.vscode.java.properties.parser.PropertiesAst.Node;

public class PropertyFinder {

	final FuzzyMap<PropertyInfo> index;
	final TypeUtil typeUtil;
	final IDocument doc;
	final int offset;
	final AntlrParser parser;

	public PropertyFinder(FuzzyMap<PropertyInfo> index, TypeUtil typeUtil, IDocument doc, int offset) {
		this.index = index;
		this.typeUtil = typeUtil;
		this.doc = doc;
		this.offset = offset;
		this.parser = new AntlrParser();
	}

	public Node findNode() {
		ParseResults parseResults = parser.parse(doc.get());
		return parseResults.ast.findNode(offset);
	}

	public DocumentRegion createRegion(Node value) {
		// Trim trailing spaces (there is no leading white space already)
		int length = value.getLength();
		try {
			length = doc.get(value.getOffset(), value.getLength()).length();
		} catch (BadLocationException e) {
			// ignore
		}
		return new DocumentRegion(doc, value.getOffset(), value.getOffset() + length);
	}

	/**
	 * Search known properties for the best 'match' to show as hover data.
	 */
	public PropertyInfo findBestHoverMatch(String propName) {
		PropertyInfo propertyInfo = index.get(propName);
		if (propertyInfo == null) {
			propertyInfo = SpringPropertyIndex.findLongestValidProperty(index, propName);
		}
		return propertyInfo;
//		//TODO: optimize, should be able to use index's treemap to find this without iterating all entries.
//		PropertyInfo best = null;
//		int bestCommonPrefixLen = 0; //We try to pick property with longest common prefix
//		int bestExtraLen = Integer.MAX_VALUE;
//		for (PropertyInfo candidate : index) {
//			int commonPrefixLen = StringUtil.commonPrefixLength(propName, candidate.getId());
//			int extraLen = candidate.getId().length()-commonPrefixLen;
//			if (commonPrefixLen == propName.length() && extraLen == 0) {
//				//exact match found, can stop searching for better matches
//				return candidate;
//			}
//			//candidate is better if...
//			if (commonPrefixLen>bestCommonPrefixLen // it has a longer common prefix
//			|| commonPrefixLen == bestCommonPrefixLen && extraLen<bestExtraLen //or same common prefix but fewer extra chars
//			) {
//				bestCommonPrefixLen = commonPrefixLen;
//				bestExtraLen = extraLen;
//				best = candidate;
//			}
//		}
//		return best;
	}

}
