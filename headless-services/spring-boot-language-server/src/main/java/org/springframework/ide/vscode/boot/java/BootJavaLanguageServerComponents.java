/*******************************************************************************
 * Copyright (c) 2016, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.app.BootLanguageServerParams;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchyAwareLookup;
import org.springframework.ide.vscode.boot.java.autowired.AutowiredHoverProvider;
import org.springframework.ide.vscode.boot.java.beans.NamedReferencesProvider;
import org.springframework.ide.vscode.boot.java.beans.ProfileReferencesProvider;
import org.springframework.ide.vscode.boot.java.beans.QualifierReferencesProvider;
import org.springframework.ide.vscode.boot.java.conditionals.ConditionalsLiveHoverProvider;
import org.springframework.ide.vscode.boot.java.copilot.CopilotAgentCommandHandler;
import org.springframework.ide.vscode.boot.java.copilot.util.ResponseModifier;
import org.springframework.ide.vscode.boot.java.data.DataRepositoryAotMetadataCodeLensProvider;
import org.springframework.ide.vscode.boot.java.data.DataRepositoryAotMetadataService;
import org.springframework.ide.vscode.boot.java.events.EventReferenceProvider;
import org.springframework.ide.vscode.boot.java.handlers.BootJavaCodeActionProvider;
import org.springframework.ide.vscode.boot.java.handlers.BootJavaCodeLensEngine;
import org.springframework.ide.vscode.boot.java.handlers.BootJavaDocumentHighlightEngine;
import org.springframework.ide.vscode.boot.java.handlers.BootJavaHoverProvider;
import org.springframework.ide.vscode.boot.java.handlers.BootJavaReconcileEngine;
import org.springframework.ide.vscode.boot.java.handlers.BootJavaReferencesHandler;
import org.springframework.ide.vscode.boot.java.handlers.BootJavaWorkspaceSymbolHandler;
import org.springframework.ide.vscode.boot.java.handlers.CodeLensProvider;
import org.springframework.ide.vscode.boot.java.handlers.CopilotCodeLensProvider;
import org.springframework.ide.vscode.boot.java.handlers.HighlightProvider;
import org.springframework.ide.vscode.boot.java.handlers.HoverProvider;
import org.springframework.ide.vscode.boot.java.handlers.ReferenceProvider;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.livehover.ActiveProfilesProvider;
import org.springframework.ide.vscode.boot.java.livehover.BeanInjectedIntoHoverProvider;
import org.springframework.ide.vscode.boot.java.livehover.ComponentInjectionsHoverProvider;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessCommandHandler;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessConnectorLocal;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessConnectorRemote;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessConnectorService;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessLiveDataProvider;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessLiveHoverUpdater;
import org.springframework.ide.vscode.boot.java.requestmapping.LiveAppURLSymbolProvider;
import org.springframework.ide.vscode.boot.java.requestmapping.RequestMappingHoverProvider;
import org.springframework.ide.vscode.boot.java.requestmapping.WebfluxHandlerCodeLensProvider;
import org.springframework.ide.vscode.boot.java.requestmapping.WebfluxRouteHighlightProdivder;
import org.springframework.ide.vscode.boot.java.rewrite.RewriteRefactorings;
import org.springframework.ide.vscode.boot.java.spel.SpelSemanticTokens;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.boot.java.value.ValueHoverProvider;
import org.springframework.ide.vscode.boot.java.value.ValuePropertyReferencesProvider;
import org.springframework.ide.vscode.boot.metadata.SpringPropertyIndexProvider;
import org.springframework.ide.vscode.commons.languageserver.composable.LanguageServerComponents;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IReconcileEngine;
import org.springframework.ide.vscode.commons.languageserver.semantic.tokens.SemanticTokensHandler;
import org.springframework.ide.vscode.commons.languageserver.util.CodeActionHandler;
import org.springframework.ide.vscode.commons.languageserver.util.CodeLensHandler;
import org.springframework.ide.vscode.commons.languageserver.util.DocumentHighlightHandler;
import org.springframework.ide.vscode.commons.languageserver.util.DocumentSymbolHandler;
import org.springframework.ide.vscode.commons.languageserver.util.HoverHandler;
import org.springframework.ide.vscode.commons.languageserver.util.InlayHintHandler;
import org.springframework.ide.vscode.commons.languageserver.util.ReferencesHandler;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleWorkspaceService;
import org.springframework.ide.vscode.commons.util.text.LanguageId;

import com.google.common.collect.ImmutableSet;

/**
 * Language Server for Spring Boot Application Properties files
 *
 * @author Martin Lippert
 */
