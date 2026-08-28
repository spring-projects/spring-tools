/*******************************************************************************
 * Copyright (c) 2018 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.ui;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.springsource.ide.eclipse.commons.internal.core.CorePlugin;

/**
 * Eclipse expressions ext point compatible Spring Property Tester able to test: {@link IProject}
 * <ul>
 * <li>{@link IProject} is a Spring Project</li>
 * <li>{@link IResource} belongs to a Spring Project<li>
 * </ul>
 *
 * @author Alex Boyko
 *
 */
public class SpringPropertyTester extends PropertyTester {

	public SpringPropertyTester() {
	}

	@Override
	public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
		if (expectedValue == null) {
			expectedValue = true;
		}
		if (receiver instanceof IProject && "isSpringProject".equals(property)) {
			return expectedValue.equals(isSpringProject((IProject) receiver));
		}
		if (receiver instanceof IResource && "isSpringProjectResource".equals(property)) {
			return expectedValue.equals(isSpringProjectResource((IResource) receiver));
		}
		return false;
	}

	public static boolean isSpringProject(IProject project) {
		if (project == null || !project.isAccessible()) {
			return false;
		}
		try {
			if (project.hasNature(JavaCore.NATURE_ID)) {
				IJavaProject jp = JavaCore.create(project);
				IClasspathEntry[] classpath = jp.getResolvedClasspath(true);
				// Look for a 'spring-boot' jar or project entry

				for (IClasspathEntry e : classpath) {
					if (isSpringJar(e) || isSpringProject(e)) {
						return true;
					}
				}
			}
		}
		catch (Exception e) {
			CorePlugin.log(e);
		}
		return false;
	}

	/**
	 * @return whether given resource is either Spring IProject or nested
	 * inside one.
	 */
	public static boolean isSpringProjectResource(IResource rsrc) {
		if (rsrc == null || !rsrc.isAccessible()) {
			return false;
		}
		boolean result = isSpringProject(rsrc.getProject());
		return result;
	}

	public static boolean isSpringProject(IClasspathEntry e) {
		if (e.getEntryKind() == IClasspathEntry.CPE_PROJECT) {
			IPath path = e.getPath();
			String name = path.lastSegment();
			return name.startsWith("spring-context");
		}
		return false;
	}

	public static boolean isSpringJar(IClasspathEntry e) {
		if (e.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
			IPath path = e.getPath();
			String name = path.lastSegment();
			return name.endsWith(".jar") && name.startsWith("spring-context");
		}
		return false;
	}

}
