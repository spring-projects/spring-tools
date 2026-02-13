/*******************************************************************************
 * Copyright (c) 2019, 2022 Pivotal, Inc.
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

import org.springframework.ide.vscode.commons.jdtls.JdtLsIndex;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.protocol.STS4LanguageClient;

public class JdtLsJavaProject extends AbstractJavaProject {

	final private STS4LanguageClient client;
	final private ProjectObserver projectObserver;

	public JdtLsJavaProject(STS4LanguageClient client, URI uri, IClasspath classpath, ProjectObserver projectObserver, IProjectBuild projectBuild) {
		super(uri, classpath, projectBuild);
		this.client = client;
		this.projectObserver = projectObserver;
	}

	@Override
	protected ClasspathIndex createIndex() {
		return new JdtLsIndex(client, getLocationUri(), projectObserver);
	}

}
