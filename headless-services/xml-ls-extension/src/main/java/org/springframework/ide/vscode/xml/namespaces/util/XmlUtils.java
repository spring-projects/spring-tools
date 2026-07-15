package org.springframework.ide.vscode.xml.namespaces.util;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Shared XML parsing security configuration.
 */
public class XmlUtils {

	/**
	 * Disables DTD/external-entity resolution to prevent XXE when parsing XML from
	 * untrusted sources (e.g. workspace files or classpath resources that may originate
	 * from a project's dependencies).
	 */
	public static void configureDocumentBuilderFactory(DocumentBuilderFactory factory) throws ParserConfigurationException {
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
	}

}
