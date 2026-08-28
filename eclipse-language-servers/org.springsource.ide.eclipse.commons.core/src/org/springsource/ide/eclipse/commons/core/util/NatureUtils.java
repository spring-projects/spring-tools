/*******************************************************************************
 * Copyright (c) 2012, 2015 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.core.util;

import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.springsource.ide.eclipse.commons.internal.core.CorePlugin;

/**
 * @author Kris De Volder
 */
public class NatureUtils {

	/**
	 * Make sure a project has a number of required natures. If the natures are not yet present, add them now.
	 * @throws CoreException
	 */
	public static void ensure(IProject project, IProgressMonitor mon, String... reqNatures) throws CoreException {
		IProjectDescription desc = project.getDescription();
		String[] oldNaturesArr = desc.getNatureIds();
		Set<String> natures = new LinkedHashSet<String>();
		for (String n : reqNatures) {
			natures.add(n);
		}
		for (String n : oldNaturesArr) {
			natures.add(n);
		}
		if (natures.size() > oldNaturesArr.length) {
			//Some natures got added
			desc.setNatureIds(natures.toArray(new String[natures.size()]));
			project.setDescription(desc, mon);
		} else {
			//No new natures added, but need to set it to force desired ordering
			desc.setNatureIds(natures.toArray(new String[natures.size()]));
			project.setDescription(desc, IResource.AVOID_NATURE_CONFIG, mon);
		}
	}

	/**
	 * Removes a nature from a project. This does nothing if the project doesn't have the nature.
	 */
	public static void remove(IProject project, String natureId, IProgressMonitor mon) throws CoreException {
		IProjectDescription desc = project.getDescription();
		String[] oldNaturesArr = desc.getNatureIds();
		Set<String> natures = new LinkedHashSet<String>();
		for (String n : oldNaturesArr) {
			if (!n.equals(natureId)) {
				natures.add(n);
			}
		}
		if (natures.size() != oldNaturesArr.length) {
			//Something removed
			desc.setNatureIds(natures.toArray(new String[natures.size()]));
			project.setDescription(desc, mon);
		}

	}

	public static boolean hasNature(IProject p, String natureId) {
		try {
			return p != null && p.isAccessible() && p.hasNature(natureId);
		} catch (CoreException e) {
			CorePlugin.log(e);
		}
		return false;
	}


}
