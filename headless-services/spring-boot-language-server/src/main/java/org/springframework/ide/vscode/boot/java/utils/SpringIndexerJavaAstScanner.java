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

import java.util.function.BiConsumer;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.handlers.SpringComponentIndexer;
import org.springframework.ide.vscode.boot.java.reconcilers.ReconcilingIndex;
import org.springframework.ide.vscode.boot.java.reconcilers.RequiredCompleteAstException;

/**
 * Walks compilation unit ASTs to collect Spring index symbols and optionally run reconciliation.
 *
 * @author Martin Lippert
 */
public class SpringIndexerJavaAstScanner {

	private static final Logger log = LoggerFactory.getLogger(SpringIndexerJavaAstScanner.class);

	private final SpringComponentIndexer[] componentIndexers;
	private final SpringIndexerJavaDependencyTracker dependencyTracker;
	private final BiConsumer<SpringIndexerJavaContext, ReconcilingIndex> reconcileAction;

	public SpringIndexerJavaAstScanner(SpringComponentIndexer[] componentIndexers,
			SpringIndexerJavaDependencyTracker dependencyTracker,
			BiConsumer<SpringIndexerJavaContext, ReconcilingIndex> reconcileAction) {
		this.componentIndexers = componentIndexers;
		this.dependencyTracker = dependencyTracker;
		this.reconcileAction = reconcileAction;
	}

	public void scanAST(SpringIndexerJavaContext context, boolean includeReconcile, ReconcilingIndex reconcilingIndex) {
		scanAST(context, includeReconcile, reconcilingIndex, true);
	}

	/**
	 * @param updateDependencyTracking when false, skips updating {@link SpringIndexerJavaDependencyTracker}
	 *        (e.g. ad-hoc document symbols from editor buffers must not overwrite disk-indexed dependencies).
	 */
	public void scanAST(SpringIndexerJavaContext context, boolean includeReconcile, ReconcilingIndex reconcilingIndex,
			boolean updateDependencyTracking) {
		try {
			context.getCu().accept(new ASTVisitor() {

				@Override
				public boolean visit(PackageDeclaration node) {
					extractSafely(context, node.toString(), () -> extractSymbolInformation(node, context));
					return super.visit(node);
				}

				@Override
				public boolean visit(TypeDeclaration node) {
					extractSafely(context, node.toString(), () -> {
						context.addScannedType(node.resolveBinding());
						extractSymbolInformation(node, context);
					});
					return super.visit(node);
				}

				@Override
				public boolean visit(RecordDeclaration node) {
					extractSafely(context, node.toString(), () -> {
						context.addScannedType(node.resolveBinding());
						extractSymbolInformation(node, context);
					});
					return super.visit(node);
				}

				@Override
				public boolean visit(AnnotationTypeDeclaration node) {
					extractSafely(context, node.toString(), () -> {
						context.addScannedType(node.resolveBinding());
						extractSymbolInformation(node, context);
					});
					return super.visit(node);
				}

				@Override
				public boolean visit(MethodDeclaration node) {
					extractSafely(context, node.toString(), () -> extractSymbolInformation(node, context));
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
			reconcileAction.accept(context, reconcilingIndex);
		}

		if (updateDependencyTracking) {
			dependencyTracker.update(context.getProject(), context.getFile(), context.getDependencies());
		}
	}

	private void extractSafely(SpringIndexerJavaContext context, String nodeDescription, SymbolExtraction extraction)
			throws RequiredCompleteAstException {
		try {
			extraction.run();
		}
		catch (RequiredCompleteAstException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("error extracting symbol information in project '{}' - for docURI '{}' - on node: {}",
					context.getProject().getElementName(), context.getDocURI(), nodeDescription, e);
		}
	}

	private void extractSymbolInformation(TypeDeclaration typeDeclaration, SpringIndexerJavaContext context) throws Exception {
		for (SpringComponentIndexer indexer : componentIndexers) {
			indexer.index(typeDeclaration, context);
		}
	}

	private void extractSymbolInformation(RecordDeclaration recordDeclaration, SpringIndexerJavaContext context) throws Exception {
		for (SpringComponentIndexer indexer : componentIndexers) {
			indexer.index(recordDeclaration, context);
		}
	}

	private void extractSymbolInformation(AnnotationTypeDeclaration annotationTypeDeclaration, SpringIndexerJavaContext context) throws Exception {
		for (SpringComponentIndexer indexer : componentIndexers) {
			indexer.index(annotationTypeDeclaration, context);
		}
	}

	private void extractSymbolInformation(MethodDeclaration methodDeclaration, SpringIndexerJavaContext context) throws Exception {
		for (SpringComponentIndexer indexer : componentIndexers) {
			indexer.index(methodDeclaration, context);
		}
	}

	private void extractSymbolInformation(PackageDeclaration packageDeclaration, SpringIndexerJavaContext context) throws Exception {
		for (SpringComponentIndexer indexer : componentIndexers) {
			indexer.index(packageDeclaration, context);
		}
	}

	@FunctionalInterface
	private interface SymbolExtraction {
		void run() throws Exception;
	}

}
