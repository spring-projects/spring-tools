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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleWorkspaceService;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

class StandaloneSettingsLoaderTest {

	@Mock SimpleLanguageServer server;
	@Mock SimpleWorkspaceService workspaceService;

	@TempDir Path projectDir;

	private AutoCloseable mocks;

	@BeforeEach
	void setup() {
		mocks = MockitoAnnotations.openMocks(this);
		when(server.getWorkspaceService()).thenReturn(workspaceService);
	}

	@AfterEach
	void teardown() throws Exception {
		mocks.close();
		System.clearProperty("spring.boot.ls.project.dir");
	}

	// -----------------------------------------------------------------------
	// setNestedValue
	// -----------------------------------------------------------------------

	@Test
	void setNestedValue_singleSegment_setsLeaf() {
		JsonObject obj = new JsonObject();
		StandaloneSettingsLoader.setNestedValue(obj, new String[] { "boot2" }, 0, new JsonPrimitive("OFF"));
		assertThat(obj.get("boot2").getAsString()).isEqualTo("OFF");
	}

	@Test
	void setNestedValue_multipleSegments_createsNestedObjects() {
		JsonObject obj = new JsonObject();
		StandaloneSettingsLoader.setNestedValue(
				obj, new String[] { "boot-java", "validation", "java", "boot2" }, 0, new JsonPrimitive("OFF"));
		assertThat(obj
				.getAsJsonObject("boot-java")
				.getAsJsonObject("validation")
				.getAsJsonObject("java")
				.get("boot2").getAsString())
			.isEqualTo("OFF");
	}

	@Test
	void setNestedValue_reuseExistingIntermediateObjects() {
		JsonObject obj = new JsonObject();
		StandaloneSettingsLoader.setNestedValue(obj, new String[] { "a", "b" }, 0, new JsonPrimitive("first"));
		StandaloneSettingsLoader.setNestedValue(obj, new String[] { "a", "c" }, 0, new JsonPrimitive("second"));
		JsonObject a = obj.getAsJsonObject("a");
		assertThat(a.get("b").getAsString()).isEqualTo("first");
		assertThat(a.get("c").getAsString()).isEqualTo("second");
	}

	// -----------------------------------------------------------------------
	// deepMerge
	// -----------------------------------------------------------------------

	@Test
	void deepMerge_emptyBase_returnsOverride() {
		JsonObject override = new JsonObject();
		override.addProperty("key", "value");
		JsonObject result = StandaloneSettingsLoader.deepMerge(new JsonObject(), override);
		assertThat(result.get("key").getAsString()).isEqualTo("value");
	}

	@Test
	void deepMerge_emptyOverride_returnsBase() {
		JsonObject base = new JsonObject();
		base.addProperty("key", "value");
		JsonObject result = StandaloneSettingsLoader.deepMerge(base, new JsonObject());
		assertThat(result.get("key").getAsString()).isEqualTo("value");
	}

	@Test
	void deepMerge_nonOverlappingKeys_bothPresent() {
		JsonObject base = new JsonObject();
		base.addProperty("a", "from-base");
		JsonObject override = new JsonObject();
		override.addProperty("b", "from-override");
		JsonObject result = StandaloneSettingsLoader.deepMerge(base, override);
		assertThat(result.get("a").getAsString()).isEqualTo("from-base");
		assertThat(result.get("b").getAsString()).isEqualTo("from-override");
	}

	@Test
	void deepMerge_scalarOverlap_overrideWins() {
		JsonObject base = new JsonObject();
		base.addProperty("key", "base-value");
		JsonObject override = new JsonObject();
		override.addProperty("key", "override-value");
		JsonObject result = StandaloneSettingsLoader.deepMerge(base, override);
		assertThat(result.get("key").getAsString()).isEqualTo("override-value");
	}

	@Test
	void deepMerge_nestedObjects_mergedDeep() {
		JsonObject base = new JsonObject();
		JsonObject baseChild = new JsonObject();
		baseChild.addProperty("from-base", "base-value");
		base.add("child", baseChild);

		JsonObject override = new JsonObject();
		JsonObject overrideChild = new JsonObject();
		overrideChild.addProperty("from-override", "override-value");
		override.add("child", overrideChild);

		JsonObject resultChild = StandaloneSettingsLoader.deepMerge(base, override).getAsJsonObject("child");
		assertThat(resultChild.get("from-base").getAsString()).isEqualTo("base-value");
		assertThat(resultChild.get("from-override").getAsString()).isEqualTo("override-value");
	}

	@Test
	void deepMerge_doesNotMutateInputs() {
		JsonObject base = new JsonObject();
		base.addProperty("key", "base");
		JsonObject override = new JsonObject();
		override.addProperty("key", "override");
		StandaloneSettingsLoader.deepMerge(base, override);
		assertThat(base.get("key").getAsString()).isEqualTo("base");
	}

	// -----------------------------------------------------------------------
	// afterSingletonsInstantiated
	// -----------------------------------------------------------------------

	@Test
	void noProjectDirProperty_doesNotFireConfigChange() {
		System.clearProperty("spring.boot.ls.project.dir");
		new StandaloneSettingsLoader(server).afterSingletonsInstantiated();
		verify(workspaceService, never()).didChangeConfiguration(any());
	}

