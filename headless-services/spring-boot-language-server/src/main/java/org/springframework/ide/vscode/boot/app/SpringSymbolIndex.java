/*******************************************************************************
 * Copyright (c) 2017, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.app;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolLocation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ide.vscode.boot.index.SpringIndexToSymbolsConverter;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.index.cache.IndexCache;
import org.springframework.ide.vscode.boot.java.BootJavaLanguageServerComponents;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchyAwareLookup;
import org.springframework.ide.vscode.boot.java.handlers.SymbolProvider;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtReconciler;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.boot.java.utils.DocumentDescriptor;
import org.springframework.ide.vscode.boot.java.utils.SpringFactoriesIndexer;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexer;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJava;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerXML;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerXMLNamespaceHandler;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerXMLNamespaceHandlerBeans;
import org.springframework.ide.vscode.boot.java.utils.SymbolHandler;
import org.springframework.ide.vscode.boot.java.utils.SymbolIndexConfig;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.java.FutureProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver.Listener;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.util.ListenerList;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleWorkspaceService;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.BeansParams;
import org.springframework.ide.vscode.commons.protocol.spring.DocumentElement;
import org.springframework.ide.vscode.commons.protocol.spring.MatchingBeansParams;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndex;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.util.Futures;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.UriUtil;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableList;

/**
 * @author Martin Lippert
 */
@Component
public class SpringSymbolIndex implements InitializingBean, SpringIndex {

	private static final String QUERY_PARAM_LOCATION_PREFIX = "locationPrefix:";
	private static final String OUTLINE_SYMBOLS_FROM_INDEX_PROPERTY = "outlineSymbolsFromIndex";
	
	@Autowired SimpleLanguageServer server;
	@Autowired BootJavaConfig config;
	@Autowired BootLanguageServerParams params;
	@Autowired AnnotationHierarchyAwareLookup<SymbolProvider> specificProviders;
	@Autowired IndexCache cache;
	@Autowired FutureProjectFinder futureProjectFinder;
	@Autowired SpringMetamodelIndex springIndex;
	@Autowired JdtReconciler jdtReconciler;
	@Autowired CompilationUnitCache cuCache;

	private final List<WorkspaceSymbol> symbols = new ArrayList<>();

	private final ConcurrentMap<String, List<WorkspaceSymbol>> symbolsByDoc = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, List<WorkspaceSymbol>> symbolsByProject = new ConcurrentHashMap<>();

	private final ExecutorService updateQueue = Executors.newSingleThreadExecutor();
	private final Map<String, CompletableFuture<Void>> latestScheduledTaskByProject = new ConcurrentHashMap<String, CompletableFuture<Void>>();
	
	private SpringIndexer[] indexers;
	private ListenerList<Void> listeners = new ListenerList<Void>();
	
	private static final Logger log = LoggerFactory.getLogger(SpringSymbolIndex.class);

	private final Listener projectListener = new Listener() {
		@Override
		public void created(IJavaProject project) {
			log.info("project created event: {}", project.getElementName());
			initializeProject(project, false);
		}

		@Override
		public void changed(IJavaProject project) {
			log.info("project changed event: {}", project.getElementName());
			initializeProject(project, false);
		}

		@Override
		public void deleted(IJavaProject project) {
			log.info("project deleted event: {}", project.getElementName());
			deleteProject(project);
		}
	};

	private SpringIndexerXML springIndexerXML;
	private SpringIndexerJava springIndexerJava;
	private SpringFactoriesIndexer factoriesIndexer;

	private String watchXMLChangedRegistration;
	
	// Futures resolved when project is initialized/indexed
	private Map<URI, CompletableFuture<IJavaProject>> initializedProjects = new HashMap<>();
	

	private SimpleWorkspaceService getWorkspaceService() {
		return server.getServer().getWorkspaceService();
	}

