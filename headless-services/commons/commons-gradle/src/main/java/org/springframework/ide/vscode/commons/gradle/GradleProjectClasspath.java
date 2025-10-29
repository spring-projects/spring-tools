/*******************************************************************************
 * Copyright (c) 2017, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.gradle;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.eclipse.EclipseExternalDependency;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.eclipse.EclipseSourceDirectory;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.JavaUtils;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.protocol.java.Jre;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

/**
 * Implementation of {@link IClasspath} for Gradle projects
 *
 * @author Alex Boyko
 *
 */
public class GradleProjectClasspath implements IClasspath {

	private static final String JAVA_HOME = "java.home";
	private static final String JAVA_RUNTIME_VERSION = "java.runtime.version";
	private static final String JAVA_BOOT_CLASS_PATH = "sun.boot.class.path";

	private EclipseProject project;
	private BuildEnvironment buildEnvironment;

	public GradleProjectClasspath(GradleCore gradle, File projectDir) throws GradleException {
		super();
		this.project = gradle.getModel(projectDir, EclipseProject.class);
		this.buildEnvironment = gradle.getModel(projectDir, BuildEnvironment.class);
	}

	@Override
	public ImmutableList<CPE> getClasspathEntries() throws Exception {
		if (project == null) {
			return ImmutableList.of();
		} else {
			Builder<CPE> entries = ImmutableList.builder();
			getJreLibs().forEach(path -> {
				CPE cpe = CPE.binary(path.toString());
				String javaVersion = JavaUtils.getJavaRuntimeMinorVersion(getJavaRuntimeVersion());
				if (javaVersion == null) {
					javaVersion = "8";
				}
				String urlStr = "https://docs.oracle.com/javase/" + javaVersion + "/docs/api/";
				try {
					cpe.setJavadocContainerUrl(URI.create(urlStr).toURL());
				} catch (MalformedURLException e) {
					log.error("Invalid javadoc URL: " + urlStr, e);
				}
				cpe.setSystem(true);
				entries.add(cpe);
			});

			for (EclipseExternalDependency dep : project.getClasspath()) {
				entries.add(new CPE(Classpath.ENTRY_KIND_BINARY, dep.getFile().getAbsolutePath()));
			}
/**			for (EclipseProjectDependency dep : project.getProjectDependencies()) {
				EclipseProject peer = findPeer(root, dep.getTargetProject().getName());
				if (peer!=null) {
					for (EclipseSourceDirectory sf : peer.getSourceDirectories()) {
						entries.add(createSourceCPE(peer, sf));
					}
				}
			} */
			for (EclipseSourceDirectory sf : project.getSourceDirectories()) {
				CPE cpe = createSourceCPE(project, sf);
				cpe.setOwn(true);
				cpe.setJavaContent(!Files.walk(sf.getDirectory().toPath()).filter(Files::isRegularFile).anyMatch(p -> !p.startsWith(".") && !p.getFileName().toString().endsWith(".java")));
				boolean isTest = false;
				try {
					isTest = sf.getClasspathAttributes().stream().filter(attr -> "gradle_used_by_scope".equals(attr.getName()) && "test".equals(attr.getValue())).findFirst().isPresent();
				} catch (Throwable t) {
					log.error("{}", t);
				}
				cpe.setTest(isTest);
				entries.add(cpe);
			}
			return entries.build();
		}
	}

	private static CPE createSourceCPE(EclipseProject project, EclipseSourceDirectory sf) {
		File sourceFolder = sf.getDirectory();
		String of = sf.getOutput();
		return CPE.source(sourceFolder.getAbsoluteFile(), new File(project.getProjectDirectory(), of));
	}

	@Override
	public String getName() {
		return project == null ? null : project.getName();
	}
	
	@Override
	public Jre getJre() {
		if (buildEnvironment == null) {
			throw new IllegalArgumentException("No Gradle build available");
		}
		return new Jre(getJavaRuntimeVersion(), buildEnvironment.getJava().getJavaHome().toPath().toString());
	}

	public String getGradleVersion()  throws GradleException {
		if (buildEnvironment == null) {
			throw new GradleException(new Exception("Cannot find Gradle version"));
		} else {
			return buildEnvironment.getGradle().getGradleVersion();
		}
	}

	public File getGradleHome() throws GradleException {
		if (buildEnvironment == null) {
			throw new GradleException(new Exception("Cannot find Gradle home folder"));
		} else {
			return buildEnvironment.getGradle().getGradleUserHome();
		}
	}

	public String getJavaRuntimeVersion() {
		return System.getProperty(JAVA_RUNTIME_VERSION);
	}

	public Optional<Path> getJavaHome() {
		if (buildEnvironment == null) {
			return Optional.of(Paths.get(System.getProperty(JAVA_HOME)));
		} else {
			return Optional.of(buildEnvironment.getJava().getJavaHome().toPath());
		}
	}
	
	private Stream<Path> getJreLibs() {
		return JavaUtils.jreLibs(() -> JavaUtils.getJavaRuntimeMinorVersion(getJavaRuntimeVersion()), this::getJavaHome, () -> System.getProperty(JAVA_BOOT_CLASS_PATH));
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof GradleProjectClasspath) {
			return super.equals(obj);
		}
		return false;
	}

}
