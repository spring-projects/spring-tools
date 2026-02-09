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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

public class WebApiVersionStrategyDuplicatedReconciler implements JdtAstReconciler {

	private static final String PROBLEM_LABEL = "API versioninig strategy is configured multiple times with the same strategy";

	public WebApiVersionStrategyDuplicatedReconciler() {
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return SpringProjectUtil.libraryVersionGreaterOrEqual(SpringProjectUtil.SPRING_WEB, 7, 0, 0).test(project);
	}

	@Override
	public ProblemType getProblemType() {
		return Boot4JavaProblemType.API_VERSIONING_STRATEGY_CONFIGURATION_DUPLICATED;
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
		Map<String, List<MethodInvocation>> invocations = new HashMap<>();
		
		body.accept(new ASTVisitor() {
			
			@Override
			public boolean visit(MethodInvocation methodInvocation) {
				if (methodInvocation.getName() == null) {
					return super.visit(methodInvocation);
				}
				
				String methodName = methodInvocation.getName().toString();
				if (WebConfigJavaIndexer.VERSIONING_CONFIG_METHODS.contains(methodName)) {
					invocations.computeIfAbsent(methodName, (n) -> new ArrayList<MethodInvocation>()).add(methodInvocation);
				}
				
				return super.visit(methodInvocation);
			}
		});
		
		for (String methodName : invocations.keySet()) {
			List<MethodInvocation> methodInvocations = invocations.get(methodName);
			
			if (methodInvocations.size() > 1) {
				for (MethodInvocation methodInvocation : methodInvocations) {
					ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), PROBLEM_LABEL,
							methodInvocation.getStartPosition(), methodInvocation.getLength());

					context.getProblemCollector().accept(problem);
				}
				
			}
			
		}
		
	}
	
}
