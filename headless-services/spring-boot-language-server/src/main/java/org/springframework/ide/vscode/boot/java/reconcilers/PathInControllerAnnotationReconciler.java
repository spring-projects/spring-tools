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
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;

public class PathInControllerAnnotationReconciler implements JdtAstReconciler {

	private static final String PROBLEM_LABEL = "attribute refers to the component name, but looks like a path definition";

	public PathInControllerAnnotationReconciler() {
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return springBootVersionGreaterOrEqual(2, 0, 0).test(project);
	}

	@Override
	public ProblemType getProblemType() {
		return Boot2JavaProblemType.PATH_IN_CONTROLLER_ANNOTATION;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);

		Path sourceFile = Paths.get(docUri);
		boolean insideOfSourceFolders = IClasspathUtil.getProjectJavaSourceFoldersWithoutTests(project.getClasspath())
				.anyMatch(f -> sourceFile.startsWith(f.toPath()));

		return new ASTVisitor() {

			@Override
			public boolean visit(TypeDeclaration typeDecl) {
				
				if (!insideOfSourceFolders) {
					return super.visit(typeDecl);
				}
					
				boolean isRestController = annotationHierarchies.isAnnotatedWith(typeDecl.resolveBinding(), Annotations.REST_CONTROLLER);
				if (!isRestController) {
					return super.visit(typeDecl);
				}
					
				Annotation controllerAnnotation = ReconcileUtils.findAnnotation(annotationHierarchies, typeDecl, Annotations.REST_CONTROLLER, false);
				if (controllerAnnotation.isSingleMemberAnnotation()) {
					SingleMemberAnnotation sma = (SingleMemberAnnotation) controllerAnnotation;
					Expression value = sma.getValue();
					String stringValue = ASTUtils.getExpressionValueAsString(value, (t) -> {});
					
					if (stringValue.contains("/")) {

						ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), PROBLEM_LABEL,
								value.getStartPosition(), value.getLength());

						context.getProblemCollector().accept(problem);
					}
				}
				
				return super.visit(typeDecl);
			}

		};
	}

}
