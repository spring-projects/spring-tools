/*******************************************************************************
 * Copyright (c) 2020, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.springframework.ide.vscode.commons.util.UriUtil;

public class DocumentDescriptor {
	
	private final String docURI;
	private final String file;

	private final long lastModified;
	
	private DocumentDescriptor(String file, String docURI, long lastModified) {
		this.file = file;
		this.docURI = docURI;
		this.lastModified = lastModified;
	}
	
	public String getFile() {
		return file;
	}

	public String getDocURI() {
		return docURI;
	}

	public long getLastModified() {
		return lastModified;
	}

	public static DocumentDescriptor createFromFile(String fileName) {
		File file = new File(fileName);
		long lastModified = file.lastModified();
		return new DocumentDescriptor(fileName, UriUtil.toUri(file).toASCIIString(), lastModified);
	}

	public static DocumentDescriptor createFromFile(String fileName, long lastModified) {
		File file = new File(fileName);
		return new DocumentDescriptor(fileName, UriUtil.toUri(file).toASCIIString(), lastModified);
	}

	public static DocumentDescriptor createFromUri(String docUri) {
		try {
			File file = new File(new URI(docUri));
			long lastModified = file.lastModified();
			return new DocumentDescriptor(file.getAbsolutePath(), docUri, lastModified);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static List<DocumentDescriptor> createFromUris(List<String> docUris) {
		return docUris.stream()
				.map(docUri -> createFromUri(docUri))
				.toList();
	}
	
}