public class BootJavaLanguageServerComponents implements LanguageServerComponents {

	//TODO: This class is supposed to go away. It is basically a 'collection of beans'.
	// I.e. all the 'components' in here should really become separate beans.

	// So... moving forward...
	// Do not add more components here. You should instead just make your new
	// components into separate beans.

	private static final Logger log = LoggerFactory.getLogger(BootJavaLanguageServerComponents.class);

	public static final Set<LanguageId> LANGUAGES = ImmutableSet.of(LanguageId.JAVA, LanguageId.CLASS);

	private final SimpleLanguageServer server;
	private final BootLanguageServerParams serverParams;

	private final SpringPropertyIndexProvider propertyIndexProvider;

	private final SpringProcessConnectorService liveDataService;

	private final ProjectObserver projectObserver;
	private final CompilationUnitCache cuCache;
	private final ResponseModifier responseModifier;

	private JavaProjectFinder projectFinder;
	private BootJavaHoverProvider hoverProvider;
	private CodeLensHandler codeLensHandler;
	private DocumentHighlightHandler highlightsEngine;
	private BootJavaReconcileEngine reconcileEngine;
	private BootJavaCodeActionProvider codeActionProvider;
	private DocumentSymbolHandler docSymbolProvider;
	private JdtSemanticTokensHandler semanticTokensHandler;
	private JdtInlayHintsHandler inlayHintsHandler;
	private SpelSemanticTokens spelSemanticTokens;
	private DataRepositoryAotMetadataService dataRepositoryAotMetadataService;
	
