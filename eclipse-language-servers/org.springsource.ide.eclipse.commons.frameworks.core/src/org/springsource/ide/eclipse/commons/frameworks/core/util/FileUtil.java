/*******************************************************************************
 * Copyright (c) 2012, 2018 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.frameworks.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
/**
 * @author Steffen Pingel
 * @author Leo Dos Santos
 * @author Christian Dupuis
 * @author Kris De Volder
 */
public class FileUtil {
	
	/**
	 * Creates a temporary folder that gets deleted on VM shutdown. <b/> WARNING:
	 * any files created in this temporary folder will also be deleted on shutdown.
	 * <b/> It also appends a timestamp to the temporary directory name.
	 *
	 * @param name
	 * @return
	 * @throws IOException
	 */
	public static File createTempDirectoryWithTimestamp(String name) throws IOException {
		return createTempDirectory(name, Long.toString(System.nanoTime()));
	}
	
	/**
	 * Creates a temporary folder that gets deleted on VM shutdown. <b/> WARNING:
	 * any files created in this temporary folder will also be deleted on shutdown.
	 *
	 * @param name
	 * @return
	 * @throws IOException
	 */
	public static File createTempDirectory(String name) throws IOException {
		return createTempDirectory(name, null);
	}


	/**
	 * Creates a temporary folder that gets deleted on VM shutdown. <b/> WARNING:
	 * any files created in this temporary folder will also be deleted on shutdown.
	 *
	 * @param name
	 * @return
	 * @throws IOException
	 */
	public static File createTempDirectory(String name, String suffix) throws IOException {
		String tempDirPrefix = (name != null ? name : "") + (suffix != null ? suffix : "");
    	File tempFolder = Files.createTempDirectory(tempDirPrefix).toFile();
		deleteOnShutdown(tempFolder);
		return tempFolder;
	}
	
	public static File createTempDirectory() throws IOException {
		return createTempDirectoryWithTimestamp("temp");
	}
	
	public static boolean isJarFile(File jarFile) {
		try {
			return jarFile!=null && jarFile.isFile() && jarFile.toString().toLowerCase().endsWith(".jar");
		} catch (Throwable e) {
			org.springsource.ide.eclipse.commons.livexp.util.Log.log(e);
			return false;
		}
	}

	private static void deleteOnShutdown(File tempFolder) {
		try {
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				deleteResource(tempFolder);
			}));
		} catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
			org.springsource.ide.eclipse.commons.livexp.util.Log.log(e);
		}
	}

	private static void deleteResource(File file) {
		if (file == null || !file.exists()) {
			return;
		}

		if (file.isDirectory()) {
			File[] files = file.listFiles();
			if (files != null) {
				for (File content : files) {
					deleteResource(content);
				}
			}
		}
		file.delete();
	}
}
