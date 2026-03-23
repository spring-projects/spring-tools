/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.lsp4j.Diagnostic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.reconcilers.CachedDiagnostics;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.ReconcilingContext;
import org.springframework.ide.vscode.boot.java.reconcilers.ReconcilingIndex;
import org.springframework.ide.vscode.boot.java.reconcilers.RequiredCompleteAstException;
import org.springframework.ide.vscode.boot.java.reconcilers.RequiredCompleteIndexException;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.util.UriUtil;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * Runs Spring validation reconcilers during Java indexing: per-file reconcile after AST scan
 * and a second pass when the full project index is required.
 *
 * @author Martin Lippert
 */
public class SpringIndexerJavaReconcileService {

	private static final Logger log = LoggerFactory.getLogger(SpringIndexerJavaReconcileService.class);

	private final JdtReconciler reconciler;
	private final BiFunction<TextDocument, BiConsumer<String, Diagnostic>, IProblemCollector> problemCollectorCreator;
	private final SpringIndexerJavaCacheHelper cacheHelper;
	private final SymbolHandler symbolHandler;
	private final SpringIndexerJavaDependencyTracker dependencyTracker;

	public SpringIndexerJavaReconcileService(JdtReconciler reconciler,
			BiFunction<TextDocument, BiConsumer<String, Diagnostic>, IProblemCollector> problemCollectorCreator,
			SpringIndexerJavaCacheHelper cacheHelper, SymbolHandler symbolHandler,
			SpringIndexerJavaDependencyTracker dependencyTracker) {
		this.reconciler = reconciler;
		this.problemCollectorCreator = problemCollectorCreator;
		this.cacheHelper = cacheHelper;
		this.symbolHandler = symbolHandler;
		this.dependencyTracker = dependencyTracker;
	}

	public List<String> identifyFilesToReconcileForPropertyChanges(IJavaProject project,
			List<String> changedPropertyFiles) {
		return reconciler.identifyFilesToReconcile(project, changedPropertyFiles);
	}

	public void logReconcilingStats() {
		log.info("reconciling stats - counter: " + reconciler.getStatsCounter());
		log.info("reconciling stats - timer: " + reconciler.getStatsTimer());
	}

	public void reconcileAfterScan(SpringIndexerJavaContext context, ReconcilingIndex reconcilingIndex) {
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

			ReconcilingContext reconcilingContext = new ReconcilingContext(context.getDocURI(), problemCollector,
					context.isFullAst(), context.isIndexComplete(), createdElements, reconcilingIndex);

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

	public void reconcileWithCompleteIndex(IJavaProject project, List<DocumentDescriptor> filesWithTimestamps)
			throws Exception {
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

				TextDocument doc = DocumentUtils.createTempTextDocument(docURI);

				IProblemCollector problemCollector = problemCollectorCreator.apply(doc, diagnosticsAggregator);

				SpringIndexerJavaContext context = new SpringIndexerJavaContext(project, cu, docURI, sourceFilePath,
						lastModified, doc, null, problemCollector, new ArrayList<>(), !ignoreMethodBodies, true,
						reconcilingResult);

				try {
					reconcileAfterScan(context, reconcilingIndex);

					Collection<String> dependencies = dependencyTracker.get(project, context.getFile());
					for (String dependency : context.getDependencies()) {
						dependencies.add(dependency);
					}

				} catch (Exception e) {
					log.error("problem creating temp document during re-reconciling for: " + docURI, e);
				}

			}
		};

		AnnotationHierarchies annotations = new AnnotationHierarchies();
		ASTParserCleanupEnabled parser = SpringIndexerJavaParserUtils.createParser(project, annotations, ignoreMethodBodies);
		try {
			parser.createASTs(javaFiles, null, new String[0], requestor, null);
		}
		finally {
			parser.cleanup();
		}

		cacheHelper.updateDiagnosticsAfterReconcile(project, javaFiles, modificationTimestamps,
				reconcilingResult.getGeneratedDiagnostics(), dependencyTracker.getAllDependencies(project));

		reconcilingResult.publishDiagnosticsOnly(symbolHandler);
	}

}
