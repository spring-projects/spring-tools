/*******************************************************************************
 * Copyright (c) 2013-2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.osgi.framework.Bundle;
import org.springframework.ide.eclipse.boot.util.version.Version;
import org.springframework.ide.eclipse.boot.util.version.VersionParser;
import org.springsource.ide.eclipse.commons.internal.core.CorePlugin;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * @author Kris De Volder
 */
public class BootPropertyTester extends PropertyTester {

	private static final Pattern JAR_VERSION_REGEXP = Pattern.compile(
			".*\\-([0-9]+\\.[0-9]+\\.[0-9]+((\\.|\\-)[^.]+)?)\\.jar");

	public static final String SPRING_BOOT_DEVTOOLS_AID = "spring-boot-devtools";
	public static final String SPRING_BOOT_DEVTOOLS_GID = "org.springframework.boot";

	public BootPropertyTester() {
	}

	//@Override
	@Override
	public boolean test(Object rsrc, String property, Object[] args, Object expectedValue) {
		if (expectedValue == null) {
			expectedValue = true;
		}
//		System.out.println(">>> BootPropertyTester");
//		System.out.println(" rsrc = "+rsrc);
//		System.out.println(" property = "+property);
//		System.out.println(" expectedValue = "+expectedValue);
		if (rsrc instanceof IProject && "isBootProject".equals(property)) {
			return expectedValue.equals(isBootProject((IProject)rsrc));
		}
		if (rsrc instanceof IResource && "isBootResource".equals(property)) {
			return expectedValue.equals(isBootResource((IResource) rsrc));
		}
		if (rsrc instanceof IResource && "hasBootDevTools".equals(property)) {
			return hasBootDevTools(((IResource) rsrc).getProject());
		}
		if (rsrc instanceof IProject && "isBoot2Project".equals(property)) {
			return expectedValue.equals(isBoot2Project((IProject)rsrc));
		}
		if (rsrc instanceof IResource && "isBoot2Resource".equals(property)) {
			return expectedValue.equals(isBoot2Resource((IResource) rsrc));
		}
		return false;
	}

	public static boolean isDevtoolsJar(IClasspathEntry e) {
		if (e.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
			IPath path = e.getPath();
			String name = path.lastSegment();
			return name.endsWith(".jar") && name.startsWith("spring-boot-devtools");
		}
		return false;
	}

	public static boolean isActuatorJar(IClasspathEntry e) {
		if (e.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
			IPath path = e.getPath();
			String name = path.lastSegment();
			return name.endsWith(".jar") && name.startsWith("spring-boot-actuator-");
		}
		return false;
	}


