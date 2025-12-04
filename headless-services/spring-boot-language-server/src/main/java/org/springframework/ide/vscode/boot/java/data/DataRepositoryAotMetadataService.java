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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.BuildCommandProvider;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;
import org.springframework.ide.vscode.commons.util.FileObserver;
import org.springframework.ide.vscode.commons.util.ListenerList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * Service for loading and caching Spring Data repository AOT metadata.
 * 
 * Provides caching of metadata loaded from JSON files with automatic cache invalidation
 * when files are created, modified, or deleted.
 * 
 * @author Martin Lippert
 */
public class DataRepositoryAotMetadataService {
	
	private static final String MODULE_JSON_PROP = "module";

	private static final String TYPE_JSON_PROP = "type";

	private static final String NAME_JSON_PROP = "name";

	private static final String METHODS = "methods";

	private static final Logger log = LoggerFactory.getLogger(DataRepositoryAotMetadataService.class);
	
	private ListenerList<Consumer<List<URI>>> listeners;
	
	private BuildCommandProvider buildCmds;
	
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
				case JDBC:
					return new DataRepositoryAotMetadata(name, type, moduleType, context.deserialize(o.get(METHODS), JdbcAotMethodMetadata[].class));
				}
			}
			return null;
		}
		
	}).create();

	public DataRepositoryAotMetadataService(FileObserver fileObserver, JavaProjectFinder projectFinder, BuildCommandProvider buildCmds) {
		this.buildCmds = buildCmds;
		this.listeners = new ListenerList<>();
		if (fileObserver != null) {
			fileObserver.onAnyChange(List.of("**/spring-aot/main/resources/**/*.json"), changedFiles -> {
				List<URI> removedEntries = new ArrayList<>();
				for (String fileUri : changedFiles) {
					URI uri = URI.create(fileUri);
					Path path = Paths.get(uri);
					Optional<DataRepositoryAotMetadata> removed = metadataCache.remove(path);
					if (removed != null) {
						removedEntries.add(uri);
					}
				}
				if (!removedEntries.isEmpty()) {
					log.info("Spring AOT Metadata refreshed: %s".formatted(removedEntries.stream().map(p -> p.toString()).collect(Collectors.joining(", "))));
					notify(removedEntries);
				}
			});
			fileObserver.onFilesDeleted(List.of("**/spring-aot", "**/spring-aot/main", "**/spring-aot/main/resources"), changedFiles -> {
				// If `spring-aot` folder is deleted VSCode would only notify about the folder deletion, no events for each contained file
				for (String fileUri : changedFiles) {
					URI uri = URI.create(fileUri);
					Path path = Paths.get(uri);
					List<URI> removedEntries = metadataCache.keySet().stream()
						.filter(p -> p.startsWith(path))
						.filter(p -> metadataCache.remove(p).isPresent())
						.map(p -> p.toUri())
						.toList();
					if (!removedEntries.isEmpty()) {
						log.info("Spring AOT Metadata refreshed: %s".formatted(removedEntries.stream().map(p -> p.toString()).collect(Collectors.joining(", "))));
						notify(removedEntries);
					}
				}
			});
		}
	}
	
	public Optional<DataRepositoryAotMetadata> getRepositoryMetadata(IJavaProject project, String repositoryType) {
		String metadataFilePath = repositoryType.replace('.', '/') + ".json";
		
		switch (project.getProjectBuild().getType()) {
		case ProjectBuild.MAVEN_PROJECT_TYPE:
			return IClasspathUtil.getOutputFolders(project.getClasspath())
					.map(outputFolder -> outputFolder.getParentFile().toPath().resolve("spring-aot/main/resources/").resolve(metadataFilePath))
					.findFirst()
					.flatMap(filePath -> metadataCache.computeIfAbsent(filePath, this::readMetadataFile));
		case ProjectBuild.GRADLE_PROJECT_TYPE:
			return IClasspathUtil.getSourceFolders(project.getClasspath())
				.filter(f -> f.isDirectory() && "aotResources".equals(f.getName()))
				.findFirst()
				.map(f -> f.toPath().resolve(metadataFilePath))
				.flatMap(filePath -> metadataCache.computeIfAbsent(filePath, this::readMetadataFile));
		}
		return Optional.empty();
	}
	
	private Optional<DataRepositoryAotMetadata> readMetadataFile(Path filePath) {
		if (Files.isRegularFile(filePath)) {
			try (BufferedReader reader = Files.newBufferedReader(filePath)) {
				return Optional.ofNullable(gson.fromJson(reader, DataRepositoryAotMetadata.class));
			} catch (Exception e) {
				log.error("Failed to read metadata file: {}", filePath, e);
			}
		}
		return Optional.empty();
	}
	
	Optional<Command> regenerateMetadataCommand(IJavaProject jp) {
		switch (jp.getProjectBuild().getType()) {
		case ProjectBuild.MAVEN_PROJECT_TYPE:
			List<String> goal = new ArrayList<>();
			if (!IClasspathUtil.getOutputFolders(jp.getClasspath()).map(f -> f.toPath()).filter(Files::isDirectory).flatMap(d -> {
				try {
					return Files.walk(d);
				} catch (IOException e) {
					return Stream.empty();
				}
			}).anyMatch(f -> Files.isRegularFile(f) && f.getFileName().toString().endsWith(".class"))) {
				// Check if source is compiled by checking that all output folders exist
				// If not compiled then add `compile` goal
				goal.add("compile");
			}
			goal.add("org.springframework.boot:spring-boot-maven-plugin:process-aot");
			return Optional.ofNullable(buildCmds.executeMavenGoal(jp, String.join(" ", goal)));
//		case ProjectBuild.GRADLE_PROJECT_TYPE:
//			List<String> command = new ArrayList<>();
//			if (!IClasspathUtil.getOutputFolders(jp.getClasspath()).map(f -> f.toPath()).filter(Files::isDirectory).flatMap(d -> {
//				try {
//					return Files.walk(d);
//				} catch (IOException e) {
//					return Stream.empty();
//				}
//			}).anyMatch(f -> Files.isRegularFile(f) && f.getFileName().toString().endsWith(".class"))) {
//				// Check if source is compiled by checking that all output folders exist
//				// If not compiled then add `build` task
//				command.add("build");
//			}
//			command.add("processAot");
//			return Optional.ofNullable(buildCmds.executeGradleBuild(jp, String.join(" ", command)));
		}
		return Optional.empty();
	}
	
	public void addListener(Consumer<List<URI>> listener) {
		listeners.add(listener);
	}
	
	public void removeListener(Consumer<List<URI>> listener) {
		listeners.remove(listener);
	}
	
	private void notify(List<URI> metadtaFiles) {
		listeners.forEach(l -> l.accept(metadtaFiles));
	}
}