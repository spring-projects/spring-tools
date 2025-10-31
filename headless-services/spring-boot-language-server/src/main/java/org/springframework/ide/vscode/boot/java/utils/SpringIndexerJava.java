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
package org.springframework.ide.vscode.boot.java.utils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.index.SpringIndexToSymbolsConverter;
import org.springframework.ide.vscode.boot.index.cache.IndexCache;
import org.springframework.ide.vscode.boot.index.cache.IndexCacheKey;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchyAwareLookup;
import org.springframework.ide.vscode.boot.java.beans.CachedIndexElement;
import org.springframework.ide.vscode.boot.java.handlers.SymbolProvider;
import org.springframework.ide.vscode.boot.java.reconcilers.CachedDiagnostics;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.ReconcilingContext;
import org.springframework.ide.vscode.boot.java.reconcilers.ReconcilingIndex;
import org.springframework.ide.vscode.boot.java.reconcilers.RequiredCompleteAstException;
import org.springframework.ide.vscode.boot.java.reconcilers.RequiredCompleteIndexException;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.PercentageProgressTask;
import org.springframework.ide.vscode.commons.languageserver.ProgressService;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.util.UriUtil;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gson.JsonObject;

/**
 * @author Martin Lippert
 */
public class SpringIndexerJava implements SpringIndexer {
	
	private static final Logger log = LoggerFactory.getLogger(SpringIndexerJava.class);

	// whenever the implementation of the indexer changes in a way that the stored data in the cache is no longer valid,
	// we need to change the generation - this will result in a re-indexing due to no up-to-date cache data being found
	private static final String GENERATION = "GEN-33";
	private static final String INDEX_FILES_TASK_ID = "index-java-source-files-task-";

	private static final String SYMBOL_KEY = "symbols";
	private static final String INDEX_KEY = "index";
	private static final String DIAGNOSTICS_KEY = "diagnostics";

	private final SymbolHandler symbolHandler;
	private final AnnotationHierarchyAwareLookup<SymbolProvider> symbolProviders;
	private final JdtReconciler reconciler;
	private final IndexCache cache;
	private final JavaProjectFinder projectFinder;
	private final ProgressService progressService;
	private final CompilationUnitCache cuCache;
	
	private boolean scanTestJavaSources = false;
	private JsonObject validationSeveritySettings;
	private int scanChunkSize = 1000;

	private FileScanListener fileScanListener = null; //used by test code only

	private final SpringIndexerJavaDependencyTracker dependencyTracker = new SpringIndexerJavaDependencyTracker();
	private final BiFunction<AtomicReference<TextDocument>, BiConsumer<String, Diagnostic>, IProblemCollector> problemCollectorCreator;

	
	public SpringIndexerJava(SymbolHandler symbolHandler, AnnotationHierarchyAwareLookup<SymbolProvider> symbolProviders, IndexCache cache,
			JavaProjectFinder projectFimder, ProgressService progressService, JdtReconciler jdtReconciler,
			BiFunction<AtomicReference<TextDocument>, BiConsumer<String, Diagnostic>, IProblemCollector> problemCollectorCreator,
			JsonObject validationSeveritySettings, CompilationUnitCache cuCache) {
		this.symbolHandler = symbolHandler;
		this.symbolProviders = symbolProviders;
		this.reconciler = jdtReconciler;
		this.cache = cache;
		this.projectFinder = projectFimder;
		this.progressService = progressService;
		
		this.problemCollectorCreator = problemCollectorCreator;
		this.validationSeveritySettings = validationSeveritySettings;
		this.cuCache = cuCache;
	}

	public SpringIndexerJavaDependencyTracker getDependencyTracker() {
		return dependencyTracker;
	}
	
	@Override
	public String[] getFileWatchPatterns() {
		return new String[] {"**/*.java", "**/*.properties", "**/*.yaml", "**/*.yml"};
	}

	@Override
	public boolean isInterestedIn(String resource) {
		return resource.endsWith(".java") || 
				(resource.contains("application") && (resource.endsWith(".properties") || resource.endsWith(".yml") || resource.endsWith(".yaml")));
	}

	@Override
	public void initializeProject(IJavaProject project, boolean clean) throws Exception {
		String[] files = this.getFiles(project);

		log.info("scan java files for symbols for project: {} - no. of files: {}", project.getElementName(), files.length);

		long startTime = System.currentTimeMillis();
		scanFiles(project, files, clean);
		long endTime = System.currentTimeMillis();

		log.info("scan java files for symbols for project: {} took ms: {}", project.getElementName(), endTime - startTime);
	}

	@Override
	public void removeProject(IJavaProject project) throws Exception {
		IndexCacheKey symbolsCacheKey = getCacheKey(project, SYMBOL_KEY);
		IndexCacheKey indexCacheKey = getCacheKey(project, INDEX_KEY);
		IndexCacheKey diagnosticsCacheKey = getCacheKey(project, DIAGNOSTICS_KEY);

		this.cache.remove(symbolsCacheKey);
		this.cache.remove(indexCacheKey);
		this.cache.remove(diagnosticsCacheKey);
	}

	@Override
	public void updateFile(IJavaProject project, DocumentDescriptor updatedDoc, String content) throws Exception {
		IndexCacheKey symbolCacheKey = getCacheKey(project, SYMBOL_KEY);
		IndexCacheKey indexCacheKey = getCacheKey(project, INDEX_KEY);
		IndexCacheKey diagnosticsCacheKey = getCacheKey(project, DIAGNOSTICS_KEY);

		if (updatedDoc != null && shouldProcessDocument(project, updatedDoc.getDocURI())) {
			if (isCacheOutdated(symbolCacheKey, updatedDoc.getDocURI(), updatedDoc.getLastModified())
					|| isCacheOutdated(indexCacheKey, updatedDoc.getDocURI(), updatedDoc.getLastModified())
					|| isCacheOutdated(diagnosticsCacheKey, updatedDoc.getDocURI(), updatedDoc.getLastModified())) {

				this.symbolHandler.removeSymbols(project, updatedDoc.getDocURI());
				scanFile(project, updatedDoc, content);
			}
		}
	}
	