	private ProjectObserver getProjectObserver() {
		return params.projectObserver;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		log.debug("Setting up {}", this);

		SymbolHandler handler = new SymbolHandler() {
			@Override
			public void addSymbols(IJavaProject project, String docURI, WorkspaceSymbol[] enhancedSymbols, List<SpringIndexElement> beanDefinitions,
					List<Diagnostic> diagnostics) {

				if (enhancedSymbols != null) {
					SpringSymbolIndex.this.addSymbolsByDoc(project, docURI, enhancedSymbols);
				}

				if (beanDefinitions != null) {
					springIndex.updateElements(project.getElementName(), docURI, beanDefinitions.toArray(SpringIndexElement[]::new));
				}
				
				if (diagnostics != null) {
					server.getTextDocumentService().publishDiagnostics(new TextDocumentIdentifier(docURI), diagnostics);
					// TODO: need to use real TextDocumentIdentifier because of the document version
				}
			}

			@Override
			public void addSymbols(IJavaProject project, WorkspaceSymbol[] enhancedSymbols,
					Map<String, List<SpringIndexElement>> beanDefinitionsByDoc, Map<String, List<Diagnostic>> diagnosticsPerDoc) {

				if (enhancedSymbols != null) {

					// organize symbols by doc URI
					Map<String, List<WorkspaceSymbol>> symbolsPerDoc = new HashMap<>();
					for (WorkspaceSymbol symbol : enhancedSymbols) {
						Either<Location, WorkspaceSymbolLocation> location = symbol.getLocation();
						String docURI = location.isLeft() ? location.getLeft().getUri() : location.getRight().getUri();
						
						symbolsPerDoc.computeIfAbsent(docURI, k -> new ArrayList<>()).add(symbol);
					}
	
					// add symbols per doc
					for (Map.Entry<String, List<WorkspaceSymbol>> entry : symbolsPerDoc.entrySet()) {
						String docURI = entry.getKey();
						List<WorkspaceSymbol> symbols = entry.getValue();
						
						SpringSymbolIndex.this.addSymbolsByDoc(project, docURI, (WorkspaceSymbol[]) symbols.toArray(new WorkspaceSymbol[symbols.size()]));
					}
				}
				
				if (beanDefinitionsByDoc != null) {

					// add beans per doc URI
					for (Entry<String, List<SpringIndexElement>> entry : beanDefinitionsByDoc.entrySet()) {
						String docURI = entry.getKey();
						List<SpringIndexElement> elements = entry.getValue();
						
						springIndex.updateElements(project.getElementName(), docURI, elements.toArray(SpringIndexElement[]::new));
					}
				}
				
				if (diagnosticsPerDoc != null) {
					for (String docURI : diagnosticsPerDoc.keySet()) {
						server.getTextDocumentService().publishDiagnostics(new TextDocumentIdentifier(docURI), diagnosticsPerDoc.get(docURI));
						// TODO: need to use real TextDocumentIdentifier because of the document version
					}
				}
			}
			
			@Override
			public void removeSymbols(IJavaProject project, String docURI) {
				SpringSymbolIndex.this.removeSymbolsByDoc(project, docURI);
//				springIndex.removeElements(project.getElementName(), docURI);
				
				// TODO remove diagnostics ?!? maybe, maybe not
				
			}

		};

		Map<String, SpringIndexerXMLNamespaceHandler> namespaceHandler = new HashMap<>();
		namespaceHandler.put("http://www.springframework.org/schema/beans", new SpringIndexerXMLNamespaceHandlerBeans());
		springIndexerXML = new SpringIndexerXML(handler, namespaceHandler, this.cache, projectFinder());
		
		BiFunction<AtomicReference<TextDocument>, BiConsumer<String, Diagnostic>, IProblemCollector> problemCollectorFactory = (docRef, aggregator) -> server.createProblemCollector(docRef, aggregator);
		springIndexerJava = new SpringIndexerJava(handler, specificProviders, this.cache, projectFinder(), server.getProgressService(), jdtReconciler, problemCollectorFactory, config.getJavaValidationSettingsJson(), cuCache);
		factoriesIndexer = new SpringFactoriesIndexer(handler, cache);

		this.indexers = new SpringIndexer[] {
				factoriesIndexer, // factories indexder should be the first, so that java indexers and reconcilers can rely on those indexed elements
				springIndexerJava
		};

		getWorkspaceService().onDidChangeWorkspaceFolders(evt -> {
			log.debug("workspace roots have changed event arrived - added: " + evt.getEvent().getAdded() + " - removed: " + evt.getEvent().getRemoved());
		});

		if (getProjectObserver() != null) {
			getProjectObserver().addListener(projectListener);
			log.info("project listener registered");
		}

		SimpleTextDocumentService documents = server.getTextDocumentService();
		documents.onDidSave(params -> {
			TextDocument document = params.getDocument();
			// Spring Boot LS get events from boot properties files as well, so filter them out
			if (BootJavaLanguageServerComponents.LANGUAGES.contains(document.getLanguageId())) {
				String docURI = document.getId().getUri();
				String content = document.get();
				this.updateDocument(docURI, content, "didSave event");
			}
		});

		config.addListener(evt -> {
			log.info("update settings of spring indexer - start");
			
			configurationChanged(SymbolIndexConfig.builder()
					.scanXml(config.isSpringXMLSupportEnabled())
					.xmlScanFolders(config.xmlBeansFoldersToScan())
					.scanTestJavaSources(config.isScanJavaTestSourcesEnabled())
					.build());
			
			log.info("update settings of spring indexer - done");
		});
		
		server.doOnInitialized(this::serverInitialized);
		server.onShutdown(this::shutdown);
	}

	public void serverInitialized() {
		List<String> globPattern = Stream.concat(Arrays.stream(springIndexerJava.getFileWatchPatterns()), Arrays.stream(factoriesIndexer.getFileWatchPatterns()))
				.collect(Collectors.toList());

		getWorkspaceService().getFileObserver().onFilesChanged(globPattern, (files) -> {
			updateDocuments(files, "file changed");
		});

		// watch for creation of files and folders (basically everything) to catch folder rename events as well
		getWorkspaceService().getFileObserver().onFilesCreated(List.of("**/*"), (files) -> {
			createDocuments(files);
		});

		// watch for deletion of files and folders (basically everything) to catch folder rename events as well
		getWorkspaceService().getFileObserver().onFilesDeleted(List.of("**/*"), (files) -> {
			deleteDocuments(files);
		});
	}

