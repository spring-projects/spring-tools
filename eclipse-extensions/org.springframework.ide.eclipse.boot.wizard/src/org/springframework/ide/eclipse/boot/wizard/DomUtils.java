/*******************************************************************************
 * Copyright (c) 2014 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public abstract class DomUtils {

	public static String getAttributeValue(Node node, String attribName ) {
		NamedNodeMap attribs = node.getAttributes();
		if (attribs != null) {
			Node value = attribs.getNamedItem(attribName);
			if (value != null) {
				short nodeType = value.getNodeType();
				if (nodeType == Node.ATTRIBUTE_NODE) {
					return value.getNodeValue();
				}
			}
		}
		return null;
	}

	public static boolean getAttributeValue(Node node, String name, boolean defaultValue) {
		String str = getAttributeValue(node, name);
		if (str == null) {
			return defaultValue;
		} else {
			return "true".equals(str);
		}
	}

}