	@Override
	public void updateFiles(IJavaProject project, DocumentDescriptor[] updatedDocs) throws Exception {
		updateJavaFiles(project, updatedDocs);
		updatePropertyFiles(project, updatedDocs);
	}
	
	private void updateJavaFiles(IJavaProject project, DocumentDescriptor[] updatedDocs) throws Exception {
		if (updatedDocs != null) {
			DocumentDescriptor[] docs = filterDocuments(project, updatedDocs);

			if (docs.length > 0) {
				for (DocumentDescriptor updatedDoc : docs) {
					this.symbolHandler.removeSymbols(project, updatedDoc.getDocURI());
				}
				scanFiles(project, docs);
			}
		}
	}
	
	private void updatePropertyFiles(IJavaProject project, DocumentDescriptor[] updatedDocs) {
		List<String> changedPropertyFiles = Arrays.stream(updatedDocs)
			.filter(doc -> doc.getDocURI().contains("application"))
			.filter(doc -> doc.getDocURI().endsWith(".properties") || doc.getDocURI().endsWith(".yml") || doc.getDocURI().endsWith(".yaml"))
			.map(doc -> doc.getDocURI())
			.toList();
		
		if (changedPropertyFiles.size() > 0) {
			List<String> filesToReconcile = reconciler.identifyFilesToReconcile(project, changedPropertyFiles);
			List<DocumentDescriptor> filesWithTimestamps = DocumentDescriptor.createFromUris(filesToReconcile);

			try {
				reconcileWithCompleteIndex(project, filesWithTimestamps);
			}
			catch (Exception e) {
				log.error("error reconcling java source as reaction to property file change", e);
			}
		}
	}

	@Override
	public void removeFiles(IJavaProject project, String[] docURIs) throws Exception {
		
		String[] files = Arrays.stream(docURIs).map(docURI -> {
			try {
				return new File(new URI(docURI)).getAbsolutePath();
			} catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}).toArray(String[]::new);
		
		IndexCacheKey symbolsCacheKey = getCacheKey(project, SYMBOL_KEY);
		IndexCacheKey indexCacheKey = getCacheKey(project, INDEX_KEY);
		IndexCacheKey diagnosticsCacheKey = getCacheKey(project, DIAGNOSTICS_KEY);

		this.cache.removeFiles(symbolsCacheKey, files, CachedSymbol.class);
		this.cache.removeFiles(indexCacheKey, files, CachedIndexElement.class);
		this.cache.removeFiles(diagnosticsCacheKey, files, CachedDiagnostics.class);
	}
	
	/**
	 * Computes document symbols ad-hoc, based on the given doc URI and the given content,
	 * re-using the AST from the compilation unit cache. This is meant to compute symbols
	 * for files opened in editors, so that symbols can be based on editor buffer content,
	 * not the file content on disc.
	 * 
	 * @deprecated (will use computeDocumentSymbols in the future exclusively)
	 */
	public List<WorkspaceSymbol> computeSymbols(IJavaProject project, String docURI, String content) throws Exception {
		if (content != null) {
			URI uri = URI.create(docURI);
			
			return cuCache.withCompilationUnit(project, uri, cu -> {
				String file = UriUtil.toFileString(docURI);
				SpringIndexerJavaScanResult result = new SpringIndexerJavaScanResult(project, new String[] {file});

				IProblemCollector voidProblemCollector = new IProblemCollector() {
					@Override
					public void endCollecting() {
					}

					@Override
					public void beginCollecting() {
					}

					@Override
					public void accept(ReconcileProblem problem) {
					}
				};

				AtomicReference<TextDocument> docRef = new AtomicReference<>();
				SpringIndexerJavaContext context = new SpringIndexerJavaContext(project, cu, docURI, file,
						0, docRef, content, voidProblemCollector, new ArrayList<>(), true, true, result);

				scanAST(context, false, new ReconcilingIndex());

				return result.getGeneratedSymbols().stream().map(s -> s.getSymbol()).collect(Collectors.toList());
			});
		}
		
		return Collections.emptyList();
	}
	
	/**
	 * Computes document symbols ad-hoc, based on the given doc URI and the given content,
	 * re-using the AST from the compilation unit cache. This is meant to compute symbols
	 * for files opened in editors, so that symbols can be based on editor buffer content,
	 * not the file content on disc.
	 */
	@Override
	public List<DocumentSymbol> computeDocumentSymbols(IJavaProject project, String docURI, String content) throws Exception {
		if (content != null) {
			URI uri = URI.create(docURI);
			
			return cuCache.withCompilationUnit(project, uri, cu -> {
				String file = UriUtil.toFileString(docURI);

				SpringIndexerJavaScanResult result = new SpringIndexerJavaScanResult(project, new String[] {file});
				
				IProblemCollector voidProblemCollector = new IProblemCollector() {
					@Override
					public void endCollecting() {
					}

					@Override
					public void beginCollecting() {
					}

					@Override
					public void accept(ReconcileProblem problem) {
					}
				};

				AtomicReference<TextDocument> docRef = new AtomicReference<>();
				SpringIndexerJavaContext context = new SpringIndexerJavaContext(project, cu, docURI, file,
						0, docRef, content, voidProblemCollector, new ArrayList<>(), true, true, result);

				scanAST(context, false, new ReconcilingIndex());

				List<SpringIndexElement> indexElements = result.getGeneratedIndexElements().stream()
					.map(cachedBean -> cachedBean.getIndexElement())
					.toList();

				return SpringIndexToSymbolsConverter.createDocumentSymbols(indexElements);
			});
		}
		
		return Collections.emptyList();
	}

	/**
	 * filter the list of given documents down to those that should be processed and that don't have
	 * valid up-to-date cache data anymore
	 */
	private DocumentDescriptor[] filterDocuments(IJavaProject project, DocumentDescriptor[] updatedDocs) {
		IndexCacheKey symbolsCacheKey = getCacheKey(project, SYMBOL_KEY);
		IndexCacheKey indexCacheKey = getCacheKey(project, INDEX_KEY);
		IndexCacheKey diagnosticsCacheKey = getCacheKey(project, DIAGNOSTICS_KEY);

		return Arrays.stream(updatedDocs)
				.filter(doc -> doc.getDocURI().endsWith(".java"))
				.filter(doc -> shouldProcessDocument(project, doc.getDocURI()))
				.filter(doc -> isCacheOutdated(symbolsCacheKey, doc.getDocURI(), doc.getLastModified())
						|| isCacheOutdated(indexCacheKey, doc.getDocURI(), doc.getLastModified())
						|| isCacheOutdated(diagnosticsCacheKey, doc.getDocURI(), doc.getLastModified()))
				.toArray(DocumentDescriptor[]::new);
	}

