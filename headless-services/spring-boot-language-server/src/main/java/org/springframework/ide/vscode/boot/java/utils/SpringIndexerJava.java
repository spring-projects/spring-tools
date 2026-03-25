/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DocumentSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.index.SpringIndexToSymbolsConverter;
import org.springframework.ide.vscode.boot.index.cache.IndexCache;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.beans.CachedIndexElement;
import org.springframework.ide.vscode.boot.java.handlers.SpringComponentIndexer;
import org.springframework.ide.vscode.boot.java.reconcilers.CachedDiagnostics;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.ReconcilingIndex;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.PercentageProgressTask;
import org.springframework.ide.vscode.commons.languageserver.ProgressService;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
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

	private static final IProblemCollector NO_OP_PROBLEM_COLLECTOR = new IProblemCollector() {
		@Override public void beginCollecting() {}
		@Override public void endCollecting() {}
		@Override public void accept(ReconcileProblem problem) {}
	};

	// whenever the implementation of the indexer changes in a way that the stored data in the cache is no longer valid,
	// we need to change the generation - this will result in a re-indexing due to no up-to-date cache data being found
	private static final String GENERATION = "GEN-35";
	private static final String INDEX_FILES_TASK_ID = "index-java-source-files-task-";

	private final SymbolHandler symbolHandler;
	private final SpringIndexerJavaReconcileService reconcileService;
	private final SpringIndexerJavaCacheHelper cacheHelper;
	private final JavaProjectFinder projectFinder;
	private final ProgressService progressService;
	private final CompilationUnitCache cuCache;
	
	private boolean scanTestJavaSources = false;
	private int scanChunkSize = 1000;

	private FileScanListener fileScanListener = null; //used by test code only

	private final SpringIndexerJavaDependencyTracker dependencyTracker = new SpringIndexerJavaDependencyTracker();
	private final BiFunction<TextDocument, BiConsumer<String, Diagnostic>, IProblemCollector> problemCollectorCreator;
	private final SpringIndexerJavaAstScanner astScanner;

	
	public SpringIndexerJava(SymbolHandler symbolHandler, SpringComponentIndexer[] componentIndexers, IndexCache cache,
			JavaProjectFinder projectFinder, ProgressService progressService, JdtReconciler jdtReconciler,
			BiFunction<TextDocument, BiConsumer<String, Diagnostic>, IProblemCollector> problemCollectorCreator,
			JsonObject validationSeveritySettings, CompilationUnitCache cuCache) {
		
		this.symbolHandler = symbolHandler;
		this.cacheHelper = new SpringIndexerJavaCacheHelper(cache, GENERATION, validationSeveritySettings);
		this.reconcileService = new SpringIndexerJavaReconcileService(jdtReconciler, problemCollectorCreator,
				cacheHelper, symbolHandler, dependencyTracker);
		this.projectFinder = projectFinder;
		this.progressService = progressService;
		
		this.problemCollectorCreator = problemCollectorCreator;
		this.cuCache = cuCache;
		this.astScanner = new SpringIndexerJavaAstScanner(componentIndexers, dependencyTracker,
				reconcileService::reconcileAfterScan);
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
		cacheHelper.removeProjectCaches(project);
		
		this.dependencyTracker.removeProject(project);
	}

	@Override
	public void updateFile(IJavaProject project, DocumentDescriptor updatedDoc, String content) throws Exception {
		if (updatedDoc != null && shouldProcessDocument(project, updatedDoc.getDocURI())) {
			if (cacheHelper.isIndexOrDiagnosticsCacheOutdated(project, updatedDoc.getDocURI(), updatedDoc.getLastModified())) {

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
			DocumentDescriptor[] docs = cacheHelper.filterDocumentsNeedingRefresh(project, updatedDocs, this::shouldProcessDocument);

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
			List<String> filesToReconcile = reconcileService.identifyFilesToReconcileForPropertyChanges(project,
					changedPropertyFiles);
			List<DocumentDescriptor> filesWithTimestamps = DocumentDescriptor.createFromUris(filesToReconcile);

			try {
				reconcileService.reconcileWithCompleteIndex(project, filesWithTimestamps);
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
		
		cacheHelper.removeFilesFromCaches(project, files);
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

				TextDocument doc = DocumentUtils.createTempTextDocument(docURI, content);
				SpringIndexerJavaContext context = new SpringIndexerJavaContext(project, cu, docURI, file,
						0, doc, content, NO_OP_PROBLEM_COLLECTOR, new ArrayList<>(), true, true, result);

				astScanner.scanAST(context, false, new ReconcilingIndex());

				List<SpringIndexElement> indexElements = result.getGeneratedIndexElements().stream()
					.map(cachedBean -> cachedBean.getIndexElement())
					.toList();

				return SpringIndexToSymbolsConverter.createDocumentSymbols(indexElements);
			});
		}
		
		return Collections.emptyList();
	}

	/**
	 * checks whether the given doc URI is from inside the given projects "interesting" folders
	 * This is used to filter out file updates for files that are outside of the source folders (for example)
	 */
	private boolean shouldProcessDocument(IJavaProject project, String docURI) {
		try {
			Path path = new File(new URI(docURI)).toPath();
			return foldersToScan(project)
					.anyMatch(sourceFolder -> path.startsWith(sourceFolder.toPath()));
		} catch (URISyntaxException e) {
			log.info("shouldProcessDocument - docURI syntax problem: {}", docURI);
			return false;
		}
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
		ASTParserCleanupEnabled parser = SpringIndexerJavaParserUtils.createParser(project, new AnnotationHierarchies(), ignoreMethodBodies);
		
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
			
			BiConsumer<String, Diagnostic> diagnosticsAggregator =
					(uri, diagnostic) -> result.getGeneratedDiagnostics().add(new CachedDiagnostics(uri, diagnostic));

			TextDocument doc = DocumentUtils.createTempTextDocument(docURI, content);

			IProblemCollector problemCollector = problemCollectorCreator.apply(doc, diagnosticsAggregator);
			SpringIndexerJavaContext context = new SpringIndexerJavaContext(project, cu, docURI, file,
					lastModified, doc, content, problemCollector, new ArrayList<>(), !ignoreMethodBodies, false, result);

			astScanner.scanAST(context, true, reconcilingIndex);

			result.publishResults(symbolHandler);

			// update cache
			cacheHelper.updateAfterSingleFileScan(project, file, lastModified, result, context.getDependencies());
			
			Set<String> scannedFiles = new HashSet<>();
			scannedFiles.add(file);
			fileScannedEvent(file);

			reconcileService.reconcileWithCompleteIndex(project, result.getMarkedForReconcilingWithCompleteIndex());

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
		
		BiConsumer<String, Diagnostic> diagnosticsAggregator =
				(uri, diagnostic) -> result.getGeneratedDiagnostics().add(new CachedDiagnostics(uri, diagnostic));

		FileASTRequestor requestor = new FileASTRequestor() {
			@Override
			public void acceptAST(String sourceFilePath, CompilationUnit cu) {
				File file = new File(sourceFilePath);
				String docURI = UriUtil.toUri(file).toASCIIString();
				
				DocumentDescriptor updatedDoc = updatedDocs.get(docURI);
				long lastModified = updatedDoc.getLastModified();
				
				TextDocument doc = DocumentUtils.createTempTextDocument(docURI);

				IProblemCollector problemCollector = problemCollectorCreator.apply(doc, diagnosticsAggregator);

				SpringIndexerJavaContext context = new SpringIndexerJavaContext(project, cu, docURI, sourceFilePath,
						lastModified, doc, null, problemCollector, new ArrayList<>(), !ignoreMethodBodies, false, result);
				
				astScanner.scanAST(context, true, reconcilingIndex);
				
				dependencies.putAll(sourceFilePath, context.getDependencies());
				scannedTypes.addAll(context.getScannedTypes());

				fileScannedEvent(sourceFilePath);
			}
		};

		AnnotationHierarchies annotationHierarchies = new AnnotationHierarchies();
		List<String[]> chunks = SpringIndexerJavaParserUtils.createChunks(javaFiles, this.scanChunkSize);
		for(int i = 0; i < chunks.size(); i++) {
			log.info("scan java files, AST parse, chunk {} for files: {}", i, javaFiles.length);
			
			ASTParserCleanupEnabled parser = SpringIndexerJavaParserUtils.createParser(project, annotationHierarchies, ignoreMethodBodies);
			parser.createASTs(chunks.get(i), null, new String[0], requestor, null);
			parser.cleanup();
		}
		
		// update the cache
		cacheHelper.updateAfterBatchScan(project, javaFiles, lastModified, result, dependencies);
		
		result.publishResults(symbolHandler);
		reconcileService.reconcileWithCompleteIndex(project, result.getMarkedForReconcilingWithCompleteIndex());

		return new ScanFilesInternallyResult(scannedTypes, result);
	}
	
	private static record ScanFilesInternallyResult(Set<String> scannedTypes, SpringIndexerJavaScanResult scanResult) {};


	private void scanAffectedFiles(IJavaProject project, Set<String> changedTypes, Set<String> alreadyScannedFiles, Set<String> alreadyMarkedForAffectedFilesIndexing) throws Exception {
		log.info("Start scanning affected files for types {}", changedTypes);
		
		Multimap<String, String> dependencies = dependencyTracker.getAllDependencies(project);
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

		log.info("Finished scanning affected files {}", filesToScan);
	}

	private void scanFiles(IJavaProject project, String[] javaFiles, boolean clean) throws Exception {
		
		SpringIndexerJavaScanResult result = null;
		
		// check cached elements first
		SpringIndexerJavaCacheHelper.FullScanRetrieveResult cached = cacheHelper.retrieveForFullScan(project, javaFiles);
		Pair<CachedIndexElement[], Multimap<String, String>> cachedIndexElements = cached.indexElements();
		Pair<CachedDiagnostics[], Multimap<String, String>> cachedDiagnostics = cached.diagnostics();

		if (!clean && cachedIndexElements != null && cachedDiagnostics != null) {
			// use cached data

			result = new SpringIndexerJavaScanResult(project, javaFiles, symbolHandler, cachedIndexElements.getLeft(), cachedDiagnostics.getLeft());
			this.dependencyTracker.restore(project, cachedIndexElements.getRight());

			log.info("scan java files used cached data: {} - no. of cached symbols retrieved: {}", project.getElementName(), result.getGeneratedIndexElements().size());
			log.info("scan java files restored cached dependency data: {} - no. of cached dependencies: {}", cachedIndexElements.getRight().size());

		}
		else {
			// continue scanning everything

			result = new SpringIndexerJavaScanResult(project, javaFiles);
			ReconcilingIndex reconcilingIndex = new ReconcilingIndex();

			final SpringIndexerJavaScanResult finalResult = result;
			BiConsumer<String, Diagnostic> diagnosticsAggregator =
					(uri, diagnostic) -> finalResult.getGeneratedDiagnostics().add(new CachedDiagnostics(uri, diagnostic));
			
			List<String[]> chunks = SpringIndexerJavaParserUtils.createChunks(javaFiles, this.scanChunkSize);
			AnnotationHierarchies annotations = new AnnotationHierarchies();
			for (int i = 0; i < chunks.size(); i++) {

				log.info("scan java files, AST parse, chunk {} for files: {}", i, javaFiles.length);
	            String[] pass2Files = scanFiles(project, annotations, chunks.get(i), diagnosticsAggregator, true, result, reconcilingIndex);

	            if (pass2Files.length > 0) {
					log.info("scan java files, AST parse, pass 2, chunk {} for files: {}", i, javaFiles.length);
					scanFiles(project, annotations, pass2Files, diagnosticsAggregator, false, result, reconcilingIndex);
				}
	        }
			
			log.info("scan java files done, number of index elements created: {}", result.getGeneratedIndexElements().size());

			cacheHelper.storeFullScanResults(project, javaFiles, result, dependencyTracker.getAllDependencies(project));
		}
		
		result.publishResults(symbolHandler);
		reconcileService.reconcileWithCompleteIndex(project, result.getMarkedForReconcilingWithCompleteIndex());

		reconcileService.logReconcilingStats();
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

				TextDocument doc = DocumentUtils.createTempTextDocument(docURI);

				IProblemCollector problemCollector = problemCollectorCreator.apply(doc, diagnosticsAggregator);

				SpringIndexerJavaContext context = new SpringIndexerJavaContext(project, cu, docURI, sourceFilePath,
						lastModified, doc, null, problemCollector, nextPassFiles, !ignoreMethodBodies, false, result);

				astScanner.scanAST(context, true, reconcilingIndex);
				progressTask.increment();
			}
		};

		ASTParserCleanupEnabled parser = SpringIndexerJavaParserUtils.createParser(project, annotations, ignoreMethodBodies);
		try {
			parser.createASTs(javaFiles, null, new String[0], requestor, null);
			return nextPassFiles.toArray(String[]::new);
		} catch (Throwable t) {
			log.error("Failed to index project '%s'".formatted(project.getElementName()), t);
			return new String[0];
		}
		finally {
			parser.cleanup();
			progressTask.done();
		}
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
		this.cacheHelper.setValidationSeveritySettings(javaValidationSettingsJson);
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
