/*******************************************************************************
 * Copyright (c) 2016, 2024 Pivotal, Inc.
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
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.ClasspathData;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.JavaUtils;
import org.springframework.ide.vscode.commons.maven.MavenCore;
import org.springframework.ide.vscode.commons.maven.MavenException;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.protocol.java.VM;
import org.springframework.ide.vscode.commons.util.RunnableWithException;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

/**
 * Classpath for a maven project
 *
 * @author Alex Boyko
 *
 */
public class MavenProjectClasspath implements IClasspath {
	
	private static Logger log = LoggerFactory.getLogger(IClasspath.class);

	private MavenCore maven;
	private File pom;
	private ClasspathData cachedData;

	MavenProjectClasspath(MavenCore maven, File pom) throws Exception {
		super();
		this.maven = maven;
		this.pom = pom;
		this.cachedData = createClasspathData();
	}

	private final MavenProject createMavenProject() throws MavenException {
		try {
			// Read with resolved dependencies
			return maven.readProject(pom, true);
		} catch (MavenException e) {
			log.error("{}", e);
			return maven.readProject(pom, false);
		}
	}

	public File getPomFile() {
		return pom;
	}

	MavenCore maven() {
		return maven;
	}

	public boolean exists() {
		return pom.exists();
	}

	@Override
	public String getName() {
		return cachedData != null ? cachedData.getName() : null;
	}
		
	private ImmutableList<CPE> resolveClasspathEntries(MavenProject project) throws Exception {
		LinkedHashSet<CPE> entries = new LinkedHashSet<>();
		safe(() -> maven.getJreLibs().forEach(path -> safe(() -> {
			CPE cpe = CPE.binary(path.toString());
			String javaVersion = maven.getJavaRuntimeMinorVersion();
			if (javaVersion == null) {
				javaVersion = "8";
			}
			cpe.setJavadocContainerUrl(new URL("https://docs.oracle.com/javase/" + javaVersion + "/docs/api/"));
			cpe.setSystem(true);
			entries.add(cpe);
			// Add at the end, not critical if throws exception, but the CPE needs to be around regardless if the below throws
			Path sources = JavaUtils.jreSources(path);
			if (sources != null) {
				cpe.setSourceContainerUrl(sources.toUri().toURL());
			}
		})));
		//Add jar dependencies...
		for (Artifact a : projectDependencies(project)) {
			File f = a.getFile();
			if (f!=null) {
				MavenProject peerProject = maven.findPeerProject(project, a);
				if (peerProject != null) {
					// Peer project dependency case
					File sourceFolder = new File(peerProject.getBuild().getSourceDirectory());
					File outputFolder = new File(peerProject.getBuild().getOutputDirectory());
					CPE cpe = CPE.source(sourceFolder, outputFolder);
					cpe.setOwn(false);
					cpe.setTest(false);
					cpe.setJavaContent(true);
					safe(() -> {
						String reportingDir = peerProject.getModel().getReporting().getOutputDirectory();
						if (reportingDir!=null) {
							File apidocs = new File(new File(reportingDir), "apidocs");
							cpe.setJavadocContainerUrl(apidocs.toURI().toURL());
						}
					});
					entries.add(cpe);
				} else {
					CPE cpe = CPE.binary(a.getFile().toPath().toString());
					safe(() -> { //add javadoc
						Artifact jdoc = maven.getJavadoc(a, project.getRemoteArtifactRepositories());
						if (jdoc!=null) {
							cpe.setJavadocContainerUrl(jdoc.getFile().toURI().toURL());
						}
					});
					safe(() -> { //add source
						Artifact source = maven.getSources(a, project.getRemoteArtifactRepositories());
						if (source!=null) {
							cpe.setSourceContainerUrl(source.getFile().toURI().toURL());
						}
					});
					entries.add(cpe);
				}
			}
		}
		//Add source folders...
		{	//main/java
			File sourceFolder = new File(project.getBuild().getSourceDirectory());
			File outputFolder = new File(project.getBuild().getOutputDirectory());
			CPE cpe = CPE.source(sourceFolder, outputFolder);
			cpe.setOwn(true);
			cpe.setTest(false);
			cpe.setJavaContent(true);
			safe(() -> {
				String reportingDir = project.getModel().getReporting().getOutputDirectory();
				if (reportingDir!=null) {
					File apidocs = new File(new File(reportingDir), "apidocs");
					cpe.setJavadocContainerUrl(apidocs.toURI().toURL());
				}
			});
			entries.add(cpe);
		}
		{	//test/java - duplicated down below without checking if folder exists
			
//			File sourceFolder = new File(project.getBuild().getTestSourceDirectory());
//			if (sourceFolder.exists()) {
//				File outputFolder = new File(project.getBuild().getTestOutputDirectory());
//				CPE cpe = CPE.source(sourceFolder, outputFolder);
//				cpe.setOwn(true);
//				cpe.setTest(true);
//				cpe.setJavaContent(true);
//				safe(() -> {
//					String reportingDir = project.getModel().getReporting().getOutputDirectory();
//					if (reportingDir!=null) {
//						File apidocs = new File(new File(reportingDir), "apidocs");
//						cpe.setJavadocContainerUrl(apidocs.toURI().toURL());
//					}
//				});
//				entries.add(cpe);
//			}
		}
		{	//main/resources
			for (Resource resource : project.getBuild().getResources()) {
				File sourceFolder = new File(resource.getDirectory());
				String targetPath = resource.getTargetPath();
				if (targetPath==null) {
					targetPath = project.getBuild().getOutputDirectory();
				}
				CPE cpe = CPE.source(sourceFolder, new File(targetPath));
				cpe.setOwn(true);
				cpe.setTest(false);
				cpe.setJavaContent(false);
				entries.add(cpe);
			}
		}
		{	//test/resources
			for (Resource resource : project.getBuild().getTestResources()) {
				File sourceFolder = new File(resource.getDirectory());
				String targetPath = resource.getTargetPath();
				if (targetPath==null) {
					targetPath = project.getBuild().getTestOutputDirectory();
				}
				CPE cpe = CPE.source(sourceFolder, targetPath==null ? null : new File(targetPath));
				cpe.setOwn(true);
				cpe.setTest(true);
				cpe.setJavaContent(false);
				entries.add(cpe);
			}
		}
		{	//test/java
			File sourceFolder = new File(project.getBuild().getTestSourceDirectory());
			File outputFolder = new File(project.getBuild().getTestOutputDirectory());
			CPE cpe = CPE.source(sourceFolder, outputFolder);
			cpe.setOwn(true);
			cpe.setTest(true);
			cpe.setJavaContent(true);
			safe(() -> {
				String reportingDir = project.getModel().getReporting().getOutputDirectory();
				if (reportingDir!=null) {
					File apidocs = new File(new File(reportingDir), "apidocs");
					cpe.setJavadocContainerUrl(apidocs.toURI().toURL());
				}
			});
			entries.add(cpe);
		}
		return ImmutableList.copyOf(entries);
	}

