/*******************************************************************************
 * Copyright (c) 2022 VMware, Inc.
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
import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.RecipeRun;
import org.openrewrite.Result;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.Validated;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.config.RecipeDescriptor;
import org.openrewrite.config.YamlResourceLoader;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J.CompilationUnit;
import org.openrewrite.maven.MavenParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.ListenerList;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.rewrite.LoadUtils;
import org.springframework.ide.vscode.commons.rewrite.LoadUtils.DurationTypeConverter;
import org.springframework.ide.vscode.commons.rewrite.ORDocUtils;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeCodeActionDescriptor;
import org.springframework.ide.vscode.commons.rewrite.config.StsEnvironment;
import org.springframework.ide.vscode.commons.rewrite.maven.MavenProjectParser;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

public class RewriteRecipeRepository implements ApplicationContextAware {
		
	private static final String CMD_REWRITE_RELOAD = "sts/rewrite/reload";
	private static final String CMD_REWRITE_EXECUTE = "sts/rewrite/execute";
	private static final String CMD_REWRITE_LIST = "sts/rewrite/list";
	private static final Logger log = LoggerFactory.getLogger(RewriteRecipeRepository.class);
	private static final String WORKSPACE_EXECUTE_COMMAND = "workspace/executeCommand";
	
	final private SimpleLanguageServer server;
	
	final private Map<String, Recipe> recipes;
	
	final private List<Recipe> globalCommandRecipes;

	final private JavaProjectFinder projectFinder;
	
	final private List<RecipeCodeActionDescriptor> codeActionDescriptors;
	
	final private ListenerList<Void> loadListeners;
	
	private ApplicationContext applicationContext;
	
	private CompletableFuture<Void> loaded;
	
	private Set<String> scanFiles = Collections.emptySet();
	private Set<String> scanDirs = Collections.emptySet();
	private Set<String> recipeFilters = Collections.emptySet();
		
	private static Gson serializationGson = new GsonBuilder()
			.registerTypeAdapter(Duration.class, new DurationTypeConverter())
			.create();
	
	public RewriteRecipeRepository(SimpleLanguageServer server, JavaProjectFinder projectFinder, BootJavaConfig config) {
		this.server = server;
		this.projectFinder = projectFinder;
		this.recipes = new HashMap<>();
		this.globalCommandRecipes = new ArrayList<>();
		this.codeActionDescriptors = new ArrayList<>();
		this.loadListeners = new ListenerList<>();
		
		server.doOnInitialized(() -> {
			this.scanDirs = config.getRecipeDirectories();
			this.scanFiles = config.getRecipeFiles();
			this.recipeFilters = config.getRecipesFilters();
			load().thenAccept(v -> registerCommands());
			config.addListener(l -> {
				if (!scanDirs.equals(config.getRecipeDirectories())
						|| !scanFiles.equals(config.getRecipeFiles())) {
					// Eclipse client sends one event for init and the other config changed event due to remote app value expr listener.
					// Therefore it is best to store the scanDirs here right after it is received, not during scan process or anything else done async
					scanDirs = config.getRecipeDirectories();
					scanFiles = config.getRecipeFiles();
					load();
				}
				Set<String> recipeFilterFromConfig = config.getRecipesFilters();
				if (!recipeFilters.equals(recipeFilterFromConfig)) {
					recipeFilters = recipeFilterFromConfig;
					updateGlobalCommandRecipes();
				}
			});
		});
	}
	
	public CompletableFuture<Void> load() {
		return this.loaded = CompletableFuture.runAsync(() -> {
			clearRecipes();
			loadRecipes();
			loadListeners.fire(null);
		});
	}
	
	private synchronized void clearRecipes() {
		recipes.clear();
		globalCommandRecipes.clear();
		codeActionDescriptors.clear();
	}
	
	private synchronized void loadRecipes() {
		String taskId = UUID.randomUUID().toString();
		try {
			server.getProgressService().progressBegin(taskId, "Loading Rewrite Recipes", null);
			log.info("Loading Rewrite Recipes...");
			StsEnvironment env = createRewriteEnvironment();
			for (Recipe r : env.listRecipes()) {
				if (r.getName() != null) {
					if (recipes.containsKey(r.getName())) {
						log.error("Duplicate ids: '" + r.getName() + "'");
					}
					recipes.put(r.getName(), r);
					
					if (isAcceptableGlobalCommandRecipe(r)) {
						globalCommandRecipes.add(r);
					}					
				}
			}
			codeActionDescriptors.addAll(env.listCodeActionDescriptors());
			log.info("Done loading Rewrite Recipes");
		} catch (Throwable t) {
			log.error("", t);
		} finally {
			server.getProgressService().progressDone(taskId);
		}
	}
	
	private void updateGlobalCommandRecipes() {
		globalCommandRecipes.clear();
		for (Recipe r : recipes.values()) {
			if (isAcceptableGlobalCommandRecipe(r)) {
				globalCommandRecipes.add(r);
			}
		}
	}
	
	private boolean isAcceptableGlobalCommandRecipe(Recipe r) {
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
	
	private static boolean isRecipeValid(Recipe r) {
		Validated validation = Validated.invalid(null, null, null);
		try {
			validation = r.validate();
		} catch (Exception e) {
			// ignore
		}
		return validation.isValid();
	}
	
	private StsEnvironment createRewriteEnvironment() {
		StsEnvironment.Builder builder = (StsEnvironment.Builder) StsEnvironment.builder().scanRuntimeClasspath();
		for (String p : scanFiles) {
			try {
				Path f = Path.of(p);
				String pathStr = f.toString();
				if (pathStr.endsWith(".jar")) {
					URLClassLoader classLoader = new URLClassLoader(new URL[] { f.toUri().toURL() },
							getClass().getClassLoader());
					builder.scanJar(f, Collections.emptyList(), classLoader);
				} else if (pathStr.endsWith(".yml") || pathStr.endsWith(".yaml")) {
					builder.load(new YamlResourceLoader(new FileInputStream(f.toFile()), f.toUri(), new Properties()));
				}
			} catch (Exception e) {
				log.error("Skipping folder " + p, e);
			}
		}
		for (String p : scanDirs) {
			try {
				Path d = Path.of(p);
				if (Files.isDirectory(d)) {
					URLClassLoader classLoader = new URLClassLoader(new URL[] { d.toUri().toURL()}, getClass().getClassLoader());
					builder.scanPath(d, Collections.emptyList(), classLoader);
				}
			} catch (Exception e) {
				log.error("Skipping folder " + p, e);
			}
		}
		return (StsEnvironment) builder.build();
	}
	
	public Optional<Recipe> getRecipe(String name) {
		return Optional.ofNullable(recipes.get(name));
	}
	
	public RecipeCodeActionDescriptor getCodeActionRecipeDescriptor(String id) {
		for (RecipeCodeActionDescriptor d : codeActionDescriptors) {
			if (id.equals(d.getId())) {
				return d;
			}
		}
		return null;
	}
	
	public List<RecipeCodeActionDescriptor> getProblemRecipeDescriptors() {
		List<RecipeCodeActionDescriptor> l = new ArrayList<>(codeActionDescriptors.size());
		for (RecipeCodeActionDescriptor d : codeActionDescriptors) {
			if (d.getProblemType() != null && server.getDiagnosticSeverityProvider().getDiagnosticSeverity(d.getProblemType()) != null) {
				l.add(d);
			}
		}
		return l;
	}
	
	public List<RecipeCodeActionDescriptor> getCodeActionRecipeDescriptors() {
		List<RecipeCodeActionDescriptor> l = new ArrayList<>(codeActionDescriptors.size());
		for (RecipeCodeActionDescriptor d : codeActionDescriptors) {
			if (d.getProblemType() == null || server.getDiagnosticSeverityProvider().getDiagnosticSeverity(d.getProblemType()) == null) {
				l.add(d);
			}
		}
		return l;
	}
	
	public List<RecipeCodeActionDescriptor> getApplicableCodeActionRecipeDescriptors(IJavaProject project, List<RecipeCodeActionDescriptor> descriptors) {
		List<RecipeCodeActionDescriptor> filtered = new ArrayList<>(descriptors.size());
		for (RecipeCodeActionDescriptor d : descriptors) {
			if (d.isApplicable(project)) {
				filtered.add(d);
			}
		}
		return filtered;
	}
	
	public CompilationUnit mark(List<? extends RecipeCodeActionDescriptor> descriptors, CompilationUnit compilationUnit) {
		CompilationUnit cu = compilationUnit;
		for (RecipeCodeActionDescriptor d : descriptors) {
			TreeVisitor<?, ExecutionContext> markVisitor = d.getMarkerVisitor(applicationContext);
			if (markVisitor != null) {
				cu = (CompilationUnit) markVisitor.visit(cu, new InMemoryExecutionContext(e -> log.error("", e)));
			}
		}
		return cu;
	}
	
	private static JsonElement recipeToJson(Recipe r) {
		JsonElement jsonElement = serializationGson.toJsonTree(r.getDescriptor());
		return jsonElement;
	}
	
	private void registerCommands() {
		log.info("Registering commands for rewrite recipes...");
		
		Builder<Object> listBuilder = ImmutableList.builder();
		
		server.onCommand(CMD_REWRITE_LIST, params -> {
			JsonElement uri = (JsonElement) params.getArguments().get(0);
			return loaded.thenApply(v -> {
				if (uri == null) {
					return Collections.emptyList();
				} else {
					return listProjectRefactoringRecipes(uri.getAsString()).stream().map(RewriteRecipeRepository::recipeToJson).collect(Collectors.toList());
				}
			});
		});
		listBuilder.add(CMD_REWRITE_LIST);
		
		server.onCommand(CMD_REWRITE_EXECUTE, params -> {
			String uri = ((JsonElement) params.getArguments().get(0)).getAsString();
			JsonElement recipesJson = ((JsonElement) params.getArguments().get(1));
			
			RecipeDescriptor d = serializationGson.fromJson(recipesJson, RecipeDescriptor.class);
			
			Recipe aggregateRecipe = LoadUtils.createRecipe(d, id -> getRecipe(id).map(r -> r.getClass()).orElse(null));
			
			if (aggregateRecipe instanceof DeclarativeRecipe && aggregateRecipe.getRecipeList().isEmpty()) {
				throw new RuntimeException("No recipes found to perform!");
			} else if (aggregateRecipe.getRecipeList().size() == 1) {
				Recipe r = aggregateRecipe.getRecipeList().get(0);
				String progressToken = params.getWorkDoneToken() == null || params.getWorkDoneToken().getLeft() == null ? r.getName() : params.getWorkDoneToken().getLeft();
				return apply(r, uri, progressToken);
			} else {
				String progressToken = params.getWorkDoneToken() == null || params.getWorkDoneToken().getLeft() == null ? aggregateRecipe.getName() : params.getWorkDoneToken().getLeft();
				return apply(aggregateRecipe, uri, progressToken);
			}
		});
		listBuilder.add(CMD_REWRITE_EXECUTE);
		
		server.onCommand(CMD_REWRITE_RELOAD, params -> load().thenApply((v) -> "executed"));
		listBuilder.add(CMD_REWRITE_RELOAD);
		
		for (Recipe r : globalCommandRecipes) {
			listBuilder.add(createGlobalCommand(r));
		}
		
		String registrationId = UUID.randomUUID().toString();
		RegistrationParams params = new RegistrationParams(ImmutableList.of(
				new Registration(registrationId,
						WORKSPACE_EXECUTE_COMMAND,
						ImmutableMap.of("commands", listBuilder.build())
				)
		));
				
		server.getClient().registerCapability(params).thenAccept((v) -> {
			server.onShutdown(() -> server.getClient().unregisterCapability(new UnregistrationParams(List.of(new Unregistration(registrationId, WORKSPACE_EXECUTE_COMMAND)))));			
			log.info("Done registering commands for rewrite recipes");
		});
		
	}
	
	private String createGlobalCommand(Recipe r) {
		String commandId = "sts/rewrite/recipe/" + r.getName();
		server.onCommand(commandId, params -> {
			final String progressToken = params.getWorkDoneToken() == null || params.getWorkDoneToken().getLeft() == null ? r.getName() : params.getWorkDoneToken().getLeft();
			String uri = ((JsonElement) params.getArguments().get(0)).getAsString();
			return apply(r, uri, progressToken);	
		});
		return commandId;
	}
	
	private CompletableFuture<Object> apply(Recipe r, String uri, String progressToken) {
		return CompletableFuture.supplyAsync(() -> {
			server.getProgressService().progressBegin(progressToken, r.getDisplayName(), "Initiated...");
			return projectFinder.find(new TextDocumentIdentifier(uri));
		}).thenCompose(p -> {
			if (p.isPresent()) {
				try {
					Optional<WorkspaceEdit> edit = apply(r, p.get());
					return CompletableFuture.completedFuture(edit).thenCompose(we -> {
						if (we.isPresent()) {
							server.getProgressService().progressEvent(progressToken, "Applying document changes...");
							return server.getClient().applyEdit(new ApplyWorkspaceEditParams(we.get(), r.getDisplayName())).thenCompose(res -> {
								if (res.isApplied()) {
									server.getProgressService().progressDone(progressToken);
									return CompletableFuture.completedFuture("success");
								} else {
									server.getProgressService().progressDone(progressToken);
									return CompletableFuture.completedFuture(null);
								}
							});
						} else {
							server.getProgressService().progressDone(progressToken);
							return CompletableFuture.completedFuture(null);
						}
					});
				} catch (Throwable t) {
					server.getProgressService().progressDone(progressToken);
					throw t;
				}
			}
			return CompletableFuture.completedFuture(null);
		});
	}
	
	private Optional<WorkspaceEdit> apply(Recipe r, IJavaProject project) {
		Path absoluteProjectDir = Paths.get(project.getLocationUri());
		server.getProgressService().progressEvent(r.getName(), "Parsing files...");
		MavenProjectParser projectParser = createRewriteMavenParser(absoluteProjectDir,
				new InMemoryExecutionContext(), p -> {
					TextDocument doc = server.getTextDocumentService().getLatestSnapshot(p.toUri().toString());
					if (doc != null) {
						return new Parser.Input(p, () -> new ByteArrayInputStream(doc.get().getBytes()));
					}
					return null;
				});
		List<SourceFile> sources = projectParser.parse(absoluteProjectDir, getClasspathEntries(project));
		server.getProgressService().progressEvent(r.getName(), "Computing changes...");
		RecipeRun reciperun = r.run(sources, new InMemoryExecutionContext(e -> log.error("", e)));
		List<Result> results = reciperun.getResults();
		return ORDocUtils.createWorkspaceEdit(absoluteProjectDir, server.getTextDocumentService(), results);
	}
	
	private List<Recipe> listProjectRefactoringRecipes(String uri) {
		if (uri != null) {
			Optional<IJavaProject> projectOpt = projectFinder.find(new TextDocumentIdentifier(uri));
			if (projectOpt.isPresent()) {
				List<Recipe> commandDescriptors = new ArrayList<>(globalCommandRecipes.size()); 
				for (Recipe r : globalCommandRecipes) {
					commandDescriptors.add(r);
				}
				return commandDescriptors;
			}
		}
		return Collections.emptyList();
	}
	
    private static MavenProjectParser createRewriteMavenParser(Path absoluteProjectDir, ExecutionContext context, Function<Path, Parser.Input> inputProvider) {
        MavenParser.Builder mavenParserBuilder = MavenParser.builder()
                .mavenConfig(absoluteProjectDir.resolve(".mvn/maven.config"));

        MavenProjectParser mavenProjectParser = new MavenProjectParser(
                mavenParserBuilder,
                JavaParser.fromJavaVersion(),
                context,
                inputProvider
        );
        return mavenProjectParser;
    }
    
	private static List<Path> getClasspathEntries(IJavaProject project) {
		if (project == null) {
			return List.of();
		} else {
			IClasspath classpath = project.getClasspath();
			Stream<File> classpathEntries = IClasspathUtil.getAllBinaryRoots(classpath).stream();
			return classpathEntries
					.filter(file -> file.exists())
					.filter(file -> file.getName().endsWith(".jar"))
					.map(file -> file.getAbsoluteFile().toPath()).collect(Collectors.toList());
		}
	}
	
	public void onRecipesLoaded(Consumer<Void> l) {
		loadListeners.add(l);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
	
//	private static Recipe convert(Recipe r, RecipeDescriptor d) {
//		try {
//			if (d.selected) {
//				if (d.children != null && !d.children.isEmpty()) {
//					Recipe recipe = r instanceof DeclarativeRecipe ? new DeclarativeRecipe(r.getName(), r.getDisplayName(), r.getDescription(), r.getTags(), r.getEstimatedEffortPerOccurrence(), null)
//							: r.getClass().getDeclaredConstructor().newInstance();
//					int i = 0;
//					for (Recipe sr : r.getRecipeList()) {
//						Recipe convertedSubRecipe = convert(sr, d.children.get(i++));
//						if (convertedSubRecipe != null) {
//							recipe.doNext(convertedSubRecipe);
//						}
//					}
//					return recipe;
//				} else {
//					return r;
//				}
//			}
//		} catch (Exception e) {
//			log.error("", e);
//		}
//		return null;
//	}

//	@SuppressWarnings("unused")
//	private static class RecipeDescriptor {
//		String id;
//		String label;
//		String detail;
//		List<RecipeDescriptor> children;
//		boolean selected;
//		
//		RecipeDescriptor(Recipe r) {
//			this.id = r.getName();
//			this.label = r.getDisplayName();
//			this.detail = r.getDescription();
//			List<Recipe> subRecipes = r.getRecipeList();
//			if (r instanceof DeclarativeRecipe && !subRecipes.isEmpty() && (subRecipes.size() > 1 || subRecipes.get(0) instanceof DeclarativeRecipe)) {
//				this.children = r.getRecipeList().stream().map(sr -> new RecipeDescriptor(sr)).collect(Collectors.toList());
//			}
//		}
//	}
	
}