	/**
	 * checks whether the given doc URI is from inside the given projects "interesting" folders
	 * This is used to filter out file updates for files that are outside of the source folders (for example)
	 */
	private boolean shouldProcessDocument(IJavaProject project, String docURI) {
		try {
			Path path = new File(new URI(docURI)).toPath();
			return foldersToScan(project)
					.filter(sourceFolder -> path.startsWith(sourceFolder.toPath()))
					.findFirst()
					.isPresent();
		} catch (URISyntaxException e) {
			log.info("shouldProcessDocument - docURI syntax problem: {}", docURI);
			return false;
		}
	}
	
	private boolean isCacheOutdated(IndexCacheKey cacheKey, String docURI, long modifiedTimestamp) {
		long cachedModificationTImestamp = this.cache.getModificationTimestamp(cacheKey, UriUtil.toFileString(docURI));
		return modifiedTimestamp > cachedModificationTImestamp;
	}

	private void scanFiles(IJavaProject project, DocumentDescriptor[] docs) throws Exception {
		Set<String> scannedFiles = new HashSet<>();
		for (int i = 0; i < docs.length; i++) {
			scannedFiles.add(docs[i].getFile());
		}
		
		ScanFilesInternallyResult result = scanFilesInternally(project, docs);
		scanAffectedFiles(project, result.scannedTypes, scannedFiles, result.scanResult.getMarkedForAffectedFilesIndexing());
	}

	private void scanFile(IJavaProject project, DocumentDescriptor updatedDoc, String content) throws Exception {
		final boolean ignoreMethodBodies = false;
		ASTParserCleanupEnabled parser = createParser(project, new AnnotationHierarchies(), ignoreMethodBodies);
		
		String docURI = updatedDoc.getDocURI();
		long lastModified = updatedDoc.getLastModified();
		
		if (content == null) {
			Path path = new File(new URI(docURI)).toPath();
			content = new String(Files.readAllBytes(path));
		}
		
		String unitName = docURI.substring(docURI.lastIndexOf("/") + 1); // skip over '/'
		parser.setUnitName(unitName);
		log.debug("Scan file: {}", unitName);
		parser.setSource(content.toCharArray());

		CompilationUnit cu = (CompilationUnit) parser.createAST(null);

		if (cu != null) {
			String file = UriUtil.toFileString(docURI);

			SpringIndexerJavaScanResult result = new SpringIndexerJavaScanResult(project, new String[] {file});
			ReconcilingIndex reconcilingIndex = new ReconcilingIndex();
			
			AtomicReference<TextDocument> docRef = new AtomicReference<>();
			BiConsumer<String, Diagnostic> diagnosticsAggregator = new BiConsumer<>() {
				@Override
				public void accept(String docURI, Diagnostic diagnostic) {
					result.getGeneratedDiagnostics().add(new CachedDiagnostics(docURI, diagnostic));
				}
			};
			
			IProblemCollector problemCollector = problemCollectorCreator.apply(docRef, diagnosticsAggregator);

			SpringIndexerJavaContext context = new SpringIndexerJavaContext(project, cu, docURI, file,
					lastModified, docRef, content, problemCollector, new ArrayList<>(), !ignoreMethodBodies, false, result);

			scanAST(context, true, reconcilingIndex);

			result.publishResults(symbolHandler);

			// update cache
			
			IndexCacheKey symbolCacheKey = getCacheKey(project, SYMBOL_KEY);
			IndexCacheKey indexCacheKey = getCacheKey(project, INDEX_KEY);
			IndexCacheKey diagnosticsCacheKey = getCacheKey(project, DIAGNOSTICS_KEY);
			
			this.cache.update(symbolCacheKey, file, lastModified, result.getGeneratedSymbols(), context.getDependencies(), CachedSymbol.class);
			this.cache.update(indexCacheKey, file, lastModified, result.getGeneratedIndexElements(), context.getDependencies(), CachedIndexElement.class);
			this.cache.update(diagnosticsCacheKey, file, lastModified, result.getGeneratedDiagnostics(), context.getDependencies(), CachedDiagnostics.class);
			
			Set<String> scannedFiles = new HashSet<>();
			scannedFiles.add(file);
			fileScannedEvent(file);

			reconcileWithCompleteIndex(project, result.getMarkedForReconcilingWithCompleteIndex());

			scanAffectedFiles(project, context.getScannedTypes(), scannedFiles, result.getMarkedForAffectedFilesIndexing());
		}
	}
	