	public void configurationChanged(SymbolIndexConfig config) {
		CompletableFuture.runAsync(() -> configureIndexer(config), this.updateQueue);

		Collection<? extends IJavaProject> projects = projectFinder().all();
		for (IJavaProject project : projects) {
			initializeProject(project, true);
		}
	}
	
	public void configureIndexer(SymbolIndexConfig config) {
		synchronized (this) {
			if (config.isScanXml() && !(Arrays.asList(this.indexers).contains(springIndexerXML))) {
				this.indexers = new SpringIndexer[] { springIndexerJava, factoriesIndexer, springIndexerXML };
				springIndexerXML.updateScanFolders(config.getXmlScanFolders());
				addXmlFileListeners(Arrays.asList(springIndexerXML.getFileWatchPatterns()));
			} else if (!config.isScanXml() && Arrays.asList(this.indexers).contains(springIndexerXML)) {
				this.indexers = new SpringIndexer[] { springIndexerJava, factoriesIndexer };
				springIndexerXML.updateScanFolders(new String[0]);
				removeXmlFileListeners();
			} else if (config.isScanXml()) {
				if (springIndexerXML.updateScanFolders(config.getXmlScanFolders())) {
					// should remove the old listeners before adding the new ones
					addXmlFileListeners(Arrays.asList(springIndexerXML.getFileWatchPatterns()));
				}
			}
			springIndexerJava.setScanTestJavaSources(config.isScanTestJavaSources());
			springIndexerJava.setValidationSeveritySettings(this.config.getJavaValidationSettingsJson());
		}
	}
	
	private void addXmlFileListeners(List<String> globPattern) {
		removeXmlFileListeners();
		watchXMLChangedRegistration = getWorkspaceService().getFileObserver().onFilesChanged(globPattern,
				(files) -> {
					updateDocuments(files, "xml changed");
				});
	}
	
	private void removeXmlFileListeners() {
		if (watchXMLChangedRegistration != null) {
			getWorkspaceService().getFileObserver().unsubscribe(watchXMLChangedRegistration);
			watchXMLChangedRegistration = null;
		}
	}
	
	public void shutdown() {
		try {
			synchronized(this) {
				if (updateQueue != null && !updateQueue.isShutdown()) {
					updateQueue.shutdownNow();
				}

				if (getProjectObserver() != null) {
					getProjectObserver().removeListener(projectListener);
				}
			}
		} catch (Exception e) {
			log.error("{}", e);
		}
	}
	
	public CompletableFuture<Void> initializeProject(IJavaProject project, boolean clean) {
		CompletableFuture<Void> cf = _initializeProject(project, clean);
		cf.thenAccept( f -> {
			projectInitializedFuture(project).complete(null);
		});
		
		return cf;
	}
	
	private CompletableFuture<Void> _initializeProject(IJavaProject project, boolean clean) {
		try {
			if (SpringProjectUtil.isBootProject(project) || SpringProjectUtil.isSpringProject(project)) {

				if (project.getElementName() == null) {
					// Projects indexed by name. No name  - no index for it
					log.debug("Project with NULL name is being initialized");
					return CompletableFuture.completedFuture(null);
					
				} else {
					
					synchronized(this) { // synchronized since the `indexers` array can change via a settings change
						@SuppressWarnings("unchecked")
						CompletableFuture<Void>[] futures = new CompletableFuture[this.indexers.length + 1];

						// clean future
						futures[0] = CompletableFuture.runAsync(() -> {
							removeSymbolsByProject(project);
							springIndex.removeProject(project.getElementName());
						}, this.updateQueue);
						
						// index futures
						for (int i = 0; i < this.indexers.length; i++) {
							InitializeProject initializeItem = new InitializeProject(project, this.indexers[i], clean);
							futures[i + 1] = CompletableFuture.runAsync(initializeItem, this.updateQueue);
						}
						
						CompletableFuture<Void> future = CompletableFuture.allOf(futures);
						
						future = future.thenAccept(v -> server.getClient().indexUpdated()).thenAccept(v -> listeners.fire(v));

						this.latestScheduledTaskByProject.put(project.getElementName(), future);
						return future;
					}
				}
			} else {
				return deleteProject(project);
			}
		} catch (Throwable  e) {
			log.error("", e);
			return Futures.error(e);
		}
	}

	public SpringIndexerJava getJavaIndexer() {
		return springIndexerJava;
	}
	
	public CompletableFuture<Void> deleteProject(IJavaProject project) {
		try {
			if (project.getElementName() == null) {
				// Projects indexed by name. No name  - no index for it
				log.debug("Project with NULL name is being removed");
				return CompletableFuture.completedFuture(null);
			} else {
				DeleteProject initializeItem = new DeleteProject(project, this.indexers);
				CompletableFuture<Void> future = CompletableFuture.runAsync(initializeItem, this.updateQueue);
				this.latestScheduledTaskByProject.put(project.getElementName(), future);

				return future;
			}
		} catch (Throwable  e) {
			log.error("", e);
			return Futures.error(e);
		}
	}

