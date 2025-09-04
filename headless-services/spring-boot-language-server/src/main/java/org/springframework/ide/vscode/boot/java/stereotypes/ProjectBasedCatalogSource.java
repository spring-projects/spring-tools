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
package org.springframework.ide.vscode.boot.java.stereotypes;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import org.jmolecules.stereotype.catalog.support.CatalogSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;

public class ProjectBasedCatalogSource implements CatalogSource {

	private static final Logger log = LoggerFactory.getLogger(ProjectBasedCatalogSource.class);
	
	private static final String[] DEFAULT_STEREOTYPE_DEFINITIONS = {
			"/stereotype-defaults/spring-jmolecules-stereotypes.json", 
			"/stereotype-defaults/jpa-jmolecules-stereotypes.json"};

	private final IJavaProject project;

	public ProjectBasedCatalogSource(IJavaProject project) {
		this.project = project;
	}

	@Override
	public Stream<URL> getSources() {
		List<URL> result = new ArrayList<>();
		
		try {
			IClasspath classpath = project.getClasspath();
			Collection<CPE> entries = classpath.getClasspathEntries();

			for (CPE cpe : entries) {
				if (Classpath.ENTRY_KIND_SOURCE.equals(cpe.getKind()) && !cpe.isTest() && !cpe.isSystem()) {
					String path = cpe.getPath();

					File stereotypes = new File(path, CatalogSource.DEFAULT_STEREOTYPE_LOCATION);
					if (stereotypes.exists() && stereotypes.isFile()) {
						result.add(stereotypes.toURI().toURL());
					}

					File groups = new File(path, CatalogSource.DEFAULT_GROUP_LOCATION);
					if (groups.exists() && groups.isFile()) {
						result.add(groups.toURI().toURL());
					}
				}
				
				if (Classpath.ENTRY_KIND_BINARY.equals(cpe.getKind()) && !cpe.isTest() && !cpe.isSystem()) {
					String libPath = cpe.getPath();
					
					try (JarFile jarFile = new JarFile(libPath)) {
						ZipEntry stereotypeEntry = jarFile.getEntry(CatalogSource.DEFAULT_STEREOTYPE_LOCATION);
						if (stereotypeEntry != null) {
							URI uri = new URI("jar:" + new File(libPath).toURI().toString() + "!/" + CatalogSource.DEFAULT_STEREOTYPE_LOCATION);
							result.add(uri.toURL());
						}

						ZipEntry groupEntry = jarFile.getEntry(CatalogSource.DEFAULT_GROUP_LOCATION);
						if (groupEntry != null) {
							URI uri = new URI("jar:" + new File(libPath).toURI().toString() + "!/" + CatalogSource.DEFAULT_GROUP_LOCATION);
							result.add(uri.toURL());
						}
					}
				}
			}

			if (result.size() == 0) {

				// use default hard-coded stereotype definitions for projects that don't have jmolecules on the classpath

				for (String defaultDefinition : DEFAULT_STEREOTYPE_DEFINITIONS) {
					URL defaultStereotypes = this.getClass().getResource(defaultDefinition);

					if (defaultStereotypes != null) {
						log.info("using default stereotypes " + defaultDefinition + " for project: " + this.project.getElementName());
						result.add(defaultStereotypes);
					}
					else {
						log.error("error looking up default stereotypes " + defaultDefinition + " for project: " + this.project.getElementName());
					}
				}
			}

		} catch (Exception e) {
			log.error("error looking up stereotype metadata for project: " + this.project.getElementName(), e);
		}
		
		
		return result.stream();
	}

}
