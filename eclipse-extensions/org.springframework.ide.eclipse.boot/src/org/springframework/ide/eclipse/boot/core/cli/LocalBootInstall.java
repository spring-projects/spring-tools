/*******************************************************************************
 *  Copyright (c) 2013, 2017 GoPivotal, Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core.cli;

import java.io.File;

import org.springframework.ide.eclipse.boot.core.cli.install.BootInstall;
import org.springframework.ide.eclipse.boot.util.Log;

/**
 * Sprint Boot Installation that is found in a local
 * directory manually installed by the user.
 *
 * @author Kris De Volder
 */
public class LocalBootInstall extends BootInstall {

	private File home;
	private String version; //cache to avoid rescanning contents.

	public LocalBootInstall(File home, String name) {
		super(home.toURI().toString(), name);
		this.home = home;
	}

	@Override
	public File getHome() throws Exception {
		return home;
	}

	@Override
	public String getVersion() {
		try {
			if (version == null) {
				File[] jars = getBootLibJars();
				for (File file : jars) {
					//Looking for a jar of the form "spring-boot-*-${version}.jar
					if (file.getName().startsWith("spring-boot-")) {
						version = getSpringBootCliJarVersion(file.getName());
						if (version != null) {
							break;
						}
					}
				}
			}
		} catch (Exception e) {
			Log.log(e);
		} finally {
			//No matter what, set the version before proceeding from here.
			if (version == null) {
				version = super.getVersion();
			}
		}
		return version;
	}

	@Override
	public void clearCache() {
		//nothing to do since this doesn't need caching as its already local and unzipped
	}

	@Override
	public boolean mayRequireDownload() {
		return false;
	}

	/**
	 * Extract the version of the JAR from its file name
	 * @param fileName JAR file name
	 * @return version of the JAR
	 */
	private static String getSpringBootCliJarVersion(String fileName) {
		String version = null;
		if (fileName.startsWith("spring-") && fileName.endsWith(".jar")) {
			int end = fileName.length()-4; //4 chars in ".jar"
			int start = fileName.lastIndexOf("-");
			if (start >= 0) {
				version = fileName.substring(start+1, end);
			}
		}
		return version;
	}

}