	public CompletableFuture<Void> createDocument(String docURI) {
		String[] docURIs = unfold(docURI);
		return createDocuments(docURIs);
	}

	public CompletableFuture<Void> createDocuments(String[] docURIs) {
		synchronized(this) {
			List<CompletableFuture<Void>> futures = new ArrayList<>();
			
			docURIs = unfold(docURIs);

			for (SpringIndexer indexer : this.indexers) {
				String[] interestingDocs = getDocumentsInterestingForIndexer(indexer, docURIs);
				Map<String, IJavaProject> projectsForDocs = getProjectsForDocs(interestingDocs);
				Map<IJavaProject, List<String>> projectMapping = getProjectMapping(projectsForDocs);
				
				for (IJavaProject project : projectMapping.keySet()) {
					List<String> docs = projectMapping.get(project);
					
					try {
						DocumentDescriptor[] updatedDocs = docs.stream().map(doc -> createUpdatedDoc(doc)).toArray(DocumentDescriptor[]::new);
						futures.add(updateItems(project, updatedDocs, indexer));
					}
					catch (Exception e) {
						log.error("{}", e);
					}
				}
			}

			CompletableFuture<Void> future = CompletableFuture.allOf((CompletableFuture[]) futures.toArray(new CompletableFuture[futures.size()]));
			future = future.thenAccept(v -> server.getClient().indexUpdated()).thenAccept(v -> listeners.fire(v));
			return future;
		}
	}

	public static String[] unfold(String... docURIs) {
		Set<String> result = new HashSet<>();
		
		for (int i = 0; i < docURIs.length; i++) {
			File file = UriUtil.toFile(docURIs[i]);
			Path path = file.toPath();
			
			if (Files.isRegularFile(path)) {
				result.add(docURIs[i]);
			}
			else if (Files.isDirectory(path)) {
				try {
					Files.walk(path)
						.filter(Files::isRegularFile)
						.map(filePath -> filePath.toAbsolutePath().toString())
						.map(filePath -> UriUtil.toUri(new File(filePath)).toASCIIString())
						.forEach(docURI -> result.add(docURI));
				} catch (IOException e) {
					log.error("error unfolding: " + path.toString(), e);
				}
			}
		}
		
		return (String[]) result.toArray(new String[result.size()]);
	}

	private JavaProjectFinder projectFinder() {
		return params.projectFinder;
	}

	public CompletableFuture<Void> updateDocument(String docURI, String content, String reason) {
		log.info("Update document [{}]: {}", reason, docURI);
		
		synchronized(this) {
			List<CompletableFuture<Void>> futures = new ArrayList<>();

			for (SpringIndexer indexer : this.indexers) {
				if (indexer.isInterestedIn(docURI)) {
					Optional<IJavaProject> maybeProject = projectFinder().find(new TextDocumentIdentifier(docURI));
					if (maybeProject.isPresent()) {
						try {
							DocumentDescriptor updatedDoc = createUpdatedDoc(docURI);
							futures.add(updateItem(maybeProject.get(), updatedDoc, content, indexer));
						}
						catch (Exception e) {
							log.error("{}", e);
						}
					}
				}
			}
			CompletableFuture<Void> future = CompletableFuture.allOf((CompletableFuture[]) futures.toArray(new CompletableFuture[futures.size()]));
			future = future.thenAccept(v -> server.getClient().indexUpdated()).thenAccept(v -> listeners.fire(v));
			return future;
		}
	}

	public CompletableFuture<Void> updateDocuments(String[] docURIs, String reason) {
		for (String docURI : docURIs) {
			log.info("Update document [{}]: {}", reason, docURI);
		}
		
		synchronized(this) {
			List<CompletableFuture<Void>> futures = new ArrayList<>();

			for (SpringIndexer indexer : this.indexers) {
				String[] interestingDocs = getDocumentsInterestingForIndexer(indexer, docURIs);
				Map<String, IJavaProject> projectsForDocs = getProjectsForDocs(interestingDocs);
				Map<IJavaProject, List<String>> projectMapping = getProjectMapping(projectsForDocs);
				
				for (IJavaProject project : projectMapping.keySet()) {
					List<String> docs = projectMapping.get(project);
					
					try {
						DocumentDescriptor[] updatedDocs = docs.stream().map(doc -> createUpdatedDoc(doc)).toArray(DocumentDescriptor[]::new);
						futures.add(updateItems(project, updatedDocs, indexer));
					}
					catch (Exception e) {
						log.error("{}", e);
					}
				}
			}
			CompletableFuture<Void> future = CompletableFuture.allOf((CompletableFuture[]) futures.toArray(new CompletableFuture[futures.size()]));
			future = future.thenAccept(v -> server.getClient().indexUpdated()).thenAccept(v -> listeners.fire(v));
			return future;
		}
	}
	