	private ScanFilesInternallyResult scanFilesInternally(IJavaProject project, DocumentDescriptor[] docs) throws Exception {
		final boolean ignoreMethodBodies = false;
		
		// this is to keep track of already scanned files to avoid endless loops due to circular dependencies
		Set<String> scannedTypes = new HashSet<>();

		Map<String, DocumentDescriptor> updatedDocs = new HashMap<>(); // docURI -> UpdatedDoc
		String[] javaFiles = new String[docs.length];
		long[] lastModified = new long[docs.length];

		for (int i = 0; i < docs.length; i++) {
			updatedDocs.put(docs[i].getDocURI(), docs[i]);

			javaFiles[i] = docs[i].getFile();
			lastModified[i] = docs[i].getLastModified();
		}
		
		SpringIndexerJavaScanResult result = new SpringIndexerJavaScanResult(project, javaFiles);
		ReconcilingIndex reconcilingIndex = new ReconcilingIndex();
		
		Multimap<String, String> dependencies = MultimapBuilder.hashKeys().hashSetValues().build();
		
		BiConsumer<String, Diagnostic> diagnosticsAggregator = new BiConsumer<>() {
			@Override
			public void accept(String docURI, Diagnostic diagnostic) {
				result.getGeneratedDiagnostics().add(new CachedDiagnostics(docURI, diagnostic));
			}
		};

		FileASTRequestor requestor = new FileASTRequestor() {
			@Override
			public void acceptAST(String sourceFilePath, CompilationUnit cu) {
				File file = new File(sourceFilePath);
				String docURI = UriUtil.toUri(file).toASCIIString();
				
				DocumentDescriptor updatedDoc = updatedDocs.get(docURI);
				long lastModified = updatedDoc.getLastModified();
				
				AtomicReference<TextDocument> docRef = new AtomicReference<>();

				IProblemCollector problemCollector = problemCollectorCreator.apply(docRef, diagnosticsAggregator);

				SpringIndexerJavaContext context = new SpringIndexerJavaContext(project, cu, docURI, sourceFilePath,
						lastModified, docRef, null, problemCollector, new ArrayList<>(), !ignoreMethodBodies, false, result);
				
				scanAST(context, true, reconcilingIndex);
				
				dependencies.putAll(sourceFilePath, context.getDependencies());
				scannedTypes.addAll(context.getScannedTypes());

				fileScannedEvent(sourceFilePath);
			}
		};

		AnnotationHierarchies annotationHierarchies = new AnnotationHierarchies();
		List<String[]> chunks = createChunks(javaFiles, this.scanChunkSize);
		for(int i = 0; i < chunks.size(); i++) {
			log.info("scan java files, AST parse, chunk {} for files: {}", i, javaFiles.length);
			
			ASTParserCleanupEnabled parser = createParser(project, annotationHierarchies, ignoreMethodBodies);
			parser.createASTs(chunks.get(i), null, new String[0], requestor, null);
			parser.cleanup();
		}
		
		// update the cache
		
		IndexCacheKey symbolsCacheKey = getCacheKey(project, SYMBOL_KEY);
		IndexCacheKey indexCacheKey = getCacheKey(project, INDEX_KEY);
		IndexCacheKey diagnosticsCacheKey = getCacheKey(project, DIAGNOSTICS_KEY);

		this.cache.update(symbolsCacheKey, javaFiles, lastModified, result.getGeneratedSymbols(), dependencies, CachedSymbol.class);
		this.cache.update(indexCacheKey, javaFiles, lastModified, result.getGeneratedIndexElements(), dependencies, CachedIndexElement.class);
		this.cache.update(diagnosticsCacheKey, javaFiles, lastModified, result.getGeneratedDiagnostics(), dependencies, CachedDiagnostics.class);
		
		result.publishResults(symbolHandler);
		reconcileWithCompleteIndex(project, result.getMarkedForReconcilingWithCompleteIndex());

		return new ScanFilesInternallyResult(scannedTypes, result);
	}
	
	private static record ScanFilesInternallyResult(Set<String> scannedTypes, SpringIndexerJavaScanResult scanResult) {};


	private void scanAffectedFiles(IJavaProject project, Set<String> changedTypes, Set<String> alreadyScannedFiles, Set<String> alreadyMarkedForAffectedFilesIndexing) throws Exception {
		log.info("Start scanning affected files for types {}", changedTypes);
		
		Multimap<String, String> dependencies = dependencyTracker.getAllDependencies();
		Set<String> filesToScan = new HashSet<>();
		
		for (String affectedFile : alreadyMarkedForAffectedFilesIndexing) {
			if (!alreadyScannedFiles.contains(affectedFile)) {
				filesToScan.add(affectedFile);
			}
		}
		
		for (String file : dependencies.keys()) {
			if (!alreadyScannedFiles.contains(file)) {
				Collection<String> dependsOn = dependencies.get(file);
				if (dependsOn.stream().anyMatch(changedTypes::contains)) {
					filesToScan.add(file);
				}
			}
		}
		
		if (!filesToScan.isEmpty()) {
			DocumentDescriptor[] docsToScan = filesToScan.stream()
					.map(file -> DocumentDescriptor.createFromFile(file))
					.toArray(DocumentDescriptor[]::new);
			
			for (DocumentDescriptor docToScan : docsToScan) {
				this.symbolHandler.removeSymbols(project, docToScan.getDocURI());
			}
			
			scanFilesInternally(project, docsToScan);
		}

		log.info("Finished scanning affected files {}", alreadyScannedFiles);
	}

	private void scanFiles(IJavaProject project, String[] javaFiles, boolean clean) throws Exception {
		
		SpringIndexerJavaScanResult result = null;
		
		// check cached elements first
		IndexCacheKey symbolsCacheKey = getCacheKey(project, SYMBOL_KEY);
		IndexCacheKey indexCacheKey = getCacheKey(project, INDEX_KEY);
		IndexCacheKey diagnosticsCacheKey = getCacheKey(project, DIAGNOSTICS_KEY);

		Pair<CachedSymbol[], Multimap<String, String>> cachedSymbols = this.cache.retrieve(symbolsCacheKey, javaFiles, CachedSymbol.class);
		Pair<CachedIndexElement[], Multimap<String, String>> cachedIndexElements = this.cache.retrieve(indexCacheKey, javaFiles, CachedIndexElement.class);
		Pair<CachedDiagnostics[], Multimap<String, String>> cachedDiagnostics = this.cache.retrieve(diagnosticsCacheKey, javaFiles, CachedDiagnostics.class);

		if (!clean && cachedSymbols != null && cachedIndexElements != null && cachedDiagnostics != null) {
			// use cached data

			result = new SpringIndexerJavaScanResult(project, javaFiles, symbolHandler, cachedSymbols.getLeft(), cachedIndexElements.getLeft(), cachedDiagnostics.getLeft());
			this.dependencyTracker.restore(cachedSymbols.getRight());

			log.info("scan java files used cached data: {} - no. of cached symbols retrieved: {}", project.getElementName(), result.getGeneratedSymbols().size());
			log.info("scan java files restored cached dependency data: {} - no. of cached dependencies: {}", cachedSymbols.getRight().size());

		}
		else {
			// continue scanning everything

			result = new SpringIndexerJavaScanResult(project, javaFiles);
			ReconcilingIndex reconcilingIndex = new ReconcilingIndex();

			final SpringIndexerJavaScanResult finalResult = result;
			BiConsumer<String, Diagnostic> diagnosticsAggregator = new BiConsumer<>() {
				@Override
				public void accept(String docURI, Diagnostic diagnostic) {
					finalResult.getGeneratedDiagnostics().add(new CachedDiagnostics(docURI, diagnostic));
				}
			};
			
			List<String[]> chunks = createChunks(javaFiles, this.scanChunkSize);
			AnnotationHierarchies annotations = new AnnotationHierarchies();
			for (int i = 0; i < chunks.size(); i++) {

				log.info("scan java files, AST parse, chunk {} for files: {}", i, javaFiles.length);
	            String[] pass2Files = scanFiles(project, annotations, chunks.get(i), diagnosticsAggregator, true, result, reconcilingIndex);

	            if (pass2Files.length > 0) {
					log.info("scan java files, AST parse, pass 2, chunk {} for files: {}", i, javaFiles.length);
					scanFiles(project, annotations, pass2Files, diagnosticsAggregator, false, result, reconcilingIndex);
				}
	        }
			
			log.info("scan java files done, number of symbols created: " + result.getGeneratedSymbols().size());

			this.cache.store(symbolsCacheKey, javaFiles, result.getGeneratedSymbols(), dependencyTracker.getAllDependencies(), CachedSymbol.class);
			this.cache.store(indexCacheKey, javaFiles, result.getGeneratedIndexElements(), dependencyTracker.getAllDependencies(), CachedIndexElement.class);
			this.cache.store(diagnosticsCacheKey, javaFiles, result.getGeneratedDiagnostics(), dependencyTracker.getAllDependencies(), CachedDiagnostics.class);
		}
		
		result.publishResults(symbolHandler);
		reconcileWithCompleteIndex(project, result.getMarkedForReconcilingWithCompleteIndex());
		
		log.info("reconciling stats - counter: " + reconciler.getStatsCounter());
		log.info("reconciling stats - timer: " + reconciler.getStatsTimer());
	}

