/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import static org.springframework.ide.vscode.commons.java.SpringProjectUtil.springBootVersionGreaterOrEqual;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

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
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;
import org.springframework.ide.vscode.commons.rewrite.java.NoPathInControllerAnnotation;

public class PathInControllerAnnotationReconciler implements JdtAstReconciler {

	private static final String FIX_LABEL = "Move path to a `@RequestMapping`";

	private static final String PROBLEM_LABEL = "attribute refers to the component name, but looks like a path definition";
	
	private static final List<String> CONTROLLER_ANNOTATIONS = List.of(Annotations.CONTROLLER); 
	
	private final QuickfixRegistry registry;

	public PathInControllerAnnotationReconciler(QuickfixRegistry registry) {
		this.registry = registry;
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
				
				Optional<String> annotatedWith = CONTROLLER_ANNOTATIONS.stream().filter(annotationType -> annotationHierarchies.isAnnotatedWith(typeDecl.resolveBinding(), annotationType)).findFirst();
				if (annotatedWith.isEmpty()) {
					return super.visit(typeDecl);
				}
					
				Annotation controllerAnnotation = ReconcileUtils.findAnnotation(annotationHierarchies, typeDecl, annotatedWith.get(), true);
				if (controllerAnnotation.isSingleMemberAnnotation()) {
					SingleMemberAnnotation sma = (SingleMemberAnnotation) controllerAnnotation;
					Expression value = sma.getValue();
					String stringValue = ASTUtils.getExpressionValueAsString(value, (t) -> {});
					
					if (stringValue.contains("/")) {

						ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), PROBLEM_LABEL,
								value.getStartPosition(), value.getLength());
						
						String strUri = docUri.toASCIIString();
						ReconcileUtils.setRewriteFixes(registry, problem, List.of(
								// Assume node scope is just the whole file for this reconciler and quick fix only
								new FixDescriptor(NoPathInControllerAnnotation.class.getName(), List.of(strUri), ReconcileUtils.buildLabel(FIX_LABEL, RecipeScope.NODE))
									.withRecipeScope(RecipeScope.FILE),
								new FixDescriptor(NoPathInControllerAnnotation.class.getName(), List.of(strUri), ReconcileUtils.buildLabel(FIX_LABEL, RecipeScope.PROJECT))
									.withRecipeScope(RecipeScope.PROJECT)
									
						));

						context.getProblemCollector().accept(problem);
					}
				}
				
				return super.visit(typeDecl);
			}

		};
	}

}
