/*******************************************************************************
 * Copyright (c) 2019, 2026 Pivotal, Inc.
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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

public class DocumentUtils {

	public static TextDocument createTempTextDocument(String docURI) {
		try {
			Path path = new File(new URI(docURI)).toPath();
			String content = new String(Files.readAllBytes(path));
			
			return createTempTextDocumentInternal(docURI, content);
		}
		catch (IOException | URISyntaxException e) {
			throw new RuntimeException();
		}
	}

	public static TextDocument createTempTextDocument(String docURI, String content) {
		if (content == null) {
			return createTempTextDocument(docURI);
		}
		else {
			return createTempTextDocumentInternal(docURI, content);
		}
	}

	private static TextDocument createTempTextDocumentInternal(String docURI, String content) {
		TextDocument doc = new TextDocument(docURI, LanguageId.PLAINTEXT, 0, content);
		return doc;
	}

}
