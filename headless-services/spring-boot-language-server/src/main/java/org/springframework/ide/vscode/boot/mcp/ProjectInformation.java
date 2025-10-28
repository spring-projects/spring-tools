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

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.stereotype.Component;

/**
 * @author Martin Lippert
 */
@Component
public class ProjectInformation {

	private final JavaProjectFinder projectFinder;

	public ProjectInformation(JavaProjectFinder projectFinder) {
		this.projectFinder = projectFinder;;
	}


	@Tool(description = """
						This function provides a list of all the projects that are in the current workspace
						""")
	public List<Project> getProjectList() throws Exception {
		return projectFinder.all()
				.stream()
				.map(project -> new Project(project.getElementName(), SpringProjectUtil.isBootProject(project), project.getClasspath().getVM() == null ? null : project.getClasspath().getVM().version()))
				.toList();
	}
	
	public static record Project(String projectNane, boolean isSpringBootProject, String javaVersion) {}


	@Tool(description = """
						This function provides information about which version of Spring Boot the given project from the workspace uses
						""")
	public Version getSpringBootVersion(
			@ToolParam(description = "the name of the project in the workspace of the user") String projectName) throws Exception {
		
		IJavaProject project = getProject(projectName);

		Version version = SpringProjectUtil.getSpringBootVersion(project);
		if (version == null) {
			throw new Exception("no spring boot version found for project with name " + projectName);
		}

		return version;
	}


	@Tool(description = """
						This function provides information about which version of Java the given project from the workspace uses
						""")
	public String getJavaVersion(
			@ToolParam(description = "the name of the project in the workspace of the user") String projectName) throws Exception {
		
		IJavaProject project = getProject(projectName);
		IClasspath classpath = project.getClasspath();
		
		return classpath.getVM().version();
	}


	@Tool(description = """
						This function provides detailed information about the libraries that this project uses and the versio of those libraries.
						It gets this information from the resolved classpath and is therefore very precise.
						""")
	public List<Library> getResolvedProjectClasspath(
			@ToolParam(description = "the name of the project in the workspace of the user") String projectName) throws Exception {
		
		IJavaProject project = getProject(projectName);
		
		IClasspath classpath = project.getClasspath();
		Collection<CPE> classpathEntries = classpath.getClasspathEntries();

		return classpathEntries.stream()
			.filter(cpe -> Classpath.ENTRY_KIND_BINARY.equals(cpe.getKind()))
			.filter(cpe -> !cpe.isSystem())
			.map(cpe -> new Library(cpe.getPath(), cpe.getVersion().toString()))
			.toList();
	}
	
	public static record Library(String name, String version) {};
	
	
	//
	//
	//


	private IJavaProject getProject(String projectName) throws Exception {
		Optional<? extends IJavaProject> found = projectFinder.all()
				.stream()
				.filter(project -> project.getElementName().toLowerCase().equals(projectName.toLowerCase()))
				.findFirst();

		if (found.isEmpty()) {
			throw new Exception("project with name " + projectName + " not found");
		}
		else {
			return found.get();
		}
	}

}
