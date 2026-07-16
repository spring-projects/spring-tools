/*******************************************************************************
 * Copyright (c) 2022 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.boot.ls;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;

@SuppressWarnings("restriction")
public class BootProjectTester extends PropertyTester {

	@Override
	public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
		if ("isBootResource".equals(property)) {
			IResource resource = null;
			if (receiver instanceof IAdaptable) {
				resource = ((IAdaptable) receiver).getAdapter(IResource.class);
			} else if (receiver instanceof IDocument) {
				resource = LSPEclipseUtils.getFile((IDocument) receiver);
			}
			if (resource != null) {
				IProject project = resource.getProject();
				if (project != null && JavaProject.hasJavaNature(project)) {
					IJavaProject jp = JavaCore.create(project);
					if (jp != null) {
						return isProjectWithDependency(jp, "spring-boot");
					}
				}
			}
		} else if ("isProjectWithDependencyResource".equals(property)) {
			IResource resource = null;
			if (receiver instanceof IAdaptable) {
				resource = ((IAdaptable) receiver).getAdapter(IResource.class);
			} else if (receiver instanceof IDocument) {
				resource = LSPEclipseUtils.getFile((IDocument) receiver);
			}
			if (resource != null) {
				IProject project = resource.getProject();
				if (project != null && JavaProject.hasJavaNature(project)) {
					IJavaProject jp = JavaCore.create(project);
					if (jp != null) {
						return isProjectWithDependency(jp, (String) args[0]);
					}
				}
			}
		}
		return false;
	}
	
	private static boolean isProjectWithDependency(IJavaProject jp, String dep) {
		if (jp == null || ! jp.getProject().isAccessible()) {
			return false;
		}
		try {
			IClasspathEntry[] classpath = jp.getResolvedClasspath(true);

			for (IClasspathEntry e : classpath) {
				if (isDependencyJar(dep, e) || isDependencyProject(dep, e)) {
					return true;
				}
			}
		} catch (Exception e) {
			BootLanguageServerPlugin.getDefault().getLog().error("Failed to resolve classpath for project '%s'".formatted(jp.getElementName()), e);
		}
		return false;
	}
	
	private static boolean isDependencyJar(String dep, IClasspathEntry e) {
		if (e.getEntryKind()==IClasspathEntry.CPE_LIBRARY) {
			IPath path = e.getPath();
			String name = path.lastSegment();
			return name.endsWith(".jar") && name.startsWith(dep);
		}
		return false;
	}
	
	private static boolean isDependencyProject(String dep, IClasspathEntry e) {
		if (e.getEntryKind()==IClasspathEntry.CPE_PROJECT) {
			IPath path = e.getPath();
			String name = path.lastSegment();
			return name.startsWith(dep);
		}
		return false;
	}

}
