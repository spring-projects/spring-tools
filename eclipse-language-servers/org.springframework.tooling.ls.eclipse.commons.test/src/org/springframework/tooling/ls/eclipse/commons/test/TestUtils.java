/*******************************************************************************
 * Copyright (c) 2018, 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
 package org.springframework.tooling.ls.eclipse.commons.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Model;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IMavenProjectImportResult;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;
import org.eclipse.m2e.core.project.ResolverConfiguration;
import org.springframework.ide.vscode.commons.protocol.java.TypeData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class TestUtils {
	
	private static Gson gson = new GsonBuilder().setPrettyPrinting().create();
	
	public static IJavaProject createTestProject(String name) throws Exception {
		File testProjectSourceLocation = new File(FileLocator.toFileURL(Platform.getBundle("org.springframework.tooling.ls.eclipse.commons.test").getEntry("test-projects/"+name)).toURI());
		File targetWorkspace = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile();
		
		FileUtils.copyDirectory(testProjectSourceLocation, new File(targetWorkspace, name));

		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
		project.create(null);
		project.open(null);
		project.refreshLocal(IResource.DEPTH_INFINITE, null);

		assertTrue(project.hasNature(JavaCore.NATURE_ID));
		ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, null);

		return JavaCore.create(project);
	}
	
	public static IProject importMavenProject(String name) throws Exception {
		File testProjectSourceLocation = new File(FileLocator.toFileURL(Platform.getBundle("org.springframework.tooling.ls.eclipse.commons.test").getEntry("test-projects/"+name)).toURI());
		File targetWorkspace = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile();
		
		File destDir = new File(targetWorkspace, name);
		FileUtils.copyDirectory(testProjectSourceLocation, destDir);
		File pom = new File(destDir, "pom.xml");
		return createEclipseProjectFromExistingMavenProject(pom, new NullProgressMonitor()).get(0);
	}
	
	public static void saveJsonData(String fileName, Object data) throws Exception {
		File jsonFile = new File("/Users/aboyko/git/sts4/eclipse-language-servers/org.springframework.tooling.ls.eclipse.commons.test/java-data-json/"+fileName);
		FileUtils.write(jsonFile, toJsonString(data));
	}
	
	public static String toJsonString(Object obj) {
		return gson.toJson(obj);
	}
	
	public static TypeData loadJsonData(String fileName) throws Exception {
		return gson.fromJson(loadJsonString(fileName), TypeData.class);
	}
	
	public static String loadJsonString(String fileName) throws Exception {
		File jsonFile = new File(FileLocator.toFileURL(Platform.getBundle("org.springframework.tooling.ls.eclipse.commons.test").getEntry("java-data-json/"+fileName)).toURI());
		return FileUtils.readFileToString(jsonFile);
	}
	
	public static void deleteAllProjects() throws Exception {
		IProject[] allProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (IProject project : allProjects) {
			project.refreshLocal(IResource.DEPTH_INFINITE, null);
			project.close(null);
			deleteProject(project);
		}
		ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, null);
	}
	
	private static void deleteProject(IProject project) throws Exception {
		int retryCount = 10; // wait 1 minute at most
		Exception lastException = null;
		while (project.exists() && --retryCount >= 0) {
			try {
				project.delete(true, true, new NullProgressMonitor());
				lastException = null;
			} catch (Exception e) {
				lastException = e;
				try {
					Thread.sleep(1000);
				}
				catch (InterruptedException e1) {
				}
			}
		}
		if (lastException != null) {
			throw lastException;
		}
	}

	public static void setAutoBuilding(boolean enabled) throws CoreException {
		IWorkspaceDescription wsd = ResourcesPlugin.getWorkspace().getDescription();
		if (!wsd.isAutoBuilding() == enabled) {
			wsd.setAutoBuilding(enabled);
			ResourcesPlugin.getWorkspace().setDescription(wsd);
		}
	}
	
	public static List<IProject> createEclipseProjectFromExistingMavenProject(File pomFile, IProgressMonitor monitor) throws Exception {

		Model model = MavenPlugin.getMavenModelManager().readMavenModel(pomFile);
		String derivedProjectName = model.getName();
		if (derivedProjectName == null) {
			derivedProjectName = model.getArtifactId();
		}
		if (derivedProjectName == null) {
			String[] groupPieces = model.getGroupId().split("\\.");
			int lastIndex = groupPieces.length - 1;
			if (lastIndex >= 0) {
				derivedProjectName = groupPieces[lastIndex];
			} else {
				throw new Exception("Bad pom.xml: no name, artifactId, or groupId.");
			}
		}
		MavenProjectInfo parent = null;
		MavenProjectInfo projectInfo = new MavenProjectInfo(derivedProjectName, pomFile, model, parent);
		ArrayList<MavenProjectInfo> projectInfos = new ArrayList<MavenProjectInfo>();
		projectInfos.add(projectInfo);
		ResolverConfiguration resolverConfiguration = new ResolverConfiguration();
		String activeProfiles = "pom.xml";
		resolverConfiguration.setSelectedProfiles(activeProfiles);
		ProjectImportConfiguration configuration = new ProjectImportConfiguration(resolverConfiguration);

		List<IMavenProjectImportResult> importResults = MavenPlugin.getProjectConfigurationManager().importProjects(projectInfos, configuration,
				monitor);
		
		List<IProject> projects = new ArrayList<>(importResults.size());
		for (IMavenProjectImportResult importResult : importResults) {
			// skip projects which have not been properly imported 
			IProject project = importResult.getProject();
			if (project != null) {
				MavenPlugin.getProjectConfigurationManager().updateProjectConfiguration(project, monitor);
				project.refreshLocal(IResource.DEPTH_INFINITE, null);
				assertTrue(project.hasNature(JavaCore.NATURE_ID));
				ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, null);
				projects.add(project);
			}
		}
		return projects;
	}
}