	private String[] scanFiles(IJavaProject project, AnnotationHierarchies annotations, String[] javaFiles,
			BiConsumer<String, Diagnostic> diagnosticsAggregator, boolean ignoreMethodBodies, SpringIndexerJavaScanResult result, ReconcilingIndex reconcilingIndex) throws Exception {
		
		PercentageProgressTask progressTask = this.progressService.createPercentageProgressTask(INDEX_FILES_TASK_ID + project.getElementName(),
				javaFiles.length, "Spring Tools: Indexing Java Sources for '" + project.getElementName() + "'");

		List<String> nextPassFiles = new ArrayList<>();

		FileASTRequestor requestor = new FileASTRequestor() {

			@Override
			public void acceptAST(String sourceFilePath, CompilationUnit cu) {
				File file = new File(sourceFilePath);
				String docURI = UriUtil.toUri(file).toASCIIString();
				long lastModified = file.lastModified();
				AtomicReference<TextDocument> docRef = new AtomicReference<>();

				IProblemCollector problemCollector = problemCollectorCreator.apply(docRef, diagnosticsAggregator);

				SpringIndexerJavaContext context = new SpringIndexerJavaContext(project, cu, docURI, sourceFilePath,
						lastModified, docRef, null, problemCollector, nextPassFiles, !ignoreMethodBodies, false, result);

				scanAST(context, true, reconcilingIndex);
				progressTask.increment();
			}
		};

		ASTParserCleanupEnabled parser = createParser(project, annotations, ignoreMethodBodies);
		try {
			parser.createASTs(javaFiles, null, new String[0], requestor, null);
			return (String[]) nextPassFiles.toArray(new String[nextPassFiles.size()]);
		} catch (Throwable t) {
			log.error("Failed to index project '%s'".formatted(project.getElementName()), t);
			return new String[0];
		}
		finally {
			parser.cleanup();
			progressTask.done();
		}
	}

	private void reconcileWithCompleteIndex(IJavaProject project, List<DocumentDescriptor> filesWithTimestamps) throws Exception {
		if (filesWithTimestamps.isEmpty()) {
			return;
		}
		
		boolean ignoreMethodBodies = false;
		
		String[] javaFiles = new String[filesWithTimestamps.size()];
		long[] modificationTimestamps = new long[filesWithTimestamps.size()];
		
		for (int i = 0; i < filesWithTimestamps.size(); i++) {
			javaFiles[i] = filesWithTimestamps.get(i).getFile();
			modificationTimestamps[i] = filesWithTimestamps.get(i).getLastModified();
		}
		
		log.info("additional reconciling with complete index triggered for: " + Arrays.toString(javaFiles));
		
		// reconcile
		SpringIndexerJavaScanResult reconcilingResult = new SpringIndexerJavaScanResult(project, javaFiles);
		ReconcilingIndex reconcilingIndex = new ReconcilingIndex();
		
		BiConsumer<String, Diagnostic> diagnosticsAggregator = new BiConsumer<>() {
			@Override
			public void accept(String docURI, Diagnostic diagnostic) {
				reconcilingResult.getGeneratedDiagnostics().add(new CachedDiagnostics(docURI, diagnostic));
			}
		};

		FileASTRequestor requestor = new FileASTRequestor() {

			@Override
			public void acceptAST(String sourceFilePath, CompilationUnit cu) {
				File file = new File(sourceFilePath);
				String docURI = UriUtil.toUri(file).toASCIIString();
				long lastModified = file.lastModified();
				AtomicReference<TextDocument> docRef = new AtomicReference<>();

				IProblemCollector problemCollector = problemCollectorCreator.apply(docRef, diagnosticsAggregator);

				SpringIndexerJavaContext context = new SpringIndexerJavaContext(project, cu, docURI, sourceFilePath,
						lastModified, docRef, null, problemCollector, new ArrayList<>(), !ignoreMethodBodies, true, reconcilingResult);

				try {
					DocumentUtils.getTempTextDocument(docURI, docRef, null); // initialize the docRef with a real document before running validations
					reconcile(context, reconcilingIndex);
					
					Collection<String> dependencies = dependencyTracker.get(context.getFile());
					for (String dependency : context.getDependencies()) {
						dependencies.add(dependency);
					}
					
				} catch (Exception e) {
					log.error("problem creating temp document during re-reconciling for: " + docURI, e);
				}
				
			}
		};

		AnnotationHierarchies annotations = new AnnotationHierarchies();
		ASTParserCleanupEnabled parser = createParser(project, annotations, ignoreMethodBodies);
		try {
			parser.createASTs(javaFiles, null, new String[0], requestor, null);
		}
		finally {
			parser.cleanup();
		}
		
		// cache update
		IndexCacheKey diagnosticsCacheKey = getCacheKey(project, DIAGNOSTICS_KEY);
		this.cache.update(diagnosticsCacheKey, javaFiles, modificationTimestamps, reconcilingResult.getGeneratedDiagnostics(), dependencyTracker.getAllDependencies(), CachedDiagnostics.class);

		// publish diagnostics
		reconcilingResult.publishDiagnosticsOnly(symbolHandler);
	}

