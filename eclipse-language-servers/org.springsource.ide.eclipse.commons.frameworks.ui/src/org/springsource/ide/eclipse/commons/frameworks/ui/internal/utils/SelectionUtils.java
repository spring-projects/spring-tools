/*******************************************************************************
 * Copyright (c) 2012 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.frameworks.ui.internal.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IFileEditorInput;
import org.springsource.ide.eclipse.commons.frameworks.core.FrameworkCoreActivator;
import org.springsource.ide.eclipse.commons.ui.SpringUIUtils;


/**
 * In several places (typically action delegates and the like) we end up writing very similar "selectionChanged" methods. Often
 * replicating bugs in the selection handling logic across them. Ideally we should centralize "selection processing" in here,
 * so that we won't have to fix these bugs multiple times.
 * 
 * @author Kris De Volder
 */
public class SelectionUtils {

	/**
	 * Determine a List of project based on selection. If selection is a "IStructuredSelection" (typicallly, from
	 * a tree viewer). Then all selected elements will be adapted to IResources and their associated projects determined.
	 * These projects will be added to the list unless:
	 *   - project is already in the list
	 *   - project doesn't match the ProjectFilter.
	 * <p>
	 * If the selection is not an IStructuredSelection, then we will attempt to determine a single project 
	 * based on the active text editor. (This is to support action enablement while editing).
	 * 
	 * @param selection Some selection that just became the 'active' selection.
	 * @param filter Projects that don't match the filter will be silently ignored.
	 */
	public static List<IProject> getProjects(ISelection selection, ProjectFilter filter) {
		LinkedHashSet<IProject> selected = new LinkedHashSet<IProject>();
		//Note: use a set to collect projects, to avoid duplicates if user selected multiple resources in same project
		//   => do not want to execute commands more than once per project!
		if (selection instanceof IStructuredSelection) {
			Iterator<?> iter = ((IStructuredSelection) selection).iterator();
			while (iter.hasNext()) {
				Object obj = iter.next();
				IResource resource = null;
				if (obj instanceof IResource) {
					resource = (IResource) obj;
				} else if (obj instanceof IAdaptable) {
					resource = (IResource) ((IAdaptable) obj)
					.getAdapter(IResource.class);
				}
				if (resource != null) {
					if (filter.isAcceptable(resource.getProject())) {
						selected.add(resource.getProject());
					}
				}
			}
		} else {
			if (SpringUIUtils.getActiveEditor() != null) {
				if (SpringUIUtils.getActiveEditor().getEditorInput() instanceof IFileEditorInput) {
					selected.add(
							(((IFileEditorInput) SpringUIUtils.getActiveEditor().getEditorInput()).getFile()
									.getProject()));
				}
			}
		}
		return new ArrayList<IProject>(selected);
	}

	public static IPackageFragment getPackage(IStructuredSelection selection) {
		Object element = selection.getFirstElement();
		if (element != null) {
			if (element instanceof IJavaElement) {
				return getPackageFragment((IJavaElement) element);
			}
		}
		return null;
	}

	public static IType getType(IStructuredSelection selection) {
		try {
			Object element = selection.getFirstElement();
			if (element instanceof IJavaElement) {
				IJavaElement javaElement = (IJavaElement)element;
				IType type = (IType) javaElement.getAncestor(IJavaElement.TYPE);
				if (type != null) {
					return type;
				}
				if (javaElement instanceof ICompilationUnit) {
					//Also handle case where a compilatuon unit is selected, in this case, get the
					//first type declared in it.
					ICompilationUnit compUnit = (ICompilationUnit) javaElement;
					IType[] types = compUnit.getAllTypes();
					if (types != null && types.length > 0) {
						return types[0];
					}
				}
			}
		} catch (Exception e) {
			FrameworkCoreActivator.logError("Error getting Java Type from selection", e);
		}
		return null;
	}

	private static IPackageFragment getPackageFragment(IJavaElement element) {
		return (IPackageFragment) element.getAncestor(IJavaElement.PACKAGE_FRAGMENT);
	}

	/**
	 * Gets containing package fragment root of a given IJavaElement
	 */
	public static IPackageFragmentRoot getPackageFragmentRoot(IJavaElement element) {
		return (IPackageFragmentRoot) element.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
	}

	
}
