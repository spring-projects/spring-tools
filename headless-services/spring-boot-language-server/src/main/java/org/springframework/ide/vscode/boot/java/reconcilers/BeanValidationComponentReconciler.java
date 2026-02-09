/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.AddValidatedAnnotation;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;

/**
 * Reconciler that checks for component classes using bean validation annotations on
 * method parameters or return values and ensures they are annotated with @Validated.
 * Web controllers (annotated with @Controller or @RestController) are excluded from this check.
 * 
 * @author Martin Lippert
 */
public class BeanValidationComponentReconciler implements JdtAstReconciler {

	private static final String PROBLEM_LABEL = "'@Validated' is missing on a component class using bean validation annotations";
	private static final String FIX_LABEL = "Add missing '@Validated' annotation";

	private final QuickfixRegistry quickfixRegistry;

	public BeanValidationComponentReconciler(QuickfixRegistry quickfixRegistry) {
		this.quickfixRegistry = quickfixRegistry;
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return true;
	}

	@Override
	public ProblemType getProblemType() {
		return Boot2JavaProblemType.MISSING_VALIDATED_ANNOTATION;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		return new ASTVisitor() {

			@Override
			public boolean visit(TypeDeclaration classDecl) {
				AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);

				if (isApplicableClass(cu, classDecl, annotationHierarchies) && hasBeanValidationAnnotations(classDecl, annotationHierarchies)) {
					SimpleName nameAst = classDecl.getName();
					ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), PROBLEM_LABEL,
							nameAst.getStartPosition(), nameAst.getLength());

					String id = AddValidatedAnnotation.class.getName();

					ReconcileUtils.setRewriteFixes(quickfixRegistry, problem,
							List.of(new FixDescriptor(id, List.of(docUri.toASCIIString()),
									ReconcileUtils.buildLabel(FIX_LABEL, RecipeScope.FILE))
									.withRecipeScope(RecipeScope.FILE)
									)
							);

					context.getProblemCollector().accept(problem);
				}
				return true;
			}

		};
	}

	private boolean isApplicableClass(CompilationUnit cu, TypeDeclaration classDecl, AnnotationHierarchies annotationHierarchies) {
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

		// Check if the class is a @Component (or meta-annotated with @Component)
		if (!annotationHierarchies.isAnnotatedWith(typeBinding, Annotations.COMPONENT)) {
			return false;
		}

		// Skip web controllers (@Controller, @RestController)
		if (annotationHierarchies.isAnnotatedWith(typeBinding, Annotations.CONTROLLER)) {
			return false;
		}

		// Check if '@Validated' is already present
		if (annotationHierarchies.isAnnotatedWith(typeBinding, Annotations.VALIDATED)) {
			return false;
		}
		
		return true;
	}

	private boolean hasBeanValidationAnnotations(TypeDeclaration classDecl, AnnotationHierarchies annotationHierarchies) {
		for (MethodDeclaration method : classDecl.getMethods()) {
			// Check method parameter annotations
			@SuppressWarnings("unchecked")
			List<SingleVariableDeclaration> parameters = method.parameters();
			for (SingleVariableDeclaration param : parameters) {
				if (hasValidationAnnotation(param.modifiers(), annotationHierarchies)) {
					return true;
				}
			}

			// Check method return value annotations (annotations on the method itself)
			if (hasValidationAnnotation(method.modifiers(), annotationHierarchies)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasValidationAnnotation(List<?> modifiers, AnnotationHierarchies annotationHierarchies) {
		for (Object mod : modifiers) {
			if (mod instanceof Annotation) {
				Annotation a = (Annotation) mod;
				ITypeBinding aType = a.resolveTypeBinding();
				if (aType != null) {
					String fqn = aType.getQualifiedName();
					if (Annotations.BEAN_VALIDATION_ANNOTATIONS.contains(fqn)) {
						return true;
					}
				}
			}
		}
		return false;
	}

}