	private Map<String, IJavaProject> getProjectsForDocs(String[] docURIs) {
		Map<String, IJavaProject> result = new HashMap<>();
		
		for (String docURI : docURIs) {
			Optional<IJavaProject> project = projectFinder().find(new TextDocumentIdentifier(docURI));
			if (project.isPresent()) {
				result.put(docURI, project.get());
			}
		}
		return result;
	}

	private Map<IJavaProject, List<String>> getProjectMapping(Map<String, IJavaProject> docsToProject) {
		return docsToProject.keySet().stream().collect(Collectors.groupingBy(docURI -> docsToProject.get(docURI)));
	}
	
	private Map<IJavaProject, Set<String>> getDocsPerProjectFromPaths(String[] paths) {
		Map<IJavaProject, Set<String>> result = new HashMap<>();
		
		for (String path : paths) {
			Optional<IJavaProject> project = projectFinder().find(new TextDocumentIdentifier(path));
			if (project.isPresent()) {
				result.putIfAbsent(project.get(), new HashSet<>());
				
				Set<String> docs = result.get(project.get());
				docs.addAll(getDocsFromPath(project.get(), path));
			}
		}
		
		return result;
	}
	
	private Collection<? extends String> getDocsFromPath(IJavaProject project, String path) {
		List<WorkspaceSymbol> allProjectSymbols = this.symbolsByProject.get(project.getElementName());
		
		Set<String> result = new HashSet<>();

		if (allProjectSymbols != null) {
			for (WorkspaceSymbol symbol : allProjectSymbols) {
				Either<Location, WorkspaceSymbolLocation> location = symbol.getLocation();
	
				String docURI = null;
				if (location.isLeft()) {
					docURI = location.getLeft().getUri();
				}
				else if (location.isRight()) {
					docURI = location.getRight().getUri();
				}
				
				if (docURI != null && docURI.startsWith(path)) {
					result.add(docURI);
				}
			}
		}
		
		return result;
	}

	private String[] getDocumentsInterestingForIndexer(SpringIndexer indexer, String[] docURIs) {
		return Arrays.stream(docURIs).filter(docURI -> indexer.isInterestedIn(docURI)).toArray(String[]::new);
	}

