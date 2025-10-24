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

public class Prompts {
	
	public static final List<McpServerFeatures.SyncPromptSpecification> PROMPTS = initializePrompts();
	
	private static List<SyncPromptSpecification> initializePrompts() {
		ArrayList<SyncPromptSpecification> prompts = new ArrayList<>();
		
		prompts.addAll(GenerateNewPrompts.PROMPTS);
		
		return prompts;
	}
	
}