	private void scanAST(final SpringIndexerJavaContext context, boolean includeReconcile, ReconcilingIndex reconcilingIndex) {
		try {
			context.getCu().accept(new ASTVisitor() {
				
				@Override
				public boolean visit(PackageDeclaration node) {
					try {
						extractSymbolInformation(node, context);
					}
					catch (RequiredCompleteAstException e) {
						throw e;
					}
					catch (Exception e) {
						log.error("error extracting symbol information in project '" + context.getProject().getElementName() + "' - for docURI '" + context.getDocURI() + "' - on node: " + node.toString(), e);
					}
					return super.visit(node);
				}
	
				@Override
				public boolean visit(TypeDeclaration node) {
					try {
						context.addScannedType(node.resolveBinding());
						extractSymbolInformation(node, context);
					}
					catch (RequiredCompleteAstException e) {
						throw e;
					}
					catch (Exception e) {
						log.error("error extracting symbol information in project '" + context.getProject().getElementName() + "' - for docURI '" + context.getDocURI() + "' - on node: " + node.toString(), e);
					}
					
					return super.visit(node);
				}
	
				@Override
				public boolean visit(RecordDeclaration node) {
					try {
						context.addScannedType(node.resolveBinding());
						extractSymbolInformation(node, context);
					}
					catch (RequiredCompleteAstException e) {
						throw e;
					}
					catch (Exception e) {
						log.error("error extracting symbol information in project '" + context.getProject().getElementName() + "' - for docURI '" + context.getDocURI() + "' - on node: " + node.toString(), e);
					}
					
					return super.visit(node);
				}
	
				@Override
				public boolean visit(MethodDeclaration node) {
					try {
						extractSymbolInformation(node, context);
					}
					catch (RequiredCompleteAstException e) {
						throw e;
					}
					catch (Exception e) {
						log.error("error extracting symbol information in project '" + context.getProject().getElementName() + "' - for docURI '" + context.getDocURI() + "' - on node: " + node.toString(), e);
					}
					return super.visit(node);
				}
	
				@Override
				public boolean visit(SingleMemberAnnotation node) {
					try {
						extractSymbolInformation(node, context);
					}
					catch (RequiredCompleteAstException e) {
						throw e;
					}
					catch (Exception e) {
						log.error("error extracting symbol information in project '" + context.getProject().getElementName() + "' - for docURI '" + context.getDocURI() + "' - on node: " + node.toString(), e);
					}
	
					return super.visit(node);
				}
	
				@Override
				public boolean visit(NormalAnnotation node) {
					try {
						extractSymbolInformation(node, context);
					}
					catch (RequiredCompleteAstException e) {
						throw e;
					}
					catch (Exception e) {
						log.error("error extracting symbol information in project '" + context.getProject().getElementName() + "' - for docURI '" + context.getDocURI() + "' - on node: " + node.toString(), e);
					}
	
					return super.visit(node);
				}
	
				@Override
				public boolean visit(MarkerAnnotation node) {
					try {
						extractSymbolInformation(node, context);
					}
					catch (RequiredCompleteAstException e) {
						throw e;
					}
					catch (Exception e) {
						log.error("error extracting symbol information in project '" + context.getProject().getElementName() + "' - for docURI '" + context.getDocURI() + "' - on node: " + node.toString(), e);
					}
	
					return super.visit(node);
				}
			});
		}
		catch (RequiredCompleteAstException e) {
			if (!context.isFullAst()) {
				context.getNextPassFiles().add(context.getFile());
				context.resetDocumentRelatedElements(context.getDocURI());
			}
			else {
				log.error("Complete AST required but it is complete already. Analyzing ", context.getDocURI());
			}
		}
			
		if (includeReconcile) {
			reconcile(context, reconcilingIndex);
		}
		
		dependencyTracker.update(context.getFile(), context.getDependencies());;
	}

	private void reconcile(SpringIndexerJavaContext context, ReconcilingIndex reconcilingIndex) {
		// reconciling
		IProblemCollector problemCollector = new IProblemCollector() {
			
			List<ReconcileProblem> problems = new ArrayList<>();
			
			@Override
			public void beginCollecting() {
				problems.clear();
			}
			
			@Override
			public void accept(ReconcileProblem problem) {
				problems.add(problem);
			}

			@Override
			public void endCollecting() {
				for (ReconcileProblem p : problems) {
					context.getProblemCollector().accept(p);
				}
			}
		};

		try {

			problemCollector.beginCollecting();

			List<SpringIndexElement> createdElements = context.getGeneratedIndexElements().stream()
					.filter(cachedIndexElement -> cachedIndexElement.getDocURI().equals(context.getDocURI()))
					.map(cachedIndexElement -> cachedIndexElement.getIndexElement())
					.toList();

			ReconcilingContext reconcilingContext = new ReconcilingContext(context.getDocURI(), problemCollector, context.isFullAst(), context.isIndexComplete(), createdElements, reconcilingIndex);

			reconciler.reconcile(context.getProject(), URI.create(context.getDocURI()), context.getCu(), reconcilingContext);
			
			for (String dependency : reconcilingContext.getDependencies()) {
				context.addDependency(dependency);
			}
			
			context.getResult().markForAffectedFilesIndexing(reconcilingContext.getMarkedForAffectedFilesIndexing());
			
			problemCollector.endCollecting();
			
		} catch (RequiredCompleteAstException e) {
			if (!context.isFullAst()) {
				// Let problems be found in the next pass, don't add the problems to the aggregate problems collector to not duplicate them with the next pass
				context.getNextPassFiles().add(context.getFile());
				context.resetDocumentRelatedElements(context.getDocURI());
			} else {
				problemCollector.endCollecting();
				log.error("Complete AST required but it is complete already. Parsing ", context.getDocURI());
			}
		} catch (RequiredCompleteIndexException e) {
			context.getResult().markForReconcilingWithCompleteIndex(context.getFile(), context.getLastModified());
			log.error("Complete AST required but it is complete already. Parsing ", context.getDocURI());
		}
	}

