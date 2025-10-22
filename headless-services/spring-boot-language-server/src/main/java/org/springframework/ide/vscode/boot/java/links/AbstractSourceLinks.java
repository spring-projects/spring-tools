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
package org.springframework.ide.vscode.boot.java.links;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.eclipse.core.runtime.Assert;
import org.springframework.ide.vscode.boot.java.commands.Misc;
import org.springframework.ide.vscode.commons.java.IJavaProject;

public abstract class AbstractSourceLinks implements SourceLinks {

	public Optional<URI> sourceLinkForJarEntry(IJavaProject contextProject, URI uri) {
		Assert.isTrue(Misc.JAR.equals(uri.getScheme()));
		try {
			JarURLConnection c = (JarURLConnection) uri.toURL().openConnection();
			Path jarPath = Paths.get(c.getJarFile().getName());
			if (!Files.exists(jarPath)) {
				// URL for CF resources looks like
				// jar:file:/home/vcap/app/lib/gs-rest-service-complete.jar!/hello/MyService.class
				String entryName = c.getEntryName();
				if (entryName.endsWith(CLASS)) {
					String fqName = entryName.substring(0, entryName.length() - CLASS.length()).replace(File.separator,
							".");
					return sourceLinkUrlForFQName(null, fqName).map(s -> URI.create(s));
				}
				return Optional.empty();
			}
		} catch (IOException e) {
			log.error("", e);
		}
		return Optional.of(uri);
	}

}
