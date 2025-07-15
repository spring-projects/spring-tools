/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.mcp;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.stereotype.Component;

@Component
public class IdeProjectEnvironment {
	
	private SimpleLanguageServer server;
	private JavaProjectFinder projectFinder;

	public IdeProjectEnvironment(SimpleLanguageServer server, JavaProjectFinder projectFinder) {
		this.server = server;
		this.projectFinder = projectFinder;
	}

	public Dependency[] getDependencies(String projectName) {
		IJavaProject foundProject = null;
		
		// identify the exact project by name
		Collection<? extends IJavaProject> allProjects = projectFinder.all();
		for (IJavaProject project : allProjects) {
			if (project.getElementName().equals(projectName)) {
				foundProject = project;
			}
		}

		// identify the project via the open documents
		if (foundProject == null) {
			Collection<TextDocument> allOpenDocuments = server.getTextDocumentService().getAll();
			
			if (allOpenDocuments.size() > 0) {
				TextDocument firstOpenDoc = allOpenDocuments.iterator().next();
				Optional<IJavaProject> optional = projectFinder.find(firstOpenDoc.getId());
				if (optional.isPresent()) {
					foundProject = optional.get();
				}
			}
		}
		
		// fallback to the first project if nothing else helps
		if (foundProject == null) {
			if (allProjects.size() > 0) {
				foundProject = allProjects.iterator().next();
			}
		}

		// if there is a project, use that
		if (foundProject != null) {
			List<Dependency> result = new ArrayList<>();

			try {
				Collection<CPE> entries = foundProject.getClasspath().getClasspathEntries();
				for (CPE cpe : entries) {
					if (cpe.getKind().equals(Classpath.ENTRY_KIND_BINARY) && !cpe.isSystem()) {
						result.add(createDependencyFrom(cpe));
					}
				}
			} catch (Exception e) {
			}
			
			return (Dependency[]) result.toArray(new Dependency[result.size()]);
		}
		
		return new Dependency[0];
	}

	private Dependency createDependencyFrom(CPE cpe) {
		String path = cpe.getPath();
		
		// strip full path
		String name = path.substring(path.lastIndexOf(File.separator) + 1);

		// strip version
		name = name.substring(0, name.lastIndexOf('-'));

		String version = cpe.getVersion() != null ? cpe.getVersion().toString() : "";

		return new Dependency(name, version);
	}

}
