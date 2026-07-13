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
package org.springsource.ide.eclipse.commons.frameworks.core.maintype;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.springsource.ide.eclipse.commons.frameworks.core.ExceptionUtil;
import org.springsource.ide.eclipse.commons.frameworks.core.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

//TODO: maybe we should use m2e in future. For now its simple enough we can do this ourselves
// and avoid a mandatory dependency on m2e.

/**
 * Wrapper around a pom file. Can parse the pom and retrieve interesting data from it.
 * It caches a Document internally so it avoids repeatedly parsing the same file
 * for repeated info requests.
 */
class PomParser {

	private Document doc;
	private HashMap<String, String> properties = null; //extracted from doc when first prop is requested.
	
	private void parse(InputStream input) throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		XmlUtils.configureDocumentBuilderFactory(dbFactory);
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		this.doc = dBuilder.parse(input);
	}
	

	public PomParser(IFile pomFile) throws CoreException {
		parse(pomFile);
	}


	private void parse(IFile file) throws CoreException {
		InputStream input = null;
		try {
			parse(input = file.getContents(true));
		} catch (Exception e) {
			throw ExceptionUtil.coreException(e);
		} finally {
			if (input!=null) {
				try {
					input.close();
				} catch (IOException e) {
				}
			}
		}
	}

	public String getProperty(String name) {
		Map<String, String> props = getProperties();
		return props.get(name);
	}


	private Map<String, String> getProperties() {
		if (properties==null) {
			properties = new HashMap<String,String>();
			NodeList propLists = doc.getElementsByTagName("properties");
			//There should really only be one propList but anyhoo!
			for (int i = 0; i < propLists.getLength(); i++) {
				Node propList = propLists.item(i);
				NodeList propNodes = propList.getChildNodes();
				for (int j = 0; j < propNodes.getLength(); j++) {
					Node propNode = propNodes.item(j);
					String name = XmlUtils.getTagName(propNode);
					if (name!=null) {
						String value = propNode.getTextContent();
						properties.put(name, value);
					}
				}
			}
		}
		return properties;
	}

	
}
