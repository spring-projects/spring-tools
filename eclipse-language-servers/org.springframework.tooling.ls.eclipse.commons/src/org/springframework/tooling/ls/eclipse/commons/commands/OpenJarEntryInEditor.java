/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.commons.commands;

import java.net.URI;
import java.util.Optional;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJarEntryResource;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.ui.PartInitException;
import org.springframework.tooling.ls.eclipse.commons.LanguageServerCommonsActivator;

@SuppressWarnings("restriction")
public class OpenJarEntryInEditor extends AbstractHandler implements IHandler {

	private static final String JAR_URI_PARAM = "jarUri";

	record JarUri(IPath jar, IPath path) {}

	private static JarUri createJarUri(URI uri) {
		if (!"jar".equals(uri.getScheme())) {
			throw new IllegalArgumentException();
		}
		String s = uri.getSchemeSpecificPart();
		int idx = s.indexOf('!');
		if (idx <= 0) {
			throw new IllegalArgumentException();
		}
		return new JarUri(new Path(URI.create(s.substring(0, idx)).getPath()), new Path(s.substring(idx + 1)));
 	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		String projectName = event.getParameter(OpenJavaElementInEditor.PROJECT_NAME);
		String jarUriStr = event.getParameter(JAR_URI_PARAM);

		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);

		if (project != null && jarUriStr != null) {
			URI uri = URI.create(jarUriStr);
			IJavaProject javaProject = JavaCore.create(project);
			if (javaProject != null) {
				JarUri jarUri = createJarUri(uri);
				findJavaObj(javaProject, jarUri).ifPresent(s -> {
					try {
						EditorUtility.openInEditor(s, true);
					} catch (PartInitException e) {
						LanguageServerCommonsActivator.getInstance().getLog().log(e.getStatus());
					}
				});
			} else {
				LanguageServerCommonsActivator.getInstance().getLog().log(new Status(IStatus.WARNING,
						LanguageServerCommonsActivator.PLUGIN_ID, "Cannot find project: " + projectName));
			}
		}

		return null;
	}

	private static Optional<Object> findJavaObj(IJavaProject j, JarUri uri) {
		try {
			for (IPackageFragmentRoot fr : j.getAllPackageFragmentRoots()) {
				if (uri.jar().equals(fr.getPath())) {
					for (Object o : fr.getNonJavaResources()) {
						if (o instanceof IJarEntryResource je) {
							return findJarEntry(je, uri.path());
						}
					}
					if ("class".equals(uri.path().getFileExtension())) {
						String packageName = uri.path().removeLastSegments(1).toString().replace(IPath.SEPARATOR, '.');
						IPackageFragment pkg = fr.getPackageFragment(packageName);
						if (pkg != null) {
							IClassFile cf = pkg.getClassFile(uri.path().lastSegment());
							if (cf != null) {
								return Optional.of(cf);
							}
						}
					}

				}
			}
		} catch (JavaModelException e) {
			LanguageServerCommonsActivator.getInstance().getLog().log(e.getStatus());
		}
		return Optional.empty();
	}

	private static Optional<Object> findJarEntry(IJarEntryResource r, IPath p) {
		if (r.getFullPath().equals(p)) {
			return Optional.of(r);
		} else if (r.getFullPath().isPrefixOf(p)) {
			for (IJarEntryResource c : r.getChildren()) {
				Optional<Object> opt = findJarEntry(c, p);
				if (opt.isPresent()) {
					return opt;
				}
			}
		}
		return Optional.empty();
	}

}
