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
package org.springframework.ide.vscode.boot.java.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.OS;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;
import org.springframework.ide.vscode.commons.util.FileObserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;

/**
 * Service for loading and caching Spring Data repository AOT metadata.
 * 
 * Provides caching of metadata loaded from JSON files with automatic cache invalidation
 * when files are created, modified, or deleted.
 * 
 * @author Martin Lippert
 */
public class DataRepositoryAotMetadataService {
	
	static final String CMD_REFESH_METADATA = "sts/boot/aot/refresh-metadata";
	
	private static final String MODULE_JSON_PROP = "module";

	private static final String TYPE_JSON_PROP = "type";

	private static final String NAME_JSON_PROP = "name";

	private static final String METHODS = "methods";

	private static final Logger log = LoggerFactory.getLogger(DataRepositoryAotMetadataService.class);
	
	private static final Object MAVEN_LOCK = new Object(); 
	
	// Cache: file path -> parsed metadata (Optional.empty() if file doesn't exist or failed to parse)
	private final ConcurrentMap<Path, Optional<DataRepositoryAotMetadata>> metadataCache = new ConcurrentHashMap<>();
	
	private final Gson gson = new GsonBuilder().registerTypeAdapter(DataRepositoryAotMetadata.class, new JsonDeserializer<DataRepositoryAotMetadata>() {

		@Override
		public DataRepositoryAotMetadata deserialize(JsonElement json, Type typeOfT,
				JsonDeserializationContext context) throws JsonParseException {
			JsonObject o = json.getAsJsonObject();
			JsonElement e = o.get(MODULE_JSON_PROP);
			if (e.isJsonPrimitive()) {
				String module = e.getAsString();
				String name = context.deserialize(o.get(NAME_JSON_PROP), String.class);
				String type = context.deserialize(o.get(TYPE_JSON_PROP), String.class);
				DataRepositoryModule moduleType = DataRepositoryModule.valueOf(module.toUpperCase());
				switch (moduleType) {
				case MONGODB:
					return new DataRepositoryAotMetadata(name, type, moduleType, context.deserialize(o.get(METHODS), MongoAotMethodMetadata[].class));
				case JPA:
					return new DataRepositoryAotMetadata(name, type, moduleType, context.deserialize(o.get(METHODS), JpaAotMethodMetadata[].class));
				}
			}
			return null;
		}
		
	}).create();

	public DataRepositoryAotMetadataService(SimpleLanguageServer server, FileObserver fileObserver, JavaProjectFinder projectFinder) {
		server.onCommand(CMD_REFESH_METADATA, params -> {
			Object o = params.getArguments().get(0);
			String projectName = o instanceof JsonPrimitive ? ((JsonPrimitive) o).getAsString() : o.toString();
			return projectFinder.all().stream().filter(jp -> projectName.equals(jp.getElementName())).findFirst()
					.map(DataRepositoryAotMetadataService.this::regenerateAotMetadata)
					.orElse(CompletableFuture.failedFuture(
							new IllegalArgumentException("Cannot find project with name '%s'".formatted(projectName))));
		});
		if (fileObserver != null) {
			fileObserver.onAnyChange(List.of("**/spring-aot/main/resources/**/*.json"), changedFiles -> {
				List<Path> removedEntries = new ArrayList<>();
				for (String filePath : changedFiles) {
					Path path = Path.of(filePath);
					Optional<DataRepositoryAotMetadata> removed = metadataCache.remove(path);
					if (removed != null) {
						removedEntries.add(path);
					}
				}
				if (!removedEntries.isEmpty()) {
					
				}
			});
		}
	}
	
	public Optional<DataRepositoryAotMetadata> getRepositoryMetadata(IJavaProject project, String repositoryType) {
		String metadataFilePath = repositoryType.replace('.', '/') + ".json";
		
		return IClasspathUtil.getOutputFolders(project.getClasspath())
			.map(outputFolder -> outputFolder.getParentFile().toPath().resolve("spring-aot/main/resources/").resolve(metadataFilePath))
			.findFirst()
			.flatMap(filePath -> metadataCache.computeIfAbsent(filePath, this::readMetadataFile));
	}
	
	private Optional<DataRepositoryAotMetadata> readMetadataFile(Path filePath) {
		if (Files.isRegularFile(filePath)) {
			try (BufferedReader reader = Files.newBufferedReader(filePath)) {
				return Optional.ofNullable(gson.fromJson(reader, DataRepositoryAotMetadata.class));
			} catch (IOException e) {
				log.error("Failed to read metadata file: {}", filePath, e);
			}
		}
		return Optional.empty();
	}
	
	public CompletableFuture<Void> regenerateAotMetadata(IJavaProject jp) {
		switch (jp.getProjectBuild().getType()) {
			case ProjectBuild.MAVEN_PROJECT_TYPE:
				return CompletableFuture.runAsync(() -> mavenRegenerateMetadata(jp));
		}
		return CompletableFuture.failedFuture(new IllegalStateException("Cannot generate AOT metadata"));
	}
	
	private CompletableFuture<Void> mavenRegenerateMetadata(IJavaProject jp) {
		synchronized(MAVEN_LOCK) {
			List<String> cmd = new ArrayList<>();
			Path projectPath = Paths.get(jp.getLocationUri());
			String mvnw = OS.isWindows() ? "mvnw.bat" : "./mvnw";
			cmd.add(Files.isRegularFile(projectPath.resolve(mvnw)) ? mvnw : "mvn");
			if (!IClasspathUtil.getOutputFolders(jp.getClasspath()).map(f -> f.toPath()).allMatch(Files::isDirectory)) {
				// Check if source is compiled by checking that all output folders exist
				// If not compiled then add `compile` goal
				cmd.add("compile");
			}
			cmd.add("org.springframework.boot:spring-boot-maven-plugin:process-aot");
			try {
				return Runtime.getRuntime().exec(cmd.toArray(new String[cmd.size()]), null, projectPath.toFile()).onExit().thenAccept(process -> {
					if (process.exitValue() != 0) {
						throw new CompletionException("Failed to generate AOT metadata", new IllegalStateException("Errors running maven command: %s".formatted(String.join(" ", cmd))));
					}
				});
			} catch (IOException e) {
				throw new CompletionException(e);
			}
		}
	}
}