	private void extractSymbolInformation(TypeDeclaration typeDeclaration, final SpringIndexerJavaContext context) throws Exception {
		Collection<SymbolProvider> providers = symbolProviders.getAll();
		if (!providers.isEmpty()) {
			TextDocument doc = DocumentUtils.getTempTextDocument(context.getDocURI(), context.getDocRef(), context.getContent());
			for (SymbolProvider provider : providers) {
				provider.addSymbols(typeDeclaration, context, doc);
			}
		}
	}

	private void extractSymbolInformation(RecordDeclaration typeDeclaration, final SpringIndexerJavaContext context) throws Exception {
		Collection<SymbolProvider> providers = symbolProviders.getAll();
		if (!providers.isEmpty()) {
			TextDocument doc = DocumentUtils.getTempTextDocument(context.getDocURI(), context.getDocRef(), context.getContent());
			for (SymbolProvider provider : providers) {
				provider.addSymbols(typeDeclaration, context, doc);
			}
		}
	}

	private void extractSymbolInformation(MethodDeclaration methodDeclaration, final SpringIndexerJavaContext context) throws Exception {
		Collection<SymbolProvider> providers = symbolProviders.getAll();
		if (!providers.isEmpty()) {
			TextDocument doc = DocumentUtils.getTempTextDocument(context.getDocURI(), context.getDocRef(), context.getContent());
			for (SymbolProvider provider : providers) {
				provider.addSymbols(methodDeclaration, context, doc);
			}
		}
	}

	private void extractSymbolInformation(PackageDeclaration packageDeclaration, final SpringIndexerJavaContext context) throws Exception {
		Collection<SymbolProvider> providers = symbolProviders.getAll();
		if (!providers.isEmpty()) {
			TextDocument doc = DocumentUtils.getTempTextDocument(context.getDocURI(), context.getDocRef(), context.getContent());
			for (SymbolProvider provider : providers) {
				provider.addSymbols(packageDeclaration, context, doc);
			}
		}
	}

	private void extractSymbolInformation(Annotation node, final SpringIndexerJavaContext context) throws Exception {
		IAnnotationBinding annotationBinding = node.resolveAnnotationBinding();

		if (annotationBinding != null) {
			
			ITypeBinding typeBinding = annotationBinding.getAnnotationType();
			
			// symbol and index scanning
			List<ITypeBinding> metaAnnotations = new ArrayList<>();
			AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(node);
			for (Iterator<IAnnotationBinding> itr = annotationHierarchies.iterator(typeBinding); itr.hasNext();) {
				IAnnotationBinding ab = itr.next();
				//
				// If meta annotations of the current annotation is a "sub-type" of one of the annotations from symbol providers then add it to meta annotations
				//
				if (annotationHierarchies.isAnnotatedWithAnnotationByBindingKey(ab, symbolProviders::containsKey)) {
					metaAnnotations.add(ab.getAnnotationType());
				}
			}
			
			Collection<SymbolProvider> providers = symbolProviders.get(annotationHierarchies, annotationBinding);
			if (!providers.isEmpty()) {
				TextDocument doc = DocumentUtils.getTempTextDocument(context.getDocURI(), context.getDocRef(), context.getContent());
				for (SymbolProvider provider : providers) {
					provider.addSymbols(node, typeBinding, metaAnnotations, context, doc);
				}
			} else {
				WorkspaceSymbol symbol = provideDefaultSymbol(node, context);
				if (symbol != null) {
					context.getGeneratedSymbols().add(new CachedSymbol(context.getDocURI(), context.getLastModified(), symbol));
					
//					SimpleSymbolElement symbolIndexElement = new SimpleSymbolElement(symbol);
//					SpringIndexElement parentIndexElement = context.getNearestIndexElementForNode(node.getParent());
//					if (parentIndexElement != null) {
//						parentIndexElement.addChild(symbolIndexElement);
//					}
//					else {
//						context.getBeans().add(new CachedBean(context.getDocURI(), symbolIndexElement));
//					}
//					context.setIndexElementForASTNode(node.getParent(), symbolIndexElement);
				}
			}
			
		}
		else {
			log.debug("type binding not around: " + context.getDocURI() + " - " + node.toString());
		}
	}

	private WorkspaceSymbol provideDefaultSymbol(Annotation node, final SpringIndexerJavaContext context) {
		try {
			ITypeBinding type = node.resolveTypeBinding();
			if (type != null) {
				String qualifiedName = type.getQualifiedName();
				if (qualifiedName != null
						&& ((qualifiedName.startsWith("org.springframework") && !qualifiedName.startsWith("org.springframework.lang"))
							|| Annotations.JAKARTA_ANNOTATIONS.contains(qualifiedName))) {
					TextDocument doc = DocumentUtils.getTempTextDocument(context.getDocURI(), context.getDocRef(), context.getContent());
					return DefaultSymbolProvider.provideDefaultSymbol(node, doc);
				}
			}
		}
		catch (RequiredCompleteAstException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("error creating default symbol in project '" + context.getProject().getElementName() + "' - for docURI '" + context.getDocURI() + "' - on node: " + node.toString(), e);
		}

		return null;
	}

	public static ASTParserCleanupEnabled createParser(IJavaProject project, AnnotationHierarchies annotationHierarchies, boolean ignoreMethodBodies) throws Exception {
		String[] classpathEntries = getClasspathEntries(project);
		String[] sourceEntries = getSourceEntries(project);
		String complianceJavaVersion = getComplianceJavaVersion(project.getClasspath().getJre() == null ? null : project.getClasspath().getJre().version());
		
		return new ASTParserCleanupEnabled(classpathEntries, sourceEntries, complianceJavaVersion, annotationHierarchies, ignoreMethodBodies);
	}

