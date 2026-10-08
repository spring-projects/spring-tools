/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom
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
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.ide.vscode.commons.java.IProjectBuild;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
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
	
	/**
	 * The task of the Spring Boot Gradle plugin that generates the AOT sources and resources, it exists if the
	 * GraalVM native build tools plugin is applied.
	 */
	private static final String GRADLE_AOT_TASK = "processAot";

	/**
	 * Name of the folder the Spring Boot Gradle plugin generates the AOT resources, which contain the repository
	 * metadata, into. It is a source folder of the project (the AOT source set that the GraalVM native build tools
	 * plugin adds) once the project was synchronized after the folder was generated.
	 */
	private static final String GRADLE_AOT_RESOURCES_DIR = "aotResources";

	/**
	 * The folder of the Spring Boot Maven plugin with the AOT resources, relative to the build directory (`target`).
	 */
	private static final String MAVEN_AOT_RESOURCES_PATH = "spring-aot/main/resources";

	/**
	 * The folders whose deletion makes the AOT metadata go away, by name, in Maven projects: the folder with the
	 * metadata and the ones above it.
	 */
	private static final List<String> MAVEN_METADATA_FOLDERS = List.of("spring-aot", "spring-aot/main", MAVEN_AOT_RESOURCES_PATH);

	/**
	 * The same for Gradle projects: the AOT resources folder and the `generated` folder it is in. The build directory
	 * itself is not listed, its name is not known.
	 */
	private static final List<String> GRADLE_METADATA_FOLDERS = List.of("generated", GRADLE_AOT_RESOURCES_DIR);

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
		this(fileObserver, projectFinder, buildCmds, null);
	}

	public DataRepositoryAotMetadataService(FileObserver fileObserver, JavaProjectFinder projectFinder, BuildCommandProvider buildCmds, ProjectObserver projectObserver) {
		this.buildCmds = buildCmds;
		this.listeners = new ListenerList<>();
		if (projectObserver != null) {
			listenForProjectChanges(projectObserver);
		}
		if (fileObserver != null) {
			fileObserver.onAnyChange(List.of("**/" + MAVEN_AOT_RESOURCES_PATH + "/**/*.json", "**/" + GRADLE_AOT_RESOURCES_DIR + "/**/*.json"), changedFiles -> {
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
			fileObserver.onFilesDeleted(Stream.concat(MAVEN_METADATA_FOLDERS.stream(), GRADLE_METADATA_FOLDERS.stream())
					.map(folder -> "**/" + folder).toList(), this::onMetadataFoldersDeleted);
		}
	}

	private void onMetadataFoldersDeleted(String[] changedFiles) {
		// If a folder such as `spring-aot` or `generated` is deleted VSCode would only notify about the folder deletion, no events for each contained file
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
	}

	/**
	 * Gradle only: the lenses depend on the tasks of the build (`processAot`) and on the AOT resources folder being a
	 * source folder, which it only is after the project was synchronized (Maven finds its metadata without that).
	 * That is a change of the build or the classpath of the project and not of a document, so notify the listeners
	 * (the lenses).
	 */
	private void listenForProjectChanges(ProjectObserver projectObserver) {
		projectObserver.addListener(new ProjectObserver.Listener() {

			@Override
			public void created(IJavaProject project) {
			}

			@Override
			public void changed(IJavaProject project, boolean clean) {
				if (ProjectBuild.GRADLE_PROJECT_TYPE.equals(buildType(project))) {
					DataRepositoryAotMetadataService.this.notify(List.of(project.getLocationUri()));
				}
			}

			@Override
			public void buildChanged(IJavaProject project) {
				if (ProjectBuild.GRADLE_PROJECT_TYPE.equals(buildType(project))) {
					DataRepositoryAotMetadataService.this.notify(List.of(project.getLocationUri()));
				}
			}

			@Override
			public void deleted(IJavaProject project) {
			}

		});
	}

	/**
	 * The project's build type, or <code>null</code> when it is not known.
	 * <p>
	 * Not every project has a build this server can classify: the invisible project the language server
	 * creates for a plain folder has none, and neither does a project contributed by an importer that is
	 * neither Maven nor Gradle - {@code ClasspathUtil.createProjectBuild} then reports a build whose type
	 * is null. Switching on that throws, and the callers here are on the indexing path, where an
	 * exception costs a whole type its symbols.
	 */
	private static String buildType(IJavaProject project) {
		IProjectBuild build = project.getProjectBuild();
		return build == null ? null : build.getType();
	}
	
	/**
	 * Whether the build of the project can generate the AOT metadata. For a Gradle project that is only so if
	 * the build has the <code>processAot</code> task, which the Spring Boot Gradle plugin registers when the
	 * GraalVM native build tools plugin is applied. The IDE reports the tasks of the build without running Gradle.
	 * If it does not know them (yet) the project is given the benefit of the doubt.
	 */
	public boolean isAotGenerationAvailable(IJavaProject project) {
		IProjectBuild build = project.getProjectBuild();
		if (build != null && ProjectBuild.GRADLE_PROJECT_TYPE.equals(build.getType())) {
			Set<String> tasks = build.getTasks();
			return tasks == null || tasks.contains(GRADLE_AOT_TASK);
		}
		return true;
	}
	
	public Optional<DataRepositoryAotMetadata> getRepositoryMetadata(IJavaProject project, String repositoryType) {
		String metadataFilePath = repositoryType.replace('.', '/') + ".json";
		
		String buildType = buildType(project);
		if (buildType == null) {
			return Optional.empty();
		}
		
		switch (buildType) {
		case ProjectBuild.MAVEN_PROJECT_TYPE:
			return IClasspathUtil.getOutputFolders(project.getClasspath())
					.map(outputFolder -> outputFolder.getParentFile().toPath().resolve(MAVEN_AOT_RESOURCES_PATH).resolve(metadataFilePath))
					.findFirst()
					.flatMap(filePath -> metadataCache.computeIfAbsent(filePath, this::readMetadataFile));
		case ProjectBuild.GRADLE_PROJECT_TYPE:
			return IClasspathUtil.getSourceFolders(project.getClasspath())
					.filter(f -> f.isDirectory() && GRADLE_AOT_RESOURCES_DIR.equals(f.getName()))
					.findFirst()
					.map(File::toPath)
					.map(folder -> folder.resolve(metadataFilePath))
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
		String buildType = buildType(jp);
		if (buildType == null) {
			return Optional.empty();
		}
		
		switch (buildType) {
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
		case ProjectBuild.GRADLE_PROJECT_TYPE:
			if (!isAotGenerationAvailable(jp)) {
				// The build has no `processAot` task: the plugin that adds it (GraalVM native build tools) is not applied
				return Optional.empty();
			}
			List<String> command = new ArrayList<>();
			if (!IClasspathUtil.getOutputFolders(jp.getClasspath()).map(f -> f.toPath()).filter(Files::isDirectory).flatMap(d -> {
				try {
					return Files.walk(d);
				} catch (IOException e) {
					return Stream.empty();
				}
			}).anyMatch(f -> Files.isRegularFile(f) && f.getFileName().toString().endsWith(".class"))) {
				// Source is not compiled yet
				command.add("classes");
			}
			// aotClasses runs processAot (sources, metadata) and also compiles the generated classes, such as the
			// Spring Data repository implementations
			command.add("aotClasses");
			return Optional.ofNullable(buildCmds.executeGradleBuild(jp, String.join(" ", command)));
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