/*******************************************************************************
 * Copyright (c) 2018, 2022 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.java;

import java.net.URI;

import org.springframework.ide.vscode.commons.jandex.JandexClasspath;
import org.springframework.ide.vscode.commons.jandex.JandexIndex.JavadocProviderFactory;
import org.springframework.ide.vscode.commons.languageserver.java.JavadocService;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.util.FileObserver;

public class JavaProject extends AbstractJavaProject {

	private final FileObserver fileObserver;
	private final JavadocProviderFactory javadocProviderFactory;

	public JavaProject(FileObserver fileObserver, URI uri, IClasspath classpath, JavadocService javadocService, IProjectBuild projectBuild) {
		super(uri, classpath, projectBuild);
		this.fileObserver = fileObserver;
		this.javadocProviderFactory = (classpathResource) -> {
			CPE cpe = IClasspathUtil.findEntryForBinaryRoot(classpath, classpathResource);
			return javadocService.javadocProvider(uri, cpe);
		};
	}

	@Override
	protected ClasspathIndex createIndex() {
		return new JandexClasspath(getClasspath(), fileObserver, javadocProviderFactory);
	}

}
