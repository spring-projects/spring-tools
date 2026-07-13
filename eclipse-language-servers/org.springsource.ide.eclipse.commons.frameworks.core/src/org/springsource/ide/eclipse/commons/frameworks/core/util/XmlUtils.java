/*******************************************************************************
 * Copyright (c) 2013, 2026 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.frameworks.core.util;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Helper methods to access pieces of XMLData in a parsed Document.
 */
public class XmlUtils {

	public static String getTagName(Node labelNode) {
		if (labelNode.getNodeType()==Node.ELEMENT_NODE) {
			return labelNode.getNodeName();
		}
		return null;
	}

	/**
	 * Disables DTD/external-entity resolution to prevent XXE when parsing XML from
	 * untrusted sources (e.g. workspace files that may originate from cloned repositories).
	 */
	public static void configureDocumentBuilderFactory(DocumentBuilderFactory factory) throws ParserConfigurationException {
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
	}

	/**
	 * Disables DTD/external-entity resolution to prevent XXE when parsing XML from
	 * untrusted sources (e.g. workspace files that may originate from cloned repositories).
	 */
	public static void configureSaxParserFactory(SAXParserFactory factory) throws ParserConfigurationException, SAXException {
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
	}

}
