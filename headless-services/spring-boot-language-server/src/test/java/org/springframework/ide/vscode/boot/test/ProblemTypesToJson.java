/*******************************************************************************
 * Copyright (c) 2020, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.Boot3JavaProblemType;
import org.springframework.ide.vscode.boot.java.Boot4JavaProblemType;
import org.springframework.ide.vscode.boot.java.SpelProblemType;
import org.springframework.ide.vscode.boot.java.SpringAiProblemType;
import org.springframework.ide.vscode.boot.java.SpringAotJavaProblemType;
import org.springframework.ide.vscode.boot.java.cron.CronProblemType;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.QueryProblemType;
import org.springframework.ide.vscode.boot.properties.reconcile.ApplicationPropertiesProblemType;
import org.springframework.ide.vscode.boot.validation.generations.preferences.VersionValidationProblemType;
import org.springframework.ide.vscode.boot.yaml.reconcile.ApplicationYamlProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemCategory;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemCategory.Toggle;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemSeverity;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemTypeParameter;
import org.springframework.ide.vscode.commons.util.Streams;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.util.JSONCursor;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Helper that dumps out ProblemTypes to a json file. This file can 
 * be used to drive problem severity configuration ui. See 
 * org.springframework.ide.eclipse.editor.support.preferences.ProblemSeverityPreferityPageFromMetadata
 * <p>
 * Run this from Eclipse simply by selecting this file and do
 * "Run As >> Java Application". This does two things:
 * <ul>
 * <li> Dumps metadata into 'problem-types.json' metadata. This file is packaged with the language
 * server jar.
 * <li> Updates the 'package.json' file in vscode-spring-boot extension with the same metadata.
 * </ul>
 */
public class ProblemTypesToJson {

	public static final String packageJsonPath = "../../vscode-extensions/vscode-spring-boot/package.json";
	public static final String resourcesPath = "src/main/resources";
	public static final String metaDataFileName = "problem-types.json";
	public static final String problemCategoriesFileName = "problem-categories.json";

	/** Settings branch for per-problem parameters (severity stays on {@code spring-boot.ls.problem}). */
	public static final String PROBLEM_PARAMETERS_PROPERTY_PREFIX = "spring-boot.ls.problem-parameters";
	
	public static class ProblemTypeParameterData {
		String key;
		String label;
		String description;
		String type;
		String defaultValue;

		public ProblemTypeParameterData() {}

		public ProblemTypeParameterData(ProblemTypeParameter p) {
			this.key = p.getKey();
			this.label = p.getLabel();
			this.description = p.getDescription();
			this.type = p.getType().name().toLowerCase();
			this.defaultValue = p.getDefaultValue();
		}

		public String getKey() {
			return key;
		}

		public String getLabel() {
			return label;
		}

		public String getDescription() {
			return description;
		}

		public String getType() {
			return type;
		}

		public String getDefaultValue() {
			return defaultValue;
		}
	}
	
	public static class ProblemTypeData {
		String code;
		String label;
		String description;
		String defaultSeverity;
		List<ProblemTypeParameterData> parameters;
		
		public ProblemTypeData() {}
		
		public ProblemTypeData(ProblemType type) {
			this.code = type.getCode();
			this.description = type.getDescription();
			this.defaultSeverity =type.getDefaultSeverity().name();
			this.label = type.getLabel();
			this.parameters = type.getParameters().stream().map(ProblemTypeParameterData::new).collect(Collectors.toList());
		}
		
		public ProblemTypeData(String defaultSeverity) {
			super();
			this.setDefaultSeverity(defaultSeverity);
		}

		public String getCode() {
			return code;
		}
		public void setCode(String code) {
			this.code = code;
		}
		public String getDescription() {
			return description;
		}
		public void setDescription(String description) {
			this.description = description;
		}

		public String getDefaultSeverity() {
			return defaultSeverity;
		}

		public void setDefaultSeverity(String defaultSeverity) {
			this.defaultSeverity = defaultSeverity;
		}

		public String getLabel() {
			return label;
		}
		
