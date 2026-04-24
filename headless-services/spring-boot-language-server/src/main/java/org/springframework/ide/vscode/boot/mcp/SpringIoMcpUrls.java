/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.mcp;

/**
 * Derives MCP RestClient base URLs from the same workspace settings as
 * {@link org.springframework.ide.vscode.boot.app.BootJavaConfig#getSpringIOApiUrl()}.
 */
public final class SpringIoMcpUrls {

	private SpringIoMcpUrls() {
	}

	/**
	 * Strips a trailing {@code /projects} segment from the configured projects index URL
	 * (for example {@code https://api.spring.io/projects}) so HAL paths such as
	 * {@code /projects/spring-boot/releases} resolve against the correct origin.
	 *
	 * @param projectsListUrl value from {@code BootJavaConfig#getSpringIOApiUrl()}
	 * @return API origin without trailing slash, never {@code null}
	 */
	public static String deriveApiBaseFromProjectsListUrl(String projectsListUrl) {
		if (projectsListUrl == null || projectsListUrl.isBlank()) {
			return "https://api.spring.io";
		}
		String u = projectsListUrl.trim();
		while (u.endsWith("/")) {
			u = u.substring(0, u.length() - 1);
		}
		if (u.endsWith("/projects")) {
			return u.substring(0, u.length() - "/projects".length());
		}
		return u;
	}
}
