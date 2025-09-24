/*******************************************************************************
 * Copyright (c) 2016-2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.concourse;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Paths;

import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ide.vscode.concourse.bootiful.ConcourseLanguageServerTest;
import org.springframework.ide.vscode.concourse.github.GithubInfoProvider;
import org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ConcourseLanguageServerTest
public class ConcourseLanguageServerInitializerTest {

	public static File getTestResource(String name) throws URISyntaxException {
		return Paths.get(ConcourseLanguageServerInitializerTest.class.getResource(name).toURI()).toFile();
	}

	@Autowired LanguageServerHarness harness;
	@MockitoBean GithubInfoProvider github;

    @Test
    void createAndInitializeServerWithWorkspace() throws Exception {
        File workspaceRoot = getTestResource("/workspace/");
        assertExpectedInitResult(harness.intialize(workspaceRoot));
    }

    @Test
    void createAndInitializeServerWithoutWorkspace() throws Exception {
        File workspaceRoot = null;
        assertExpectedInitResult(harness.intialize(workspaceRoot));
    }

	private void assertExpectedInitResult(InitializeResult initResult) {
		if (Boolean.getBoolean("lsp.lazy.completions.disable")) {
			assertThat(initResult.getCapabilities().getCompletionProvider().getResolveProvider()).isFalse();
		} else {
			assertThat(initResult.getCapabilities().getCompletionProvider().getResolveProvider()).isTrue();
		}
		assertThat(initResult.getCapabilities().getTextDocumentSync().getLeft()).isEqualTo(TextDocumentSyncKind.Incremental);
	}

}