	private DocumentDescriptor createUpdatedDoc(String docURI) throws RuntimeException {
		try {
			File file = new File(new URI(docURI));
			long lastModified = file.lastModified();
			return new DocumentDescriptor(UriUtil.toUri(file).toASCIIString(), lastModified);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	public CompletableFuture<Void> deleteDocument(String deletedDocURI) {
		return deleteDocuments(new String[] {deletedDocURI});
	}

	public CompletableFuture<Void> deleteDocuments(String[] deletedPathURIs) {
		synchronized(this) {
			try {
				List<CompletableFuture<Void>> futures = new ArrayList<>();
				Map<IJavaProject, Set<String>> projectMapping = getDocsPerProjectFromPaths(deletedPathURIs);
				
				for (IJavaProject project : projectMapping.keySet()) {
					Set<String> docURIs = projectMapping.get(project);
					
					if (docURIs != null && docURIs.size() > 0) {
						DeleteItems deleteItems = new DeleteItems(project, (String[]) docURIs.toArray(new String[docURIs.size()]), this.indexers);
						CompletableFuture<Void> future = CompletableFuture.runAsync(deleteItems, this.updateQueue);
	
						this.latestScheduledTaskByProject.put(project.getElementName(), future);
						futures.add(future);
					}
				}

				if (futures.size() > 0) {
					CompletableFuture<Void> future = CompletableFuture.allOf((CompletableFuture[]) futures.toArray(new CompletableFuture[futures.size()]));
					future = future.thenAccept(v -> server.getClient().indexUpdated()).thenAccept(v -> listeners.fire(v));
					return future;
				}
				else {
					return CompletableFuture.completedFuture(null);
				}
			}
			catch (Exception e) {
				log.error("", e);
				return Futures.error(e);
			}
		}
	}

	public List<WorkspaceSymbol> getAllSymbols(String query) {
		if (query != null && query.length() > 0) {
			synchronized(this.symbols) {
				return searchMatchingSymbols(this.symbols, query);
			}
		} else {
			synchronized(this.symbols) {
				return this.symbols.stream().collect(Collectors.toList());
			}
		}
	}
	
	synchronized private CompletableFuture<IJavaProject> projectInitializedFuture(IJavaProject project) {
		if (project == null) {
			return CompletableFuture.completedFuture(null);
		} else {
			URI uri = project.getLocationUri();
			return initializedProjects.computeIfAbsent(uri, u -> CompletableFuture.completedFuture(project));
		}
	}
	
	public List<? extends WorkspaceSymbol> getSymbols(String docURI) {
		if (System.getProperty(OUTLINE_SYMBOLS_FROM_INDEX_PROPERTY) != null) {
			return getWorkspaceSymbolsFromMetamodelIndex(docURI);
		}
		else {
			return getWorkspaceSymbolsFromSymbolIndex(docURI);
		}
	}
	
	public List<? extends DocumentSymbol> getDocumentSymbols(String docURI) {
		if (System.getProperty(OUTLINE_SYMBOLS_FROM_INDEX_PROPERTY) != null
				|| config.isBeanStructureTreeEnabled()) {
			return getDocumentSymbolsFromMetamodelIndex(docURI);
		}
		else {
			return getDocumentSymbolsFromSymbolsIndex(docURI);
		}
	}
	
	public List<? extends WorkspaceSymbol> getWorkspaceSymbolsFromMetamodelIndex(String docURI) {
		List<WorkspaceSymbol> result = new ArrayList<>();
		
		Deque<DocumentSymbol> remainingSymbols = new ArrayDeque<>();
		List<? extends DocumentSymbol> documentSymbols = getDocumentSymbolsFromMetamodelIndex(docURI);
		
		remainingSymbols.addAll(documentSymbols);
		
		while (!remainingSymbols.isEmpty()) {
			DocumentSymbol documentSymbol = remainingSymbols.poll();
			
			WorkspaceSymbol workspaceSymbol = new WorkspaceSymbol();
			workspaceSymbol.setName(documentSymbol.getName());
			workspaceSymbol.setKind(documentSymbol.getKind());
			workspaceSymbol.setTags(documentSymbol.getTags());
			
			Location location = new Location(docURI, documentSymbol.getRange());
			workspaceSymbol.setLocation(Either.forLeft(location));
			
			result.add(workspaceSymbol);

			if (documentSymbol.getChildren() != null) {
				remainingSymbols.addAll(documentSymbol.getChildren());
			}
		}
		
		return result;
	}
	
	public List<? extends DocumentSymbol> getDocumentSymbolsFromSymbolsIndex(String docURI) {
		List<DocumentSymbol> result = new ArrayList<>();
		
		List<? extends WorkspaceSymbol> symbols = getWorkspaceSymbolsFromSymbolIndex(docURI);
		for (WorkspaceSymbol symbol : symbols) {
			DocumentSymbol docSymbol = new DocumentSymbol();
			docSymbol.setName(symbol.getName());
			docSymbol.setKind(symbol.getKind());
			docSymbol.setRange(symbol.getLocation().getLeft().getRange());
			docSymbol.setSelectionRange(symbol.getLocation().getLeft().getRange());
			docSymbol.setTags(symbol.getTags());
			
			result.add(docSymbol);
		}
		
		return result;
	}

	public List<? extends DocumentSymbol> getDocumentSymbolsFromMetamodelIndex(String docURI) {
		try {
			TextDocument doc = server.getTextDocumentService().getLatestSnapshot(docURI);
			URI uri = URI.create(docURI);

			CompletableFuture<IJavaProject> projectInitialized = futureProjectFinder.findFuture(uri).thenCompose(project -> projectInitializedFuture(project));
			IJavaProject project = projectInitialized.get(15, TimeUnit.SECONDS);

			ImmutableList.Builder<DocumentSymbol> builder = ImmutableList.builder();

			if (project != null && doc != null) {

				// Collect symbols from the opened document
				synchronized(this) {
					for (SpringIndexer indexer : this.indexers) {
						if (indexer.isInterestedIn(docURI)) {
							try {
								List<DocumentSymbol> adhocDocumentSymbols = indexer.computeDocumentSymbols(project, docURI, doc.get());
								builder.addAll(adhocDocumentSymbols);
							} catch (Exception e) {
								log.error("{}", e);
							}
						}
					}
				}

			} else {
				
				// Take symbols from the index if there is no opened document.
				DocumentElement document = springIndex.getDocument(docURI);
				if (document != null) {
					List<SpringIndexElement> children = document.getChildren();
					builder.addAll(SpringIndexToSymbolsConverter.createDocumentSymbols(children));
				}

			}
			return builder.build();
		} catch (Exception e) {
			log.warn("", e);
			return Collections.emptyList();
		}
	}
	
	public List<? extends WorkspaceSymbol> getWorkspaceSymbolsFromSymbolIndex(String docURI) {
		try {
			TextDocument doc = server.getTextDocumentService().getLatestSnapshot(docURI);
			URI uri = URI.create(docURI);
			CompletableFuture<IJavaProject> projectInitialized = futureProjectFinder.findFuture(uri).thenCompose(project -> projectInitializedFuture(project));
			IJavaProject project = projectInitialized.get(15, TimeUnit.SECONDS);
			ImmutableList.Builder<WorkspaceSymbol> builder = ImmutableList.builder();
			if (project != null && doc != null) {
				// Collect symbols from the opened document
				synchronized(this) {
					for (SpringIndexer indexer : this.indexers) {
						if (indexer.isInterestedIn(docURI)) {
							try {
								for (WorkspaceSymbol enhanced : indexer.computeSymbols(project, docURI,
										doc.get())) {
									builder.add(enhanced);
								}
							} catch (Exception e) {
								log.error("{}", e);
							}
						}
					}
				}
			} else {
				// Take symbols from the index if there is no opened document.
				List<WorkspaceSymbol> docSymbols = this.symbolsByDoc.get(uri.toASCIIString());
				if (docSymbols != null) {
					synchronized (docSymbols) {
						for (WorkspaceSymbol symbol : docSymbols) {
							builder.add(symbol);
						}
					}
				}
			}
			return builder.build();
		} catch (Exception e) {
			log.warn("", e);
			return Collections.emptyList();
		}
	}
	
	@Override
	public CompletableFuture<List<Bean>> beans(BeansParams params) {
		String projectName = params.getProjectName();
		CompletableFuture<Void> latestTask = this.latestScheduledTaskByProject.get(projectName);

		if (latestTask != null) {
			return latestTask.thenApply((e) -> {
				Bean[] beansOfProject = springIndex.getBeansOfProject(projectName);
				return beansOfProject != null ? Arrays.asList(beansOfProject) : Collections.emptyList();
			});
		}
		else {
			return CompletableFuture.completedFuture(null);
		}
	}

	@Override
	public CompletableFuture<List<Bean>> matchingBeans(MatchingBeansParams params) {
		String projectName = params.getProjectName();
		String matchType = params.getBeanTypeToMatch();

		CompletableFuture<Void> latestTask = this.latestScheduledTaskByProject.get(projectName);

		if (latestTask != null) {
			return latestTask.thenApply((e) -> {
				Bean[] matchingBeans = springIndex.getMatchingBeans(projectName, matchType);
				return matchingBeans != null ? Arrays.asList(matchingBeans) : Collections.emptyList();
			});
		}
		else {
			return CompletableFuture.completedFuture(null);
		}
	}

	/**
	 * inserts a noop operation into the worker/update quene, which allows invokers to use the
	 * returned future to wait for the queue items in the queue to be completed which got inserted before
	 * this noop.
	 */
	public CompletableFuture<Void> waitOperation() {
		return CompletableFuture.runAsync(new Runnable() {
			@Override
			public void run() {
			}
		}, this.updateQueue);
	}

	private List<WorkspaceSymbol> searchMatchingSymbols(List<WorkspaceSymbol> allsymbols, String query) {
		String locationPrefix = "";

		if (query.startsWith(QUERY_PARAM_LOCATION_PREFIX)) {

			int separatorIndex = query.indexOf("?");
			if (separatorIndex > 0) {
				locationPrefix = query.substring(QUERY_PARAM_LOCATION_PREFIX.length(), separatorIndex);
				query = query.substring(separatorIndex + 1);
			}
			else {
				locationPrefix = query.substring(QUERY_PARAM_LOCATION_PREFIX.length());
				query = query.substring(QUERY_PARAM_LOCATION_PREFIX.length() + locationPrefix.length());
			}
		}

		if (query.startsWith("*")) {
			query = query.substring(1);
		}

		String finalQuery = query;
		String finalLocationPrefix = locationPrefix;

		return allsymbols.stream()
				.filter(symbol -> {
					Either<Location, WorkspaceSymbolLocation> eitherLocation = symbol.getLocation();
					if (eitherLocation.isLeft()) {
						return eitherLocation.getLeft().getUri().startsWith(finalLocationPrefix);
					}
					if (eitherLocation.isRight()) {
						return symbol.getLocation().getRight().getUri().startsWith(finalLocationPrefix);
					}
					return false;
				})
				.filter(symbol -> StringUtil.containsCharactersCaseInsensitive(symbol.getName(), finalQuery))
				.collect(Collectors.toList());
	}



	//
	//
	// worker queue items to initialize, update, or delete symbols for files and projects
	//
	//

	private class InitializeProject implements Runnable {

		private final IJavaProject project;
		private final SpringIndexer indexer;
		private final boolean clean;

		public InitializeProject(IJavaProject project, SpringIndexer indexer, boolean clean) {
			this.project = project;
			this.indexer = indexer;
			this.clean = clean;
			log.debug("{} created ", indexer.getClass().getSimpleName());
		}

		@Override
		public void run() {
			log.debug("{} starting...", indexer.getClass().getSimpleName());
			try {
				indexer.initializeProject(project, this.clean);

				log.debug("{} completed", indexer.getClass().getSimpleName());
			} catch (Throwable e) {
				log.error("{} threw exception", indexer.getClass().getSimpleName(), e);
			}
		}
	}

	CompletableFuture<Void> updateItem(IJavaProject project, DocumentDescriptor updatedDoc, String content, SpringIndexer indexer) {
		log.debug("scheduling updateItem {}. {},  {}, {}", project.getElementName(), updatedDoc.getDocURI(), updatedDoc.getLastModified(), indexer);

		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				log.debug("updateItem {}. {},  {}, {}", project.getElementName(), updatedDoc.getDocURI(), updatedDoc.getLastModified(), indexer);
				indexer.updateFile(project, updatedDoc, content);
			} catch (Exception e) {
				log.error("{}", e);
			}
		}, this.updateQueue);
		