		public void setLabel(String label) {
			this.label = label;
		}

		public List<ProblemTypeParameterData> getParameters() {
			return parameters;
		}

		public void setParameters(List<ProblemTypeParameterData> parameters) {
			this.parameters = parameters;
		}
	}
	
	public static class ProblemCategoryData implements Comparable<ProblemCategoryData> {
		private String id;
		private String label;
		private Toggle toggle;
		private int order;
		private List<ProblemTypeData> problemTypes;
		
		ProblemCategoryData(ProblemCategory category, Collection<ProblemType> problemTypes) {
			this.id = category.getId();
			this.label = category.getLabel();
			this.toggle = category.getToggle();
			this.order = category.order;
			this.problemTypes = problemTypes.stream().map(ProblemTypeData::new).collect(Collectors.toList());
		}
		
		ProblemCategoryData(ProblemCategory category) {
			this(category, Collections.emptyList());
		}

		public String getLabel() {
			return label;
		}

		public Toggle getToggle() {
			return toggle;
		}

		public int getOrder() {
			return order;
		}

		public String getId() {
			return id;
		}

		public List<ProblemTypeData> getProblemTypes() {
			return problemTypes;
		}

		@Override
		public int compareTo(ProblemCategoryData o) {
			return getOrder() - o.getOrder();
		}
		
	}
	

	private List<ProblemCategoryData> problemCategories = new ArrayList<>();

	public static void main(String[] args) throws Exception {
		ProblemTypesToJson writer = new ProblemTypesToJson();
		
		writer.collectProblemTypeData(SpelProblemType.values());
		writer.collectProblemTypeData(ApplicationYamlProblemType.values());
		writer.collectProblemTypeData(Boot2JavaProblemType.values());
		writer.collectProblemTypeData(ApplicationPropertiesProblemType.values());
		writer.collectProblemTypeData(Boot3JavaProblemType.values());
		writer.collectProblemTypeData(Boot4JavaProblemType.values());
		writer.collectProblemTypeData(SpringAotJavaProblemType.values());
		writer.collectProblemTypeData(VersionValidationProblemType.values());
		writer.collectProblemTypeData(QueryProblemType.values());
		writer.collectProblemTypeData(CronProblemType.values());
		writer.collectProblemTypeData(SpringAiProblemType.values());
		
		Collections.sort(writer.problemCategories);
		
		writer.dump();
		
		writer.updatePackageJson(new File(packageJsonPath), "spring-boot.ls.problem");
	}

	private void dump() throws IOException {
		Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
		String json = gson.toJson(problemCategories);
		FileUtils.writeStringToFile(new File(resourcesPath+"/"+metaDataFileName), json);		
	}
	
	private void collectProblemTypeData(ProblemType[] problems) {
		for (int i = 0; i < problems.length; i++) {
			ProblemCategory category = problems[i].getCategory();
			if (category != null) {
				ProblemCategoryData categoryData = problemCategories.stream().filter(c -> c.getId().equals(category.getId())).findFirst().orElseGet(() -> {
					ProblemCategoryData d = new ProblemCategoryData(category);
					problemCategories.add(d);
					return d;
				});
				categoryData.getProblemTypes().add(new ProblemTypeData(problems[i]));
			} else {
				throw new IllegalStateException("No category for problem " + problems[i].getCode());
			}
		}
	}
	
