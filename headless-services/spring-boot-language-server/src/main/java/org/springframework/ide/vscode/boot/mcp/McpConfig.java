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

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.ide.vscode.boot.mcp.prompts.Prompts;

import io.modelcontextprotocol.server.McpServerFeatures;

/**
 * @author Martin Lippert
 */
@Configuration
public class McpConfig {
	
	@Bean
	ToolCallbackProvider registerTools(
    		SpringVersionsAndGenerations springVersionsAndGenerations,
    		SpringIoApiMcpTools springIoApiMcpTools,
    		SpringIndexAccess springIndexAccess,
    		ProjectInformation projectInformation,
    		StereotypeInformation stereotypeInformation,
    		RequestMappingMcpTools requestMappingMcpTools,
    		ComponentAnalysisMcpTools componentAnalysisMcpTools) {
    	
        return ToolCallbackProvider.from(ToolCallbacks.from(
        		springVersionsAndGenerations,
        		springIoApiMcpTools,
        		springIndexAccess,
        		projectInformation,
        		stereotypeInformation,
        		requestMappingMcpTools,
        		componentAnalysisMcpTools));
	}

	/**
	 * MCP protocol prompts (templates clients can expand into user/assistant messages), separate from {@link ToolCallbackProvider} tools.
	 */
	@Bean
	List<McpServerFeatures.SyncPromptSpecification> springToolsPrompts() {
		return Prompts.PROMPTS;
	}

}
