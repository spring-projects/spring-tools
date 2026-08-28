/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerProjectExecutor;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandParams;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SuppressWarnings("restriction")
public class BootLsCommandUtils {

	private static Gson GSON = new Gson();

	public static LanguageServerProjectExecutor getLanguageServers(String command) {
		return LanguageServers.forProject(null).withFilter(cap -> {
			ExecuteCommandOptions commandCap = cap.getExecuteCommandProvider();
			if (commandCap != null) {
				List<String> supportedCommands = commandCap.getCommands();
				return supportedCommands != null && supportedCommands.contains(command);
			}
			return false;
		}).withPreferredServer(LanguageServersRegistry.getInstance()
					.getDefinition("org.eclipse.languageserver.languages.springboot"));
	}

	public static <T> CompletableFuture<Optional<T>> executeCommand(TypeToken<T> resType, String cmd, Object... params) {
		return getLanguageServers(cmd).computeFirst(ls -> ls.getWorkspaceService().executeCommand(new ExecuteCommandParams(
				cmd,
				ImmutableList.copyOf(params)
		))).thenApply(o -> o.map(v -> GSON.fromJson(GSON.toJsonTree(v), resType)));
	}

}
