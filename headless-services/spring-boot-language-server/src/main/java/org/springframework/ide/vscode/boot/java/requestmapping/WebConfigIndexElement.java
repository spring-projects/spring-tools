/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.requestmapping;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Location;
import org.springframework.ide.vscode.commons.protocol.spring.AbstractSpringIndexElement;

public class WebConfigIndexElement extends AbstractSpringIndexElement {
	
	private final boolean isVersioningSupported;
	private final String versionSupportStrategy;
	private final String[] supportedVersions;
	private final Location location;
	
	public WebConfigIndexElement(boolean isVersioningSupported, String versionSupportStrategy, String[] supportedVersions, Location location) {
		this.isVersioningSupported = isVersioningSupported;
		this.versionSupportStrategy = versionSupportStrategy;
		this.supportedVersions = supportedVersions;
		this.location = location;
	}
	
	public boolean isVersioningSupported() {
		return isVersioningSupported;
	}
	
	public String getVersionSupportStrategy() {
		return versionSupportStrategy;
	}
	
	public String[] getSupportedVersions() {
		return supportedVersions;
	}
	
	public Location getLocation() {
		return location;
	}
	
	public static class Builder {
		
		private boolean isVersioningSupported = false;
		private String versionSupportStrategy = null;
		private List<String> supportedVersions = new ArrayList<>(10);
		
		public Builder isVersionSupported(boolean isVersionSupported) {
			this.isVersioningSupported = isVersionSupported;
			return this;
		}
		
		public Builder versionStrategy(String versionSupportStrategy) {
			this.versionSupportStrategy = versionSupportStrategy;
			return this;
		}
		
		public Builder supportedVersion(String supportedVersion) {
			this.supportedVersions.add(supportedVersion);
			return this;
		}
		
		public WebConfigIndexElement buildFor(Location location) {
			return new WebConfigIndexElement(this.isVersioningSupported, this.versionSupportStrategy, (String[]) this.supportedVersions.toArray(new String[this.supportedVersions.size()]), location);
		}

	}

}
