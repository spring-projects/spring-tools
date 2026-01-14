/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.mcp;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Martin Lippert
 */
@Configuration
public class McpConfig {
	
	@Bean
	ToolCallbackProvider registerTools(
    		SpringVersionsAndGenerations springVersionsAndGenerations,
    		SpringIndexAccess springIndexAccess,
    		ProjectInformation projectInformation,
    		StereotypeInformation stereotypeInformation,
    		RequestMappingMcpTools requestMappingMcpTools,
    		ComponentAnalysisMcpTools componentAnalysisMcpTools) {
    	
        return ToolCallbackProvider.from(ToolCallbacks.from(
        		springVersionsAndGenerations,
        		springIndexAccess,
        		projectInformation,
        		stereotypeInformation,
        		requestMappingMcpTools,
        		componentAnalysisMcpTools));
	}
	
//    @Bean
//    List<McpServerFeatures.SyncPromptSpecification> springToolsPrompts() {
//    	return Prompts.PROMPTS;
//    }
    
}
