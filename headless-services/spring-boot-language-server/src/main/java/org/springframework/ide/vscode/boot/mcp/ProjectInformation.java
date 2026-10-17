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

import java.net.URI;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.stereotype.Component;

/**
 * @author Martin Lippert
 */
@Component
public class ProjectInformation {

	private final ProjectLookup projects;

	public ProjectInformation(ProjectLookup projects) {
		this.projects = projects;
	}


	@Tool(description = """
			Lists all Java projects in the workspace with Boot flag, JRE level and root directory.
			Use each Project.projectName when calling other tools; those tools match this name case-insensitively.
			Project.location is the absolute path of the project root (the directory holding pom.xml or build.gradle),
			so a source file belongs to the project whose location is the longest prefix of the file's path.
			""")
	public List<Project> getProjectList() throws Exception {
		return projects.all()
				.stream()
				.map(project -> new Project(project.getElementName(), SpringProjectUtil.isBootProject(project),
						project.getClasspath().getJre() == null ? null : project.getClasspath().getJre().version(),
						locationOf(project)))
				.toList();
	}

	private static String locationOf(IJavaProject project) {
		URI uri = project.getLocationUri();
		if (uri == null) {
			return null;
		}
		try {
			return Paths.get(uri).toString();
		} catch (RuntimeException e) {
			return uri.toString();
		}
	}
	
	public static record Project(String projectName, boolean isSpringBootProject, String javaVersion, String location) {}


	@Tool(description = """
			Returns the Spring Boot version for a workspace Java project (from the resolved classpath / BOM).
			""")
	public Version getSpringBootVersion(
			@ToolParam(description = "IDE project name from getProjectList().projectName (case-insensitive match)") String projectName) throws Exception {
		
		IJavaProject project = projects.get(projectName);

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
		
		IJavaProject project = projects.get(projectName);
		IClasspath classpath = project.getClasspath();
		
		return classpath.getJre().version();
	}


	@Tool(description = """
			Returns non-system binary classpath entries for the project (resolved JARs) with versions from build tooling.
			Each Library.name is the classpath entry path (often a local .m2 or Gradle cache path), not necessarily a Maven coordinate; use it to see exact resolved artifacts.
			""")
	public List<Library> getResolvedProjectClasspath(
			@ToolParam(description = "IDE project name from getProjectList().projectName (case-insensitive match)") String projectName) throws Exception {
		
		IJavaProject project = projects.get(projectName);
		
		IClasspath classpath = project.getClasspath();
		Collection<CPE> classpathEntries = classpath.getClasspathEntries();

		return classpathEntries.stream()
			.filter(cpe -> Classpath.ENTRY_KIND_BINARY.equals(cpe.getKind()))
			.filter(cpe -> !cpe.isSystem())
			.map(cpe -> new Library(cpe.getPath(), cpe.getVersion()))
			.toList();
	}
	
	public static record Library(String name, String version) {};

}
