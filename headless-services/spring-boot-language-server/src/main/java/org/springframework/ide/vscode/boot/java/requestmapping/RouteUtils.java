/*******************************************************************************
 * Copyright (c) 2018, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.requestmapping;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

/**
 * @author Martin Lippert
 */
public class RouteUtils {
	
	public static WorkspaceSymbol createRouteSymbol(Location location, String path,
			String[] httpMethods, String[] contentTypes, String[] acceptTypes, String version) {
		
		if (path != null && path.length() > 0) {
			String label = "@" + (path.startsWith("/") ? path : ("/" + path));
			label += (httpMethods == null || httpMethods.length == 0 ? "" : " -- " + WebFnUtils.getStringRep(httpMethods, string -> string));
			
			label += version != null ? " - Version: " + version : "";
			
			String acceptType = WebFnUtils.getStringRep(acceptTypes, WebFnUtils::getMediaType);
			label += acceptType != null ? " - Accept: " + acceptType : "";
			
			String contentType = WebFnUtils.getStringRep(contentTypes, WebFnUtils::getMediaType);
			label += contentType != null ? " - Content-Type: " + contentType : "";

			return new WorkspaceSymbol(label, SymbolKind.Interface, Either.forLeft(location));
		}
		else {
			return null;
		}
		
	}

}
