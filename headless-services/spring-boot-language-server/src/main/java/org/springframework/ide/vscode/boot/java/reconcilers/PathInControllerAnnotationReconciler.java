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
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtFixDescriptor;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtRefactorings;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.MovePathToRequestMappingRefactoring;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.quickfix.Quickfix.QuickfixData;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;

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
	public Optional<ASTVisitor> createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);

		Path sourceFile = Paths.get(docUri);
		boolean insideOfSourceFolders = IClasspathUtil.getProjectJavaSourceFoldersWithoutTests(project.getClasspath())
				.anyMatch(f -> sourceFile.startsWith(f.toPath()));

		return Optional.of(new ASTVisitor() {

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
				Expression value = findPathValueExpression(controllerAnnotation);
				if (value == null) {
					return super.visit(typeDecl);
				}

				String stringValue = ASTUtils.getExpressionValueAsString(value);
				if (stringValue != null && stringValue.contains("/")) {

					ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), PROBLEM_LABEL,
							value.getStartPosition(), value.getLength());

					if (registry != null) {
						QuickfixType quickfixType = registry.getQuickfixType(JdtRefactorings.JDT_QUICKFIX);
						if (quickfixType != null) {
							String strUri = docUri.toASCIIString();
							String label = FIX_LABEL + " in file";
							JdtFixDescriptor descriptor = new JdtFixDescriptor(
									new MovePathToRequestMappingRefactoring(), List.of(strUri), label);
							problem.addQuickfix(new QuickfixData<>(quickfixType, descriptor, label, true));
						}
					}

					context.getProblemCollector().accept(problem);
				}

				return super.visit(typeDecl);
			}

		});
	}

	/**
	 * Extracts the path-candidate value expression from either the bare-literal
	 * shorthand ({@code @Controller("/path")}) or the keyword form
	 * ({@code @Controller(value = "/path")}).
	 */
	private static Expression findPathValueExpression(Annotation annotation) {
		if (annotation instanceof SingleMemberAnnotation sma) {
			return sma.getValue();
		} else if (annotation instanceof NormalAnnotation na) {
			for (Object o : na.values()) {
				MemberValuePair pair = (MemberValuePair) o;
				if ("value".equals(pair.getName().getIdentifier())) {
					return pair.getValue();
				}
			}
		}
		return null;
	}

}
