/*******************************************************************************
 * Copyright (c) 2016, 2026 Pivotal, Inc.
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
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.ClasspathFileBasedCache;
import org.springframework.ide.vscode.commons.java.DelegatingCachedClasspath;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IProjectBuild;
import org.springframework.ide.vscode.commons.java.LegacyJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavadocService;
import org.springframework.ide.vscode.commons.maven.MavenCore;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;
import org.springframework.ide.vscode.commons.util.FileObserver;

/**
 * Wrapper for Maven Core project
 *
 * @author Alex Boyko
 *
 */
public class MavenJavaProject extends LegacyJavaProject {

	private static final Logger log = LoggerFactory.getLogger(MavenJavaProject.class);

	private final File pom;
	
	private MavenJavaProject(FileObserver fileObserver, Path projectDataCache, IClasspath classpath, File pom, JavadocService javadocService) {
		super(fileObserver, pom.getParentFile().toURI(), projectDataCache, classpath, javadocService, IProjectBuild.create(ProjectBuild.MAVEN_PROJECT_TYPE, pom.toURI()));
		this.pom = pom;
	}

	public static MavenJavaProject create(FileObserver fileObserver, MavenCore maven, File pom, Path projectDataCache, JavadocService javadocService) {
		File file = projectDataCache == null
				? null
				: projectDataCache.resolve(ClasspathFileBasedCache.CLASSPATH_DATA_CACHE_FILE).toFile();
		ClasspathFileBasedCache fileBasedCache = new ClasspathFileBasedCache(file);
		DelegatingCachedClasspath classpath = new DelegatingCachedClasspath(
				() -> new MavenProjectClasspath(maven, pom),
				fileBasedCache
		);
		return new MavenJavaProject(fileObserver, projectDataCache, classpath, pom, javadocService);
	}

	public static MavenJavaProject create(FileObserver fileObserver, MavenCore maven, File pom, JavadocService javadocService) {
		MavenJavaProject thiss = create(fileObserver, maven, pom, null, javadocService);
		if (!thiss.getClasspath().isCached()) {
			try {
				thiss.getClasspath().update();
			} catch (Exception e) {
				log.error("", e);
			}
		}
		return thiss;
	}

	@Override
	public DelegatingCachedClasspath getClasspath() {
		return (DelegatingCachedClasspath) super.getClasspath();
	}

	boolean update() throws Exception {
		boolean changed = true; // a failed update leaves the classpath empty, that's a change as well
		try {
			changed = getClasspath().update();
			return changed;
		} finally {
			if (changed) {
				invalidateIndex(); // before the project cache notifies listeners about the change
			}
		}
	}

	public File pom() {
		return pom;
	}

	@Override
	public String toString() {
		return "MavenJavaProject("+getElementName()+")";
	}

	@Override
	public IProjectBuild getProjectBuild() {
		return IProjectBuild.create(ProjectBuild.MAVEN_PROJECT_TYPE, pom.toURI());
	}
	
}
