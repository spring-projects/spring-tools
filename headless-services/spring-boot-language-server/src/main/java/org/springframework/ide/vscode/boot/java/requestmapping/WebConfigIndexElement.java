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
import org.eclipse.lsp4j.Range;
import org.springframework.ide.vscode.commons.protocol.spring.AbstractSpringIndexElement;
import org.springframework.web.accept.SemanticApiVersionParser;

public class WebConfigIndexElement extends AbstractSpringIndexElement {
	
	public static final String DEFAULT_VERSION_PARSER = SemanticApiVersionParser.class.getName();

	private final ConfigType configType;
	
	private final String pathPrefix;
	
	private final List<VersioningStrategy> versionSupportStrategies;
	private final List<String> supportedVersions;
	private final String versionParser;

	private final Location location;
	
	public WebConfigIndexElement(ConfigType configType, String pathPrefix, List<VersioningStrategy> versionSupportStrategies,
			List<String> supportedVersions, String versionParser, Location location) {
		
		this.configType = configType;
		this.pathPrefix = pathPrefix;
		this.versionSupportStrategies = versionSupportStrategies;
		this.supportedVersions = supportedVersions;
		this.versionParser = versionParser;
		this.location = location;
	}
	
	public ConfigType getConfigType() {
		return configType;
	}
	
	public String getPathPrefix() {
		return pathPrefix;
	}
	
	public List<String> getVersionSupportStrategies() {
		return versionSupportStrategies.stream().map(strategy -> strategy.versioningStrategy()).toList();
	}
	
	public List<VersioningStrategy> getVersionSupportStrategiesWithRanges() {
		return versionSupportStrategies;
	}
	
	public List<String> getSupportedVersions() {
		return supportedVersions;
	}
	
	public String getVersionParser() {
		return versionParser != null ? versionParser : DEFAULT_VERSION_PARSER;
	}
	
	public Location getLocation() {
		return location;
	}
	
	public boolean isEmpty() {
		return (pathPrefix == null || pathPrefix.isBlank())
				&& (versionSupportStrategies == null || versionSupportStrategies.isEmpty())
				&& (supportedVersions == null || supportedVersions.isEmpty());
	}
	
	public static class Builder {
		
		private ConfigType configType;
		
		private String pathPrefix = null;
		private List<VersioningStrategy> versionSupportStrategies = new ArrayList<>(2);
		private List<String> supportedVersions = new ArrayList<>(2);
		private String versionParser;
		
		public Builder(ConfigType configType) {
			this.configType = configType;
		}
		
		public Builder pathPrefix(String pathPrefix) {
			this.pathPrefix = pathPrefix;
			return this;
		}
		
		public Builder versionStrategy(String versionSupportStrategy, Range range) {
			this.versionSupportStrategies.add(new VersioningStrategy(versionSupportStrategy, range));
			return this;
		}
		
		public Builder supportedVersion(String supportedVersion) {
			this.supportedVersions.add(supportedVersion);
			return this;
		}
		
		public Builder versionParser(String versionParser) {
			this.versionParser = versionParser;
			return this;
		}
		
		public WebConfigIndexElement buildFor(Location location) {
			return new WebConfigIndexElement(this.configType, this.pathPrefix, this.versionSupportStrategies, this.supportedVersions, this.versionParser, location);
		}

	}
	
	public record VersioningStrategy (String versioningStrategy, Range range) {}
	
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
