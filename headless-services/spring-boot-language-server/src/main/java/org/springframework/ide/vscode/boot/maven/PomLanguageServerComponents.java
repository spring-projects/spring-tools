/*******************************************************************************
 * Copyright (c) 2024, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.maven;

import java.util.Optional;
import java.util.Set;

import org.springframework.ide.vscode.boot.validation.generations.SpringProjectsProvider;
import org.springframework.ide.vscode.commons.languageserver.composable.LanguageServerComponents;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.languageserver.util.InlayHintHandler;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.util.text.LanguageId;

public class PomLanguageServerComponents implements LanguageServerComponents{
	
	private PomInlayHintHandler inlayHintHandler;
	
	public PomLanguageServerComponents(SimpleLanguageServer server, JavaProjectFinder projectFinder, ProjectObserver projectObserver, SpringProjectsProvider generationsProvider, org.springframework.ide.vscode.boot.validation.generations.MavenMetadataProvider mavenMetadataProvider) {
		this.inlayHintHandler = new PomInlayHintHandler(server, projectFinder, projectObserver, generationsProvider, mavenMetadataProvider);
	}

	@Override
	public Set<LanguageId> getInterestingLanguages() {
		return Set.of(LanguageId.XML);
	}

	@Override
	public Optional<InlayHintHandler> getInlayHintHandler() {
		return Optional.of(inlayHintHandler);
	}

}
