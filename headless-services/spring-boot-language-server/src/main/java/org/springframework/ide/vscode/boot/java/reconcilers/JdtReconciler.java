/*******************************************************************************
 * Copyright (c) 2022, 2025 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

public class JdtReconciler implements JavaReconciler {
	
	private static final Logger log = LoggerFactory.getLogger(JdtReconciler.class);
	
	// annotations with SpEL expression params 
	public static final String SPRING_CACHEABLE = "org.springframework.cache.annotation.Cacheable";
	public static final String SPRING_CACHE_EVICT = "org.springframework.cache.annotation.CacheEvict";
	
	public static final String SPRING_EVENT_LISTENER = "org.springframework.context.event.EventListener";
	
	public static final String SPRING_PRE_AUTHORIZE = "org.springframework.security.access.prepost.PreAuthorize";
	public static final String SPRING_PRE_FILTER = "org.springframework.security.access.prepost.PreFilter";
	public static final String SPRING_POST_AUTHORIZE = "org.springframework.security.access.prepost.PostAuthorize";
	public static final String SPRING_POST_FILTER= "org.springframework.security.access.prepost.PostFilter";
	
	public static final String SPRING_CONDITIONAL_ON_EXPRESSION = "org.springframework.boot.autoconfigure.condition.ConditionalOnExpression";
	
	private final CompilationUnitCache compilationUnitCache;
	final JdtAstReconciler[] reconcilers;
	final BootJavaConfig config;
	
	private final ConcurrentHashMap<String, List<JdtAstReconciler>> applicableReconcilersCache;
	
	private long stats_timer;
	private long stats_counter;

	public JdtReconciler(CompilationUnitCache compilationUnitCache, BootJavaConfig config, JdtAstReconciler[] reconcilers, ProjectObserver projectObserver) {
		this.compilationUnitCache = compilationUnitCache;
		this.config = config;
		this.reconcilers = reconcilers;
		
		this.stats_timer = 0;
		this.stats_counter = 0;
		
		this.applicableReconcilersCache = new ConcurrentHashMap<>();
		
		projectObserver.addListener(ProjectObserver.onAny(project -> {
			invalidateApplicableReconcilersCache(project);
		}));
		
		config.addListener(event -> {
			invalidateApplicableReconcilersCache(null);
		});
	}
	
	@Override
	public void reconcile(IJavaProject project, final IDocument doc, final IProblemCollector problemCollector) {
		if (!config.isJavaSourceReconcileEnabled()) {
			return;
		}

		final long s = System.currentTimeMillis();
		URI uri = URI.create(doc.getUri());
		compilationUnitCache.withCompilationUnit(project, uri, cu -> {
			
			if (cu != null) {

				try {
					ReconcilingContext reconcilingContext = new ReconcilingContext(doc.getUri(), problemCollector, true, true, Collections.emptyList());
					reconcile(project, URI.create(doc.getUri()), cu, reconcilingContext);
				} catch (RequiredCompleteAstException e) {
					log.error("Unexpected incomplete AST", e);
				}
				// TODO: is the index indeed complete?!? do need to react to complete index exception instead ?!?
			}

			log.info("reconciling (JDT): " + doc.getUri() + " done in " + (System.currentTimeMillis() - s) + "ms");
			return null;
		});
	}

	public ASTVisitor createCompositeVisitor(IJavaProject project, URI docURI, CompilationUnit cu, ReconcilingContext context) throws RequiredCompleteAstException {
		CompositeASTVisitor compositeVisitor = new CompositeASTVisitor();
		
		for (JdtAstReconciler reconciler : getApplicableReconcilers(project)) {
			ASTVisitor visitor = reconciler.createVisitor(project, docURI, cu, context);
			
			if (visitor != null) {
				compositeVisitor.add(visitor);
			}
		}

		return compositeVisitor;
	}
	

	public void reconcile(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context)
			throws RequiredCompleteAstException, RequiredCompleteIndexException {

		long start = System.currentTimeMillis();
		
		if (!config.isJavaSourceReconcileEnabled()) {
			return;
		}
		
		try {
			ASTVisitor compositeVisitor = createCompositeVisitor(project, docUri, cu, context);
			cu.accept(compositeVisitor);
		
//			for (JdtAstReconciler reconciler : getApplicableReconcilers(project)) {
//				try {
//					reconciler.reconcile(project, docUri, cu, problemCollector, isCompleteAst);
//				} catch (RequiredCompleteAstException e) {
//					throw e;
//				} catch (Exception e) {
//					log.error("", e);
//				}
//			}
		}
		finally {
			long end = System.currentTimeMillis();
			
			stats_counter++;
			stats_timer += (end - start);
		}
	}
	
	@Override
	public Map<IDocument, Collection<ReconcileProblem>> reconcile(IJavaProject project, List<TextDocument> docs,
			Runnable incrementProgress) {
		return Collections.emptyMap();
	}
	
	private List<JdtAstReconciler> getApplicableReconcilers(IJavaProject project) {
		return this.applicableReconcilersCache.computeIfAbsent(project.getElementName(), (name) -> {
			return computeApplicableReconcilers(project);
		});
	}

	private void invalidateApplicableReconcilersCache(IJavaProject project) {
		if (project != null) {
			this.applicableReconcilersCache.remove(project.getElementName());
		}
		else {
			this.applicableReconcilersCache.clear();
		}
	}
	
	private List<JdtAstReconciler> computeApplicableReconcilers(IJavaProject project) {
		List<JdtAstReconciler> applicableReconcilers = new ArrayList<>(reconcilers.length);
		boolean isBootProject = SpringProjectUtil.isBootProject(project);
		
		for (JdtAstReconciler r : reconcilers) {
			switch (config.getProblemApplicability(r.getProblemType())) {
			case ON:
				if (isBootProject) {
					applicableReconcilers.add(r);
				}
				break;
			case OFF:
				break;
			default: // AUTO
				if (r.isApplicable(project)) {
					applicableReconcilers.add(r);
				}
			}
		}
		return applicableReconcilers;
	}
	
	public long getStatsTimer() {
		return stats_timer;
	}
	
	public long getStatsCounter() {
		return stats_counter;
	}
	
	
}
