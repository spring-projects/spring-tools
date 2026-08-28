/*******************************************************************************
 * Copyright (c) 2014, 2023 Pivotal, Inc.
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
import java.util.Collections;
import java.util.List;

import org.springframework.ide.vscode.boot.configurationmetadata.ConfigurationMetadataItem;
import org.springframework.ide.vscode.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.ide.vscode.boot.configurationmetadata.ConfigurationMetadataSource;
import org.springframework.ide.vscode.boot.configurationmetadata.Deprecation;
import org.springframework.ide.vscode.boot.configurationmetadata.Deprecation.Level;
import org.springframework.ide.vscode.boot.configurationmetadata.ValueHint;
import org.springframework.ide.vscode.boot.configurationmetadata.ValueProvider;
import org.springframework.ide.vscode.boot.metadata.ValueProviderRegistry.ValueProviderStrategy;
import org.springframework.ide.vscode.boot.metadata.hints.HintProvider;
import org.springframework.ide.vscode.boot.metadata.hints.HintProviders;
import org.springframework.ide.vscode.boot.metadata.types.Type;
import org.springframework.ide.vscode.boot.metadata.types.TypeParser;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

/**
 * Information about a spring property, basically, this is the same as
 *
 * {@link ConfigurationMetadataProperty} but augmented with information
 * about {@link ConfigurationMetadataSource}s that declare the property.
 *
 * @author Kris De Volder
 */
public class PropertyInfo {

	/**
	 * Identifies a 'Source'. This is essentially the sames as {@link ConfigurationMetadataSource}.
	 * We could use {@link ConfigurationMetadataSource} directly, but this only contains
	 * the info that we actually use so takes less memory.
	 */
	public static class PropertySource {
		private final String sourceType;
		private final String sourceMethod;
		public PropertySource(ConfigurationMetadataSource source) {
			String st = source.getSourceType();
			this.sourceType = st != null ? st : source.getType();
			this.sourceMethod = source.getSourceMethod();
		}
		
		public PropertySource(String sourceType, String sourceMethod) {
			this.sourceType = sourceType;
			this.sourceMethod = sourceMethod;
		}
		@Override
		public String toString() {
			return sourceType+"::"+sourceMethod;
		}
		public String getSourceType() {
			return sourceType;
		}
		public String getSourceMethod() {
			return sourceMethod;
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((sourceMethod == null) ? 0 : sourceMethod.hashCode());
			result = prime * result + ((sourceType == null) ? 0 : sourceType.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			PropertySource other = (PropertySource) obj;
			if (sourceMethod == null) {
				if (other.sourceMethod != null)
					return false;
			} else if (!sourceMethod.equals(other.sourceMethod))
				return false;
			if (sourceType == null) {
				if (other.sourceType != null)
					return false;
			} else if (!sourceType.equals(other.sourceType))
				return false;
			return true;
		}
		
	}

	final private String id;
	private String type;
	final private String name;
	final private Object defaultValue;
	private String description;
	private List<PropertySource> sources;
	private Deprecation deprecation;
	private ImmutableList<ValueHint> valueHints;
	private ImmutableList<ValueHint> keyHints;
	private ValueProviderStrategy valueProvider;
	private ValueProviderStrategy keyProvider;
	private String handleKeyAs;

