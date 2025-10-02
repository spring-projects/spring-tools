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

import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;

import com.google.gson.JsonElement;

import io.micrometer.core.instrument.util.IOUtils;

public class Misc {
	
	private static final String STS_FETCH_CONTENT = "sts/resource/fetch-content";

	public Misc(SimpleLanguageServer server) {
		server.onCommand(STS_FETCH_CONTENT, params -> {
			return server.getAsync().invoke(() -> {
				if (params.getArguments().size() == 1) {
					Object o = params.getArguments().get(0);
					String r = o instanceof JsonElement ? ((JsonElement) o).getAsString() : o.toString();
					return IOUtils.toString(getClass().getResourceAsStream(r));
				}
				throw new IllegalArgumentException("The command must have one parameter.");
			});
		});
	}
	
}
