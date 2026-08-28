/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.properties;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.Assert;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment;
import org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment.AtIndex;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.gson.internal.LinkedTreeMap;

/**
 * Helper class to convert (Spring Boot) .properties file content into equivalent
 * .yml file content.
 *
 * @author Kris De Volder
 */
class PropertiesToYamlConverter {

	private String output;
	private ImmutableList.Builder<String> errors;
	private ImmutableList.Builder<String> warnings;

	class YamlBuilder {
		final YamlPath path;
		final List<Object> scalars = new ArrayList<>();
		final LinkedTreeMap<Integer, YamlBuilder> listItems = new LinkedTreeMap<>();
		final LinkedTreeMap<String, YamlBuilder> mapEntries = new LinkedTreeMap<>();

		public YamlBuilder(YamlPath path) {
			this.path = path;
		}

		void addProperty(YamlPath path, String value) {
			if (path.isEmpty()) {
				scalars.add(objectify(value));
			} else {
				YamlPathSegment segment = path.getSegment(0);
				YamlBuilder subBuilder;
				if (segment instanceof AtIndex) {
					subBuilder = getSubBuilder(listItems, segment, segment.toIndex());
				} else {
					subBuilder = getSubBuilder(mapEntries, segment, segment.toPropString());
				}
				subBuilder.addProperty(path.dropFirst(1), value);
			}
		}

		private Object objectify(String value) {
			if (value != null) {
				Object parsed = null;
				try {
					parsed = new BigInteger(value);
				} catch (NumberFormatException e) {
					try {
						parsed = new BigDecimal(value);
					} catch (NumberFormatException e2) {
						if (value.equals("true")) {
							parsed = true;
						} else if (value.equals("false")) {
							parsed = false;
						}
					}
				}
				if (parsed != null && parsed.toString().equals(value)) {
					return parsed;
				}
			}
			return value;
		}

		private <T> YamlBuilder getSubBuilder(LinkedTreeMap<T, YamlBuilder> subBuilders, YamlPathSegment segment, T key) {
			YamlBuilder existing = subBuilders.get(key);
			if (existing == null) {
				subBuilders.put(key, existing = new YamlBuilder(path.append(segment)));
			}
			return existing;
		}

		public Object build() {
			if (!scalars.isEmpty()) {
				if (listItems.isEmpty() && mapEntries.isEmpty()) {
					if (scalars.size() > 1) {
						warnings.add("Multiple values "+ scalars +" assigned to '"+path.toPropString()+"'. Values are merged into a yaml sequence node.");
						return scalars;
					} else {
						return scalars.get(0);
					}
				} else {
					if (!mapEntries.isEmpty()) {
						errors.add(
								"Direct assignment '"+path.toPropString()+"="+scalars.get(0)+"' can not be combined " +
								"with sub-property assignment '"+path.toPropString()+"." + mapEntries.keySet().iterator().next()+"...'. "+
								"Direct assignment is dropped!"
						);
					} else {
						errors.add(
								"Direct assignment '"+path.toPropString()+"="+scalars.get(0)+"' can not be combined " +
								"with sequence assignment '"+path.toPropString()+"[" + listItems.keySet().iterator().next()+"]...'. "+
								"Direct assignments are dropped!"
						);
					}
					scalars.clear();
				}
			}
			Assert.isLegal(scalars.isEmpty());
			if (!listItems.isEmpty() && !mapEntries.isEmpty()) {
				warnings.add("'"+path.toPropString()+"' has some entries that look like list items and others that look like map entries. "
						+ "All these entries are treated as map entries!");
				for (Entry<Integer, YamlBuilder> listItem : listItems.entrySet()) {
					mapEntries.put(listItem.getKey().toString(), listItem.getValue());
				}
				listItems.clear();
			}
			if (!listItems.isEmpty()) {
				return listItems.values().stream()
					.map(childBuilder -> childBuilder.build())
					.collect(Collectors.toList());
			} else {
				LinkedTreeMap<String, Object> map = new LinkedTreeMap<>();
				for (Entry<String, YamlBuilder> entry : mapEntries.entrySet()) {
					map.put(entry.getKey(), entry.getValue().build());
				}
				return map;
			}
		}
	}

	public PropertiesToYamlConverter(Multimap<String, String> properties) {
		this.errors = ImmutableList.builder();
		this.warnings = ImmutableList.builder();
		if (properties.isEmpty()) {
			output = "";
			return;
		}
		YamlBuilder root = new YamlBuilder(YamlPath.EMPTY);
		for (Entry<String, String> e : properties.entries()) {
			root.addProperty(YamlPath.fromProperty(e.getKey()), e.getValue());
		}
		Object object = root.build();

		DumperOptions options = new DumperOptions();
		options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
		options.setPrettyFlow(true);

		Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()), new Representer(options), options);
		this.output = yaml.dump(object);
	}

	public List<String> getErrors() {
		return errors.build();
	}
	
	public List<String> getWarnings() {
		return warnings.build();
	}

	public String getYaml() {
		return output;
	}

}
