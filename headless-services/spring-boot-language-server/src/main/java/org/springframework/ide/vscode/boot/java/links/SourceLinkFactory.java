/*******************************************************************************
 * Copyright (c) 2018, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.links;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.LspClient;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;

/**
 * Factory for creating {@link SourceLinks}
 *
 * @author Alex Boyko
 *
 */
public final class SourceLinkFactory {

	public static final SourceLinks NO_SOURCE_LINKS = new SourceLinks() {

		@Override
		public Optional<String> sourceLinkUrlForFQName(IJavaProject project, String fqName) {
			return Optional.empty();
		}

		@Override
		public Optional<String> sourceLinkUrlForClasspathResource(String path) {
			return Optional.empty();
		}

		@Override
		public Optional<String> sourceLinkForResourcePath(Path path) {
			return Optional.empty();
		}

		@Override
		public Optional<URI> sourceLinkForJarEntry(IJavaProject contextProject, URI uri) {
			return Optional.empty();
		}

	};

	/**
	 * Creates {@link SourceLinks} for specific server based on client type
	 * @param server the boot LS
	 * @return appropriate source links object
	 */
	public static SourceLinks createSourceLinks(SimpleLanguageServer server, CompilationUnitCache cuCache, JavaProjectFinder projectFinder) {
		switch (LspClient.currentClient()) {
		case VSCODE:
		case THEIA:
			return /*new VSCodeSourceLinks(cuCache);*/server == null ? new VSCodeSourceLinks(cuCache, projectFinder) :new  JavaServerSourceLinks(server, projectFinder);
		case ECLIPSE:
			return /*new EclipseSourceLinks();*/server == null ? new EclipseSourceLinks(projectFinder) : new JavaServerSourceLinks(server, projectFinder);
		case ATOM:
			return new AtomSourceLinks(cuCache, projectFinder);
		default:
			return NO_SOURCE_LINKS;
		}

	}

}
