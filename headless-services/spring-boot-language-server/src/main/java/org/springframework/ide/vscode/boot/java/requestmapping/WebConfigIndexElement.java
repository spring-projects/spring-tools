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
	
	private final ConfigType configType;
	
	private final String pathPrefix;
	
	private final List<String> versionSupportStrategies;
	private final List<String> supportedVersions;
	private final Location location;
	
	public WebConfigIndexElement(ConfigType configType, String pathPrefix, List<String> versionSupportStrategies, List<String> supportedVersions, Location location) {
		this.configType = configType;
		this.pathPrefix = pathPrefix;
		this.versionSupportStrategies = versionSupportStrategies;
		this.supportedVersions = supportedVersions;
		this.location = location;
	}
	
	public ConfigType getConfigType() {
		return configType;
	}
	
	public String getPathPrefix() {
		return pathPrefix;
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
		
		private ConfigType configType;
		
		private String pathPrefix = null;
		private List<String> versionSupportStrategies = new ArrayList<>(2);
		private List<String> supportedVersions = new ArrayList<>(2);
		
		public Builder(ConfigType configType) {
			this.configType = configType;
		}
		
		public Builder pathPrefix(String pathPrefix) {
			this.pathPrefix = pathPrefix;
			return this;
		}
		
		public Builder versionStrategy(String versionSupportStrategy) {
			this.versionSupportStrategies.add(versionSupportStrategy);
			return this;
		}
		
		public Builder supportedVersion(String supportedVersion) {
			this.supportedVersions.add(supportedVersion);
			return this;
		}
		
		public WebConfigIndexElement buildFor(Location location) {
			return new WebConfigIndexElement(this.configType, this.pathPrefix, this.versionSupportStrategies, this.supportedVersions, location);
		}

	}
	
	public enum ConfigType {
		WEB_CONFIG ("Web Config"),
		PROPERTIES ("Properties Config");
		
		private final String label;

		private ConfigType(String label) {
			this.label = label;
		}

		String getLabel() {
			return this.label;
		}
	};

}