	public static boolean hasDevtools(IProject p) {
		try {
			if (p != null && p.isAccessible()) {
				IJavaProject jp = JavaCore.create(p);
				if (jp.exists()) {
					IClasspathEntry[] classpath = jp.getResolvedClasspath(true);
					if (classpath != null) {
						for (IClasspathEntry e : classpath) {
							if (BootPropertyTester.isDevtoolsJar(e)) {
								return true;
							}
						}
					}
				}
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return false;
	}

	public static boolean isBootProject(IProject project) {
		if (project == null || ! project.isAccessible()) {
			return false;
		}
		try {
			if (project.hasNature(JavaCore.NATURE_ID)) {
				if (!isExcludedProject(project)) {
					IJavaProject jp = JavaCore.create(project);
					IClasspathEntry[] classpath = jp.getResolvedClasspath(true);
					//Look for a 'spring-boot' jar or project entry

					for (IClasspathEntry e : classpath) {
						if (isBootJar(e) || isBootProject(e)) {
							return true;
						}
					}
				}
			}
		} catch (Exception e) {
			CorePlugin.log(e);
		}
		return false;
	}

	public static boolean isBoot2Project(IProject project) {
		if (project == null || ! project.isAccessible()) {
			return false;
		}
		try {
			if (project.hasNature(JavaCore.NATURE_ID)) {
				if (!isExcludedProject(project)) {
					IJavaProject jp = JavaCore.create(project);
					IClasspathEntry[] classpath = jp.getResolvedClasspath(true);
					//Look for a 'spring-boot' jar or project entry

					for (IClasspathEntry e : classpath) {
						if ((isBootJar(e) || isBootProject(e)) && isVersion(e, "2.")) {
							return true;
						}
					}
				}
			}
		} catch (Exception e) {
			CorePlugin.log(e);
		}
		return false;
	}

	public static boolean hasBootDevTools(IProject project) {
		try {
			ISpringBootProject bootProject = SpringBootCore.getDefault().project(project);
			if (bootProject != null) {
				return fastHasDevTools(bootProject);
			}
		} catch (CoreException e) {
			Log.log(e);
		} catch (TimeoutException e) {
			// ignore timeouts. Could lots of them while maven updates classpath
		} catch (InterruptedException e) {
			// ignore iterrupts?
		} catch (ExecutionException e) {
			Log.log(e);
		}
		return false;
	}

	/**
	 * Like hasDevTools, but suitable for calling on the UI thread. This operation may fail
	 * with a {@link TimeoutException} if it can not be readily determined whether a project
	 * has dev tools as a dependency (this may happen, for example because m2e is still in the process of
	 * resolving the dependencies). It would be undesirable to block on the UI thread to wait for this
	 * process to complete.
	 */
	public static boolean fastHasDevTools(IProject bootProject) throws TimeoutException, InterruptedException, ExecutionException {
		if (bootProject == null) return false;
		CompletableFuture<Boolean> result = new CompletableFuture<>();
		Job job = new Job("Check for devtools") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					result.complete(hasDevtools(bootProject));
				} catch (Throwable e) {
					result.completeExceptionally(e);
				}
				return Status.OK_STATUS;
			}
		};
		job.setSystem(true);
		job.schedule();
		return result.get(100, TimeUnit.MILLISECONDS);
	}

	/**
	 * Like hasDevTools, but suitable for calling on the UI thread. This operation may fail
	 * with a {@link TimeoutException} if it can not be readily determined whether a project
	 * has dev tools as a dependency (this may happen, for example because m2e is still in the process of
	 * resolving the dependencies). It would be undesirable to block on the UI thread to wait for this
	 * process to complete.
	 */
	@Deprecated // Does not work for gradle
	public static boolean fastHasDevTools(ISpringBootProject bootProject) throws TimeoutException, InterruptedException, ExecutionException {
		CompletableFuture<Boolean> result = new CompletableFuture<>();
		Job job = new Job("Check for devtools") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					result.complete(hasDevTools(bootProject));
				} catch (Throwable e) {
					result.completeExceptionally(e);
				}
				return Status.OK_STATUS;
			}
		};
		job.setSystem(true);
		job.schedule();
		return result.get(100, TimeUnit.MILLISECONDS);
	}

	@Deprecated // Does not work for gradle (use method with IProject parameter instead)
	public static boolean hasDevTools(ISpringBootProject bootProject) {
		try {
			List<IMavenCoordinates> deps = bootProject.getDependencies();
			if (deps != null) {
				for (IMavenCoordinates d : deps) {
					if (SPRING_BOOT_DEVTOOLS_AID.equals(d.getArtifactId())) {
						return true;
					}
				}
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return false;
	}

	private static boolean isExcludedProject(IProject project) {
		Pattern exclusion = BootPreferences.getInstance().getProjectExclusion();
		return exclusion.matcher(project.getName()).matches();
	}

	/**
	 * @return whether given resource is either Spring Boot IProject or nested inside one.
	 */
	public static boolean isBootResource(IResource rsrc) {
		if (rsrc == null || ! rsrc.isAccessible()) {
			return false;
		}
		boolean result = isBootProject(rsrc.getProject());
		return result;
	}

	private Object isBoot2Resource(IResource rsrc) {
		if (rsrc == null || ! rsrc.isAccessible()) {
			return false;
		}
		boolean result = isBoot2Project(rsrc.getProject());
		return result;
	}

	public static boolean isBootProject(IClasspathEntry e) {
		if (e.getEntryKind() == IClasspathEntry.CPE_PROJECT) {
			IPath path = e.getPath();
			String name = path.lastSegment();
			return name.startsWith("spring-boot");
		}
		return false;
	}

	public static boolean isVersion(IClasspathEntry e, String v) {
		if (e.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
			IPath path = e.getPath();
			String name = path.lastSegment();
			String version = getVersionFromJarName(name);
			return version.startsWith(v);
		}
		return false;
	}

	public static boolean isBootJar(IClasspathEntry e) {
		if (e.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
			IPath path = e.getPath();
			String name = path.lastSegment();
			return name.endsWith(".jar") && name.startsWith("spring-boot");
		}
		return false;
	}

	/**
	 * Attempt to determine spring-boot version from project's classpath, in a form that
	 * is easy to compare to version ranges. May return null if the version couldn't be
	 * determined.
	 */
	public static Version getBootVersion(IProject p) {
		try {
			if (p.isAccessible() && p.hasNature(JavaCore.NATURE_ID)) {
				IJavaProject jp = JavaCore.create(p);
				IClasspathEntry[] cp = jp.getResolvedClasspath(true);
				for (IClasspathEntry e : cp) {
					if (isBootJar(e)) {
						String version = getJarVersion(e);
						if (version != null) {
							return VersionParser.DEFAULT.parse(version);
						}
					}
				}
			}
		} catch (Exception error) {
			Log.log(error);
		}
		return null;
	}

	private static String getJarVersion(IClasspathEntry e) {
		String name = e.getPath().lastSegment();
		return getVersionFromJarName(name);
	}

	public static String getVersionFromJarName(String name) {
		//Example: (old format) spring-boot-starter-web-1.2.3.RELEASE.jar
		//         (new format) spring-boot-starter-web-2.4.0.jar
		//         (new format) spring-boot-starter-web-2.4.0-SNAPSHOT.jar

		//Pattern regexp = Pattern.compile(".*\\-([^.]+\\.[^.]+\\.[^.]+\\.[^.]+)\\.jar");
		Pattern regexp = JAR_VERSION_REGEXP;

		Matcher matcher = regexp.matcher(name);
		if (matcher.matches()) {
			String versionStr = matcher.group(1);
			return versionStr;
		}
		return null;
	}

	public static boolean supportsLifeCycleManagement(IProject project) {
		try {
			Version version = BootPropertyTester.getBootVersion(project);
			if (version != null) {
				return VersionParser.DEFAULT.parseRange("1.3.0").match(version);
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return false;
	}

	/**
	 * this is a workaround for an initialization issue around m2e
	 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=479245
	 *
	 * this workaround tries to avoid very early m2e.jdt activation
	 * and therefore tries to reduce the likelihood of running into
	 * the issue.
	 */
	public static boolean workaroundMavenBundleInitializationIssue(IProject project) {
		try {
			if (project.hasNature("org.eclipse.m2e.core.maven2Nature")) {
				Bundle bundle = Platform.getBundle("org.eclipse.m2e.jdt");
				if (bundle != null) {
					if (bundle.getState() != Bundle.ACTIVE) {
						return true;
					}
				}
			}
		} catch (CoreException e) {
		}

		return false;
	}




}
