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

import static org.springframework.ide.vscode.commons.java.SpringProjectUtil.springBootVersionGreaterOrEqual;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtFixDescriptor;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtRefactorings;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.RestControllerRefactoring;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.quickfix.Quickfix.QuickfixData;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;

/**
 * Reconciler that detects classes annotated with both '@Controller' and
 * '@ResponseBody' and offers a quickfix to combine them into '@RestController'.
 *
 * @author Martin Lippert
 */
public class RestControllerReconciler implements JdtAstReconciler {

	private static final String PROBLEM_LABEL = "Combine into `@RestController`";
	private static final String FIX_ALL_LABEL = "Combine all into `@RestController` in file";

	private final QuickfixRegistry registry;

	public RestControllerReconciler(QuickfixRegistry registry) {
		this.registry = registry;
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return springBootVersionGreaterOrEqual(2, 0, 0).test(project);
	}

	@Override
	public ProblemType getProblemType() {
		return Boot2JavaProblemType.REST_CONTROLLER_COMBINATION;
	}

	@Override
	public Optional<ASTVisitor> createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);

		List<Integer> typeOffsets = new ArrayList<>();
		List<ReconcileProblemImpl> problems = new ArrayList<>();

		return Optional.of(new ASTVisitor() {

			@Override
			public boolean visit(TypeDeclaration node) {
				Annotation controllerAnnotation = ReconcileUtils.findAnnotation(annotationHierarchies, node, Annotations.CONTROLLER, false);
				Annotation responseBodyAnnotation = ReconcileUtils.findAnnotation(annotationHierarchies, node, Annotations.RESPONSE_BODY, false);

				if (controllerAnnotation == null || responseBodyAnnotation == null) {
					return super.visit(node);
				}

				int start = Math.min(controllerAnnotation.getStartPosition(), responseBodyAnnotation.getStartPosition());
				int end = Math.max(
						controllerAnnotation.getStartPosition() + controllerAnnotation.getLength(),
						responseBodyAnnotation.getStartPosition() + responseBodyAnnotation.getLength());

				ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), PROBLEM_LABEL, start, end - start);

				QuickfixType quickfixType = registry.getQuickfixType(JdtRefactorings.JDT_QUICKFIX);
				if (quickfixType != null) {
					String uri = docUri.toASCIIString();
					JdtFixDescriptor fix = new JdtFixDescriptor(
							new RestControllerRefactoring(node.getStartPosition()),
							List.of(uri),
							PROBLEM_LABEL);
					problem.addQuickfix(new QuickfixData<>(quickfixType, fix, PROBLEM_LABEL, true));
				}

				typeOffsets.add(node.getStartPosition());
				problems.add(problem);
				context.getProblemCollector().accept(problem);

				return super.visit(node);
			}

			@Override
			public void endVisit(CompilationUnit node) {
				if (!typeOffsets.isEmpty()) {
					QuickfixType quickfixType = registry.getQuickfixType(JdtRefactorings.JDT_QUICKFIX);
					if (quickfixType != null) {
						JdtFixDescriptor fixAll = new JdtFixDescriptor(
								new RestControllerRefactoring(typeOffsets.stream().mapToInt(i -> i).toArray()),
								List.of(docUri.toASCIIString()),
								FIX_ALL_LABEL);
						for (ReconcileProblemImpl problem : problems) {
							problem.addQuickfix(new QuickfixData<>(quickfixType, fixAll, FIX_ALL_LABEL, false));
						}
					}
				}
			}

		});
	}

}
