package org.springframework.ide.vscode.xml.namespaces.util;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Scanner to quickly identify the namespace that is declared inside an XSD.
 * @author Martin Lippert
 * @since 2.8.0
 */
public class TargetNamespaceScanner {
	
	private static Logger LOGGER = Logger.getLogger(TargetNamespaceScanner.class.getName());

	/**
	 * Returns the target namespace URI of the XSD identified by the given
	 * <code>url</code>.
	 */
	public static String getTargetNamespace(URL url) {
		if (url == null) {
			return null;
		}

		ClassLoader ccl = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(TargetNamespaceScanner.class.getClassLoader());
			
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setValidating(false);

			factory.setFeature("http://xml.org/sax/features/validation", false);
			factory.setFeature("http://apache.org/xml/features/validation/dynamic", false);
			factory.setFeature("http://apache.org/xml/features/validation/schema", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);

			DocumentBuilder docBuilder = factory.newDocumentBuilder();
			
			Document doc = docBuilder.parse(url.openStream());
			
			return doc.getDocumentElement().getAttribute("targetNamespace");
		} catch (SAXException|IOException|ParserConfigurationException e) {
			LOGGER.log(Level.WARNING, e, null);
		}
		finally {
			Thread.currentThread().setContextClassLoader(ccl);
		}
		return null;
	}

}
