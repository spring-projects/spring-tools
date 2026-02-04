/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.ide.vscode.boot.java.Boot4JavaProblemType;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigJavaIndexer;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;

public class WebApiVersionStrategyPathSegmentReconciler implements JdtAstReconciler {

	private static final String PROBLEM_LABEL = "API versioninig path segment strategy should not be mixed with other strategies";

	public WebApiVersionStrategyPathSegmentReconciler() {
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return SpringProjectUtil.libraryVersionGreaterOrEqual(SpringProjectUtil.SPRING_WEB, 7, 0, 0).test(project);
	}

	@Override
	public ProblemType getProblemType() {
		return Boot4JavaProblemType.API_VERSIONING_VIA_PATH_SEGMENT_CONFIGURED_IN_COMBINATION;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		return new ASTVisitor() {

			@Override
			public boolean visit(TypeDeclaration type) {
				ITypeBinding webConfigType = WebConfigJavaIndexer.getWebConfig(type);
				if (webConfigType == null) {
					return super.visit(type);
				}
				
				MethodDeclaration configureVersioningMethod = WebConfigJavaIndexer.findMethod(type, webConfigType, WebConfigJavaIndexer.CONFIGURE_API_VERSIONING_METHOD);
				if (configureVersioningMethod == null) {
					return super.visit(type);
				}
				
				if (!context.isCompleteAst()) { // needs full method bodies to continue
					throw new RequiredCompleteAstException();
				}
				
				Block methodBody = configureVersioningMethod.getBody();
				if (methodBody == null) {
					return super.visit(type);
				}

				analyzeMethodBody(methodBody, context);

				return super.visit(type);
			}

		};
	}
	
	private void analyzeMethodBody(Block body, ReconcilingContext context) {
		AtomicReference<MethodInvocation> usePathSegmentInvocation = new AtomicReference<>();
		AtomicBoolean useAdditionalVersioningStrategy = new AtomicBoolean(false);

		body.accept(new ASTVisitor() {
			
			@Override
			public boolean visit(MethodInvocation methodInvocation) {
				if (methodInvocation.getName() == null) {
					return super.visit(methodInvocation);
				}

				String methodName = methodInvocation.getName().toString();
				
				if (WebConfigJavaIndexer.USE_PATH_SEGMENT.equals(methodName)) {
					usePathSegmentInvocation.set(methodInvocation);
				}
				else if (WebConfigJavaIndexer.VERSIONING_CONFIG_METHODS.contains(methodName)) {
					useAdditionalVersioningStrategy.set(true);
				}
				
				return super.visit(methodInvocation);
			}
		});
		
		if (usePathSegmentInvocation.get() != null && useAdditionalVersioningStrategy.get()) {
			ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), PROBLEM_LABEL,
					usePathSegmentInvocation.get().getStartPosition(), usePathSegmentInvocation.get().getLength());

			context.getProblemCollector().accept(problem);
		}

	}
	
}
