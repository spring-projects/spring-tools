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
package org.springframework.ide.vscode.boot.mcp.prompts;

import java.util.ArrayList;
import java.util.List;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

public class GenerateNewPrompts {
	
	public static final List<McpServerFeatures.SyncPromptSpecification> PROMPTS = initializePrompts();

	private static List<SyncPromptSpecification> initializePrompts() {
		ArrayList<SyncPromptSpecification> prompts = new ArrayList<>();
		
		var generateRepositorySample = new McpSchema.Prompt("create a sample Spring Data JDBC repository", "Creates a sample Spring Data JDBC repository with a sample domain type",
                List.of(new McpSchema.PromptArgument("domain-type-name", "The name to the domain type the repository should be for", true)));

        var promptSpecification = new McpServerFeatures.SyncPromptSpecification(generateRepositorySample, (exchange, getPromptRequest) -> {
        	String domainTypeName = (String) getPromptRequest.arguments().get("domain-type-name");
        	String content =
        			"""
        			Please create a sample repository based on Spring Data JDBC for the domain type '%domain-type-name%'.
        			When creating a Spring Data JDBC repository, extend the `ListCrudRepository` interface.
			
        			Please create the corresponding domain type as well. Do not come up with several attributes for the domain type. Keep it simple.
        			""";

        	var userMessage = new PromptMessage(Role.USER, new TextContent(
        			content.replace("%domain-type-name%", domainTypeName)));

        	return new GetPromptResult("", List.of(userMessage));
        });
        
        prompts.add(promptSpecification);
		
		return prompts;
	}

}
