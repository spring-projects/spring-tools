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
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtFixDescriptor;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtRefactorings;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.SpringJUnitConfigRefactoring;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.quickfix.Quickfix.QuickfixData;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;

/**
 * Reconciler that detects test classes annotated with both
 * '@ExtendWith(SpringExtension.class)' and '@ContextConfiguration' and offers a
 * quickfix to combine them into '@SpringJUnitConfig'.
 *
 * @author Martin Lippert
 */
public class SpringJUnitConfigReconciler implements JdtAstReconciler {

	private static final String PROBLEM_LABEL = "Combine into `@SpringJUnitConfig`";
	private static final String FIX_ALL_LABEL = "Combine all into `@SpringJUnitConfig` in file";

	private final QuickfixRegistry registry;

	public SpringJUnitConfigReconciler(QuickfixRegistry registry) {
		this.registry = registry;
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		// '@SpringJUnitConfig' was introduced with Spring Framework 5.0 (Spring Boot 2.0.0)
		return springBootVersionGreaterOrEqual(2, 0, 0).test(project);
	}

	@Override
	public ProblemType getProblemType() {
		return Boot2JavaProblemType.SPRING_JUNIT_CONFIG_COMBINATION;
	}

	@Override
	public Optional<ASTVisitor> createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);

		List<Integer> typeOffsets = new ArrayList<>();
		List<ReconcileProblemImpl> problems = new ArrayList<>();

		return Optional.of(new ASTVisitor() {

			@Override
			public boolean visit(TypeDeclaration node) {
				Annotation extendWithAnnotation = findSpringExtensionAnnotation(node);

				// only a directly present '@ContextConfiguration' can be merged - a meta-annotated
				// one (including the one on '@SpringJUnitConfig' itself) has to stay where it is
				Annotation contextConfigurationAnnotation = ReconcileUtils.findAnnotation(annotationHierarchies, node,
						Annotations.CONTEXT_CONFIGURATION, false);

				if (extendWithAnnotation == null || contextConfigurationAnnotation == null) {
					return super.visit(node);
				}

				if (ReconcileUtils.findAnnotation(annotationHierarchies, node, Annotations.SPRING_JUNIT_CONFIG, false) != null) {
					return super.visit(node);
				}

				// '@SpringBootTest' and the test slice annotations already apply the
				// 'SpringExtension' themselves, so removing the '@ExtendWith' alone is the right
				// fix there - that case is flagged by the 'UnnecessarySpringExtensionReconciler'
				if (hasSpringBootTestAnnotation(node)) {
					return super.visit(node);
				}

				int start = Math.min(extendWithAnnotation.getStartPosition(), contextConfigurationAnnotation.getStartPosition());
				int end = Math.max(
						extendWithAnnotation.getStartPosition() + extendWithAnnotation.getLength(),
						contextConfigurationAnnotation.getStartPosition() + contextConfigurationAnnotation.getLength());

				ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), PROBLEM_LABEL, start, end - start);

				QuickfixType quickfixType = registry.getQuickfixType(JdtRefactorings.JDT_QUICKFIX);
				if (quickfixType != null) {
					JdtFixDescriptor fix = new JdtFixDescriptor(
							new SpringJUnitConfigRefactoring(node.getStartPosition()),
							List.of(docUri.toASCIIString()),
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
								new SpringJUnitConfigRefactoring(typeOffsets.stream().mapToInt(i -> i).toArray()),
								List.of(docUri.toASCIIString()),
								FIX_ALL_LABEL);
						for (ReconcileProblemImpl problem : problems) {
							problem.addQuickfix(new QuickfixData<>(quickfixType, fixAll, FIX_ALL_LABEL, false));
						}
					}
				}
			}

			private boolean hasSpringBootTestAnnotation(TypeDeclaration node) {
				return Annotations.SPRING_BOOT_TEST_ANNOTATIONS.stream()
						.anyMatch(fqn -> ReconcileUtils.findAnnotation(annotationHierarchies, node, fqn, true) != null);
			}

		});
	}

	/**
	 * Returns the '@ExtendWith' annotation on the given type whose only value is
	 * 'SpringExtension.class', or <code>null</code> if there is none. An '@ExtendWith'
	 * that registers further extensions cannot be folded into '@SpringJUnitConfig' since
	 * those extensions would be lost.
	 */
	private static Annotation findSpringExtensionAnnotation(TypeDeclaration type) {
		for (Object mod : type.modifiers()) {
			if (mod instanceof Annotation a && isOnlySpringExtension(a)) {
				return a;
			}
		}
		return null;
	}

	private static boolean isOnlySpringExtension(Annotation a) {
		if (!Annotations.JUNIT_EXTEND_WITH.endsWith(a.getTypeName().getFullyQualifiedName())) {
			return false;
		}

		IAnnotationBinding annotationBinding = a.resolveAnnotationBinding();
		if (annotationBinding == null
				|| !Annotations.JUNIT_EXTEND_WITH.equals(annotationBinding.getAnnotationType().getQualifiedName())
				|| annotationBinding.getDeclaredMemberValuePairs().length != 1) {
			return false;
		}

		IMemberValuePairBinding pair = annotationBinding.getDeclaredMemberValuePairs()[0];
		if (!"value".equals(pair.getName())) {
			return false;
		}

		ITypeBinding typeBinding = null;
		if (pair.getValue() instanceof ITypeBinding binding) {
			typeBinding = binding;
		}
		else if (pair.getValue() instanceof Object[] arr) {
			if (arr.length != 1) {
				return false;
			}
			if (arr[0] instanceof ITypeBinding binding) {
				typeBinding = binding;
			}
		}

		return typeBinding != null && Annotations.SPRING_EXTENSION.equals(typeBinding.getQualifiedName());
	}

}