		this.latestScheduledTaskByProject.put(project.getElementName(), future);
		return future;
	}

	CompletableFuture<Void> updateItems(IJavaProject project, DocumentDescriptor[] updatedDoc, SpringIndexer indexer) {
		for (DocumentDescriptor doc : updatedDoc) {
			log.debug("scheduling updateItem {}. {},  {}, {}", project.getElementName(), doc.getDocURI(), doc.getLastModified(), indexer);
		}

		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			
			try {
				for (DocumentDescriptor doc : updatedDoc) {
					log.debug("updateItem {}. {},  {}, {}", project.getElementName(), doc.getDocURI(), doc.getLastModified(), indexer);
				}

				indexer.updateFiles(project, updatedDoc);
			} catch (Exception e) {
				log.error("{}", e);
			}
		}, this.updateQueue);
		
		this.latestScheduledTaskByProject.put(project.getElementName(), future);
		return future;
	}

	private class DeleteItems implements Runnable {

		private final String[] docURIs;
		private final IJavaProject project;
		private final SpringIndexer[] indexer;

		public DeleteItems(IJavaProject project, String[] docURIs, SpringIndexer[] indexer) {
			this.project = project;
			this.docURIs = docURIs;
			this.indexer = indexer;
		}

		@Override
		public void run() {
			try {
				for (String doc : this.docURIs) {
					removeSymbolsByDoc(project, doc);
					springIndex.removeElements(project.getElementName(), doc);
				}
				
				for (SpringIndexer index : this.indexer) {
					index.removeFiles(project, docURIs);
				}

			} catch (Exception e) {
				log.error("{}", e);
			}
		}
	}

	private class DeleteProject implements Runnable {

		private final IJavaProject project;
		private final SpringIndexer[] indexer;

		public DeleteProject(IJavaProject project, SpringIndexer[] indexer) {
			this.project = project;
			this.indexer = indexer;
			log.debug("{} created ", this);
		}

		@Override
		public void run() {
			log.debug("{} starting...", this);
			try {
				removeSymbolsByProject(project);
				for (SpringIndexer index : this.indexer) {
					index.removeProject(project);
				}
				springIndex.removeProject(project.getElementName());
				server.getClient().indexUpdated();
				
				log.debug("{} completed", this);
			} catch (Throwable e) {
				log.error("{} threw exception", this, e);
			}
		}

	}

	private void addSymbolsByDoc(IJavaProject project, String docURI, WorkspaceSymbol[] enhancedSymbols) {

		List<WorkspaceSymbol> docSymbols = symbolsByDoc.computeIfAbsent(docURI, s -> new ArrayList<WorkspaceSymbol>());
		List<WorkspaceSymbol> projectSymbols = symbolsByProject.computeIfAbsent(project.getElementName(), s -> new ArrayList<WorkspaceSymbol>());

		for (WorkspaceSymbol enhancedSymbol : enhancedSymbols) {

			synchronized(this.symbols) {
				symbols.add(enhancedSymbol);
			}

			synchronized(docSymbols) {
				docSymbols.add(enhancedSymbol);
			}

			synchronized(projectSymbols) {
				projectSymbols.add(enhancedSymbol);
			}

		}
	
	}

	private void removeSymbolsByDoc(IJavaProject project, String docURI) {
		List<WorkspaceSymbol> oldSymbols = symbolsByDoc.remove(docURI);
		if (oldSymbols != null) {

			List<WorkspaceSymbol> copy = null;
			synchronized(oldSymbols) {
				copy = new ArrayList<>(oldSymbols);
			}

			synchronized(this.symbols) {
				this.symbols.removeAll(copy);
			}

			List<WorkspaceSymbol> projectSymbols = symbolsByProject.get(project.getElementName());
			if (projectSymbols != null) {
				synchronized(projectSymbols) {
					projectSymbols.removeAll(copy);
				}
			}
		}
	}

	private void removeSymbolsByProject(IJavaProject project) {
		// If project name is null it cannot be in the cache
		if (project.getElementName() == null) {
			return;
		}
		List<WorkspaceSymbol> oldSymbols = symbolsByProject.remove(project.getElementName());
		if (oldSymbols != null) {

			List<WorkspaceSymbol> copy = null;
			synchronized(oldSymbols) {
				copy = new ArrayList<>(oldSymbols);
			}

			synchronized(this.symbols) {
				symbols.removeAll(copy);
			}

			Set<String> keySet = symbolsByDoc.keySet();
			Iterator<String> docIter = keySet.iterator();
			while (docIter.hasNext()) {
				String docURI = docIter.next();
				List<WorkspaceSymbol> docSymbols = symbolsByDoc.get(docURI);
				synchronized(docSymbols) {
					docSymbols.removeAll(copy);

					if (docSymbols.isEmpty()) {
						docIter.remove();
					}
				}
			}
		}

	}
	
	public void onUpdate(Consumer<Void> listener) {
		listeners.add(listener);
	}

}
