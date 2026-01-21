/*******************************************************************************
 * Copyright (c) 2022, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.openrewrite.java.spring.framework.BeanMethodsNotPublic;
import org.openrewrite.marker.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.rewrite.RewriteRefactorings;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.quickfix.Quickfix.QuickfixData;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;

public class BeanMethodNotPublicReconciler implements JdtAstReconciler {
		
	private static final Logger log = LoggerFactory.getLogger(BeanMethodNotPublicReconciler.class);
	private static final String LABEL = "Remove 'public' from @Bean method";

	private final QuickfixRegistry quickfixRegistry;
    
    public BeanMethodNotPublicReconciler(QuickfixRegistry quickfixRegistry) {
		this.quickfixRegistry = quickfixRegistry;
    }
	
	@Override
	public boolean isApplicable(IJavaProject project) {
		Version version = SpringProjectUtil.getDependencyVersionByName(project, SpringProjectUtil.SPRING_BOOT);		
		return version != null && version.getMajor() >= 2;
	}

	@Override
	public ProblemType getProblemType() {
		return Boot2JavaProblemType.JAVA_PUBLIC_BEAN_METHOD;
	}
	
	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {

		return new ASTVisitor() {

			@Override
			public boolean visit(SingleMemberAnnotation node) {
				try {
					visitAnnotation(project, cu, docUri, node, context.getProblemCollector());
				} catch (Exception e) {
				}
				return super.visit(node);
			}

			@Override
			public boolean visit(NormalAnnotation node) {
				try {
					visitAnnotation(project, cu, docUri, node, context.getProblemCollector());
				} catch (Exception e) {
				}
				return super.visit(node);
			}

			@Override
			public boolean visit(MarkerAnnotation node) {
				try {
					visitAnnotation(project, cu, docUri, node, context.getProblemCollector());
				} catch (Exception e) {
				}
				return super.visit(node);
			}

		};
	}

	public static final boolean isNotOverridingPublicMethod(IMethodBinding methodBinding) {
		return !isOverriding(methodBinding) && (methodBinding.getModifiers() & Modifier.PUBLIC) != 0;
	}
	
	private void visitAnnotation(IJavaProject project, CompilationUnit cu, URI docUri, Annotation node,	IProblemCollector problemCollector) {
		ITypeBinding typeBinding = node.resolveTypeBinding();
		if (typeBinding != null && Annotations.BEAN.equals(typeBinding.getQualifiedName()) && node.getParent() instanceof MethodDeclaration) {
			MethodDeclaration method = (MethodDeclaration) node.getParent();
			IMethodBinding methodBinding = method.resolveBinding();
			if (isNotOverridingPublicMethod(methodBinding)) {
				
				ReconcileProblemImpl problem = ((List<?>)method.modifiers()).stream()
						.filter(Modifier.class::isInstance)
						.map(Modifier.class::cast)
						.filter(modifier -> modifier.isPublic())
						.findFirst()
						.map(modifier -> new ReconcileProblemImpl(
								getProblemType(), Boot2JavaProblemType.JAVA_PUBLIC_BEAN_METHOD.getLabel(),
								modifier.getStartPosition(), modifier.getLength()))
						.orElse(new ReconcileProblemImpl(
								getProblemType(), Boot2JavaProblemType.JAVA_PUBLIC_BEAN_METHOD.getLabel(),
								method.getName().getStartPosition(), method.getName().getLength()));

				addQuickFixes(cu, docUri, problem, method);
				
				problemCollector.accept(problem);
			}
		}
	}
	
	private static final boolean isOverriding(IMethodBinding binding) {
		try {
			if (binding != null) {
				Field f = binding.getClass().getDeclaredField("binding");
				f.setAccessible(true);
				org.eclipse.jdt.internal.compiler.lookup.MethodBinding  value = (org.eclipse.jdt.internal.compiler.lookup.MethodBinding) f.get(binding);
				return value.isOverriding();
			}
		} catch (Exception e) {
			log.error("", e);
		}
		return false;
	}
	
	private void addQuickFixes(CompilationUnit cu, URI docUri, ReconcileProblemImpl problem, MethodDeclaration method) {
		if (quickfixRegistry != null) {
			
			String id = BeanMethodsNotPublic.class.getName();
		
			FixDescriptor fix1 = new FixDescriptor(id, List.of(docUri.toASCIIString()), LABEL)
					.withRecipeScope(RecipeScope.NODE)
					.withRangeScope(ReconcileUtils.createOpenRewriteRange(cu, method, null));

			Range methodRange = ReconcileUtils.createOpenRewriteRange(cu, method, null);
			fix1 = fix1.withRangeScope(methodRange);

			FixDescriptor fix2 = new FixDescriptor(id, List.of(docUri.toASCIIString()), ReconcileUtils.buildLabel(LABEL, RecipeScope.FILE))
					.withRecipeScope(RecipeScope.FILE);
	
			FixDescriptor fix3 = new FixDescriptor(id, List.of(docUri.toASCIIString()), ReconcileUtils.buildLabel(LABEL, RecipeScope.PROJECT))
					.withRecipeScope(RecipeScope.PROJECT);
			
			
			QuickfixType quickfixType = quickfixRegistry.getQuickfixType(RewriteRefactorings.REWRITE_RECIPE_QUICKFIX);
	
			if (quickfixType != null /* && recipeRepo.getRecipe(ID).isPresent()*/ ) { // recipes load async, so they might not be around when this validation runs
				// in addition to that we assume and ship those recipes with the language server anyway, so we can assume they are around

				problem.addQuickfix(new QuickfixData<>(quickfixType, fix1, fix1.getLabel()));
				problem.addQuickfix(new QuickfixData<>(quickfixType, fix2, fix2.getLabel()));
				problem.addQuickfix(new QuickfixData<>(quickfixType, fix3, fix3.getLabel()));
			}
		}
	}

}
