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

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.stereotype.Component;

@Component
public class EmbeddedMcpServer {
	
	private SimpleLanguageServer server;

	public EmbeddedMcpServer(SimpleLanguageServer server) {
		this.server = server;
	}
	
    @EventListener
    public void onApplicationEvent(final ServletWebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        
        server.doOnInitialized(() -> {
        	server.getClient().showMessage(new MessageParams(MessageType.Info, "Embedded Spring Tools MCP server started at port: " + port));
        });
    }

}
