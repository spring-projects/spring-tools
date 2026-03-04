/*******************************************************************************
 * Copyright (c) 2018, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.commons;

import java.io.File;

import org.eclipse.core.runtime.Platform;

public class JRE {

	private final File javaHome;

	public JRE(File javaHome) {
		this.javaHome = javaHome;
	}

	public File getJavaHome() {
		return javaHome;
	}

	public String getJavaExecutable() {
		if (javaHome.exists()) {
			File bin = new File(javaHome, "bin");
			String exeName = Platform.getOS().equals(Platform.OS_WIN32) ? "java.exe" : "java";
			File exe = new File(bin, exeName);
			if (exe.isFile()) {
				return exe.getAbsolutePath();
			}
		}
		return null;
	}

	public int getMajorVersion() {
		return parseVersion(System.getProperty("java.version"));
	}

	public static int parseVersion(String versionString) {
		int dash = versionString.indexOf('-');
		if (dash >= 0) {
			versionString = versionString.substring(0, dash);
		}
		return Integer.parseInt(versionString.split("\\.")[0]);
	}

	public static JRE currentJRE() {
		return new JRE(new File(System.getProperty("java.home")));
	}

	@Override
	public String toString() {
		return "JRE(" + javaHome + ")";
	}
}
