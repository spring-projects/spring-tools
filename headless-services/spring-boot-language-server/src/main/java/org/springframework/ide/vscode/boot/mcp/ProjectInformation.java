/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom
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
			Lists all Java projects in the workspace with Boot flag and JRE level.
			Use each Project.projectName when calling other tools; those tools match this name case-insensitively.
			""")
	public List<Project> getProjectList() throws Exception {
		return projectFinder.all()
				.stream()
				.map(project -> new Project(project.getElementName(), SpringProjectUtil.isBootProject(project), project.getClasspath().getJre() == null ? null : project.getClasspath().getJre().version()))
				.toList();
	}
	
	public static record Project(String projectName, boolean isSpringBootProject, String javaVersion) {}


	@Tool(description = """
			Returns the Spring Boot version for a workspace Java project (from the resolved classpath / BOM).
			""")
	public Version getSpringBootVersion(
			@ToolParam(description = "IDE project name from getProjectList().projectName (case-insensitive match)") String projectName) throws Exception {
		
		IJavaProject project = getProject(projectName);

		Version version = SpringProjectUtil.getSpringBootVersion(project);
		if (version == null) {
			throw new Exception("no spring boot version found for project with name " + projectName);
		}

		return version;
	}


	@Tool(description = """
			Returns the Java/JRE version configured for the project's classpath.
			""")
	public String getJavaVersion(
			@ToolParam(description = "IDE project name from getProjectList().projectName (case-insensitive match)") String projectName) throws Exception {
		
		IJavaProject project = getProject(projectName);
		IClasspath classpath = project.getClasspath();
		
		return classpath.getJre().version();
	}


	@Tool(description = """
			Returns non-system binary classpath entries for the project (resolved JARs) with versions from build tooling.
			Each Library.name is the classpath entry path (often a local .m2 or Gradle cache path), not necessarily a Maven coordinate; use it to see exact resolved artifacts.
			""")
	public List<Library> getResolvedProjectClasspath(
			@ToolParam(description = "IDE project name from getProjectList().projectName (case-insensitive match)") String projectName) throws Exception {
		
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
