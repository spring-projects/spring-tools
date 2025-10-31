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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
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
import org.openrewrite.config.Environment;
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
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;
import org.springframework.ide.vscode.commons.rewrite.ORDocUtils;
import org.springframework.ide.vscode.commons.rewrite.gradle.GradleIJavaProjectParser;
import org.springframework.ide.vscode.commons.rewrite.java.ProjectParser;
import org.springframework.ide.vscode.commons.rewrite.maven.MavenIJavaProjectParser;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

public class RewriteRecipeRepository {
	
	private static final Logger log = LoggerFactory.getLogger(RewriteRecipeRepository.class);
	
	final private SimpleLanguageServer server;
	
	final private JavaProjectFinder projectFinder;
	
	private CompletableFuture<Map<String, Recipe>> recipesFuture = null;
	
	public RewriteRecipeRepository(SimpleLanguageServer server, JavaProjectFinder projectFinder, BootJavaConfig config) {
		this.server = server;
		this.projectFinder = projectFinder;
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
	
	private Environment createRewriteEnvironment() {
		Environment.Builder builder = Environment.builder().scanRuntimeClasspath();
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
	
	@SuppressWarnings("unchecked")
	CompletableFuture<Object> applyToBuildFiles(Recipe r, String uri, String progressToken, boolean askForPreview) {
		return projectFinder.find(new TextDocumentIdentifier(uri)).map(p -> {
			final IndefiniteProgressTask progressTask = server.getProgressService().createIndefiniteProgressTask(progressToken, r.getDisplayName(), "Initiated...");
			final IJavaProject project = p;
			return CompletableFuture.supplyAsync(() -> {
				Path absoluteProjectDir = Paths.get(project.getLocationUri());
				progressTask.progressEvent("Parsing files...");
				ProjectParser projectParser = getProjectParser(project);
				return (List<SourceFile>) projectParser.parseBuildFiles(absoluteProjectDir, new InMemoryExecutionContext(e -> log.error("Project Parsing error:", e)));
			})
			.thenCompose(sources -> computeWorkspaceEditAwareOfPreview(r, sources, progressTask, askForPreview))
			.thenCompose(we -> applyEdit(we, progressTask, r.getDisplayName()))
			.whenComplete((o,t) -> progressTask.done());
		}).orElse(CompletableFuture.failedFuture(new IllegalArgumentException("Cannot find Spring Boot project for uri: " + uri)));
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
				ProjectParser projectParser = getProjectParser(project);
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
    
    private ProjectParser getProjectParser(IJavaProject jp) {
    	return createRewriteProjectParser(jp,
				pr -> {
					TextDocument doc = server.getTextDocumentService().getLatestSnapshot(pr.toUri().toASCIIString());
					if (doc != null) {
						return new Parser.Input(pr, () -> new ByteArrayInputStream(doc.get().getBytes()));
					}
					return null;
				});
    }
    
}