	public BootJavaLanguageServerComponents(ApplicationContext appContext) {
		this.server = appContext.getBean(SimpleLanguageServer.class);
		this.serverParams = appContext.getBean(BootLanguageServerParams.class);
		this.responseModifier = appContext.getBean(ResponseModifier.class);

		projectFinder = serverParams.projectFinder;
		projectObserver = serverParams.projectObserver;

		propertyIndexProvider = serverParams.indexProvider;

		SimpleWorkspaceService workspaceService = server.getWorkspaceService();
		SimpleTextDocumentService documents = server.getTextDocumentService();

		this.cuCache = appContext.getBean(CompilationUnitCache.class);
		SpringSymbolIndex springSymbolIndex = appContext.getBean(SpringSymbolIndex.class);
		SpringMetamodelIndex springIndex = appContext.getBean(SpringMetamodelIndex.class);
		BootJavaConfig config = appContext.getBean(BootJavaConfig.class);
		SourceLinks sourceLinks = appContext.getBean(SourceLinks.class);

		this.liveDataService = appContext.getBean(SpringProcessConnectorService.class);
		SpringProcessLiveDataProvider liveDataProvider = appContext.getBean(SpringProcessLiveDataProvider.class);
		
		this.reconcileEngine = appContext.getBean(BootJavaReconcileEngine.class);
		this.codeActionProvider = appContext.getBean(BootJavaCodeActionProvider.class);


		ReferencesHandler referencesHandler = createReferenceHandler(server, projectFinder, springIndex, springSymbolIndex, cuCache);
		documents.onReferences(referencesHandler);
		
		//
		// live data component wiring
		//

		// central live data components (to coordinate live data flow)

		// connect the live data provider with the hovers (for data extraction and live updates)
		hoverProvider = createHoverHandler(projectFinder, sourceLinks, liveDataProvider);
		new SpringProcessLiveHoverUpdater(server, hoverProvider, projectFinder, liveDataProvider);

		// deal with locally running processes and their connections
		SpringProcessConnectorLocal liveDataLocalProcessConnector = new SpringProcessConnectorLocal(liveDataService, projectObserver, config);

		// create and handle commands
		new SpringProcessCommandHandler(server, liveDataService, liveDataLocalProcessConnector, appContext.getBeansOfType(SpringProcessConnectorRemote.class).values());
		
		new CopilotAgentCommandHandler(server, projectFinder,responseModifier);

		docSymbolProvider = params -> springSymbolIndex.getDocumentSymbols(params.getTextDocument().getUri());
		
		workspaceService.onWorkspaceSymbol(new BootJavaWorkspaceSymbolHandler(springSymbolIndex,
				new LiveAppURLSymbolProvider(liveDataProvider)));

		spelSemanticTokens = appContext.getBean(SpelSemanticTokens.class);
		dataRepositoryAotMetadataService = appContext.getBean(DataRepositoryAotMetadataService.class);
		codeLensHandler = createCodeLensEngine(springIndex, projectFinder, server, spelSemanticTokens, dataRepositoryAotMetadataService,
				appContext.getBean(RewriteRefactorings.class), config);

		highlightsEngine = createDocumentHighlightEngine(appContext);
		documents.onDocumentHighlight(highlightsEngine);
		
		Map<String, JdtSemanticTokensProvider> jdtSemanticTokensProviders = appContext.getBeansOfType(JdtSemanticTokensProvider.class);
		if (!jdtSemanticTokensProviders.isEmpty()) {
			semanticTokensHandler = new JdtSemanticTokensHandler(cuCache, projectFinder, jdtSemanticTokensProviders.values());
		}
		
		Map<String, JdtInlayHintsProvider> jdtInlayHintsProviders = appContext.getBeansOfType(JdtInlayHintsProvider.class);
		if (!jdtSemanticTokensProviders.isEmpty()) {
			inlayHintsHandler = new JdtInlayHintsHandler(cuCache, projectFinder, jdtInlayHintsProviders.values());
		}
		
		config.addListener(ignore -> {
			log.info("update live process tracker settings - start");
			
			// live information automatic process tracking
			// live information data fetch params
			liveDataService.setMaxRetryCount(config.getLiveInformationFetchDataMaxRetryCount());
			liveDataService.setRetryDelayInSeconds(config.getLiveInformationFetchDataRetryDelayInSeconds());
			
			log.info("update live process tracker settings - done");
		});
		
		server.doOnInitialized(this::initialized);
		server.onShutdown(this::shutdown);
	}

	public SimpleLanguageServer getServer() {
		return server;
	}

	@Override
	public HoverHandler getHoverProvider() {
		return hoverProvider;
	}

	public Optional<CodeLensHandler> getCodeLensHandler() {
		return Optional.of(codeLensHandler);
	}

	public DocumentHighlightHandler getDocumentHighlightHandler() {
		return highlightsEngine;
	}
	
	@Override
	public Optional<IReconcileEngine> getReconcileEngine() {
		return Optional.of(reconcileEngine);
	}

	@Override
	public Optional<DocumentSymbolHandler> getDocumentSymbolProvider() {
		return Optional.of(docSymbolProvider);
	}

	private void initialized() {
	}

	private void shutdown() {
		this.cuCache.dispose();
	}


