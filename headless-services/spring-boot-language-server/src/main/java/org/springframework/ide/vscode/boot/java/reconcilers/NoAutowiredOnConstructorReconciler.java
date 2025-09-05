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
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.openrewrite.java.spring.NoAutowiredOnConstructor;
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

public class NoAutowiredOnConstructorReconciler implements JdtAstReconciler {

	private static final String PROBLEM_LABEL = "Unnecessary `@Autowired` annotation";
	private static final String FIX_LABEL = "Remove unnecessary `@Autowired` annotation";

	private QuickfixRegistry registry;

	public NoAutowiredOnConstructorReconciler(QuickfixRegistry registry) {
		this.registry = registry;
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return springBootVersionGreaterOrEqual(2, 0, 0).test(project);
	}

	@Override
	public ProblemType getProblemType() {
		return Boot2JavaProblemType.JAVA_AUTOWIRED_CONSTRUCTOR;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);

		return new ASTVisitor() {

			@Override
			public boolean visit(TypeDeclaration typeDecl) {
				Path sourceFile = Paths.get(docUri);
				if (IClasspathUtil.getProjectJavaSourceFoldersWithoutTests(project.getClasspath())
						.anyMatch(f -> sourceFile.startsWith(f.toPath()))) {

					// lombok annotation around -> don't check for constructors
					if (ASTUtils.getAnnotations(typeDecl).stream()
							.map(annotation -> annotation.resolveTypeBinding().getQualifiedName())
							.filter(annotationType -> Annotations.LOMBOK_CONSTRUCTOR_ANNOTATIONS.contains(annotationType))
							.findAny().isPresent()) {
						return super.visit(typeDecl);
					}

					// more than one constructor ?
					int constructorCount = 0;
					MethodDeclaration constructor = null;
					for (MethodDeclaration method : typeDecl.getMethods()) {
						if (method.isConstructor()) {
							constructorCount++;
							if (constructorCount > 1) {
								return super.visit(typeDecl);
							} else {
								constructor = method;
							}
						}
					}

					// one constructor -> check for autowired annotation
					if (constructor != null) {
						Annotation autowiredAnnotation = ReconcileUtils.findAnnotation(annotationHierarchies, constructor,
								Annotations.AUTOWIRED, false);
						if (autowiredAnnotation != null) {
							ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), PROBLEM_LABEL,
									autowiredAnnotation.getStartPosition(), autowiredAnnotation.getLength());
							ReconcileUtils.setRewriteFixes(registry, problem,
									List.of(new FixDescriptor(NoAutowiredOnConstructor.class.getName(), List.of(docUri.toASCIIString()), FIX_LABEL)
											.withRecipeScope(RecipeScope.NODE)
											.withRangeScope(ReconcileUtils.createOpenRewriteRange(cu, typeDecl, null))));
							context.getProblemCollector().accept(problem);
						}
					}

				}
				return super.visit(typeDecl);
			}

		};
	}

}
