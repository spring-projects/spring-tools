/*******************************************************************************
 * Copyright (c) 2017, 2022 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.java;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

public interface IJavaProject {

	final static String PROJECT_CACHE_FOLDER = ".sts4-cache";

	IClasspath getClasspath();
	IProjectBuild getProjectBuild();
	ClasspathIndex getIndex();
	URI getLocationUri();
	boolean exists();

	default String getElementName() {
		return getClasspath().getName();
	}

	/**
	 * Returns the JavaCore options for this project. These control compiler compliance,
	 * formatter settings (indentation, tab/space), and other JDT core behaviors.
	 * <p>
	 * When project-specific options are available (received from the JDT LS extension
	 * via the classpath notification), they are returned. Otherwise, returns an empty map.
	 * <p>
	 * Consumers in modules that have JDT core on the classpath should fall back to
	 * {@code JavaCore.getOptions()} when this returns an empty map.
	 * <p>
	 * This method never returns {@code null}.
	 *
	 * @return a non-null map of JavaCore option keys to values, or empty if not available
	 */
	default Map<String, String> getJavaCoreOptions() {
		return Collections.emptyMap();
	}

}
