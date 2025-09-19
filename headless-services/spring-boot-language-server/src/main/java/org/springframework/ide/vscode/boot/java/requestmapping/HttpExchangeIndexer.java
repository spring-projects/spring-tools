/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.requestmapping;

import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.Annotation;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;

/**
 * @author Martin Lippert
 */
public class HttpExchangeIndexer {
	
	private static final Set<String> ATTRIBUTE_NAME_VALUE_PATH = Set.of("value", "url");

	private static final Set<String> ATTRIBUTE_NAME_METHOD = Set.of("method");
	private static final Set<String> ATTRIBUTE_NAME_ACCEPT = Set.of("accept");
	private static final Set<String> ATTRIBUTE_NAME_CONTENT_TYPE = Set.of("contentType");
	private static final Set<String> ATTRIBUTE_NAME_VERSION = Set.of("version");
	
	private static final Map<String, String[]> METHOD_MAPPING = Map.of(
			Annotations.GET_EXCHANGE, new String[] { "GET" },
			Annotations.POST_EXCHANGE, new String[] { "POST" },
			Annotations.DELETE_EXCHANGE, new String[] { "DELETE" },
			Annotations.PUT_EXCHANGE, new String[] { "PUT" },
			Annotations.PATCH_EXCHANGE, new String[] { "PATCH" }
			);

	
	public static String[] getPath(Annotation node, SpringIndexerJavaContext context) {
		return WebEndpointIndexer.getPath(node, context, ATTRIBUTE_NAME_VALUE_PATH);
	}

	public static String[] getParentPath(Annotation node, SpringIndexerJavaContext context) {
		return WebEndpointIndexer.getParentPath(node, context, ATTRIBUTE_NAME_VALUE_PATH, Annotations.HTTP_EXCHANGE);
	}
	
	public static String[] getAcceptTypes(Annotation node, SpringIndexerJavaContext context) {
		return WebEndpointIndexer.getAttributeValues(node, context, ATTRIBUTE_NAME_ACCEPT, Annotations.HTTP_EXCHANGE);
	}
	
	public static String[] getContentTypes(Annotation node, SpringIndexerJavaContext context) {
		return WebEndpointIndexer.getAttributeValues(node, context, ATTRIBUTE_NAME_CONTENT_TYPE, Annotations.HTTP_EXCHANGE);
	}

	public static String[] getMethod(Annotation node, SpringIndexerJavaContext context) {
		return WebEndpointIndexer.getMethod(node, context, ATTRIBUTE_NAME_METHOD, METHOD_MAPPING, Annotations.HTTP_EXCHANGE);
	}

	public static String getVersion(Annotation node, SpringIndexerJavaContext context) {
		String[] versions = WebEndpointIndexer.getAttributeValues(node, context, ATTRIBUTE_NAME_VERSION, Annotations.SPRING_REQUEST_MAPPING);
		return versions != null && versions.length == 1 ? versions[0] : null;
	}

}
