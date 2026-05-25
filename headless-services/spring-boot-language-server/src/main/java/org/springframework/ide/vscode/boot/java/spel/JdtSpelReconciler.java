/*******************************************************************************
 * Copyright (c) 2024, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.spel;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.springframework.ide.vscode.boot.java.SpelProblemType;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.ReconcilingContext;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;

public class JdtSpelReconciler implements JdtAstReconciler {
	
	final private AnnotationParamSpelExtractor[] spelExtractors = AnnotationParamSpelExtractor.SPEL_EXTRACTORS;
	final private SpelReconciler spelReconciler;

	public JdtSpelReconciler(SpelReconciler spelReconciler) {
		this.spelReconciler = spelReconciler;
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return SpringProjectUtil.hasDependencyStartingWith(project, "spring-expression", null);
	}

	@Override
	public ProblemType getProblemType() {
		return SpelProblemType.JAVA_SPEL_EXPRESSION_SYNTAX;
	}

	@Override
	public Optional<ASTVisitor> createVisitor(IJavaProject project, URI docURI, CompilationUnit cu, ReconcilingContext context) {
		return Optional.of(new ASTVisitor() {
			@Override
			public boolean visit(SingleMemberAnnotation node) {
				Arrays.stream(spelExtractors)
					.map(e -> e.getSpelRegion(node))
					.filter(o -> o.isPresent())
					.map(o -> o.get())
					.forEach(snippet -> spelReconciler.reconcile(snippet.getText(), snippet::toSingleJavaRange, context.getProblemCollector()));
				return super.visit(node);
			}
			
			@Override
			public boolean visit(NormalAnnotation node) {
				Arrays.stream(spelExtractors)
					.map(e -> e.getSpelRegion(node))
					.filter(o -> o.isPresent())
					.map(o -> o.get())
					.forEach(snippet -> spelReconciler.reconcile(snippet.getText(), snippet::toSingleJavaRange, context.getProblemCollector()));
				return super.visit(node);
			}

		});
	}

}
