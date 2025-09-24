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
package org.springframework.ide.vscode.bosh;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Paths;

import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ide.vscode.bosh.bootiful.BoshLanguageServerTest;
import org.springframework.ide.vscode.bosh.models.CloudConfigModel;
import org.springframework.ide.vscode.bosh.models.DynamicModelProvider;
import org.springframework.ide.vscode.bosh.models.ReleasesModel;
import org.springframework.ide.vscode.bosh.models.StemcellsModel;
import org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BoshLanguageServerTest
public class BoshLanguageServerInitializerTest {

	public static File getTestResource(String name) throws URISyntaxException {
		return Paths.get(BoshLanguageServerInitializerTest.class.getResource(name).toURI()).toFile();
	}

	@MockitoBean DynamicModelProvider<CloudConfigModel> cloudConfigProvider;
	@MockitoBean DynamicModelProvider<StemcellsModel> stemcellsProvider;
	@MockitoBean DynamicModelProvider<ReleasesModel> releasesProvider;

	@Autowired
	LanguageServerHarness harness;

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
