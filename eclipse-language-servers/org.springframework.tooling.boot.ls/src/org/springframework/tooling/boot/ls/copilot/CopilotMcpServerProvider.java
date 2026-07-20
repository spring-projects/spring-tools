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
package org.springframework.tooling.boot.ls.copilot;

//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.TimeUnit;
//
//import org.eclipse.jface.preference.IPreferenceStore;
//import org.springframework.tooling.boot.ls.BootLanguageServerPlugin;
//import org.springframework.tooling.boot.ls.Constants;
//
//import com.microsoft.copilot.eclipse.ui.extensions.IMcpRegistrationProvider;
//
//public class CopilotMcpServerProvider implements IMcpRegistrationProvider {
//
//	@Override
//	public CompletableFuture<String> getMcpServerConfigurations() {
//		return CompletableFuture.supplyAsync(() -> {
//			IPreferenceStore prefs = BootLanguageServerPlugin.getDefault().getPreferenceStore();
//
//			if (!prefs.getBoolean(Constants.PREF_AI_MCP_ENABLED)) {
//				return null;
//			}
//
//			String port = prefs.getString(Constants.PREF_AI_MCP_PORT);
//
//			return """
//					{
//						"servers": {
//							"spring-tools-local-dev-server": {
//								"url": "http://localhost:%s/mcp",
//								"type": "http"
//							}
//						}
//					}
//			""".formatted(port);
//		}, CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS));
//	}
//
//}