	public ProblemTypesToJson read() throws IOException {
		try (InputStream input = this.getClass().getResourceAsStream("/"+metaDataFileName)) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			TypeToken<List<ProblemCategoryData>> tt = new TypeToken<List<ProblemCategoryData>>() {};
			problemCategories = gson.fromJson(new InputStreamReader(input), tt.getType());
		}
		return this;
	}

	public void validate(String name, ProblemType[] actualProblemTypes) {
		List<ProblemTypeData> metadataProblemTypes = problemCategories.stream().filter(c -> c.getId().equals(name)).findFirst().map(c -> c.getProblemTypes()).orElse(Collections.emptyList());
		Set<String> metadataHas = metadataProblemTypes.stream().map(x -> x.getCode()).collect(Collectors.toSet());
		Set<String> actualHas = Stream.of(actualProblemTypes).map(x-> x.getCode()).collect(Collectors.toSet());
		for (String string : actualHas) {
			if (!metadataHas.contains(string)) {
				throw new IllegalStateException("No metadata for "+string);
			}
		}
		for (String string : metadataHas) {
			if (!actualHas.contains(string)) {
				throw new IllegalStateException("Metadata for non-existent problem type "+string);
			}
		}
		
		List<ProblemTypeData> sortedMeta = metadataProblemTypes.stream().sorted(Comparator.comparing(ProblemTypeData::getCode)).collect(Collectors.toList());
		List<ProblemType> sortedActual = Stream.of(actualProblemTypes).sorted(Comparator.comparing(ProblemType::getCode)).collect(Collectors.toList());
		for (int i = 0; i < sortedMeta.size(); i++) {
			ProblemTypeData metadata = sortedMeta.get(i);
			ProblemType actual = sortedActual.get(i);
			assertEquals(actual.getCode(), metadata.getCode());
			assertEquals(actual.getDescription(), metadata.getDescription());
			assertEquals(actual.getDefaultSeverity().toString(), metadata.getDefaultSeverity());
			assertEquals(actual.getLabel(), metadata.getLabel());
			validateParameters(metadata, actual);
		}
	}

	private void validateParameters(ProblemTypeData metadata, ProblemType actual) {
		List<ProblemTypeParameterData> md = metadata.getParameters();
		if (md == null) {
			md = Collections.emptyList();
		}
		assertEquals(actual.getParameters().size(), md.size(), "parameter count for " + actual.getCode());
		List<ProblemTypeParameter> ap = new ArrayList<>(actual.getParameters());
		ap.sort(Comparator.comparing(ProblemTypeParameter::getKey));
		List<ProblemTypeParameterData> mdSorted = new ArrayList<>(md);
		mdSorted.sort(Comparator.comparing(ProblemTypeParameterData::getKey));
		for (int i = 0; i < ap.size(); i++) {
			ProblemTypeParameter p = ap.get(i);
			ProblemTypeParameterData d = mdSorted.get(i);
			assertEquals(p.getKey(), d.getKey());
			assertEquals(p.getLabel(), d.getLabel());
			assertEquals(p.getDescription(), d.getDescription());
			assertEquals(p.getType().name().toLowerCase(), d.getType());
			assertEquals(p.getDefaultValue(), d.getDefaultValue());
		}
	}

	public void updatePackageJson(File packageJsonFile, String propertyPrefix, String id, ProblemType[] problemTypes) throws Exception {
		Gson gson = new GsonBuilder()
				.setPrettyPrinting()
				.serializeNulls()
				.create();
		JsonElement parsed = gson.fromJson(new FileReader(packageJsonFile), JsonElement.class);
		JsonArray allProps = (JsonArray) YamlPath.fromProperty("contributes.configuration.properties").traverse(new JSONCursor(parsed)).target;
		JsonObject configProps = Streams.fromIterable(allProps).filter(JsonObject.class::isInstance).map(JsonObject.class::cast).filter(o -> {
			JsonElement element = o.get("id");
			return element != null && id.equals(element.getAsString());
		}).findFirst().orElse(null);
		if (configProps != null) {
			Set<String> removeProps = configProps.keySet().stream().filter(s -> s.startsWith(propertyPrefix)).collect(Collectors.toSet());
			for (String rp : removeProps) {
				configProps.remove(rp);
			}
			JsonArray severities = new JsonArray();
			for (ProblemSeverity severity : ProblemSeverity.values()) {
				severities.add(severity.name());
			}
			for (ProblemType problemType : problemTypes) {
				ProblemTypeData data = new ProblemTypeData(problemType);
				JsonObject schema = new JsonObject();
				schema.addProperty("type", "string");
				schema.addProperty("default", data.defaultSeverity);
				schema.addProperty("description", data.description);
				schema.add("enum", severities);
				configProps.add(propertyPrefix + "." + id + "." + data.code, schema);
			}
			
			String newContent = gson.toJson(parsed);
			FileUtils.writeStringToFile(packageJsonFile, newContent);
		} else {
			throw new IllegalStateException("Cannot find config object with 'id' property equal to '" + id + "'");
		}
	}
	
	public void updatePackageJson(File packageJsonFile, String propertyPrefix) throws Exception {
		Gson gson = new GsonBuilder()
				.setPrettyPrinting()
				.disableHtmlEscaping()
				.serializeNulls()
				.create();
		JsonElement parsed = gson.fromJson(new FileReader(packageJsonFile), JsonElement.class);
		JsonArray allProps = (JsonArray) YamlPath.fromProperty("contributes.configuration").traverse(new JSONCursor(parsed)).target;
		
		JsonArray severities = new JsonArray();
		for (ProblemSeverity severity : ProblemSeverity.values()) {
			severities.add(severity.name());
		}

		for (ProblemCategoryData category : problemCategories) {
			JsonObject configProps = Streams.fromIterable(allProps).filter(JsonObject.class::isInstance).map(JsonObject.class::cast).filter(o -> {
				JsonElement element = o.get("id");
				return element != null && category.getId().equals(element.getAsString());
			}).findFirst().orElse(null);
			
			if (configProps != null) {
				allProps.remove(configProps);
			}
		}
		
		for (ProblemCategoryData category : problemCategories) {
			JsonObject configProps = new JsonObject();
			configProps.addProperty("id", category.getId());
			configProps.addProperty("title", category.getLabel());
			configProps.addProperty("order", 400 + category.order);
			JsonObject props = new JsonObject();
			if (category.getToggle() != null) {
				Toggle categorySwitch = category.getToggle();
				JsonObject switchObj = new JsonObject();
				switchObj.addProperty("type", "string");
				switchObj.addProperty("default", categorySwitch.getDefaultValue().name());
				switchObj.addProperty("description", categorySwitch.getLabel());
				JsonArray vals = new JsonArray();
				for (Toggle.Option s : categorySwitch.getValues()) {
					vals.add(s.name());
				}
				switchObj.add("enum", vals);
				props.add(categorySwitch.getPreferenceKey(), switchObj);
			}
			for (ProblemTypeData data : category.getProblemTypes()) {
				JsonObject schema = new JsonObject();
				schema.addProperty("type", "string");
				schema.addProperty("default", data.defaultSeverity);
				schema.addProperty("description", data.description);
				schema.add("enum", severities);
				props.add(propertyPrefix + "." + category.getId() + "." + data.code, schema);
				addParameterPropertySchemas(props, category, data);
			}
			configProps.add("properties", props);
			allProps.add(configProps);
		}
			
		String newContent = gson.toJson(parsed);
		FileUtils.writeStringToFile(packageJsonFile, newContent);
	}

	private static void addParameterPropertySchemas(JsonObject props, ProblemCategoryData category, ProblemTypeData data) {
		if (data.getParameters() == null || data.getParameters().isEmpty()) {
			return;
		}
		String base = PROBLEM_PARAMETERS_PROPERTY_PREFIX + "." + category.getId() + "." + data.code + ".";
		for (ProblemTypeParameterData param : data.getParameters()) {
			String fullKey = base + param.getKey();
			JsonObject schema = new JsonObject();
			String t = param.getType();
			if ("integer".equals(t)) {
				schema.addProperty("type", "integer");
				schema.addProperty("default", Integer.parseInt(param.getDefaultValue()));
				schema.addProperty("minimum", 1);
			} else if ("boolean".equals(t)) {
				schema.addProperty("type", "boolean");
				schema.addProperty("default", Boolean.parseBoolean(param.getDefaultValue()));
			} else {
				schema.addProperty("type", "string");
				schema.addProperty("default", param.getDefaultValue());
			}
			schema.addProperty("description", param.getDescription());
			props.add(fullKey, schema);
		}
	}
	
}
