/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.openrewrite.java.spring.boot2.AddConfigurationAnnotationIfBeansPresent;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;

/**
 * Reconciler that checks for classes implementing WebMvcConfigurer or WebFluxConfigurer 
 * interfaces and ensures they are annotated with @Configuration.
 * 
 * @author Martin Lippert
 */
public class WebConfigurerConfigurationReconciler implements JdtAstReconciler {

	private static final String PROBLEM_LABEL = "'@Configuration' is missing on a class implementing 'WebMvcConfigurer' or 'WebFluxConfigurer'";
	private static final String FIX_LABEL = "Add missing '@Configuration' annotation";

	private final QuickfixRegistry quickfixRegistry;

	public WebConfigurerConfigurationReconciler(QuickfixRegistry quickfixRegistry) {
		this.quickfixRegistry = quickfixRegistry;
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		// This is applicable to all Spring projects that use web configurers
		return true;
	}

	@Override
	public ProblemType getProblemType() {
		return Boot2JavaProblemType.WEB_CONFIGURER_CONFIGURATION;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		return new ASTVisitor() {

			@Override
			public boolean visit(TypeDeclaration classDecl) {
				if (isApplicableClass(cu, classDecl)) {
					SimpleName nameAst = classDecl.getName();
					ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), PROBLEM_LABEL,
							nameAst.getStartPosition(), nameAst.getLength());

//					String id = AddConfigurationAnnotationIfBeansPresent.class.getName();
//
//					ReconcileUtils.setRewriteFixes(quickfixRegistry, problem,
//							List.of(new FixDescriptor(id, List.of(docUri.toASCIIString()),
//									ReconcileUtils.buildLabel(FIX_LABEL, RecipeScope.FILE))
//									.withRecipeScope(RecipeScope.FILE),
//									new FixDescriptor(id, List.of(docUri.toASCIIString()),
//											ReconcileUtils.buildLabel(FIX_LABEL, RecipeScope.PROJECT))
//											.withRecipeScope(RecipeScope.PROJECT)));

					context.getProblemCollector().accept(problem);
				}
				return true;
			}

		};
	}

	private boolean isApplicableClass(CompilationUnit cu, TypeDeclaration classDecl) {
		// Skip interfaces
		if (classDecl.isInterface()) {
			return false;
		}

		// Skip abstract classes
		if (Modifier.isAbstract(classDecl.getModifiers())) {
			return false;
		}

		boolean isStatic = Modifier.isStatic(classDecl.getModifiers());

		if (!isStatic) {
			// no static keyword? check if it is top level class in the CU
			for (ASTNode p = classDecl.getParent(); p != cu && p != null; p = p.getParent()) {
				if (p instanceof TypeDeclaration) {
					return false;
				}
			}
		}

		ITypeBinding typeBinding = classDecl.resolveBinding();
		if (typeBinding == null) {
			return false;
		}

		// Check if the class implements WebMvcConfigurer or WebFluxConfigurer using ASTUtils
		ITypeBinding webConfigurerBinding = ASTUtils.findInTypeHierarchy(typeBinding, 
				Set.of(Annotations.WEB_MVC_CONFIGURER_INTERFACE, Annotations.WEB_FLUX_CONFIGURER_INTERFACE));
		
		if (webConfigurerBinding == null) {
			return false;
		}

		// Check if '@Configuration' is already present
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);
		return !annotationHierarchies.isAnnotatedWith(typeBinding, Annotations.CONFIGURATION);
	}

}