	protected BootJavaHoverProvider createHoverHandler(JavaProjectFinder javaProjectFinder, SourceLinks sourceLinks,
			SpringProcessLiveDataProvider liveDataProvider) {

		AnnotationHierarchyAwareLookup<HoverProvider> providers = new AnnotationHierarchyAwareLookup<>();

		ValueHoverProvider valueHoverProvider = new ValueHoverProvider();
		RequestMappingHoverProvider requestMappingHoverProvider = new RequestMappingHoverProvider();
		AutowiredHoverProvider autowiredHoverProvider = new AutowiredHoverProvider(sourceLinks);
		ComponentInjectionsHoverProvider componentInjectionsHoverProvider = new ComponentInjectionsHoverProvider(sourceLinks);
		BeanInjectedIntoHoverProvider beanInjectedIntoHoverProvider = new BeanInjectedIntoHoverProvider(sourceLinks);
		ConditionalsLiveHoverProvider conditionalsLiveHoverProvider = new ConditionalsLiveHoverProvider();

		providers.put(Annotations.VALUE, valueHoverProvider);

		providers.put(Annotations.SPRING_REQUEST_MAPPING, requestMappingHoverProvider);
		providers.put(Annotations.SPRING_GET_MAPPING, requestMappingHoverProvider);
		providers.put(Annotations.SPRING_POST_MAPPING, requestMappingHoverProvider);
		providers.put(Annotations.SPRING_PUT_MAPPING, requestMappingHoverProvider);
		providers.put(Annotations.SPRING_DELETE_MAPPING, requestMappingHoverProvider);
		providers.put(Annotations.SPRING_PATCH_MAPPING, requestMappingHoverProvider);
		providers.put(Annotations.PROFILE, new ActiveProfilesProvider());

		providers.put(Annotations.AUTOWIRED, autowiredHoverProvider);
		providers.put(Annotations.INJECT_JAVAX, autowiredHoverProvider);
		providers.put(Annotations.INJECT_JAKARTA, autowiredHoverProvider);

		providers.put(Annotations.COMPONENT, componentInjectionsHoverProvider);
		providers.put(Annotations.BEAN, beanInjectedIntoHoverProvider);

		providers.put(Annotations.CONDITIONAL, conditionalsLiveHoverProvider);
		providers.put(Annotations.CONDITIONAL_ON_BEAN, conditionalsLiveHoverProvider);
		providers.put(Annotations.CONDITIONAL_ON_MISSING_BEAN, conditionalsLiveHoverProvider);
		providers.put(Annotations.CONDITIONAL_ON_PROPERTY, conditionalsLiveHoverProvider);
		providers.put(Annotations.CONDITIONAL_ON_RESOURCE, conditionalsLiveHoverProvider);
		providers.put(Annotations.CONDITIONAL_ON_CLASS, conditionalsLiveHoverProvider);
		providers.put(Annotations.CONDITIONAL_ON_MISSING_CLASS, conditionalsLiveHoverProvider);
		providers.put(Annotations.CONDITIONAL_ON_CLOUD_PLATFORM, conditionalsLiveHoverProvider);
		providers.put(Annotations.CONDITIONAL_ON_WEB_APPLICATION, conditionalsLiveHoverProvider);
		providers.put(Annotations.CONDITIONAL_ON_NOT_WEB_APPLICATION, conditionalsLiveHoverProvider);
		providers.put(Annotations.CONDITIONAL_ON_ENABLED_INFO_CONTRIBUTOR, conditionalsLiveHoverProvider);
		providers.put(Annotations.CONDITIONAL_ON_ENABLED_RESOURCE_CHAIN, conditionalsLiveHoverProvider);
		providers.put(Annotations.CONDITIONAL_ON_ENABLED_ENDPOINT, conditionalsLiveHoverProvider);
		providers.put(Annotations.CONDITIONAL_ON_ENABLED_HEALTH_INDICATOR, conditionalsLiveHoverProvider);
		providers.put(Annotations.CONDITIONAL_ON_EXPRESSION, conditionalsLiveHoverProvider);
		providers.put(Annotations.CONDITIONAL_ON_JAVA, conditionalsLiveHoverProvider);
		providers.put(Annotations.CONDITIONAL_ON_JNDI, conditionalsLiveHoverProvider);
		providers.put(Annotations.CONDITIONAL_ON_SINGLE_CANDIDATE, conditionalsLiveHoverProvider);

		return new BootJavaHoverProvider(this, javaProjectFinder, providers, liveDataProvider);
	}

