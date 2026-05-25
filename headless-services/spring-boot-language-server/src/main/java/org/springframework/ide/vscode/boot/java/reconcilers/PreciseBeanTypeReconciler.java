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

import static org.springframework.ide.vscode.commons.java.SpringProjectUtil.springBootVersionGreaterOrEqual;

import java.net.URI;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.openrewrite.java.spring.boot3.PreciseBeanType;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.SpringAotJavaProblemType;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;

public class PreciseBeanTypeReconciler implements JdtAstReconciler {
	
	private static final String LABEL = "Ensure concrete bean type";

	private QuickfixRegistry registry;
	
	public PreciseBeanTypeReconciler(QuickfixRegistry registry) {
		this.registry = registry;
	}
	
	@Override
	public boolean isApplicable(IJavaProject project) {
		return springBootVersionGreaterOrEqual(3, 0, 0).test(project);
	}

	@Override
	public ProblemType getProblemType() {
		return SpringAotJavaProblemType.JAVA_CONCRETE_BEAN_TYPE;
	}

	@Override
	public Optional<ASTVisitor> createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		final AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);

		return Optional.of(new ASTVisitor() {
			
			private MethodDeclaration currentMethod;
			
			private List<ITypeBinding> currentReturnTypes = new ArrayList<>();

			@Override
			public boolean visit(MethodDeclaration method) {
				IMethodBinding methodBinding = method.resolveBinding();
				if (methodBinding != null) {
					boolean isBeanMethod = annotationHierarchies.isAnnotatedWith(methodBinding, Annotations.BEAN);
					if (isBeanMethod) {
						if (context.isCompleteAst()) {
							if (currentMethod == null)  {// Do not jump into anonymous class methods
								currentMethod = method;
								currentReturnTypes = new ArrayList<>();
								return true;
							}
						} else {
							throw new RequiredCompleteAstException();
						}
					}
				}
				return false;
			}

			@Override
			public void endVisit(MethodDeclaration method) {
				if (currentMethod == method) {
					ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), LABEL, method.getReturnType2().getStartPosition(), method.getReturnType2().getLength());
					if (currentReturnTypes.size() > 1) {
						context.getProblemCollector().accept(problem);
					} else if (currentReturnTypes.size() == 1 && !method.resolveBinding().getReturnType().isAssignmentCompatible(currentReturnTypes.get(0))) {
						String uri = docUri.toASCIIString();
						String replacementType = currentReturnTypes.get(0).getName();
						String recipeId = PreciseBeanType.class.getName();
						ReconcileUtils.setRewriteFixes(registry, problem, List.of(
							new FixDescriptor(recipeId, List.of(uri), ReconcileUtils.buildLabel("Replace return type with '" + replacementType + "'", RecipeScope.NODE))
								.withRecipeScope(RecipeScope.NODE)
								.withRangeScope(ReconcileUtils.createOpenRewriteRange(cu, method, null)),
							new FixDescriptor(recipeId, List.of(uri), ReconcileUtils.buildLabel(LABEL, RecipeScope.FILE))
								.withRecipeScope(RecipeScope.FILE),
							new FixDescriptor(recipeId, List.of(uri), ReconcileUtils.buildLabel(LABEL, RecipeScope.PROJECT))
								.withRecipeScope(RecipeScope.PROJECT)
						));
						context.getProblemCollector().accept(problem);
					}
					
					currentMethod = null;
					currentReturnTypes = new ArrayList<>();
				}
				super.endVisit(method);
			}

			@Override
			public boolean visit(ReturnStatement node) {
				Expression expression = node.getExpression();
				if (expression == null) {
					return super.visit(node);
				}
				
				ITypeBinding type = expression.resolveTypeBinding();
				if (type == null) {
					return super.visit(node);
				}
				
				if (currentReturnTypes.isEmpty()) {
					currentReturnTypes.add(type);
				} else {
					for (ListIterator<ITypeBinding> itr = currentReturnTypes.listIterator(); itr.hasNext();) {
						ITypeBinding t = itr.next();
						if (t.isAssignmentCompatible(type)) {
							itr.remove();
						}
					}
					currentReturnTypes.add(type);
				}
				return super.visit(node);
			}
			
		});
	}

}
