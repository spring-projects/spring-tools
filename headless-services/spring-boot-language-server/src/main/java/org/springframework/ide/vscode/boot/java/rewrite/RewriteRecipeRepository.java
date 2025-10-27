/*******************************************************************************
 * Copyright (c) 2022, 2024 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.rewrite;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ChangeAnnotation;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceEditChangeAnnotationSupportCapabilities;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.ParseExceptionResult;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.RecipeRun;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.Validated;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.config.Environment;
import org.openrewrite.config.RecipeDescriptor;
import org.openrewrite.config.YamlResourceLoader;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.JavaParser;
import org.openrewrite.maven.MavenParser;
import org.openrewrite.tree.ParseError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.IndefiniteProgressTask;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.ListenerList;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;
import org.springframework.ide.vscode.commons.rewrite.LoadUtils;
import org.springframework.ide.vscode.commons.rewrite.LoadUtils.DurationTypeConverter;
import org.springframework.ide.vscode.commons.rewrite.ORDocUtils;
import org.springframework.ide.vscode.commons.rewrite.gradle.GradleIJavaProjectParser;
import org.springframework.ide.vscode.commons.rewrite.java.ProjectParser;
import org.springframework.ide.vscode.commons.rewrite.maven.MavenIJavaProjectParser;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public class RewriteRecipeRepository {
	
	enum RecipeFilter {
		ALL,
		BOOT_UPGRADE,
		NON_BOOT_UPGRADE
	}

	private static final Pattern P1 = Pattern.compile("(Upgrade|Migrate)SpringBoot_\\d+_\\d+");
	
	private static final Map<RecipeFilter, Predicate<Recipe>> RECIPE_LIST_FILTERS = new HashMap<>();
	static {
		RECIPE_LIST_FILTERS.put(RecipeFilter.ALL, r -> true);
		RECIPE_LIST_FILTERS.put(RecipeFilter.BOOT_UPGRADE, r -> {
			String n = lastTokenAfterDot(r.getName());
			return P1.matcher(n).matches();
		});
		RECIPE_LIST_FILTERS.put(RecipeFilter.NON_BOOT_UPGRADE, r -> {
			return RECIPE_LIST_FILTERS.get(RecipeFilter.BOOT_UPGRADE).negate().test(r);
		});
	}
		
	private static final String CMD_REWRITE_RELOAD = "sts/rewrite/reload";
	private static final String CMD_REWRITE_EXECUTE = "sts/rewrite/execute";
	private static final String CMD_REWRITE_LIST = "sts/rewrite/list";
	private static final String CMD_REWRITE_SUBLIST = "sts/rewrite/sublist";
	private static final Logger log = LoggerFactory.getLogger(RewriteRecipeRepository.class);
	
	private static final Set<String> UNINITIALIZED_SET = Collections.emptySet();
	
	final private SimpleLanguageServer server;
	
	final private JavaProjectFinder projectFinder;
	
	final private ListenerList<Void> loadListeners;
	
	private CompletableFuture<Map<String, Recipe>> recipesFuture = null;
	
	private Set<String> scanFiles;
	private Set<String> scanDirs;
	private Set<String> recipeFilters;
		
	static final Gson serializationGson = new GsonBuilder()
			.registerTypeAdapter(Duration.class, new DurationTypeConverter())
			.setPrettyPrinting()
			.create();
	
	public RewriteRecipeRepository(SimpleLanguageServer server, JavaProjectFinder projectFinder, BootJavaConfig config) {
		this.server = server;
		this.projectFinder = projectFinder;
		this.loadListeners = new ListenerList<>();
		this.scanDirs = UNINITIALIZED_SET;
		this.scanFiles = UNINITIALIZED_SET;
		this.recipeFilters = UNINITIALIZED_SET;
		
		config.addListener(l -> {
			Set<String> recipeFilterFromConfig = config.getRecipesFilters();
			if (recipeFilters == UNINITIALIZED_SET || !recipeFilters.equals(recipeFilterFromConfig)) {
				recipeFilters = recipeFilterFromConfig;
			}
			if (scanDirs == UNINITIALIZED_SET || !scanDirs.equals(config.getRecipeDirectories())
					|| scanFiles == UNINITIALIZED_SET || !scanFiles.equals(config.getRecipeFiles())) {
				// Eclipse client sends one event for init and the other config changed event due to remote app value expr listener.
				// Therefore it is best to store the scanDirs here right after it is received, not during scan process or anything else done async
				scanDirs = config.getRecipeDirectories();
				scanFiles = config.getRecipeFiles();
				clearRecipes();
			}
		});
		
		registerCommands();
	}
		
	private synchronized void clearRecipes() {
		recipesFuture = null;
	}
	
	private synchronized Map<String, Recipe> loadRecipes() {
		IndefiniteProgressTask progressTask = server.getProgressService().createIndefiniteProgressTask(UUID.randomUUID().toString(), "Loading Rewrite Recipes", null);
		Map<String, Recipe> recipes = new HashMap<>();
		try {
			log.info("Loading Rewrite Recipes...");
			Environment env = createRewriteEnvironment();
			for (Recipe r : env.listRecipes()) {
				if (r.getName() != null) {
					if (recipes.containsKey(r.getName())) {
						log.error("Duplicate ids: '" + r.getName() + "'");
					}
					recipes.put(r.getName(), r);
				}
			}
			log.info("Done loading Rewrite Recipes");
		} catch (Throwable t) {
			log.error("", t);
		} finally {
			progressTask.done();
		}
		return recipes;
	}
	
	private boolean isAcceptableGlobalCommandRecipe(Recipe r) {
		if (recipeFilters.isEmpty()) {
			return isRecipeValid(r);
		} else {
			for (String filter : recipeFilters) {
				if (!filter.isBlank()) {
					// Check if wild-card character present
					if (filter.indexOf('*') < 0) {
						// No wild-card - direct equality 
						if (filter.equals(r.getName())) {
							return isRecipeValid(r);
						}
					} else {
						// Wild-card present - convert to regular expression
						if (Pattern.matches(filter.replaceAll("\\*", "\\.*"), r.getName())) {
							return isRecipeValid(r);
						}
					}
				}
			}
			return false;
		}
	}
	
	private static boolean isRecipeValid(Recipe r) {
		Validated<?> validation = Validated.invalid(null, null, null);
		try {
			validation = r.validate();
		} catch (Exception e) {
			// ignore
		}
		return validation.isValid();
	}
	
	private Environment createRewriteEnvironment() {
		Environment.Builder builder = Environment.builder().scanRuntimeClasspath();
		for (String p : scanFiles) {
			try {
				Path f = Path.of(p);
				String pathStr = f.toString();
				if (pathStr.endsWith(".jar")) {
					URLClassLoader classLoader = new URLClassLoader(new URL[] { f.toUri().toURL() },
							getClass().getClassLoader());
					builder.scanJar(f, new ArrayList<>(), classLoader);
				} else if (pathStr.endsWith(".yml") || pathStr.endsWith(".yaml")) {
					builder.load(new YamlResourceLoader(new FileInputStream(f.toFile()), f.toUri(), new Properties()));
				}
			} catch (Exception e) {
				log.error("Skipping folder " + p, e);
			}
		}
//		for (String p : scanDirs) {
//			try {
//				Path d = Path.of(p);
//				if (Files.isDirectory(d)) {
//					URLClassLoader classLoader = new URLClassLoader(new URL[] { d.toUri().toURL()}, getClass().getClassLoader());
//					builder.scanPath(d, Collections.emptyList(), classLoader);
//				}
//			} catch (Exception e) {
//				log.error("Skipping folder " + p, e);
//			}
//		}
		return builder.build();
	}
	
	public CompletableFuture<Map<String, Recipe>> recipes() {
		if (recipesFuture == null) {
			recipesFuture = CompletableFuture.supplyAsync(this::loadRecipes);
		}
		return recipesFuture;
	}
	
	public CompletableFuture<Optional<Recipe>> getRecipe(String name) {
		return recipes().thenApply(recipes -> Optional.ofNullable(recipes.get(name)));
	}
	
	private static JsonElement recipeToJson(Recipe r) {
		RecipeDescriptor descriptor = r.getDescriptor();
		JsonElement jsonElement = serializationGson.toJsonTree(Map.of(
				"name", descriptor.getName(),
				"displayName", descriptor.getDisplayName(),
				"description", descriptor.getDescription(),
				"options", descriptor.getOptions(),
				"tags", descriptor.getTags(),
				"hasSubRecipes", !descriptor.getRecipeList().isEmpty()
		));
		return jsonElement;
	}
	
	CompletableFuture<List<Recipe>> getRootRecipes(Predicate<Recipe> rootFilter) {
		return recipes().thenApply(recipesMap -> recipesMap.values().stream().filter(rootFilter).collect(Collectors.toList()));
	}
	
	CompletableFuture<List<Recipe>> getSubRecipes(String rootRecipeId, List<Integer> path) {
		return recipes().thenApply(recipesMap -> {
			Recipe recipe = recipesMap.get(rootRecipeId);
			for (int i : path) {
				if (i < recipe.getRecipeList().size()) {
					recipe = recipe.getRecipeList().get(i);
				} else {
					return Collections.emptyList();
				}
			}
			return recipe == null ? Collections.emptyList() : recipe.getRecipeList();
		});
	}
	
	Recipe createRecipeFromSelection(Recipe original, RecipeSelectionDescriptor[] selection) {
		if (selection == null) {
			return original;
		} else {
			boolean sameSubrecipes = true;
			List<Recipe> newSubRecipes = new ArrayList<>(selection.length);
			for (int i = 0; i < selection.length; i++) {
				if (selection[i].selected) {
					Recipe originalSubRecipe = original.getRecipeList().get(i);
					Recipe newSubRecipe = createRecipeFromSelection(originalSubRecipe, selection[i].subselection());
					newSubRecipes.add(newSubRecipe);
					if (sameSubrecipes) {
						sameSubrecipes = newSubRecipe == originalSubRecipe;
					}
				} else {
					sameSubrecipes = false;
				}
			}
			if (sameSubrecipes) {
				return original;
			} else {
				@SuppressWarnings("unchecked")
				Recipe newRecipe = LoadUtils.createRecipe(original.getDescriptor(), id -> {
					try {
						return (Class<Recipe>) Class.forName(id);
					} catch (ClassNotFoundException e) {
						return null;
					}
				}, true);
				newRecipe.getRecipeList().addAll(newSubRecipes);
				return newRecipe;
			}
		}
	}
	
	private void registerCommands() {
		server.onCommand(CMD_REWRITE_LIST, params -> {
			RecipeFilter f = params.getArguments().size() > 0 ? RecipeFilter.valueOf(((JsonElement) params.getArguments().get(0)).getAsString()) : RecipeFilter.ALL;
			
			return getRootRecipes(r -> isAcceptableGlobalCommandRecipe(r) && RECIPE_LIST_FILTERS.get(f).test(r)).thenApply(recipes -> recipes.stream()
					.map(RewriteRecipeRepository::recipeToJson)
					.collect(Collectors.toList()));			
		});
		
		server.onCommand(CMD_REWRITE_SUBLIST, params -> {
			String rootRecipeId = ((JsonElement) params.getArguments().get(0)).getAsString();
			JsonArray path = (JsonArray) params.getArguments().get(1);
			
			return getSubRecipes(rootRecipeId, path.asList().stream().map(j -> j.getAsInt()).collect(Collectors.toList())).thenApply(recipes -> recipes.stream()
					.map(RewriteRecipeRepository::recipeToJson)
					.collect(Collectors.toList()));			
		});
		
		server.onCommand(CMD_REWRITE_EXECUTE, params -> {
			return recipes().thenCompose(recipesMap -> {
				String uri = ((JsonElement) params.getArguments().get(0)).getAsString();
				JsonElement recipesJson = ((JsonElement) params.getArguments().get(1));
				boolean askForPreview = params.getArguments().size() > 2 ? ((JsonElement) params.getArguments().get(2)).getAsBoolean() : false;
				
				RecipeSelectionDescriptor[] descriptors = serializationGson.fromJson(recipesJson, RecipeSelectionDescriptor[].class);
				List<Recipe> recipes = Arrays.stream(descriptors).map(d -> createRecipeFromSelection(recipesMap.get(d.id()), d.subselection())).collect(Collectors.toList());
				if (recipes.size() == 1) {
					Recipe r = recipes.get(0);
					String progressToken = params.getWorkDoneToken() == null
							|| params.getWorkDoneToken().getLeft() == null
									? (r.getName() == null ? UUID.randomUUID().toString() : r.getName())
									: params.getWorkDoneToken().getLeft();
					return apply(r, uri, progressToken, askForPreview);
				} else {
					String name = recipes.size() + " recipes";
					DeclarativeRecipe aggregateRecipe = new DeclarativeRecipe(
							name,
							name,
							recipes.stream().map(r -> r.getDescription()).collect(Collectors.joining("\n")),
							recipes.stream().flatMap(r -> r.getTags().stream()).collect(Collectors.toSet()),
							null,
							null,
							false,
							Collections.emptyList()
					);
					aggregateRecipe.getRecipeList().addAll(recipes);
					String progressToken = params.getWorkDoneToken() == null
							|| params.getWorkDoneToken().getLeft() == null
									? (aggregateRecipe.getName() == null ? UUID.randomUUID().toString()
											: aggregateRecipe.getName())
									: params.getWorkDoneToken().getLeft();
					return apply(aggregateRecipe, uri, progressToken, askForPreview);
				}
			});
		});
		
		server.onCommand(CMD_REWRITE_RELOAD, params -> {
			clearRecipes();
			return CompletableFuture.completedFuture("executed");
		});
		
	}
	
	CompletableFuture<Object> apply(Recipe r, String uri, String progressToken, boolean askForPreview) {
		final IndefiniteProgressTask progressTask = server.getProgressService().createIndefiniteProgressTask(progressToken, r.getDisplayName(), "Initiated...");
		return CompletableFuture.supplyAsync(() -> {
			return projectFinder.find(new TextDocumentIdentifier(uri));
		}).thenCompose(p -> {
			if (p.isPresent()) {
				IJavaProject project = p.get();
				Path absoluteProjectDir = Paths.get(project.getLocationUri());
				progressTask.progressEvent("Parsing files...");
				ProjectParser projectParser = createRewriteProjectParser(project,
						pr -> {
							TextDocument doc = server.getTextDocumentService().getLatestSnapshot(pr.toUri().toASCIIString());
							if (doc != null) {
								return new Parser.Input(pr, () -> new ByteArrayInputStream(doc.get().getBytes()));
							}
							return null;
						});
				List<SourceFile> sources = projectParser.parse(absoluteProjectDir, new InMemoryExecutionContext(e -> log.error("Project Parsing error:", e)));
				
				return computeWorkspaceEditAwareOfPreview(r, sources, progressTask, askForPreview)
					.thenCompose(we -> applyEdit(we, progressTask, r.getDisplayName()));
			} else {
				return CompletableFuture.failedFuture(new IllegalArgumentException("Cannot find Spring Boot project for uri: " + uri));
			}
		}).whenComplete((o,t) -> progressTask.done());
	}
	
	CompletableFuture<Optional<WorkspaceEdit>> computeWorkspaceEditAwareOfPreview(Recipe r, List<SourceFile> sources, IndefiniteProgressTask progressTask, boolean askForPreview) {
		String changeAnnotationId = UUID.randomUUID().toString();
		Optional<WorkspaceEdit> we = computeWorkspaceEdit(r, sources, progressTask, changeAnnotationId);
		if (we.isPresent()) {
			return askForPreview ? askForPreview(we.get(), changeAnnotationId) : CompletableFuture.completedFuture(we);
		}
		return CompletableFuture.completedFuture(Optional.empty());
	}
	
	private CompletableFuture<Optional<WorkspaceEdit>> askForPreview(WorkspaceEdit workspaceEdit, String changeAnnotationId) {
		return server.getClientCapabilities().thenApply(capabilities -> {
			WorkspaceEditChangeAnnotationSupportCapabilities changeAnnotationSupport = capabilities.getWorkspace().getWorkspaceEdit().getChangeAnnotationSupport();
			return changeAnnotationSupport != null && changeAnnotationSupport.getGroupsOnLabel() != null && changeAnnotationSupport.getGroupsOnLabel().booleanValue();
		}).thenCompose(supportsChangeAnnotation -> {
			if (supportsChangeAnnotation) {
				final MessageActionItem previewChanges = new MessageActionItem("Preview");
				final MessageActionItem applyChanges = new MessageActionItem("Apply");
				ShowMessageRequestParams messageParams = new ShowMessageRequestParams();
				messageParams.setType(MessageType.Info);
				messageParams.setMessage("Do you want to preview changes before applying or apply right away?");
				messageParams.setActions(List.of(applyChanges, previewChanges));
				return server.getClient().showMessageRequest(messageParams).thenApply(ma -> {
					if (previewChanges.equals(ma) ) {
						return Optional.of(true);
					} else if (applyChanges.equals(ma)) {
						return Optional.of(false);
					} else {
						return Optional.<Boolean>empty();
					}
				});
			} else {
				return CompletableFuture.completedFuture(Optional.of(false));
			}
		}).thenApply(optNeedsConfirmation -> optNeedsConfirmation.map(needsConfirmation -> {
				ChangeAnnotation changeAnnotation = workspaceEdit.getChangeAnnotations().get(changeAnnotationId);
				changeAnnotation.setNeedsConfirmation(needsConfirmation);
				return workspaceEdit;
		}));
	}
	
	private CompletableFuture<Object> applyEdit(Optional<WorkspaceEdit> we, IndefiniteProgressTask progressTask, String title) {
		if (we.isPresent()) {
			WorkspaceEdit workspaceEdit = we.get();
			if (progressTask != null) {
				progressTask.progressEvent("Applying document changes...");
			}
			return server.getClient().applyEdit(new ApplyWorkspaceEditParams(workspaceEdit, title)).thenCompose(res -> {
				if (res.isApplied()) {
					return CompletableFuture.completedFuture("success");
				} else {
					return CompletableFuture.completedFuture(null);
				}
			});
		} else {
			return CompletableFuture.completedFuture(null);
		}
	}
	
	Optional<WorkspaceEdit> computeWorkspaceEdit(Recipe r, List<SourceFile> sources, IndefiniteProgressTask progressTask, String changeAnnotationId) {
		reportParseErrors(sources.stream().filter(ParseError.class::isInstance).map(ParseError.class::cast).collect(Collectors.toList()));
		if (progressTask != null) {
			progressTask.progressEvent("Computing changes...");
		}
		RecipeRun reciperun = r.run(new InMemoryLargeSourceSet(sources), new InMemoryExecutionContext(e -> log.error("Recipe execution failed", e)));
		List<Result> results = reciperun.getChangeset().getAllResults();
		return ORDocUtils.createWorkspaceEdit(server.getTextDocumentService(), results, changeAnnotationId).map(we -> {
			ChangeAnnotation changeAnnotation = new ChangeAnnotation(r.getDisplayName());
			we.setChangeAnnotations(Map.of(changeAnnotationId, changeAnnotation));
			return we;
		});
	}
	
	private void reportParseErrors(List<ParseError> parseErrors) {
		if (!parseErrors.isEmpty()) {
			for (ParseError err : parseErrors) {
				ParseExceptionResult parseException = err.getMarkers().findFirst(ParseExceptionResult.class).get();
				if (parseException == null) {
					log.warn("OpenRewrite failed to parse '{}' with unknown error", err.getSourcePath());
				} else {
					log.warn("OpenRewrite parser '{}' failed to parse '{}' with error:\n{}", parseException.getParserType(), err.getSourcePath(), parseException.getMessage());
				}
			}
			server.getMessageService().warning("Failed to parse %d files (See Language Server :\n%s".formatted(parseErrors.size(), parseErrors
					.stream().map(pe -> pe.getSourcePath().toFile().toString()).collect(Collectors.joining("\n"))));
		}
	}
	
    private static ProjectParser createRewriteProjectParser(IJavaProject jp, Function<Path, Parser.Input> inputProvider) {
		switch (jp.getProjectBuild().getType()) {
    	case ProjectBuild.MAVEN_PROJECT_TYPE:
            MavenParser.Builder mavenParserBuilder = MavenParser.builder();
    		return new MavenIJavaProjectParser(jp, JavaParser.fromJavaVersion(), inputProvider, mavenParserBuilder);
    	case ProjectBuild.GRADLE_PROJECT_TYPE:
    		return new GradleIJavaProjectParser(jp, JavaParser.fromJavaVersion(), inputProvider);
    	default:
    		throw new IllegalStateException("The project is neither Maven nor Gradle!");
    	}
    }
    
	public void onRecipesLoaded(Consumer<Void> l) {
		loadListeners.add(l);
	}

	private static String lastTokenAfterDot(String s) {
		int idx = s.lastIndexOf('.');
		if (idx >= 0 && idx < s.length() - 1) {
			return s.substring(idx + 1);
		}
		return s;
	}
	
	record RecipeSelectionDescriptor(boolean selected, String id, RecipeSelectionDescriptor[] subselection) {};
		
}
