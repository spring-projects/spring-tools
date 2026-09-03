/*******************************************************************************
 * Copyright (c) 2019, 2021 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.app;

import java.io.File;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("languageserver.boot")
public class BootLsConfigProperties {

	/**
	 * Enables/disables Jandex indexing of Java types. When disabled JDT LS will be
	 * used for type and package searches, type finding for FQ name etc.
	 */
	private boolean enableJandexIndex = false;

	public boolean isEnableJandexIndex() {
		return enableJandexIndex;
	}

	public void setEnableJandexIndex(boolean enableJandexIndex) {
		this.enableJandexIndex = enableJandexIndex;
	}

	/**
	 * Enable's or disables disk-based symbol cache. This is
	 * enabled by default.
	 */
	private boolean symbolCacheEnabled = true;

	public boolean isSymbolCacheEnabled() {
		return symbolCacheEnabled;
	}

	public void setSymbolCacheEnabled(boolean symbolCacheEnabled) {
		this.symbolCacheEnabled = symbolCacheEnabled;
	}
	
	/**
	 * The path to the cache directory where the language server stores
	 * symbol information
	 */
	private String symbolCacheDir = System.getProperty("user.home") + File.separatorChar + ".sts4" + File.separatorChar + ".symbolCache";

	public String getSymbolCacheDir() {
		return symbolCacheDir;
	}

	public void setSymbolCacheDir(String symbolCacheDir) {
		this.symbolCacheDir = symbolCacheDir;
	}

	/**
	 * The path to the directory where the language server persists logical structure view
	 * baselines, so they survive a language server restart.
	 */
	private String structureBaselineDir = System.getProperty("user.home") + File.separatorChar + ".sts4" + File.separatorChar + ".structureBaselines";

	public String getStructureBaselineDir() {
		return structureBaselineDir;
	}

	public void setStructureBaselineDir(String structureBaselineDir) {
		this.structureBaselineDir = structureBaselineDir;
	}

}
