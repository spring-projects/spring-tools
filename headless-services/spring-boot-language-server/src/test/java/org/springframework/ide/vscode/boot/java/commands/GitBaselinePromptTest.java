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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.protocol.STS4LanguageClient;
import org.springframework.ide.vscode.commons.protocol.SetConfigurationParams;

/**
 * The automatic git baseline is off by default; turning on highlighting changes asks the user
 * whether to turn it on - asked by the language server, answered by setting the client's settings.
 *
 * @author Martin Lippert
 */
public class GitBaselinePromptTest {

	private SimpleLanguageServer server;
	private STS4LanguageClient client;
	private BootJavaConfig config;

	@BeforeEach
	void setup() {
		client = mock(STS4LanguageClient.class);
		when(client.setConfiguration(any())).thenReturn(CompletableFuture.completedFuture(null));

		server = mock(SimpleLanguageServer.class);
		when(server.getClient()).thenReturn(client);

		config = mock(BootJavaConfig.class);
		when(config.isStructureGitBaselineEnabled()).thenReturn(false);
		when(config.isStructureGitBaselinePromptEnabled()).thenReturn(true);
	}

	@Test
	void enablingTurnsTheAutomaticGitBaselineOn() throws Exception {
		answering(GitBaselinePrompt.ENABLE);

		new GitBaselinePrompt(server, config).diffEnabled().get(5, TimeUnit.SECONDS);

		ArgumentCaptor<ShowMessageRequestParams> asked = ArgumentCaptor.forClass(ShowMessageRequestParams.class);
		verify(client).showMessageRequest(asked.capture());
		assertThat(asked.getValue().getActions()).containsExactly(GitBaselinePrompt.ENABLE, GitBaselinePrompt.NOT_NOW, GitBaselinePrompt.DONT_ASK_AGAIN);
		assertThat(asked.getValue().getMessage()).contains("git commit");

		verify(client).setConfiguration(new SetConfigurationParams("boot-java.structure.git-baseline-enabled", true));
	}

	@Test
	void dontAskAgainTurnsTheQuestionOff() throws Exception {
		answering(GitBaselinePrompt.DONT_ASK_AGAIN);

		new GitBaselinePrompt(server, config).diffEnabled().get(5, TimeUnit.SECONDS);

		verify(client).setConfiguration(new SetConfigurationParams("boot-java.structure.git-baseline-prompt", false));
	}

	/**
	 * "Not Now", or dismissing the message, changes nothing - and the question comes at most once per
	 * language server run, however often highlighting changes is turned on.
	 */
	@Test
	void notNowChangesNothingAndIsNotAskedAgainInTheSameRun() throws Exception {
		answering(GitBaselinePrompt.NOT_NOW);
		GitBaselinePrompt prompt = new GitBaselinePrompt(server, config);

		prompt.diffEnabled().get(5, TimeUnit.SECONDS);
		prompt.diffEnabled().get(5, TimeUnit.SECONDS);

		verify(client, times(1)).showMessageRequest(any());
		verify(client, never()).setConfiguration(any());
	}

	@Test
	void dismissingTheMessageChangesNothing() throws Exception {
		answering(null);

		new GitBaselinePrompt(server, config).diffEnabled().get(5, TimeUnit.SECONDS);

		verify(client, never()).setConfiguration(any());
	}

	@Test
	void notAskedWhenTheAutomaticGitBaselineIsOnAlready() throws Exception {
		when(config.isStructureGitBaselineEnabled()).thenReturn(true);

		new GitBaselinePrompt(server, config).diffEnabled().get(5, TimeUnit.SECONDS);

		verify(client, never()).showMessageRequest(any());
	}

	@Test
	void notAskedWhenTheUserDoesNotWantToBeAsked() throws Exception {
		when(config.isStructureGitBaselinePromptEnabled()).thenReturn(false);

		new GitBaselinePrompt(server, config).diffEnabled().get(5, TimeUnit.SECONDS);

		verify(client, never()).showMessageRequest(any());
	}

	@Test
	void registersTheCommandClientsTellTheServerWith() {
		new GitBaselinePrompt(server, config);

		ArgumentCaptor<String> command = ArgumentCaptor.forClass(String.class);
		verify(server).onCommand(command.capture(), any());
		assertThat(command.getValue()).isEqualTo("sts/spring-boot/structure/diffEnabled");
	}

	private void answering(MessageActionItem answer) {
		when(client.showMessageRequest(any())).thenReturn(CompletableFuture.completedFuture(answer));
	}

}
