/*******************************************************************************
 * Copyright (c) 2015, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.metadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ide.vscode.boot.configurationmetadata.ConfigurationMetadataGroup;
import org.springframework.ide.vscode.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.ide.vscode.boot.configurationmetadata.ConfigurationMetadataRepository;
import org.springframework.ide.vscode.boot.configurationmetadata.ConfigurationMetadataSource;
import org.springframework.ide.vscode.boot.metadata.PropertyInfo.PropertySource;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.util.FuzzyMap;
import org.springframework.ide.vscode.commons.util.StringUtil;

import com.google.common.collect.ImmutableSet;

public class SpringPropertyIndex {
	
	public static final SpringPropertyIndex EMPTY_INDEX = new SpringPropertyIndex(null);

	private final ValueProviderRegistry valueProviders;
	
	private final Map<String, Collection<PropertySource>> groups = new HashMap<String, Collection<PropertySource>>();
	
	private final FuzzyMap<PropertyInfo> properties = new FuzzyMap<PropertyInfo>() {
		@Override
		protected String getKey(PropertyInfo entry) {
			return entry.getId();
		}
	};

	private SpringPropertyIndex(ValueProviderRegistry valueProviders) {
		this.valueProviders = valueProviders;
	}
	
	private void loadFromClasspath(IClasspath projectPath) {
		PropertiesLoader loader = new PropertiesLoader();
		addMetadata(loader.load(projectPath));
		//^^^ Should be done in bg? It seems fast enough for now.
	}
	
	private void addMetadata(ConfigurationMetadataRepository metadata) {
		Collection<ConfigurationMetadataProperty> allEntries = metadata.getAllProperties().values();
		for (ConfigurationMetadataProperty item : allEntries) {
			properties.add(new PropertyInfo(valueProviders, item));
		}

		for (ConfigurationMetadataGroup group : metadata.getAllGroups().values()) {
			ImmutableSet.Builder<PropertySource> sources = ImmutableSet.builder();
			for (ConfigurationMetadataSource source : group.getSources().values()) {
				PropertySource propertySource = new PropertySource(source);
				sources.add(propertySource);
				for (ConfigurationMetadataProperty prop : source.getProperties().values()) {
					PropertyInfo info = properties.get(prop.getId());
					if (info.getSources().isEmpty()) {
						info.addSource(propertySource);
					}
				}
			}
			groups.put(group.getId(), sources.build());
		}
	}

	public void add(ConfigurationMetadataProperty propertyInfo) {
		properties.add(new PropertyInfo(valueProviders, propertyInfo));
	}

	private String dumpString(Object v) {
		if (v == null) {
			return "null";
		}
		return dumpString(""+v);
	}

	private String dumpString(String s) {
		if (s == null) {
			return "null";
		} else {
			StringBuilder buf = new StringBuilder("\"");
			for (char c : s.toCharArray()) {
				switch (c) {
				case '\r':
					buf.append("\\r");
					break;
				case '\n':
					buf.append("\\n");
					break;
				case '\\':
					buf.append("\\\\");
					break;
				case '\"':
					buf.append("\\\"");
					break;
				default:
					buf.append(c);
					break;
				}
			}
			buf.append("\"");
			return buf.toString();
		}
	}

	/**
	 * Find the longest known property that is a prefix of the given name. Here prefix does not mean
	 * 'string prefix' but a prefix in the sense of treating '.' as a kind of separators. So
	 * 'prefix' is not allowed to end in the middle of a 'segment'.
	 */
	public static PropertyInfo findLongestValidProperty(FuzzyMap<PropertyInfo> index, String name) {
		int bracketPos = name.indexOf('[');
		int endPos = bracketPos >= 0 ? bracketPos : name.length();
		PropertyInfo prop = null;
		String prefix = null;
		while (endPos > 0 && prop == null) {
			prefix = name.substring(0, endPos);
			String canonicalPrefix = StringUtil.camelCaseToHyphens(prefix);
			prop = index.get(canonicalPrefix);
			if (prop == null) {
				endPos = name.lastIndexOf('.', endPos-1);
			}
		}
		if (prop != null) {
			//We should meet caller's expectation that matched properties returned by this method
			// match the names exactly even if we found them using relaxed name matching.
			return prop.withId(prefix);
		}
		return null;
	}

	public FuzzyMap<PropertyInfo> getProperties() {
		return properties;
	}

	public int size() {
		return properties.size();
	}

	public Collection<PropertySource> getGroupSources(String prefix) {
		return groups.get(prefix);
	}
	
	public static final Builder builder(ValueProviderRegistry valueProviders) {
		return new Builder(valueProviders);
	}

	public static class Builder {
		
		private final ValueProviderRegistry valueProviders;
		private IClasspath projectClasspath;
		private List<ConfigurationMetadataRepository> metadataList;
		
		private Builder(ValueProviderRegistry valueProviders) {
			this.valueProviders = valueProviders;
			this.metadataList = new ArrayList<>(); 
		}
		
		public Builder withClasspath(IClasspath projectClasspath) {
			this.projectClasspath = projectClasspath;
			return this;
		}
		
		public Builder withMetadata(ConfigurationMetadataRepository metadata) {
			metadataList.add(metadata);
			return this;
		}
		
		public SpringPropertyIndex build() {
			SpringPropertyIndex index = new SpringPropertyIndex(valueProviders);
			for (ConfigurationMetadataRepository m : metadataList) {
				index.addMetadata(m);
			}
			index.loadFromClasspath(projectClasspath);
			return index;
		}
		
	}
}