	protected ReferencesHandler createReferenceHandler(SimpleLanguageServer server, JavaProjectFinder projectFinder,
			SpringMetamodelIndex index, SpringSymbolIndex symbolIndex, CompilationUnitCache cuCache) {
		
		Map<String, ReferenceProvider> specificProviders = new HashMap<>();

		specificProviders.put(Annotations.VALUE, new ValuePropertyReferencesProvider(projectFinder, index));
		specificProviders.put(Annotations.CONDITIONAL_ON_PROPERTY, new ValuePropertyReferencesProvider(projectFinder, index));
		specificProviders.put(Annotations.QUALIFIER, new QualifierReferencesProvider(index));
		specificProviders.put(Annotations.NAMED_JAKARTA, new NamedReferencesProvider(index));
		specificProviders.put(Annotations.NAMED_JAVAX, new NamedReferencesProvider(index));
		specificProviders.put(Annotations.PROFILE, new ProfileReferencesProvider(index));
		
		List<ReferenceProvider> unspecificProviders = new ArrayList<>();
		unspecificProviders.add(new EventReferenceProvider(index));

		return new BootJavaReferencesHandler(this, cuCache, projectFinder, specificProviders, unspecificProviders);
	}

	protected BootJavaCodeLensEngine createCodeLensEngine(SpringMetamodelIndex springIndex, JavaProjectFinder projectFinder, SimpleLanguageServer server,
			SpelSemanticTokens spelSemanticTokens, DataRepositoryAotMetadataService repositoryAotMetadataService, RewriteRefactorings refactorings,
			BootJavaConfig config) {

		Collection<CodeLensProvider> codeLensProvider = new ArrayList<>();
		codeLensProvider.add(new WebfluxHandlerCodeLensProvider(springIndex));
		codeLensProvider.add(new CopilotCodeLensProvider(projectFinder, server, spelSemanticTokens));
		codeLensProvider.add(new DataRepositoryAotMetadataCodeLensProvider(projectFinder, repositoryAotMetadataService, refactorings, config));

		return new BootJavaCodeLensEngine(this, codeLensProvider);
	}

	protected BootJavaDocumentHighlightEngine createDocumentHighlightEngine(ApplicationContext appContext) {
		Collection<HighlightProvider> highlightProvider = new ArrayList<>();
		highlightProvider.add(new WebfluxRouteHighlightProdivder(appContext.getBean(SpringMetamodelIndex.class)));
		
		Map<String, JdtAstDocHighlightsProvider> astHighlightProviders = appContext.getBeansOfType(JdtAstDocHighlightsProvider.class);
		if (!astHighlightProviders.isEmpty()) {
			highlightProvider.add(new JdtDocHighlightsProvider(projectFinder, cuCache, astHighlightProviders.values()));
		}

		return new BootJavaDocumentHighlightEngine(this, highlightProvider);
	}

	public ProjectObserver getProjectObserver() {
		return projectObserver;
	}

	public JavaProjectFinder getProjectFinder() {
		return projectFinder;
	}

	public SpringPropertyIndexProvider getSpringPropertyIndexProvider() {
		return propertyIndexProvider;
	}

	public CompilationUnitCache getCompilationUnitCache() {
		return cuCache;
	}

	public SimpleTextDocumentService getTextDocumentService() {
		return server.getTextDocumentService();
	}

	public BootLanguageServerParams getServerParams() {
		return this.serverParams;
	}

	@Override
	public Set<LanguageId> getInterestingLanguages() {
		return LANGUAGES;
	}

	@Override
	public Optional<CodeActionHandler> getCodeActionProvider() {
		return Optional.ofNullable(codeActionProvider);
	}

	@Override
	public Optional<SemanticTokensHandler> getSemanticTokensHandler() {
		return Optional.ofNullable(semanticTokensHandler);
	}

	@Override
	public Optional<InlayHintHandler> getInlayHintHandler() {
		return Optional.ofNullable(inlayHintsHandler);
	}

	
}
