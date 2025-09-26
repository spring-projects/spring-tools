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
	
	private final String pathPrefix;
	
	private final boolean isVersioningSupported;
	private final List<String> versionSupportStrategies;
	private final List<String> supportedVersions;
	private final Location location;
	
	public WebConfigIndexElement(String pathPrefix, boolean isVersioningSupported, List<String> versionSupportStrategies, List<String> supportedVersions, Location location) {
		this.pathPrefix = pathPrefix;
		this.isVersioningSupported = isVersioningSupported;
		this.versionSupportStrategies = versionSupportStrategies;
		this.supportedVersions = supportedVersions;
		this.location = location;
	}
	
	public String getPathPrefix() {
		return pathPrefix;
	}
	
	public boolean isVersioningSupported() {
		return isVersioningSupported;
	}
	
	public List<String> getVersionSupportStrategies() {
		return versionSupportStrategies;
	}
	
	public List<String> getSupportedVersions() {
		return supportedVersions;
	}
	
	public Location getLocation() {
		return location;
	}
	
	public static class Builder {
		
		private String pathPrefix = null;

		private boolean isVersioningSupported = false;
		private List<String> versionSupportStrategies = new ArrayList<>(2);
		private List<String> supportedVersions = new ArrayList<>(2);
		
		public Builder pathPrefix(String pathPrefix) {
			this.pathPrefix = pathPrefix;
			return this;
		}
		
		public Builder versionStrategy(String versionSupportStrategy) {
			this.versionSupportStrategies.add(versionSupportStrategy);
			this.isVersioningSupported = true;
			return this;
		}
		
		public Builder supportedVersion(String supportedVersion) {
			this.supportedVersions.add(supportedVersion);
			this.isVersioningSupported = true;
			return this;
		}
		
		public WebConfigIndexElement buildFor(Location location) {
			return new WebConfigIndexElement(this.pathPrefix, this.isVersioningSupported, this.versionSupportStrategies, this.supportedVersions, location);
		}

	}

}
