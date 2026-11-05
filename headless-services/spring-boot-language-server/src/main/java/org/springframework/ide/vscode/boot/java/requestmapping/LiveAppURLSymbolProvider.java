/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
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
						// no project at hand here, so the version path segment can only come from the app's properties
						VersionedPaths versionedPaths = new VersionedPaths(rm, live, List.of());
						for (String path : versionedPaths.getSplitPath()) {
							String url = UrlUtil.createUrl(urlScheme, host, port, versionedPaths.getVersionedPath(path), contextPath);
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
