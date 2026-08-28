/*******************************************************************************
 * Copyright (c) 2017, 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.maven.java;

import java.io.File;
import java.util.Collection;
import java.util.Optional;

import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.FileBasedJavaProjectFinder;
import org.springframework.ide.vscode.commons.maven.MavenCore;
import org.springframework.ide.vscode.commons.util.FileUtils;

/**
 * Finds Maven projects. Looks for <code>pom.xml</code> file
 *
 * @author Alex Boyko
 *
 */
public class MavenProjectFinder extends FileBasedJavaProjectFinder {

	private MavenProjectCache cache;

	public MavenProjectFinder(MavenProjectCache cache) {
		this.cache = cache;
	}

	@Override
	public Optional<IJavaProject> find(File file) {
		File pomFile = FileUtils.findFile(file, MavenCore.POM_XML);
		if (pomFile != null) {
			return Optional.ofNullable(cache.project(pomFile));
		}
		return Optional.empty();
	}

	@Override
	protected Optional<IJavaProject> findProjectByName(String name) {
		return cache.projectByName(name);
	}

	@Override
	public Collection<? extends IJavaProject> all() {
		return cache.all();
	}
}
