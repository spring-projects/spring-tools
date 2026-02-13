/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
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
	 * In production, these options are received from the JDT LS extension via the
	 * classpath notification and are always populated. The default implementation
	 * returns an empty map for use by test doubles and legacy implementations that
	 * don't go through the classpath event pipeline.
	 * <p>
	 * This method never returns {@code null}.
	 *
	 * @return a non-null map of JavaCore option keys to values
	 */
	Map<String, String> getJavaCoreOptions();

}
