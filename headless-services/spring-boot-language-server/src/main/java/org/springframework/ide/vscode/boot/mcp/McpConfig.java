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

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ide.vscode.boot.mcp.prompts.Prompts;

import io.modelcontextprotocol.server.McpServerFeatures;

/**
 * The tools in this package are annotated with {@code @McpTool} and picked up by Spring AI's
 * MCP annotation scanner, which publishes their read-only/write hints to MCP clients.
 *
 * @author Martin Lippert
 */
@Configuration
public class McpConfig {

	/**
	 * MCP protocol prompts (templates clients can expand into user/assistant messages), separate from tools.
	 */
	@Bean
	List<McpServerFeatures.SyncPromptSpecification> springToolsPrompts() {
		return Prompts.PROMPTS;
	}

}
