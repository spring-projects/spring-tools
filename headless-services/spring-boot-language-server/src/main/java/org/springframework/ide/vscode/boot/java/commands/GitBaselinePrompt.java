/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.commands;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.protocol.SetConfigurationParams;

/**
 * Asks the user whether to turn on the automatic git baseline ({@link GitBaselineTracker}) when they
 * turn on highlighting changes in the logical structure view and it is off - asked by the language
 * server, so every client gets the same question, and answered by setting the client's settings
 * ({@code sts/setConfiguration}).
 *
 * <p>Clients tell the server via {@code sts/spring-boot/structure/diffEnabled}: when the user turns
 * highlighting changes on, and once at startup when it is on already. The question comes at most
 * once per language server run - "Not Now" asks again with the next run, "Don't Ask Again" turns the
 * question off for good ({@code boot-java.structure.git-baseline-prompt}).
 *
 * @author Martin Lippert
 */
public class GitBaselinePrompt {

	private static final Logger log = LoggerFactory.getLogger(GitBaselinePrompt.class);

	static final String DIFF_ENABLED_CMD = "sts/spring-boot/structure/diffEnabled";

	static final MessageActionItem ENABLE = new MessageActionItem("Enable");
	static final MessageActionItem NOT_NOW = new MessageActionItem("Not Now");
	static final MessageActionItem DONT_ASK_AGAIN = new MessageActionItem("Don't Ask Again");

	static final String MESSAGE = "Track the logical structure automatically with git? "
			+ "Spring Tools would then capture a baseline of each project's logical structure at every git commit, "
			+ "so that the Logical Structure view always highlights what changed since your last commit - "
			+ "without capturing a baseline by hand. A project with uncommitted changes gets its first baseline with its next commit.";

	private final SimpleLanguageServer server;
	private final BootJavaConfig config;
	private final AtomicBoolean asked = new AtomicBoolean();

	public GitBaselinePrompt(SimpleLanguageServer server, BootJavaConfig config) {
		this.server = server;
		this.config = config;

		server.onCommand(DIFF_ENABLED_CMD, params -> diffEnabled().thenApply(v -> null));
	}

	/**
	 * Asks the user, unless the automatic git baseline is on already, the user does not want to be
	 * asked, or this language server run asked already.
	 *
	 * @return done once the user answered, and the answer was passed on to the client
	 */
	CompletableFuture<Void> diffEnabled() {
		if (config.isStructureGitBaselineEnabled() || !config.isStructureGitBaselinePromptEnabled() || server.getClient() == null
				|| !asked.compareAndSet(false, true)) {
			return CompletableFuture.completedFuture(null);
		}

		ShowMessageRequestParams params = new ShowMessageRequestParams();
		params.setType(MessageType.Info);
		params.setMessage(MESSAGE);
		params.setActions(List.of(ENABLE, NOT_NOW, DONT_ASK_AGAIN));

		return server.getClient().showMessageRequest(params).thenCompose(answer -> {
			if (ENABLE.equals(answer)) {
				log.info("the user turned on the automatic git baseline of the logical structure view");
				return setConfiguration(BootJavaConfig.STRUCTURE_GIT_BASELINE_ENABLED, true);
			}
			else if (DONT_ASK_AGAIN.equals(answer)) {
				log.info("the user does not want to be asked about the automatic git baseline of the logical structure view again");
				return setConfiguration(BootJavaConfig.STRUCTURE_GIT_BASELINE_PROMPT, false);
			}
			// "Not Now", or the message was dismissed: asked again with the next run
			return CompletableFuture.completedFuture(null);
		}).exceptionally(e -> {
			log.warn("failed to ask the user about the automatic git baseline of the logical structure view", e);
			return null;
		});
	}

	private CompletableFuture<Void> setConfiguration(String key, Object value) {
		return server.getClient().setConfiguration(new SetConfigurationParams(key, value)).thenApply(v -> null);
	}

}
