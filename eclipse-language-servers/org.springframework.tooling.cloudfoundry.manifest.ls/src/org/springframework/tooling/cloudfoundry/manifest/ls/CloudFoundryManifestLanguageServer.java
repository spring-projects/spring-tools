/*******************************************************************************
 * Copyright (c) 2016, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.cloudfoundry.manifest.ls;

import static org.springframework.tooling.ls.eclipse.commons.preferences.LanguageServerConsolePreferenceConstants.CLOUDFOUNDRY_SERVER;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.services.LanguageServer;
import org.springframework.tooling.ls.eclipse.commons.STS4LanguageServerProcessStreamConnector;

import com.google.common.collect.ImmutableMap;

/**
 * @author Martin Lippert
 */
public class CloudFoundryManifestLanguageServer extends STS4LanguageServerProcessStreamConnector {
	
	private LanguageServer languageServer;
	private URI rootPath;

	private static Object cfTargetOptionSettings = null;
	private static List<CloudFoundryManifestLanguageServer> servers = new CopyOnWriteArrayList<>();

	public CloudFoundryManifestLanguageServer() {
		super(CLOUDFOUNDRY_SERVER);
		
		initExecutableJarCommand(
				Paths.get("servers", "manifest-yaml-language-server"),
				"manifest-yaml-language-server",
				Arrays.asList(
						"-Dlsp.lazy.completions.disable=true",
						"-Dspring.config.location=classpath:/application.properties"
				)
		);
		
		setWorkingDirectory(getWorkingDirLocation());
	}
	
	@Override
	public void handleMessage(Message message, LanguageServer languageServer, URI rootPath) {
		if (message instanceof ResponseMessage) {
			ResponseMessage responseMessage = (ResponseMessage)message;
			if (responseMessage.getResult() instanceof InitializeResult) {
				this.languageServer = languageServer;
				this.rootPath = rootPath;
				updateLanguageServer();
				servers.add(this);
			}
		}
	}
	
	public static void setCfTargetLoginOptions(Object cfTargetOptions) {
		cfTargetOptionSettings = cfTargetOptions;
		
		for (CloudFoundryManifestLanguageServer server : servers) {
			server.updateLanguageServer();
		}
	}

	@Override
	public void stop() {
		servers.remove(this);
		super.stop();
	}
	
	@Override
	public Object getInitializationOptions(URI rootUri) {
		Object opts = cfTargetOptionSettings;
		return opts!=null?opts:ImmutableMap.of();
	}
	
	private void updateLanguageServer() {
		DidChangeConfigurationParams params = new DidChangeConfigurationParams(getInitializationOptions(rootPath));
		languageServer.getWorkspaceService().didChangeConfiguration(params);
	}

	@Override
	protected String getPluginId() {
		return Constants.PLUGIN_ID;
	}
}
