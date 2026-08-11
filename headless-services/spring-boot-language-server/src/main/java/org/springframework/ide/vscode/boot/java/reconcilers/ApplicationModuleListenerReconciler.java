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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot3JavaProblemType;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.ApplicationModuleListenerRefactoring;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtFixDescriptor;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtRefactorings;
import org.springframework.ide.vscode.boot.modulith.ModulithService;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.quickfix.Quickfix.QuickfixData;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;

/**
 * Reconciler that detects methods annotated with the combination of '@Async', '@Transactional'
 * and '@TransactionalEventListener' in Spring Modulith projects and offers a quickfix to combine
 * them into '@ApplicationModuleListener'.
 *
 * @author Martin Lippert
 */
public class ApplicationModuleListenerReconciler implements JdtAstReconciler {

	private static final String PROBLEM_LABEL = "Combine into `@ApplicationModuleListener`";
	private static final String FIX_ALL_LABEL = "Combine all into `@ApplicationModuleListener` in file";

	private static final Set<String> SUPPORTED_TRANSACTIONAL_ATTRIBUTES = Set.of("readOnly", "propagation");
	private static final Set<String> SUPPORTED_EVENT_LISTENER_ATTRIBUTES = Set.of("id", "condition");

	private final QuickfixRegistry registry;

	public ApplicationModuleListenerReconciler(QuickfixRegistry registry) {
		this.registry = registry;
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return ModulithService.isModulithDependentProject(project);
	}

	@Override
	public ProblemType getProblemType() {
		return Boot3JavaProblemType.MODULITH_APPLICATION_MODULE_LISTENER;
	}

	@Override
	public Optional<ASTVisitor> createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);

		List<Integer> methodOffsets = new ArrayList<>();
		List<ReconcileProblemImpl> problems = new ArrayList<>();

		return Optional.of(new ASTVisitor() {

			@Override
			public boolean visit(MethodDeclaration node) {
				Annotation asyncAnnotation = ReconcileUtils.findAnnotation(annotationHierarchies, node, Annotations.ASYNC, false);
				Annotation transactionalAnnotation = ReconcileUtils.findAnnotation(annotationHierarchies, node, Annotations.TRANSACTIONAL, false);
				Annotation eventListenerAnnotation = ReconcileUtils.findAnnotation(annotationHierarchies, node, Annotations.TRANSACTIONAL_EVENT_LISTENER, false);

				if (asyncAnnotation == null || transactionalAnnotation == null || eventListenerAnnotation == null) {
					return true;
				}

				if (hasUnsupportedArguments(asyncAnnotation, Set.of())
						|| hasUnsupportedArguments(transactionalAnnotation, SUPPORTED_TRANSACTIONAL_ATTRIBUTES)
						|| hasUnsupportedArguments(eventListenerAnnotation, SUPPORTED_EVENT_LISTENER_ATTRIBUTES)) {
					return true;
				}

				int start = min(asyncAnnotation.getStartPosition(), transactionalAnnotation.getStartPosition(), eventListenerAnnotation.getStartPosition());
				int end = max(
						asyncAnnotation.getStartPosition() + asyncAnnotation.getLength(),
						transactionalAnnotation.getStartPosition() + transactionalAnnotation.getLength(),
						eventListenerAnnotation.getStartPosition() + eventListenerAnnotation.getLength());

				ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), PROBLEM_LABEL, start, end - start);

				QuickfixType quickfixType = registry.getQuickfixType(JdtRefactorings.JDT_QUICKFIX);
				if (quickfixType != null) {
					String uri = docUri.toASCIIString();
					JdtFixDescriptor fix = new JdtFixDescriptor(
							new ApplicationModuleListenerRefactoring(node.getStartPosition()),
							List.of(uri),
							PROBLEM_LABEL);
					problem.addQuickfix(new QuickfixData<>(quickfixType, fix, PROBLEM_LABEL, true));
				}

				methodOffsets.add(node.getStartPosition());
				problems.add(problem);
				context.getProblemCollector().accept(problem);

				return true;
			}

			@Override
			public void endVisit(CompilationUnit node) {
				if (!methodOffsets.isEmpty()) {
					QuickfixType quickfixType = registry.getQuickfixType(JdtRefactorings.JDT_QUICKFIX);
					if (quickfixType != null) {
						JdtFixDescriptor fixAll = new JdtFixDescriptor(
								new ApplicationModuleListenerRefactoring(methodOffsets.stream().mapToInt(i -> i).toArray()),
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

	/**
	 * Returns {@code true} if the annotation is a bare single-member annotation (unsupported
	 * shortcut argument, e.g. '@TransactionalEventListener(SomeEvent.class)'), or a normal
	 * annotation using an argument name that isn't in {@code supportedNames}.
	 */
	private static boolean hasUnsupportedArguments(Annotation a, Set<String> supportedNames) {
		if (a.isSingleMemberAnnotation()) {
			return true;
		}
		if (!a.isNormalAnnotation()) {
			// marker annotation, e.g. plain '@Async' - no arguments to worry about
			return false;
		}
		for (Object o : ((NormalAnnotation) a).values()) {
			MemberValuePair pair = (MemberValuePair) o;
			if (!supportedNames.contains(pair.getName().getIdentifier())) {
				return true;
			}
		}
		return false;
	}

	private static int min(int a, int b, int c) {
		return Math.min(a, Math.min(b, c));
	}

	private static int max(int a, int b, int c) {
		return Math.max(a, Math.max(b, c));
	}

}