	@Test
	void noSettingsFiles_doesNotFireConfigChange() {
		System.setProperty("spring.boot.ls.project.dir", projectDir.toString());
		new StandaloneSettingsLoader(server).afterSingletonsInstantiated();
		verify(workspaceService, never()).didChangeConfiguration(any());
	}

	@Test
	void onlyJsonFile_firesWithJsonSettings() throws IOException {
		System.setProperty("spring.boot.ls.project.dir", projectDir.toString());
		writeClaudeFile("spring-tools.json", """
				{
				  "boot-java": { "validation": { "java": { "boot2": "OFF" } } }
				}
				""");

		new StandaloneSettingsLoader(server).afterSingletonsInstantiated();

		JsonObject settings = captureSettings();
		assertThat(settings
				.getAsJsonObject("boot-java")
				.getAsJsonObject("validation")
				.getAsJsonObject("java")
				.get("boot2").getAsString())
			.isEqualTo("OFF");
	}

	@Test
	void onlyPropertiesFile_firesWithConvertedSettings() throws IOException {
		System.setProperty("spring.boot.ls.project.dir", projectDir.toString());
		writeClaudeFile("spring-tools.properties",
				"boot-java.validation.java.boot2=OFF\n" +
				"spring-boot.ls.problem.boot2.JAVA_PUBLIC_BEAN_METHOD=IGNORE\n");

		new StandaloneSettingsLoader(server).afterSingletonsInstantiated();

		JsonObject settings = captureSettings();
		assertThat(settings
				.getAsJsonObject("boot-java")
				.getAsJsonObject("validation")
				.getAsJsonObject("java")
				.get("boot2").getAsString())
			.isEqualTo("OFF");
		assertThat(settings
				.getAsJsonObject("spring-boot")
				.getAsJsonObject("ls")
				.getAsJsonObject("problem")
				.getAsJsonObject("boot2")
				.get("JAVA_PUBLIC_BEAN_METHOD").getAsString())
			.isEqualTo("IGNORE");
	}

	@Test
	void propertiesFileWithComments_ignoresCommentLines() throws IOException {
		System.setProperty("spring.boot.ls.project.dir", projectDir.toString());
		writeClaudeFile("spring-tools.properties",
				"# This is a comment\n" +
				"boot-java.validation.java.boot2=OFF\n" +
				"\n" +
				"# Another comment\n" +
				"boot-java.validation.spel.on=ON\n");

		new StandaloneSettingsLoader(server).afterSingletonsInstantiated();

		JsonObject settings = captureSettings();
		JsonObject validation = settings.getAsJsonObject("boot-java").getAsJsonObject("validation");
		assertThat(validation.getAsJsonObject("java").get("boot2").getAsString()).isEqualTo("OFF");
		assertThat(validation.getAsJsonObject("spel").get("on").getAsString()).isEqualTo("ON");
	}

	@Test
	void bothFiles_mergedOnce_jsonOverridesProperties() throws IOException {
		System.setProperty("spring.boot.ls.project.dir", projectDir.toString());
		// properties: boot2=ON (will be overridden) and spel=ON (only in properties)
		writeClaudeFile("spring-tools.properties",
				"boot-java.validation.java.boot2=ON\n" +
				"boot-java.validation.spel.on=ON\n");
		// JSON: boot2=OFF (overrides the properties value)
		writeClaudeFile("spring-tools.json", """
				{ "boot-java": { "validation": { "java": { "boot2": "OFF" } } } }
				""");

		new StandaloneSettingsLoader(server).afterSingletonsInstantiated();

		// didChangeConfiguration must be called exactly once (merged, not twice)
		JsonObject settings = captureSettings();
		JsonObject validation = settings.getAsJsonObject("boot-java").getAsJsonObject("validation");
		assertThat(validation.getAsJsonObject("java").get("boot2").getAsString())
				.as("JSON value overrides properties value").isEqualTo("OFF");
		assertThat(validation.getAsJsonObject("spel").get("on").getAsString())
				.as("Key only in properties file is preserved").isEqualTo("ON");
	}

	@Test
	void invalidJsonFile_doesNotFireConfigChange() throws IOException {
		System.setProperty("spring.boot.ls.project.dir", projectDir.toString());
		writeClaudeFile("spring-tools.json", "not { valid } json");
		new StandaloneSettingsLoader(server).afterSingletonsInstantiated();
		verify(workspaceService, never()).didChangeConfiguration(any());
	}

	@Test
	void jsonFileWithNonObjectTopLevel_doesNotFireConfigChange() throws IOException {
		System.setProperty("spring.boot.ls.project.dir", projectDir.toString());
		writeClaudeFile("spring-tools.json", "[1, 2, 3]");
		new StandaloneSettingsLoader(server).afterSingletonsInstantiated();
		verify(workspaceService, never()).didChangeConfiguration(any());
	}

	// -----------------------------------------------------------------------
	// helpers
	// -----------------------------------------------------------------------

	private void writeClaudeFile(String filename, String content) throws IOException {
		Path claudeDir = projectDir.resolve(".claude");
		Files.createDirectories(claudeDir);
		Files.writeString(claudeDir.resolve(filename), content);
	}

	private JsonObject captureSettings() {
		ArgumentCaptor<DidChangeConfigurationParams> captor =
				ArgumentCaptor.forClass(DidChangeConfigurationParams.class);
		verify(workspaceService).didChangeConfiguration(captor.capture());
		return (JsonObject) captor.getValue().getSettings();
	}

}
