/*******************************************************************************
 * Copyright (c) 2015 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.validation.framework;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.springframework.ide.eclipse.boot.util.Log;

/**
 * Model element that holds data for an {@link ICompilationUnit}.
 *
 * @author Kris De Volder
 */
public class CompilationUnitElement implements IModelElement {

	private ICompilationUnit cu;
	private IClasspathEntry[] classpath;

	public CompilationUnitElement(ICompilationUnit cu) {
		Assert.isNotNull(cu);
		this.cu = cu;
	}

	public ICompilationUnit getCompilationUnit() {
		return cu;
	}

	@Override
	public IResource getElementResource() {
		return cu.getResource();
	}

	/**
	 * Fetch the resolved classpath of the project this CU belong to.
	 */
	public IClasspathEntry[] getClasspath() {
		try {
			if (classpath == null) {
					classpath = cu.getJavaProject().getResolvedClasspath(false);
			}
			} catch (Exception e) {
				//silently ignore problems resolving classpath. Rules depending on classpath should
				// not execute if classpath is not known, for whatever reason. Do not spam the error
				// log with messages about that.
			}
		return classpath;
	}
	
	public static CompilationUnitElement create(IResource resource) {
		try {
			IJavaProject project = getJavaProject(resource);
			if (project == null) {
				return null;
			}
			if (resource.getType() == IResource.FILE) {
				IJavaElement javaEl = JavaCore.create((IFile) resource);
				if (javaEl instanceof ICompilationUnit) {
					return new CompilationUnitElement((ICompilationUnit) javaEl);
				}
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return null;
	}

	private static IJavaProject getJavaProject(IResource resource) {
		IProject project = resource.getProject();
		return JavaCore.create(project);
	}

}
