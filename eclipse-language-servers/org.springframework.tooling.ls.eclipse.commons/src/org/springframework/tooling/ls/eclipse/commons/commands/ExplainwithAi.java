/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.commons.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.Parameterization;
import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.lsp4e.command.LSPCommandHandler;
import org.eclipse.lsp4j.Command;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.handlers.IHandlerService;

import com.google.gson.Gson;

public class ExplainwithAi extends AbstractHandler {

	private static final String COPILOT_CHAT_CMD = "com.microsoft.copilot.eclipse.commands.openChatView";

	@SuppressWarnings("restriction")
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		try {
			String p = event.getParameter(LSPCommandHandler.LSP_COMMAND_PARAMETER_ID);
			Command lspCmd = new Gson().fromJson(p, Command.class);
			if (lspCmd != null) {
				IWorkbenchWindow workbenchWindow = HandlerUtil.getActiveWorkbenchWindow(event);
				final IHandlerService handlerService = workbenchWindow.getService(IHandlerService.class);
				ICommandService commandService = workbenchWindow.getService(ICommandService.class);
				if (handlerService != null && commandService != null && commandService.getCommand(COPILOT_CHAT_CMD) != null) {
					org.eclipse.core.commands.Command cmd = commandService.getCommand(COPILOT_CHAT_CMD);
					Parameterization[] params = new Parameterization[] {
							new Parameterization(cmd.getParameter("com.microsoft.copilot.eclipse.commands.openChatView.inputValue"), lspCmd.getArguments().get(0).toString()),
							new Parameterization(cmd.getParameter("com.microsoft.copilot.eclipse.commands.openChatView.autoSend"), "true")
					};
					handlerService.executeCommand(new ParameterizedCommand(cmd, params), null);
				}
			}
			return null;
		} catch (Exception e) {
			throw new ExecutionException("Failed to execute Explain with AI command", e);
		}
	}


}
