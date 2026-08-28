/*******************************************************************************
 * Copyright (c) 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.frameworks.core.util;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaProject;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

public class JavaTypeUtil {

	public static String getFQTypeName(IJavaProject jp, String toParse) throws Exception {
		String fQName = fromOutputLocation(jp, toParse);
		return fQName;
	}

	private static String fromOutputLocation(IJavaProject jp, String toParse) throws Exception {
		if (jp.getOutputLocation() != null) {
			Set<IContainer> outputFolders = getOutputFolders(jp);
			if (outputFolders != null) {
				for (IContainer iContainer : outputFolders) {
					 String parseTypeFromPath = parseTypeFromPath(iContainer.getRawLocation(), toParse);
					 if (parseTypeFromPath != null) {
						 return parseTypeFromPath;
					 }
				}
			}
		}
		return null;
	}

	/**
	 * Will parse the type from a Bean resource path, given a base path for a project (e.g a project's output folder). 
	 * This will attempt to find a common root between the resource path and the base path, and relativize the remaining part
	 * which is assumed to be the type.
	 * @param base path
	 * @param resourcePath
	 * @return parsed type, or null if nothing parsed
	 */
	private static String parseTypeFromPath(IPath base, String resourcePath) {
		if (base != null) {
			String baseVal = base.toOSString();
			if (baseVal != null) {
				java.nio.file.Path basePath = Paths.get(baseVal);
				java.nio.file.Path pathContainingType = Paths.get(resourcePath);
				if (pathContainingType.startsWith(basePath)) {
					try {
						java.nio.file.Path relativize = basePath.relativize(pathContainingType);
						return relativize.toString();
					} catch (Exception e) {
						//Ignore, it means outputPath and resource path do not share common root
					}
				}
			}
		}
		return null;
	}
	
	public static Set<IContainer> getOutputFolders(IJavaProject jp) {
		IContainer defaultOutput = getDefaultOutputFolder(jp);
		if (defaultOutput != null) {
			return Collections.singleton(getDefaultOutputFolder(jp));
		} else {
			return Collections.emptySet();
		}
		//TODO: other output folders (i.e indivudla source folders can specifiy separate output folders)
	}

	public static IContainer getDefaultOutputFolder(IJavaProject jp) {
		try {
			IPath loc = jp.getOutputLocation();
			String pname = loc.segment(0);
			if (loc.segmentCount() == 1) {
				//project is its own output folder. Discouraged... but possible
				return ResourcesPlugin.getWorkspace().getRoot().getProject(pname);
			} else {
				return ResourcesPlugin.getWorkspace().getRoot().getProject(pname).getFolder(loc.removeFirstSegments(1));
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return null;
	}
}
