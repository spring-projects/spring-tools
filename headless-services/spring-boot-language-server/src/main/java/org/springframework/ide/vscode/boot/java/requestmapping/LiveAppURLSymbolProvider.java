/*******************************************************************************
 * Copyright (c) 2017, 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.requestmapping;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.livehover.v2.LiveRequestMapping;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessLiveData;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessLiveDataProvider;

/**
 * @author Martin Lippert
 */
public class LiveAppURLSymbolProvider {

	private static final Logger log = LoggerFactory.getLogger(LiveAppURLSymbolProvider.class);

	private final SpringProcessLiveDataProvider liveDataProvider;

	public LiveAppURLSymbolProvider(SpringProcessLiveDataProvider liveDataProvider) {
		this.liveDataProvider = liveDataProvider;
	}

	public List<? extends WorkspaceSymbol> getSymbols(String query) {
		List<WorkspaceSymbol> result = new ArrayList<>();

		try {
			SpringProcessLiveData[] liveData = liveDataProvider.getLatestLiveData();
			for (SpringProcessLiveData live : liveData) {
				try {
					String urlScheme = live.getUrlScheme();
					String host = live.getHost();
					String port = live.getPort();
					String contextPath = live.getContextPath();
					for (LiveRequestMapping rm : live.getRequestMappings()) {
						String[] paths = rm.getSplitPath();
						if (paths == null || paths.length == 0) {
							//Technically, this means the path 'predicate' is unconstrained, meaning any path matches.
							//So this is not quite the same as the case where path=""... but...
							//It is better for us to show one link where any path is allowed, versus showing no links where any link is allowed.
							//So we'll pretend this is the same as path="" as that gives a working link.
							paths = new String[] {""};
						}
						for (String path : paths) {
							String url = UrlUtil.createUrl(urlScheme, host, port, path, contextPath);
							result.add(new WorkspaceSymbol(url, SymbolKind.Method, Either.forLeft(new Location(url, new Range(new Position(0, 0), new Position(0, 1))))));
						}
					}
				}
				catch (Exception e) {
					log.error("", e);
				}
			}
		} catch (Exception e) {
			log.error("", e);
		}

		return result;
	}

}
