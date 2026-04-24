/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.mcp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.mcp.SpringIoMcpUrls;

class SpringIoMcpUrlsTest {

	@Test
	void deriveApiBase_stripsProjectsSuffix() {
		assertEquals("https://api.spring.io",
				SpringIoMcpUrls.deriveApiBaseFromProjectsListUrl("https://api.spring.io/projects"));
	}

	@Test
	void deriveApiBase_trimsTrailingSlash() {
		assertEquals("https://api.spring.io",
				SpringIoMcpUrls.deriveApiBaseFromProjectsListUrl("https://api.spring.io/projects/"));
	}

	@Test
	void deriveApiBase_springIoStyle() {
		assertEquals("https://spring.io/api",
				SpringIoMcpUrls.deriveApiBaseFromProjectsListUrl("https://spring.io/api/projects"));
	}

	@Test
	void deriveApiBase_blankFallsBackToDefault() {
		assertEquals("https://api.spring.io", SpringIoMcpUrls.deriveApiBaseFromProjectsListUrl(" "));
	}

	@Test
	void deriveApiBase_nullFallsBackToDefault() {
		assertEquals("https://api.spring.io", SpringIoMcpUrls.deriveApiBaseFromProjectsListUrl(null));
	}
}
