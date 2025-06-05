/*******************************************************************************
 * Copyright (c) 2016, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.handlers;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.reconcilers.JavaReconciler;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IReconcileEngine;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.util.text.IDocument;

/**
 * @author Martin Lippert
 */
public class BootJavaReconcileEngine implements IReconcileEngine {
		
	private static final Logger log = LoggerFactory.getLogger(BootJavaReconcileEngine.class);

	private final JavaProjectFinder projectFinder; 
	private final JavaReconciler[] javaReconcilers;

	public BootJavaReconcileEngine(JavaProjectFinder projectFinder, JavaReconciler[] javaReconcilers) {
		this.projectFinder = projectFinder;
		this.javaReconcilers = javaReconcilers;
	}

	@Override
	public void reconcile(final IDocument doc, final IProblemCollector problemCollector) {
		IJavaProject project = projectFinder.find(new TextDocumentIdentifier(doc.getUri())).orElse(null);
		URI docUri = URI.create(doc.getUri());
		if (project != null && "file".equals(docUri.getScheme())) {
			
			try {
				problemCollector.beginCollecting();
				
				AtomicInteger count = new AtomicInteger(0);
				
				IProblemCollector recolerProblemCollector = new IProblemCollector() {
		
					@Override
					public void beginCollecting() {
					}
		
					@Override
					public synchronized void endCollecting() {
						// If count is reached then let the endCollecting be called when all completable futures resolved
						if (count.incrementAndGet() < javaReconcilers.length) {
							problemCollector.checkPointCollecting();
						}
					}
		
					@Override
					public synchronized void accept(ReconcileProblem problem) {
						problemCollector.accept(problem);
					}
					
				};
				
				for (JavaReconciler jr : javaReconcilers) {
					try {
						jr.reconcile(project, doc, recolerProblemCollector);
					} catch (Exception e) {
						log.error("", e);
					}
				}
				
//				CompletableFuture<?>[] futures = Arrays.stream(javaReconcilers)
//						.map(jr -> CompletableFuture.runAsync(() -> jr.reconcile(project, doc, recolerProblemCollector)).exceptionally(t -> null))
//						.toArray(CompletableFuture[]::new);
//				
//				CompletableFuture.allOf(futures).thenAccept(a -> problemCollector.endCollecting()).get();
				
			} catch (Exception e) {
				log.error("", e);
			} finally {
				problemCollector.endCollecting();
			}
		
		}
	}

}
