/*******************************************************************************
 * Copyright (c) 2018, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.app;

import java.util.Collections;
import java.util.List;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.ide.vscode.boot.factories.SpringFactoriesLanguageServerComponents;
import org.springframework.ide.vscode.boot.index.cache.IndexCache;
import org.springframework.ide.vscode.boot.java.BootJavaLanguageServerComponents;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.HqlSemanticTokens;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.JpaQueryPropertiesLanguageServerComponents;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.JpqlSemanticTokens;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.JpqlSupportState;
import org.springframework.ide.vscode.boot.java.handlers.Reconciler;
import org.springframework.ide.vscode.boot.java.links.JavaElementLocationProvider;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessLiveDataProvider;
import org.springframework.ide.vscode.boot.java.spel.SpelReconciler;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.boot.maven.PomLanguageServerComponents;
import org.springframework.ide.vscode.boot.metadata.ProjectBasedPropertyIndexProvider;
import org.springframework.ide.vscode.boot.properties.BootPropertiesLanguageServerComponents;
import org.springframework.ide.vscode.boot.validation.generations.SpringProjectsProvider;
import org.springframework.ide.vscode.boot.xml.SpringXMLLanguageServerComponents;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.completion.CompositeCompletionEngine;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionEngine;
import org.springframework.ide.vscode.commons.languageserver.completion.VscodeCompletionEngineAdapter;
import org.springframework.ide.vscode.commons.languageserver.composable.CompositeLanguageServerComponents;
import org.springframework.ide.vscode.commons.languageserver.composable.LanguageServerComponents;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.languageserver.util.HoverHandler;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.commons.yaml.ast.YamlASTProvider;
import org.springframework.ide.vscode.commons.yaml.completion.YamlAssistContextProvider;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

@Component
public class BootLanguageServerInitializer implements InitializingBean {
	
	public static final String CMD_SHOW_DOC = "sts/show/document";
	
	@Autowired SimpleLanguageServer server;
	@Autowired BootLanguageServerParams params;
	@Autowired SourceLinks sourceLinks;
	@Autowired CompilationUnitCache cuCache;
	@Autowired JavaElementLocationProvider javaElementLocationProvider;
	@Autowired YamlASTProvider parser;
	@Autowired YamlStructureProvider yamlStructureProvider;
	@Autowired YamlAssistContextProvider yamlAssistContextProvider;
	@Autowired IndexCache symbolCache;
	@Autowired SpringProcessLiveDataProvider liveDataProvider;
	@Autowired ApplicationContext appContext;
	@Autowired BootJavaConfig config;
	@Autowired SpringSymbolIndex springIndexer;
	@Autowired(required = false) List<ICompletionEngine> completionEngines;
	@Autowired private JavaProjectFinder projectFinder;

	@Qualifier("adHocProperties") @Autowired ProjectBasedPropertyIndexProvider adHocProperties;

	private CompositeLanguageServerComponents components;
	private VscodeCompletionEngineAdapter completionEngineAdapter;
	
	private static final Logger log = LoggerFactory.getLogger(BootLanguageServerInitializer.class);
	
	private ProjectObserver.Listener reconcileDocumentsForProjectChange(SimpleLanguageServer s, CompositeLanguageServerComponents c, JavaProjectFinder projectFinder) {
		return new ProjectObserver.Listener() {
			
			@Override
			public void deleted(IJavaProject project) {
			}
			
			@Override
			public void created(IJavaProject project) {
				validateAll(c, s, project);
			}
			
			@Override
			public void changed(IJavaProject project) {
				validateAll(c, s, project);
			}
			
		};
	}
	
	private void validateAll(CompositeLanguageServerComponents c, SimpleLanguageServer s, IJavaProject project) {
		c.getReconcileEngine().ifPresent(reconciler -> {
			log.debug("A project changed {}, triggering reconcile on all project's open documents",
					project.getElementName());
			for (TextDocument doc : s.getTextDocumentService().getAll()) {
				if (projectFinder.find(doc.getId()).orElse(null) == project) {
					s.validateWith(doc.getId(), reconciler);
				}
			}
		});
	}
	@Override
	public void afterPropertiesSet() throws Exception {
		//TODO: CompositeLanguageServerComponents object instance serves no purpose anymore. The constructor really just contains
		// some server intialization code. Migrate that code and get rid of the ComposableLanguageServer class
		CompositeLanguageServerComponents.Builder builder = new CompositeLanguageServerComponents.Builder();
		List<LanguageServerComponents> componentsList = List.of(
			new BootPropertiesLanguageServerComponents(server, params, javaElementLocationProvider, parser, yamlStructureProvider, yamlAssistContextProvider, sourceLinks),
			new BootJavaLanguageServerComponents(appContext),
			new SpringXMLLanguageServerComponents(server, springIndexer, params, config, appContext.getBean(SpelReconciler.class)),
			new SpringFactoriesLanguageServerComponents(projectFinder, springIndexer, config),
			new PomLanguageServerComponents(server, projectFinder, params.projectObserver, appContext.getBean(SpringProjectsProvider.class)),
			new JpaQueryPropertiesLanguageServerComponents(server.getTextDocumentService(), projectFinder, appContext.getBean(JpqlSemanticTokens.class),
					appContext.getBean(HqlSemanticTokens.class), appContext.getBean(JpqlSupportState.class), (Reconciler) appContext.getBean("hqlReconciler"), (Reconciler) appContext.getBean("jpqlReconciler"))
		);
		
		for (LanguageServerComponents c : componentsList) {
			builder.add(c);	
		}
		
		components = builder.build(appContext);
		
		final SimpleTextDocumentService documents = server.getTextDocumentService();

		if (!completionEngines.isEmpty()) {
			CompositeCompletionEngine compositeCompletionEngine = new CompositeCompletionEngine();
			completionEngines.forEach(compositeCompletionEngine::add);
			completionEngineAdapter = server.createCompletionEngineAdapter(compositeCompletionEngine);
			completionEngineAdapter.setMaxCompletions(-1);
			documents.onCompletion(completionEngineAdapter::getCompletions);
			documents.onCompletionResolve(completionEngineAdapter::resolveCompletion);
		}

		HoverHandler hoverHandler = components.getHoverProvider();
		documents.onHover(hoverHandler);
		
		components.getCodeActionProvider().ifPresent(documents::onCodeAction);
		
		components.getCodeLensHandler().ifPresent(documents::onCodeLens);
		
		components.getDocumentSymbolProvider().ifPresent(documents::onDocumentSymbol);
		
		components.getInlayHintHandler().ifPresent(documents::onInlayHint);
		
		components.getSemanticTokensHandler().ifPresent(documents::onSemanticTokens);

		startListening();

		server.onCommand(CMD_SHOW_DOC, p -> {
			ShowDocumentParams showDocParams = new Gson().fromJson((JsonElement)p.getArguments().get(0), ShowDocumentParams.class);
			return server.getClient().showDocument(showDocParams).thenApply(r -> {
				if (!r.isSuccess()) {
					MessageParams messageParams = new MessageParams(MessageType.Error, "Failed to open: " + showDocParams.getUri());
					server.getClient().showMessage(messageParams);
				}
				return null;
			});
		});

		server.onShutdown(() -> {
			for (TextDocument d : documents.getAll()) {
				documents.publishDiagnostics(d.getId(), Collections.emptyList());
			}
		});
		
	}
	
	private void startListening() {
		components.getReconcileEngine().ifPresent(reconcileEngine -> {
			server.getTextDocumentService().onDidChangeContent(params -> {
				TextDocument doc = params.getDocument();
				server.validateWith(doc.getId(), reconcileEngine);
			});
			server.getTextDocumentService().onDidClose(doc -> {
				if (doc.hasChangedSinceLastSave()) {
					/*
					 *  If doc is changed since last save closing it would ignore the latest changes.
					 *  Therefore the file requires to be validated again. 
					 */
					server.validateWith(doc.getId(), reconcileEngine);
				}
				
			});
//			ServerUtils.listenToClassFileChanges(server.getWorkspaceService().getFileObserver(), projectFinder, project -> validateAll(components, server, project));
		});
		config.addListener(evt -> {
			server.getClient().refreshInlayHints();
			reconcile();
		});
		params.projectObserver.addListener(reconcileDocumentsForProjectChange(server, components, params.projectFinder));
		
//		// TODO: index update even happens on every file save. Very expensive to blindly reconcile all projects.
//		// Need to figure out a check if spring index has any changes 
////		springIndexer.onUpdate(v -> reconcile());
	}
	
	private void reconcile() {
		components.getReconcileEngine().ifPresent(reconciler -> {
			log.info("Triggering reconcile on all open documents");
			for (TextDocument doc : server.getTextDocumentService().getAll()) {
				server.validateWith(doc.getId(), reconciler);
			}
		});
	}

	public CompositeLanguageServerComponents getComponents() {
		Assert.notNull(components, "Not yet initialized, can't get components yet.");
		return components;
	}

	public void setMaxCompletions(int number) {
		if (completionEngineAdapter!=null) {
			completionEngineAdapter.setMaxCompletions(number);
		}
	}
	
}