	public PropertyInfo(String id, String type, String name,
			Object defaultValue, String description,
			Deprecation deprecation,
			List<ValueHint> valueHints,
			List<ValueHint> keyHints,
			ValueProviderStrategy valueProvider,
			ValueProviderStrategy keyProvider,
			List<PropertySource> sources) {
		super();
		this.id = id;
		this.type = type;
		this.name = name;
		this.defaultValue = defaultValue;
		this.description = description;
		this.deprecation = deprecation;
		this.valueHints = valueHints == null ? null : ImmutableList.copyOf(valueHints);
		this.keyHints = keyHints == null ? null : ImmutableList.copyOf(keyHints);
		this.valueProvider = valueProvider;
		this.keyProvider = keyProvider;
		this.sources = sources;
	}
	public PropertyInfo(ValueProviderRegistry valueProviders, ConfigurationMetadataProperty prop) {
		this(
			prop.getId(),
			prop.getType(),
			prop.getName(),
			prop.getDefaultValue(),
			prop.getDescription(),
			prop.getDeprecation(),
			prop.getHints().getValueHints(),
			prop.getHints().getKeyHints(),
			valueProviders.resolve(prop.getHints().getValueProviders()),
			valueProviders.resolve(prop.getHints().getKeyProviders()),
			null
		);
		for (ValueProvider h : prop.getHints().getValueProviders()) {
			if (h.getName().equals("handle-as")) {
				handleAs(h.getParameters().get("target"));
			}
		}
		for (ValueProvider h : prop.getHints().getKeyProviders()) {
			if (h.getName().equals("handle-as")) {
				handleKeyAs(h.getParameters().get("target"));
			}
		}
		if (prop instanceof ConfigurationMetadataItem) {
			ConfigurationMetadataItem item = (ConfigurationMetadataItem) prop;
			addSource(new PropertySource(item.getSourceType(), item.getSourceMethod()));
		}
	}
	public PropertyInfo(String p) {
		this(p, null, null, null, null, null, null, null, null, null, null);
	}
	private void handleAs(Object targetObject) {
//		debug("handle-as "+this.getId()+" -> "+targetObject);
		if (targetObject instanceof String) {
			this.type = (String)targetObject;
		}
	}
	private void handleKeyAs(Object targetObject) {
		if (targetObject instanceof String) {
			this.handleKeyAs = (String)targetObject;
		}
	}
	
	public String getId() {
		return id;
	}
	public String getType() {
		return type;
	}
	public String getName() {
		return name;
	}
	/**
	 * Gets the name of the property without the x.y.z prefix, i.e. if property is x.y.z.a then its simple name is a
	 * @return simple name
	 */
	public String getSimpleName() {
		int idx = name.lastIndexOf('.');
		if (idx >= 0 && idx < name.length() - 1) {
			return name.substring(idx + 1);
		}
		return name;
	}
	public Object getDefaultValue() {
		return defaultValue;
	}
	public String getDescription() {
		return description;
	}

	public HintProvider getHints(TypeUtil typeUtil) {
		Type type = TypeParser.parse(this.type);
		if (typeUtil.isMap(type)) {
			return HintProviders.forMap(keyHints(typeUtil), valueHints(typeUtil), TypeUtil.getDomainType(type));
		} else if (typeUtil.isSequencable(type)) {
			return HintProviders.forAllValueContexts(valueHints(typeUtil));
		} else {
			return HintProviders.forHere(valueHints(typeUtil));
		}
	}

	private HintProvider keyHints(TypeUtil typeUtil) {
		return HintProviders.basic(typeUtil.getJavaProject(), keyHints, keyProvider);
	}

	private HintProvider valueHints(TypeUtil typeUtil) {
		return HintProviders.basic(typeUtil.getJavaProject(), valueHints, valueProvider);
	}

	public List<PropertySource> getSources() {
		if (sources != null) {
			return sources;
		}
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "PropertyInfo("+getId()+")";
	}
	public PropertySource addSource(ConfigurationMetadataSource source) {
		PropertySource s = new PropertySource(source);
		addSource(s);
		return s;
	}

	public void addSource(PropertySource source) {
		if (sources == null) {
			sources = new ArrayList<PropertySource>();
		}
		sources.add(source);
	}
	
	public PropertyInfo withId(String alias) {
		if (alias.equals(id)) {
			return this;
		}
		return new PropertyInfo(alias, type, name, defaultValue, description, deprecation, valueHints, keyHints, valueProvider, keyProvider, sources);
	}

	public PropertyInfo setDescription(String description) {
		this.description = description;
		return this;
	}

	public void setDeprecation(Deprecation d) {
		this.deprecation = d;
	}

	public boolean isDeprecated() {
		return deprecation != null;
	}

	public String getDeprecationReason() {
		return deprecation == null ? null : deprecation.getReason();
	}

	public String getDeprecationReplacement() {
		return deprecation == null ? null : deprecation.getReplacement();
	}

	public Level getDeprecationLevel() {
		return deprecation == null ? null : deprecation.getLevel();
	}


	public void addValueHints(List<ValueHint> hints) {
		Builder<ValueHint> builder = ImmutableList.builder();
		builder.addAll(valueHints);
		builder.addAll(hints);
		valueHints = builder.build();
	}
	public void addKeyHints(List<ValueHint> hints) {
		Builder<ValueHint> builder = ImmutableList.builder();
		builder.addAll(keyHints);
		builder.addAll(hints);
		keyHints = builder.build();
	}
	public String getHandleKeyAs() {
		return handleKeyAs;
	}
}
