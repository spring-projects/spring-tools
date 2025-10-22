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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.ide.vscode.boot.java.commands.Misc;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.LspClient;
import org.springframework.ide.vscode.commons.languageserver.util.LspClient.Client;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.protocol.java.JavaDataParams;

public class JavaServerSourceLinks extends AbstractSourceLinks {

	private SimpleLanguageServer server;
	private JavaProjectFinder projectFinder;

	public JavaServerSourceLinks(SimpleLanguageServer server, JavaProjectFinder projectFinder) {
		this.server = server;
		this.projectFinder = projectFinder;
	}

	@Override
	public Optional<String> sourceLinkUrlForFQName(IJavaProject project, String fqName) {
		StringBuilder bindingKey = new StringBuilder();
		bindingKey.append('L');
		bindingKey.append(fqName.replace('.',  '/'));
		bindingKey.append(';');
		String projectUri = project == null ? null : project.getLocationUri().toASCIIString();
		CompletableFuture<Optional<String>> link = server.getClient().javadocHoverLink(new JavaDataParams(projectUri, bindingKey.toString(), true))
				.thenApply(l -> Optional.ofNullable(l));
		try {
			return link.get(10, TimeUnit.SECONDS).map(s -> {
				if (s.startsWith("jdt:/")) {
					return "command:java.open.file?" + URLEncoder.encode("[\"" + URLEncoder.encode(s, StandardCharsets.UTF_8) + "\"]", StandardCharsets.UTF_8);
				}
				return s;
			});
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			log.error("", e);
		}
		return Optional.empty();
	}

	@Override
	public Optional<String> sourceLinkUrlForClasspathResource(String path) {
		return SourceLinks.sourceLinkUrlForClasspathResource(this, projectFinder, path);
	}

	@Override
	public Optional<String> sourceLinkForResourcePath(Path path) {
		return Optional.ofNullable(path).map(p -> p.toUri().toASCIIString());
	}

	@Override
	public Optional<URI> sourceLinkForJarEntry(IJavaProject contextProject, URI uri) {
		// Ideally client should be asked for a URI for a JAR entry that it can deal with.
		// It feels a bit too much adding this message to STS Client at the moment hence we check if the client is Eclipse here
		return super.sourceLinkForJarEntry(contextProject, uri).map(u -> LspClient.currentClient() == Client.ECLIPSE
				? EclipseSourceLinks.eclipseIntroUriForJarEntry(contextProject.getElementName(), uri)
				: URI.create(uri.toString().replace(Misc.JAR_URL_PROTOCOL_PREFIX, Misc.BOOT_LS_URL_PRTOCOL_PREFIX)));
	}

}