	private static String getComplianceJavaVersion(String javaVersion) {
		String complianceLevel = extractComplianceVersion(javaVersion);
		// Currently the java version in the classpath seems to be 1.8, 17, 21 etc.
		return complianceLevel == null || complianceLevel.isBlank() ? JavaCore.VERSION_25 : complianceLevel;
	}
	
	private static String extractComplianceVersion(String versionString) {
        if (versionString != null && versionString.contains(".")) {
            String[] parts = versionString.split("\\.");
            if ("1".equals(parts[0])) {
    			if (parts.length > 1) {
    				return "%s.%s".formatted(parts[0], parts[1]);
    			}
    		} else {
    			String version = parts[0];
    			int idx = version.indexOf('+');
    			return idx >= 0 ? version.substring(0, idx) : version;
    		}
        }
        return null;
    }


	private static String[] getClasspathEntries(IJavaProject project) throws Exception {
		IClasspath classpath = project.getClasspath();
		Stream<File> classpathEntries = IClasspathUtil.getAllBinaryRoots(classpath).stream();
		return classpathEntries
				.filter(file -> file.exists())
				.map(file -> file.getAbsolutePath())
				.toArray(String[]::new);
	}

	private static String[] getSourceEntries(IJavaProject project) throws Exception {
		IClasspath classpath = project.getClasspath();
		Stream<File> sourceEntries = IClasspathUtil.getSourceFolders(classpath);
		return sourceEntries
				.filter(file -> file.exists())
				.map(file -> file.getAbsolutePath())
				.toArray(String[]::new);
	}
	
	private Stream<File> foldersToScan(IJavaProject project) {
		IClasspath classpath = project.getClasspath();
		return scanTestJavaSources ? IClasspathUtil.getProjectJavaSourceFolders(classpath)
				: IClasspathUtil.getProjectJavaSourceFoldersWithoutTests(classpath);
	}

	private String[] getFiles(IJavaProject project) throws Exception {
		return foldersToScan(project)
			.flatMap(folder -> {
				try {
					return Files.walk(folder.toPath());
				} catch (IOException e) {
					log.error("{}", e);
					return Stream.empty();
				}
			})
			.filter(path -> isInterestedIn(path.getFileName().toString()))
			.filter(Files::isRegularFile)
			.map(path -> path.toAbsolutePath().toString())
			.toArray(String[]::new);
	}

	private IndexCacheKey getCacheKey(IJavaProject project, String elementType) {
		IClasspath classpath = project.getClasspath();
		Stream<File> classpathEntries = IClasspathUtil.getBinaryRoots(classpath, cpe -> !Classpath.ENTRY_KIND_SOURCE.equals(cpe.getKind())).stream();

		String classpathIdentifier = classpathEntries
				.filter(file -> file.exists())
				.map(file -> file.getAbsolutePath() + "#" + file.lastModified())
				.collect(Collectors.joining(","));

		return new IndexCacheKey(project.getElementName(), "java", elementType, DigestUtils.md5Hex(GENERATION + "-" + validationSeveritySettings.toString() + "-" + classpathIdentifier).toUpperCase());
	}
	
	private List<String[]> createChunks(String[] sourceArray, int chunkSize) {

		int numberOfChunks = (int) Math.ceil((double) sourceArray.length / chunkSize);
		List<String[]> result = new ArrayList<>();

		for (int i = 0; i < numberOfChunks; i++) {
			int startIdx = i * chunkSize;
			int endIdx = Math.min(startIdx + chunkSize, sourceArray.length);

			String[] chunk = new String[endIdx - startIdx];
			System.arraycopy(sourceArray, startIdx, chunk, 0, endIdx - startIdx);
			
			result.add(chunk);
		}
		
		return result;
	}

	public void setScanTestJavaSources(boolean scanTestJavaSources) {
		if (this.scanTestJavaSources != scanTestJavaSources) {
			this.scanTestJavaSources = scanTestJavaSources;
			if (scanTestJavaSources) {
				addTestsJavaSourcesToIndex();
			} else {
				removeTestJavaSourcesFromIndex();
			}
		}
	}
	
	private void removeTestJavaSourcesFromIndex() {
		for (IJavaProject project : projectFinder.all()) {
			Path[] testJavaFiles = IClasspathUtil.getProjectTestJavaSources(project.getClasspath()).flatMap(folder -> {
				try {
					return Files.walk(folder.toPath());
				} catch (IOException e) {
					log.error("{}", e);
					return Stream.empty();
				}
			})
			.filter(path -> path.getFileName().toString().endsWith(".java"))
			.filter(Files::isRegularFile).toArray(Path[]::new);

			try {
				for (Path path : testJavaFiles) {
					URI docUri = UriUtil.toUri(path.toFile());
					symbolHandler.removeSymbols(project, docUri.toASCIIString()); 
				}
			} catch (Exception e) {
				log.error("{}", e);
			}
		}
	}
	
	private void addTestsJavaSourcesToIndex() {
		for (IJavaProject project : projectFinder.all()) {
			Path[] testJavaFiles = IClasspathUtil.getProjectTestJavaSources(project.getClasspath()).flatMap(folder -> {
				try {
					return Files.walk(folder.toPath());
				} catch (IOException e) {
					log.error("{}", e);
					return Stream.empty();
				}
			})
			.filter(path -> path.getFileName().toString().endsWith(".java"))
			.filter(Files::isRegularFile).toArray(Path[]::new);
			
			try {
				for (Path path : testJavaFiles) {
					File file = path.toFile();
					URI docUri = UriUtil.toUri(file);
					String content = FileUtils.readFileToString(file, Charset.defaultCharset());
					scanFile(project, DocumentDescriptor.createFromUri(docUri.toASCIIString()), content);
				}
			} catch (Exception e) {
				log.error("{}", e);
			}
		}
	}

	public void setScanChunkSize(int chunkSize) {
		this.scanChunkSize = chunkSize;
	}

	public void setValidationSeveritySettings(JsonObject javaValidationSettingsJson) {
		this.validationSeveritySettings = javaValidationSettingsJson;
	}

	public void setFileScanListener(FileScanListener fileScanListener) {
		this.fileScanListener = fileScanListener;
	}

	private void fileScannedEvent(String file) {
		if (fileScanListener != null) {
			fileScanListener.fileScanned(file);
		}
	}

}
