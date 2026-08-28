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

import org.springframework.ide.vscode.commons.util.StringUtil;

public class UrlUtil {

	/**
	 * Creates http URL string based on host, port and path
	 * @param host
	 * @param port
	 * @param path
	 * @param contextPath
	 * @return the resultant URL
	 */
	public static String createUrl(String urlScheme, String host, String port, String path, String contextPath) {
		if (path == null) {
			path = "";
		}
		if (host != null) {
			if (port != null) {
				if (!path.startsWith("/")) {
					path = "/" +path;
				}
				if (StringUtil.hasText(contextPath)) {
					if (!contextPath.startsWith("/")) {
						contextPath = "/" + contextPath;
					}
					path = contextPath + path;
				}
				String defaultPort = "";
				if (urlScheme.equals("http")) {
					defaultPort = "80";
				} else if (urlScheme.equals("https")) {
					defaultPort = "443";
				}
				if (defaultPort.equals(port)) {
					return urlScheme+"://"+host+path;
				} else {
					return urlScheme+"://"+ host + ":" + port +path;
				}
			}
		}
		return null;
	}

}
