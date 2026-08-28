/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.util;

import org.springsource.ide.eclipse.commons.core.util.StringUtil;

/**
 * Utility methods
 *
 * @author Kris De Volder
 */
public class Utils {

	/**
	 * Creates http URL string based on host, port and path
	 * @param host
	 * @param port
	 * @param path
	 * @return the resultant URL
	 */
	public static String createUrl(String host, int port, String path) {
		if (path == null) {
			path = "";
		}
		if (host != null) {
			if (port > 0) {
				if (!path.startsWith("/")) {
					path = "/" +path;
				}
				return "http://"+host+":"+port+path;
			}
		}
		return null;
	}

	public static String pathJoin(String p1, String p2) {
		if (!StringUtil.hasText(p1)) {
			return p2;
		}
		if (!StringUtil.hasText(p2)) {
			return p1;
		}
		while (p1.endsWith("/")) {
			p1 = p1.substring(0, p1.length()-1);
		}
		while (p2.startsWith("/")) {
			p2 = p2.substring(1);
		}
		return p1+"/"+p2;
	}

}
