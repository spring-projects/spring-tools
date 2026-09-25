/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.commands;

import java.util.List;

import org.springframework.ide.vscode.commons.java.ClasspathDependencyResolver;
import org.springframework.ide.vscode.commons.java.IJavaProject;

/**
 * Offers the JARs a project depends on, as found on its classpath by
 * {@link ClasspathDependencyResolver}.
 *
 * <p>Offered for selection already, although nothing reads stereotype elements out of a JAR yet -
 * a selected JAR is accepted and simply contributes nothing to the tree until that part exists.
 *
 * @author Martin Lippert
 */
public class JarDependencySource implements StructureDependencySource {

	private final ClasspathDependencyResolver resolver;

	public JarDependencySource(ClasspathDependencyResolver resolver) {
		this.resolver = resolver;
	}

	@Override
	public List<DependencyDescriptor> discover(IJavaProject project) {
		return resolver.jarDependenciesOf(project).stream()
				.map(jar -> jar.gav() != null
						? DependencyDescriptor.jar(jar.gav().groupId(), jar.gav().artifactId(), jar.gav().version(), jar.path())
						: DependencyDescriptor.jar(jar.name(), jar.path()))
				.toList();
	}

}
