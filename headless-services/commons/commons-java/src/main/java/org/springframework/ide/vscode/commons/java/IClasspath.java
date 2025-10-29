/*******************************************************************************
 * Copyright (c) 2016, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.java;

import java.io.File;
import java.util.Collection;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.protocol.java.Jre;

/**
 * Classpath for a Java artifact
 *
 * @author Kris De Volder
 * @author Alex Boyko
 *
 */
public interface IClasspath {

	public static final Logger log = LoggerFactory.getLogger(IClasspath.class);

	String getName();
	
	/**
	 * Classpath entries paths
	 *
	 * @return collection of classpath entries in a form file/folder paths
	 * @throws Exception
	 */
	Collection<CPE> getClasspathEntries() throws Exception;
	
	/**
	 * Finds a classpath entry among JAR libraries that start with a prefix. Prefix must typically contain the full lib name such that the match is only one.
	 * @param prefix the library prefix
	 * @return the classpath entry
	 */
	default Optional<CPE> findBinaryLibrary(String prefix) {
		try {
			for (CPE cpe : getClasspathEntries()) {
				if (Classpath.isBinary(cpe) && !cpe.isSystem() && !cpe.isTest() && new File(cpe.getPath()).getName().startsWith(prefix)) {
					return Optional.of(cpe);
				}
			}
		} catch (Exception e) {
			log.error("", e);
		}
		return Optional.empty();
	}

	/**
	 * VM info
	 * @return returns java version
	 */
	Jre getJre();

}
