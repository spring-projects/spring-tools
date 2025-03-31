/*******************************************************************************
 * Copyright (c) 2023, 2025 VMware, Inc.
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.ide.vscode.boot.java.SpringAotJavaProblemType;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.BeanPostProcessingIgnoreInAot;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;

public class BeanPostProcessingIgnoreInAotReconciler implements JdtAstReconciler {
	
	private static final String LABEL = "Add method 'isBeanExcludedFromAotProcessing' that returns 'false'";
    private static final String RUNTIME_BEAN_POST_PROCESSOR = "org.springframework.beans.factory.config.BeanPostProcessor";
    private static final String COMPILE_BEAN_POST_PROCESSOR = "org.springframework.beans.factory.aot.BeanRegistrationAotProcessor";
    public static final String METHOD_NAME = "isBeanExcludedFromAotProcessing";
	
	private QuickfixRegistry registry;

	public BeanPostProcessingIgnoreInAotReconciler(QuickfixRegistry registry) {
		this.registry = registry;
	}
	
	@Override
	public boolean isApplicable(IJavaProject project) {
		return springBootVersionGreaterOrEqual(3, 0, 0).test(project);
	}

	@Override
	public ProblemType getProblemType() {
		return SpringAotJavaProblemType.JAVA_BEAN_POST_PROCESSOR_IGNORED_IN_AOT;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {

		return new ASTVisitor() {

			@Override
			public boolean visit(TypeDeclaration typeDecl) {
				ITypeBinding type = typeDecl.resolveBinding();
				if (type != null && isApplicable(type)) {
					MethodDeclaration foundMethod = null;
					boolean markProblem = false;
					for (MethodDeclaration m : typeDecl.getMethods()) {
						if (METHOD_NAME.equals(m.getName().getIdentifier()) && m.parameters().isEmpty()) {
							foundMethod = m;
							break;
						}
					}
					if (foundMethod != null) {
						if (context.isCompleteAst()) {
							AtomicBoolean returnsTrue = new AtomicBoolean(false);
							foundMethod.accept(new ASTVisitor() {
								@Override
								public boolean visit(ReturnStatement node) {
									if (Boolean.TRUE.equals(node.getExpression().resolveConstantExpressionValue())) {
										returnsTrue.set(true);
									}
									return !returnsTrue.get();
								}
							});
							markProblem = returnsTrue.get();
						} else {
							throw new RequiredCompleteAstException();
						}
					} else {
						markProblem = true;
					}
					
					if (markProblem) {
						ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), LABEL, typeDecl.getName().getStartPosition(), typeDecl.getName().getLength());
						ReconcileUtils.setRewriteFixes(registry, problem, List.of(
								new FixDescriptor(BeanPostProcessingIgnoreInAot.class.getName(), List.of(docUri.toASCIIString()), ReconcileUtils.buildLabel(LABEL, RecipeScope.NODE))
										.withRangeScope(ReconcileUtils.createOpenRewriteRange(cu, typeDecl, null))
										.withRecipeScope(RecipeScope.NODE)
						));
						context.getProblemCollector().accept(problem);
					}
				}
				return true;
			}
			
		};
	}
	
	private static boolean isApplicable(ITypeBinding type) {
		return ASTUtils.areAllTypesInHierarchy(type, Set.of(RUNTIME_BEAN_POST_PROCESSOR, COMPILE_BEAN_POST_PROCESSOR));
	}

}