	@Override
	public ImmutableList<CPE> getClasspathEntries() throws Exception {
		return cachedData != null ? ImmutableList.copyOf(cachedData.getClasspathEntries()) : ImmutableList.of();
	}
	
	@Override
	public VM getVM() {
		return cachedData.getVM() != null ? cachedData.getVM() : null;
	}

	private Set<Artifact> projectDependencies(MavenProject project) {
		return project == null ? Collections.emptySet() : project.getArtifacts();
	}

	private List<File> projectOutput(MavenProject project) {
		if (project == null) {
			return Collections.emptyList();
		} else {
			return Arrays.asList(new File(project.getBuild().getOutputDirectory()), new File(project.getBuild().getTestOutputDirectory()));
		}
	}

	private static void safe(RunnableWithException do_stuff) {
		try {
			do_stuff.run();
		} catch (Exception e) {
// 			log.error("{}", e);
		}
	}

	private ClasspathData createClasspathData() throws Exception {
		MavenProject project = createMavenProject();

		ImmutableList<CPE> entries = resolveClasspathEntries(project);
		String name = project.getArtifact().getArtifactId();
		String javaVersion = maven.getJavaRuntimeVersion();
		String javaHome = maven.getJavaHome();
		
		return new ClasspathData(name, new LinkedHashSet<>(entries), new VM(javaVersion, Paths.get(javaHome).toString()));
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof MavenProjectClasspath) {
			MavenProjectClasspath other = (MavenProjectClasspath) obj;
			try {
				if (pom.equals(other.pom)
						&& Objects.equal(cachedData, other.cachedData)) {
					return super.equals(obj);
				}
			} catch (Throwable t) {
				log.error("{}", t);
			}
		}
		return false;
	}

}
