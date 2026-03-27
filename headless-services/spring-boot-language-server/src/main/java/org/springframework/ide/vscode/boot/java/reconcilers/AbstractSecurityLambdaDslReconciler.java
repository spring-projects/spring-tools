/*******************************************************************************
 * Copyright (c) 2023, 2026 VMware, Inc.
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
import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;

public abstract class AbstractSecurityLambdaDslReconciler implements JdtAstReconciler {
	
	private QuickfixRegistry registry;

	public AbstractSecurityLambdaDslReconciler(QuickfixRegistry registry) {
		this.registry = registry;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {

		if (context.isCompleteAst()) {
			return new ASTVisitor() {

				@Override
				public boolean visit(MethodInvocation node) {
					if (getApplicableMethodNames().contains(node.getName().getIdentifier()) && node.arguments().isEmpty()) {
						ITypeBinding type = node.getExpression().resolveTypeBinding();
						if (type != null && getTargetTypeFqName().equals(type.getQualifiedName())) {
							MethodInvocation topMethodInvocation = findTopLevelMethodInvocation(node);
							ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), getProblemLabel(), topMethodInvocation.getStartPosition(), topMethodInvocation.getLength());
							String uri = docUri.toASCIIString();
							ReconcileUtils.setRewriteFixes(registry, problem, List.of(
									new FixDescriptor(getRecipeId(), List.of(uri),
											ReconcileUtils.buildLabel(getFixLabel(), RecipeScope.NODE))
											.withRangeScope(ReconcileUtils.createOpenRewriteRange(cu, topMethodInvocation, null))
											.withRecipeScope(RecipeScope.NODE),
									new FixDescriptor(getRecipeId(), List.of(uri),
											ReconcileUtils.buildLabel(getFixLabel(), RecipeScope.FILE))
											.withRecipeScope(RecipeScope.FILE),
									new FixDescriptor(getRecipeId(), List.of(uri),
											ReconcileUtils.buildLabel(getFixLabel(), RecipeScope.PROJECT))
											.withRecipeScope(RecipeScope.PROJECT)
							));
							context.getProblemCollector().accept(problem);
							return false;
						}
					}
					return true;
				}
				
			};
			
		} else {
			if (ReconcileUtils.isAnyTypeUsed(cu, List.of(getTargetTypeFqName()))) {
				throw new RequiredCompleteAstException();
			}
			else {
				return null;
			}
		}
	}
	
	protected abstract String getFixLabel();
	protected abstract String getRecipeId();
	protected abstract String getProblemLabel();
	abstract protected String getTargetTypeFqName();
	abstract protected Collection<String> getApplicableMethodNames();

	private static MethodInvocation findTopLevelMethodInvocation(MethodInvocation m) {
		for (; m.getParent() instanceof MethodInvocation; m = (MethodInvocation) m.getParent()) {}
		return m;
	}
		
}
