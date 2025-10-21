/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.commands;

import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.util.IOUtil;

import com.google.gson.JsonElement;


public class Misc {
	
	public static final String BOOT_LS_URL_PRTOCOL_PREFIX = "spring-boot-ls:";
	public static final String JAR = "jar";
	public static final String JAR_URL_PROTOCOL_PREFIX = JAR + ":";
	
	private static final String STS_FETCH_JAR_CONTENT = "sts/jar/fetch-content";

	public Misc(SimpleLanguageServer server) {
		// Fetch JAR content via a special protocol `spring-boot-ls` as we don't want to handle all JARs in VSCode 
		server.onCommand(STS_FETCH_JAR_CONTENT, params -> {
			return server.getAsync().invoke(() -> {
				if (params.getArguments().size() == 1) {
					Object o = params.getArguments().get(0);
					String s = o instanceof JsonElement ? ((JsonElement) o).getAsString() : o.toString();
					if (s.startsWith(BOOT_LS_URL_PRTOCOL_PREFIX)) {
						s = s.replaceFirst(BOOT_LS_URL_PRTOCOL_PREFIX, JAR_URL_PROTOCOL_PREFIX);
						URI uri = URI.create(URLDecoder.decode(s, StandardCharsets.UTF_8));
						// Java has support for JAR URLs
						return IOUtil.toString((InputStream) uri.toURL().getContent());
					}
				}
				throw new IllegalArgumentException("The command must have one valid URL parameter.");
			});
		});
	}
	
}
