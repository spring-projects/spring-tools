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
package org.springframework.ide.vscode.boot.java.requestmapping;

/**
 * @author Martin Lippert
 */
public enum MediaTypeMapping {
	
	ALL("*/*"),
	APPLICATION_ATOM_XML("application/atom+xml"),
	APPLICATION_CBOR("application/cbor"),
	APPLICATION_FORM_URLENCODED("application/x-www-form-urlencoded"),
	APPLICATION_GRAPHQL_RESPONSE("application/graphql-response+json"),
	APPLICATION_JSON("application/json"),
	APPLICATION_NDJSON("application/x-ndjson"),
	APPLICATION_JSON_UTF8("application/json;charset=UTF-8"),
	APPLICATION_OCTET_STREAM("application/octet-stream"),
	APPLICATION_PDF("application/pdf"),
	APPLICATION_PROBLEM_JSON("application/problem+json"),
	APPLICATION_PROBLEM_JSON_UTF8("application/problem+json;charset=UTF-8"),
	APPLICATION_PROBLEM_XML("application/problem+xml"),
	APPLICATION_PROTOBUF("application/x-protobuf"),
	APPLICATION_RSS_XML("application/rss+xml"),
	APPLICATION_STREAM_JSON("application/stream+json"),
	APPLICATION_XHTML_XML("application/xhtml+xml"),
	APPLICATION_XML("application/xml"),
	APPLICATION_YAML("application/yaml"),
	IMAGE_GIF("image/gif"),
	IMAGE_JPEG("image/jpeg"),
	IMAGE_PNG("image/png"),
	MULTIPART_FORM_DATA("multipart/form-data"),
	MULTIPART_MIXED("multipart/mixed"),
	MULTIPART_RELATED("multipart/related"),
	TEXT_EVENT_STREAM("text/event-stream"),
	TEXT_HTML("text/html"),
	TEXT_MARKDOWN("text/markdown"),
	TEXT_PLAIN("text/plain"),
	TEXT_XML("text/xml");
	
	private String mediaType;

	private MediaTypeMapping(String mediaType) {
		this.mediaType = mediaType;
	}
	
	public String getMediaType() {
		return mediaType;
	}

}


