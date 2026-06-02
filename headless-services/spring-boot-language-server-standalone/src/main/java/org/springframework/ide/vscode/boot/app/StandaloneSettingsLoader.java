/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.stereotype.Component;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Loads language server settings from files inside the <code>.claude/</code> directory
 * of the project when the server is running in standalone mode (e.g. via the Claude Code
 * plugin).
 *
 * <p>Two file formats are supported and may coexist. Both are optional:
 *
 * <ul>
 *   <li><b>.claude/spring-tools.properties</b> — flat {@code key=value} format where each
 *       key is a dot-separated settings path:
 *       <pre>
 * boot-java.validation.java.boot2=OFF
 * spring-boot.ls.problem.boot2.JAVA_PUBLIC_BEAN_METHOD=IGNORE
 *       </pre>
 *   <li><b>.claude/spring-tools.json</b> — nested JSON format matching the VSCode
 *       {@code settings.json} structure:
 *       <pre>
 * {
 *   "boot-java": { "validation": { "java": { "boot2": "OFF" } } },
 *   "spring-boot": { "ls": { "problem": { "boot2": { "JAVA_PUBLIC_BEAN_METHOD": "IGNORE" } } } }
 * }
 *       </pre>
 * </ul>
 *
 * <p>When both files are present they are merged: the properties file provides the base
 * and the JSON file overrides it (JSON takes precedence). Settings are applied once at
 * startup via the same {@code workspace/didChangeConfiguration} code path that the VSCode
 * client uses, so all existing preference consumers ({@link BootJavaConfig}, etc.) react
 * to them automatically.
 *
 * <p>This component lives in the standalone module and is therefore only active when the
 * language server runs outside of an IDE (e.g. via the Claude Code plugin).
 */
@Component
public class StandaloneSettingsLoader implements SmartInitializingSingleton {

	private static final Logger log = LoggerFactory.getLogger(StandaloneSettingsLoader.class);

	static final String JSON_SETTINGS_FILE = ".claude/spring-tools.json";
	static final String PROPERTIES_SETTINGS_FILE = ".claude/spring-tools.properties";

	private final SimpleLanguageServer server;

	public StandaloneSettingsLoader(SimpleLanguageServer server) {
		this.server = server;
	}

	@Override
	public void afterSingletonsInstantiated() {
		String projectDir = System.getProperty("spring.boot.ls.project.dir");
		if (projectDir == null || projectDir.isBlank()) {
			return;
		}

		JsonObject merged = new JsonObject();
		boolean anyLoaded = false;

		Path propsFile = Paths.get(projectDir, PROPERTIES_SETTINGS_FILE);
		if (Files.exists(propsFile)) {
			JsonObject fromProps = loadFromProperties(propsFile);
			if (fromProps != null) {
				merged = deepMerge(merged, fromProps);
				anyLoaded = true;
				log.info("Loaded Claude settings from {}", propsFile);
			}
		}

		Path jsonFile = Paths.get(projectDir, JSON_SETTINGS_FILE);
		if (Files.exists(jsonFile)) {
			JsonObject fromJson = loadFromJson(jsonFile);
			if (fromJson != null) {
				merged = deepMerge(merged, fromJson);
				anyLoaded = true;
				log.info("Loaded Claude settings from {}", jsonFile);
			}
		}

		if (anyLoaded) {
			DidChangeConfigurationParams params = new DidChangeConfigurationParams();
			params.setSettings(merged);
			server.getWorkspaceService().didChangeConfiguration(params);
		}
	}

	private JsonObject loadFromProperties(Path file) {
		Properties props = new Properties();
		try (BufferedReader reader = Files.newBufferedReader(file)) {
			props.load(reader);
		} catch (IOException e) {
			log.warn("Failed to read Claude settings file {}: {}", file, e.getMessage());
			return null;
		}

		JsonObject root = new JsonObject();
		for (Map.Entry<Object, Object> entry : props.entrySet()) {
			String key = entry.getKey().toString().trim();
			String value = entry.getValue().toString().trim();
			if (!key.isEmpty()) {
				setNestedValue(root, key.split("\\."), 0, new JsonPrimitive(value));
			}
		}
		return root;
	}

	private JsonObject loadFromJson(Path file) {
		try {
			String json = Files.readString(file);
			JsonElement parsed = JsonParser.parseString(json);
			if (!parsed.isJsonObject()) {
				log.warn("Claude settings file {} must contain a JSON object at the top level", file);
				return null;
			}
			return parsed.getAsJsonObject();
		} catch (IOException e) {
			log.warn("Failed to read Claude settings file {}: {}", file, e.getMessage());
			return null;
		} catch (Exception e) {
			log.warn("Failed to parse Claude settings file {}: {}", file, e.getMessage());
			return null;
		}
	}

	static void setNestedValue(JsonObject obj, String[] parts, int index, JsonElement value) {
		if (index == parts.length - 1) {
			obj.add(parts[index], value);
		} else {
			JsonElement existing = obj.get(parts[index]);
			JsonObject child = (existing instanceof JsonObject) ? (JsonObject) existing : new JsonObject();
			obj.add(parts[index], child);
			setNestedValue(child, parts, index + 1, value);
		}
	}

	static JsonObject deepMerge(JsonObject base, JsonObject override) {
		JsonObject result = base.deepCopy();
		for (Map.Entry<String, JsonElement> entry : override.entrySet()) {
			String key = entry.getKey();
			JsonElement overrideVal = entry.getValue();
			JsonElement baseVal = result.get(key);
			if (overrideVal.isJsonObject() && baseVal != null && baseVal.isJsonObject()) {
				result.add(key, deepMerge(baseVal.getAsJsonObject(), overrideVal.getAsJsonObject()));
			} else {
				result.add(key, overrideVal);
			}
		}
		return result;
	}

}
