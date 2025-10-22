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

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.util.Optional;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
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

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		String projectName = event.getParameter(OpenJavaElementInEditor.PROJECT_NAME);
		String jarUriStr = event.getParameter(JAR_URI_PARAM);

		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);

		if (project != null && jarUriStr != null) {
			URI uri = URI.create(jarUriStr);
			IJavaProject javaProject = JavaCore.create(project);
			if (javaProject != null) {
				try {
				final JarURLConnection c = (JarURLConnection) uri.toURL().openConnection();
				Object inputElement = findJavaObj(javaProject, c).orElseGet(() -> {
						return new IStorage() {

							@SuppressWarnings("unchecked")
							@Override
							public <T> T getAdapter(Class<T> adapter) {
								if (URI.class.equals(adapter)) {
									return (T) uri;
								}
								return null;
							}

							@Override
							public InputStream getContents() throws CoreException {
								try {
									return c.getInputStream();
								} catch (IOException e) {
									throw new CoreException(new Status(IStatus.ERROR,
											LanguageServerCommonsActivator.PLUGIN_ID, "Cannot load JAR entry", e));
								}
							}

							@Override
							public IPath getFullPath() {
								return new Path(jarUriStr);
							}

							@Override
							public String getName() {
								return new Path(c.getEntryName()).lastSegment();
							}

							@Override
							public boolean isReadOnly() {
								return true;
							}

							@Override
							public int hashCode() {
								return uri.hashCode();
							}

							@Override
							public boolean equals(Object obj) {
								if (obj instanceof IStorage s) {
									return uri.equals(s.getAdapter(URI.class));
								}
								return false;
							}

							@Override
							public String toString() {
								return jarUriStr;
							}



						};
				});
				if (inputElement != null) {
					try {
						EditorUtility.openInEditor(inputElement, true);
					} catch (PartInitException e) {
						LanguageServerCommonsActivator.getInstance().getLog().log(e.getStatus());
					}
				}
				} catch (IOException e) {
					LanguageServerCommonsActivator.getInstance().getLog().log(new Status(IStatus.ERROR,
							LanguageServerCommonsActivator.PLUGIN_ID, "Cannot load JAR entry: " + uri));
					return null;
				}
			} else {
				LanguageServerCommonsActivator.getInstance().getLog().log(new Status(IStatus.WARNING,
						LanguageServerCommonsActivator.PLUGIN_ID, "Cannot find project: " + projectName));
			}
		}

		return null;
	}

	private static Optional<Object> findJavaObj(IJavaProject j, JarURLConnection c) {
		try {
			IPath jarPath = new Path(c.getJarFileURL().getPath());
			// jar entry name doesn't start with '/' but the JarEntryResource full path does start from '/'
			IPath entryPath = new Path("/" + c.getEntryName());
			for (IPackageFragmentRoot fr : j.getAllPackageFragmentRoots()) {
				if (jarPath.equals(fr.getPath())) {
					for (Object o : fr.getNonJavaResources()) {
						if (o instanceof IJarEntryResource je) {
							return findJarEntry(je, entryPath);
						}
					}
					if ("class".equals(entryPath.getFileExtension())) {
						String packageName = entryPath.removeLastSegments(1).toString().replace(IPath.SEPARATOR, '.');
						IPackageFragment pkg = fr.getPackageFragment(packageName);
						if (pkg != null) {
							IClassFile cf = pkg.getClassFile(entryPath.lastSegment());
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
