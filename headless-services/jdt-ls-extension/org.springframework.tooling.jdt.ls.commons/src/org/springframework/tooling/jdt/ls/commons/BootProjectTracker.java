/*******************************************************************************
 * Copyright (c) 2022, 2025 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.jdt.ls.commons;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.springframework.tooling.jdt.ls.commons.classpath.ClasspathListenerManager;

public class BootProjectTracker {
	
	private Logger logger;
	
	private Set<IJavaProject> springProjects = new HashSet<>();
	private List<Consumer<Set<IJavaProject>>> listeners = Collections.synchronizedList(new ArrayList<>());

	public BootProjectTracker(Logger logger, Collection<Consumer<Set<IJavaProject>>> listeners) {
		this.logger = logger;
		this.listeners.addAll(listeners);
		new ClasspathListenerManager(logger, new ClasspathListenerManager.ClasspathListener() {
			
			@Override
			public void classpathChanged(IJavaProject jp) {
				processProject(jp);
			}
		});
		for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			if (p != null && p.isAccessible()) {
				IJavaProject jp = JavaCore.create(p);
				if (jp != null && jp.exists()) {
					processProject(jp);
				}
			}
		}
	}
	
	private void processProject(IJavaProject jp) {
		try {
			if (isSpringProject(jp)) {
				if (springProjects.add(jp)) {
					fireEvent();
				}
			} else {
				if (springProjects.remove(jp)) {
					fireEvent();
				}
			}
		} catch (JavaModelException e) {
			logger.log(e);
		}
	}
	
	private void fireEvent() {
		for (Consumer<Set<IJavaProject>> l : listeners) {
			try {
				l.accept(springProjects);
			} catch (Exception e) {
				logger.log(e);
			}
		}
	}
	
	public void addListener(Consumer<Set<IJavaProject>> l) {
		listeners.add(l);
	}
	
	public void removeListener(Consumer<Set<IJavaProject>> l) {
		listeners.remove(l);
	}
	
	private static boolean isSpringProject(IJavaProject jp) throws JavaModelException {
		if (jp.exists()) {
			IClasspathEntry[] classpath = jp.getResolvedClasspath(true);
			// Look for a 'spring-core' jar or project entry
			for (IClasspathEntry e : classpath) {
				if (isBootJar(e) || isBootProject(e)) {
					return true;
				}
			}
		}
		return false;
	}
	
	public static Stream<IJavaProject> streamSpringProjects() {
		return Arrays.stream(ResourcesPlugin.getWorkspace().getRoot().getProjects())
			.filter(p -> p != null && p.isAccessible())
			.map(JavaCore::create)
			.filter(jp -> {
				try {
					return jp != null && jp.exists() && isSpringProject(jp);
				} catch (JavaModelException e) {
					return false;
				}
			});
	}
	
	private static boolean isBootProject(IClasspathEntry e) {
		if (e.getEntryKind()==IClasspathEntry.CPE_PROJECT) {
			IPath path = e.getPath();
			String name = path.lastSegment();
			return name.startsWith("spring-core");
		}
		return false;
	}

	private static boolean isBootJar(IClasspathEntry e) {
		if (e.getEntryKind()==IClasspathEntry.CPE_LIBRARY) {
			IPath path = e.getPath();
			String name = path.lastSegment();
			return name.endsWith(".jar") && name.startsWith("spring-core");
		}
		return false;
	}


}
