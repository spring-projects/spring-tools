/*******************************************************************************
 * Copyright (c) 2018, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.jdt.ls.commons.classpath;

import static org.springframework.ide.vscode.commons.protocol.java.Classpath.ENTRY_KIND_BINARY;
import static org.springframework.ide.vscode.commons.protocol.java.Classpath.ENTRY_KIND_SOURCE;

import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.buildship.core.internal.configuration.GradleProjectNature;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.m2e.core.MavenPlugin;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;
import org.springframework.tooling.jdt.ls.commons.Logger;

@SuppressWarnings("restriction")
public class ClasspathUtil {

	private static final Object JRE_CONTAINER_ID = "org.eclipse.jdt.launching.JRE_CONTAINER";
	
	private static Set<String> getSystemLibraryPaths(IJavaProject javaProject) {
		try {
			IClasspathEntry jreContainer = getJreContainer(javaProject.getRawClasspath());
			if (jreContainer!=null) {
				IClasspathEntry[] resolvedJreEntries = ((JavaProject)javaProject).resolveClasspath(new IClasspathEntry[] {jreContainer});
				Set<String> paths = new HashSet<>();
				for (IClasspathEntry systemEntry : resolvedJreEntries) {
					paths.add(systemEntry.getPath().toFile().getAbsolutePath());
				}
				return paths;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return Collections.emptySet();
	}
	
	private static IClasspathEntry getJreContainer(IClasspathEntry[] rawClasspath) {
		if (rawClasspath!=null) {
			for (IClasspathEntry cpe : rawClasspath) {
				if (cpe.getEntryKind()==IClasspathEntry.CPE_CONTAINER) {
					if (cpe.getPath().segment(0).equals(JRE_CONTAINER_ID)) {
						return cpe;
					}
				}
			}
		}
		return null;
	}

	public static Classpath resolve(IJavaProject javaProject, Logger logger) throws Exception {
		//log("resolving classpath " + javaProject.getElementName() +" ...");
		List<CPE> cpEntries = new ArrayList<>();
		
		IClasspathEntry[] entries = javaProject.getResolvedClasspath(true);
		Set<String> systemLibs = getSystemLibraryPaths(javaProject);

		if (entries != null) {
			for (IClasspathEntry entry : entries) {
				try {
					cpEntries.addAll(createCpes(systemLibs, javaProject, entry));
				} catch (Exception e) {
					logger.log(e);
				}
			}
		}
		IClasspathEntry jreContainer = getJreContainer(javaProject.getRawClasspath());
		String javaVersion = jreContainer == null ? null : extractJavaVersion(jreContainer.getPath().lastSegment());
		Classpath classpath = new Classpath(cpEntries, javaVersion);
		logger.debug("classpath=" + classpath.getEntries().size() + " entries");
		return classpath;
	}
	
	private static String extractJavaVersion(String versionString) {
        String[] parts = versionString.split("-");
        if (parts.length > 1) {
            return parts[1]; // for "JavaSE-17" style
        } else if (versionString.contains(".")) {
            parts = versionString.split("\\.");
            if (parts[0] == "1") {
    			if (parts.length > 1) {
    				return parts[1];
    			}
    		} else {
    			String version = parts[0];
    			int idx = version.indexOf('+');
    			return idx >= 0 ? version.substring(0, idx) : version;
    		}
        }
        return versionString;      
    }
	
	private static AtomicBoolean enabledDownloadSources = new AtomicBoolean(false);
	
	public static List<CPE> createCpes(IJavaProject javaProject, IClasspathEntry entry) throws MalformedURLException, JavaModelException {
		return createCpes(getSystemLibraryPaths(javaProject), javaProject, entry);
	}
	
	public static void enableDownloadSources() {
		if (!enabledDownloadSources.getAndSet(true)) {
			IEclipsePreferences m2eprefs = InstanceScope.INSTANCE.getNode("org.eclipse.m2e.core");
			m2eprefs.putBoolean("eclipse.m2.downloadSources", true);
		}
	}

	private static List<CPE> createCpes(Set<String> systemLibs, IJavaProject javaProject, IClasspathEntry entry)
			throws MalformedURLException, JavaModelException {
		String kind = toContentKind(entry);
		switch (kind) {
		case Classpath.ENTRY_KIND_BINARY: {
			String path = entry.getPath().toFile().getAbsolutePath();
			CPE cpe = CPE.binary(path);
			if (systemLibs.contains(path)) {
				cpe.setSystem(true);
			}
			IPath sp = entry.getSourceAttachmentPath();
			if (sp != null) {
				cpe.setSourceContainerUrl(sp.toFile().getAbsoluteFile().toURI().toURL());
				// TODO:
//					IPath srp = entry.getSourceAttachmentRootPath();
//					if (srp!=null) {
//						
//					}
			}
			return Collections.singletonList(cpe);
		}
		case Classpath.ENTRY_KIND_SOURCE: {
			if (entry.getEntryKind() == IClasspathEntry.CPE_PROJECT) {
				IPath projectPath = entry.getPath();
				return resolveDependencyProjectCPEs(projectPath);
			} else if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				CPE cpe = createSourceCPE(javaProject, entry);
				if (cpe != null) {
					cpe.setOwn(true);
					cpe.setTest(entry.isTest());
					// If any package fragment roots from classpath entry has children then there is java content in the source folder
					for (IPackageFragmentRoot root : ((JavaProject)javaProject).computePackageFragmentRoots(entry)) {
						if (root.getChildren().length > 0) {
							cpe.setJavaContent(true);
							break;
						}
					}
				}
				return cpe == null ? null : Collections.singletonList(cpe);
			}
		}
		default:
			break;
		}
		return Collections.emptyList();
	}
	
	private static CPE createSourceCPE(IJavaProject javaProject, IClasspathEntry entry) throws JavaModelException {
		IPath sourcePath = entry.getPath();
		// log("source entry =" + sourcePath);
		IPath absoluteSourcePath = resolveWorkspacePath(sourcePath);
		// log("absoluteSourcePath =" + absoluteSourcePath);
		if (absoluteSourcePath != null) {
			IPath of = entry.getOutputLocation();
			// log("outputFolder =" + of);
			IPath absoluteOutFolder;
			if (of != null) {
				absoluteOutFolder = resolveWorkspacePath(of);
			} else {
				absoluteOutFolder = resolveWorkspacePath(javaProject.getOutputLocation());
			}
			return CPE.source(absoluteSourcePath.toFile(), absoluteOutFolder.toFile(), Map.of("project", javaProject.getProject().getLocation().toString()));
		}
		return null;
	}
	
	private static List<CPE> resolveDependencyProjectCPEs(IPath projectPath) throws JavaModelException {
		if (projectPath.segmentCount() == 1) {
			IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject(projectPath.segment(0));
			if (p.isAccessible()) {
				IJavaProject jp = JavaCore.create(p);
				IClasspathEntry[] rawClasspath = jp.getRawClasspath();
				ArrayList<CPE> cpes = new ArrayList<>(rawClasspath.length);
				for (IClasspathEntry entry : rawClasspath) {
					if (toContentKind(entry) == Classpath.ENTRY_KIND_SOURCE && entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
						CPE cpe = createSourceCPE(jp, entry);
						if (cpe != null) {
							cpes.add(cpe);
						}
					}
				}
				return cpes;
			}
		}
		return Collections.emptyList();
	}
	
	private static IPath resolveWorkspacePath(IPath path) {
		if (path.segmentCount()>0) {
			String projectName = path.segment(0);
			IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
			IPath projectRoot = project.getLocation();
			if (projectRoot!=null) {
				return projectRoot.append(path.removeFirstSegments(1));
			}
		}
		return null;
	}
	
	private static String toContentKind(IClasspathEntry entry) {
		switch (entry.getContentKind()) {
		case IPackageFragmentRoot.K_BINARY:
			return ENTRY_KIND_BINARY;
		case IPackageFragmentRoot.K_SOURCE:
			return ENTRY_KIND_SOURCE;
		default:
			return "unknown: " + entry.getContentKind();
		}
	}
	
	public static ProjectBuild createProjectBuild(IJavaProject jp, Logger logger) {
//		try {
			boolean likelyGradle = false;
			boolean likelyMaven = false;
			final Path home = System.getProperty("user.home") == null ? null : new File(System.getProperty("user.home")).toPath();
			if (MavenPlugin.isMavenProject(jp.getProject())) {
				// Causes deadlock between Maven Repo Registry Initialization and the `getProject(...)` call below
				// Repo registry hangs on to Maven settings and wait for a map that the code below locks while waiting for the MavenSettings lock.
//				IMavenProjectFacade facade = MavenPlugin.getMavenProjectRegistry().getProject(jp.getProject());
//				if (facade != null) {
//					return ProjectBuild.createMavenBuild(facade.getPom().getLocationURI().toASCIIString());
//				} else {
					return ProjectBuild.createMavenBuild(jp.getProject().getFile("pom.xml").getLocationURI().toASCIIString());
//				}
			} else if (GradleProjectNature.isPresentOn(jp.getProject())) {
				IFile g = jp.getProject().getFile("build.gradle");
				if (!g.exists()) {
					g = jp.getProject().getFile("build.gradle.kts");
				}
				return ProjectBuild.createGradleBuild(g.exists() ? g.getLocationURI().toASCIIString() : null);
			} else {
				try {
					for (IClasspathEntry e : jp.getRawClasspath()) {
						if (home != null && e.getPath() != null && e.getPath().toFile() != null) {
							Path path = e.getPath().toFile().toPath();
							if (path.startsWith(home.resolve(".gradle"))) {
								likelyGradle = true;
							} else if (path.startsWith(home.resolve(".m2"))) {
								likelyMaven = true;
							}
						}
					}
				} catch (Exception e) {
					// ignore
				}
			}
			if (likelyMaven) {
				IFile f = jp.getProject().getFile("pom.xml");
				if (f.exists()) {
					return ProjectBuild.createMavenBuild(f.getLocationURI().toASCIIString());
				}
			} else if (likelyGradle) {
				IFile g = jp.getProject().getFile("build.gradle");
				if (!g.exists()) {
					g = jp.getProject().getFile("build.gradle.kts");
				}
				if (g.exists()) {
					return ProjectBuild.createGradleBuild(g.getLocationURI().toASCIIString());
				}
			}
//		} catch (JavaModelException e) {
//			// ignore
//		}
		return new ProjectBuild(null, null);
	}
}
