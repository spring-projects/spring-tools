/*******************************************************************************
 * Copyright (c) 2023, 2026 VMware, Inc.
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
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.SpringAotJavaProblemType;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtFixDescriptor;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtRefactorings;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.PreciseBeanTypeRefactoring;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.quickfix.Quickfix.QuickfixData;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;

public class PreciseBeanTypeReconciler implements JdtAstReconciler {

	private static final String LABEL = "Ensure concrete bean type";

	/**
	 * A problem found for one {@code @Bean} method, paired with its fix (if the
	 * replacement type was unambiguous) so quick fixes can be attached once the whole
	 * file has been scanned. {@code fix} and {@code replacementTypeDisplayName} are
	 * {@code null} when the method has multiple, ambiguous return types.
	 */
	private record PendingProblem(ReconcileProblemImpl problem, PreciseBeanTypeRefactoring.Fix fix,
			String replacementTypeDisplayName) {
	}

	private QuickfixRegistry registry;
	
	public PreciseBeanTypeReconciler(QuickfixRegistry registry) {
		this.registry = registry;
	}
	
	@Override
	public boolean isApplicable(IJavaProject project) {
		return springBootVersionGreaterOrEqual(3, 0, 0).test(project);
	}

	@Override
	public ProblemType getProblemType() {
		return SpringAotJavaProblemType.JAVA_CONCRETE_BEAN_TYPE;
	}

	@Override
	public Optional<ASTVisitor> createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		final AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);

		return Optional.of(new ASTVisitor() {

			private MethodDeclaration currentMethod;

			private List<ITypeBinding> currentReturnTypes = new ArrayList<>();

			/** Every reportable {@code @Bean} method problem, paired with its fix (if any), collected so the "fix all in file" quick fix can target every unambiguous one once the whole file has been scanned. */
			private final List<PendingProblem> pendingProblems = new ArrayList<>();

			@Override
			public boolean visit(MethodDeclaration method) {
				IMethodBinding methodBinding = method.resolveBinding();
				if (methodBinding != null) {
					boolean isBeanMethod = annotationHierarchies.isAnnotatedWith(methodBinding, Annotations.BEAN);
					if (isBeanMethod) {
						if (context.isCompleteAst()) {
							if (currentMethod == null)  {// Do not jump into anonymous class methods
								currentMethod = method;
								currentReturnTypes = new ArrayList<>();
								return true;
							}
						} else {
							throw new RequiredCompleteAstException();
						}
					}
				}
				return false;
			}

			@Override
			public void endVisit(MethodDeclaration method) {
				if (currentMethod == method) {
					if (currentReturnTypes.size() > 1) {
						ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), LABEL, method.getReturnType2().getStartPosition(), method.getReturnType2().getLength());
						pendingProblems.add(new PendingProblem(problem, null, null));
					} else if (currentReturnTypes.size() == 1 && !method.resolveBinding().getReturnType().isAssignmentCompatible(currentReturnTypes.get(0))) {
						ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), LABEL, method.getReturnType2().getStartPosition(), method.getReturnType2().getLength());
						ITypeBinding actualType = currentReturnTypes.get(0);
						ITypeBinding declaredType = method.resolveBinding().getReturnType();

						PreciseBeanTypeRefactoring.Fix fix = new PreciseBeanTypeRefactoring.Fix(
								method.getStartPosition(), actualType.getQualifiedName(), declaredType.getErasure().getQualifiedName());
						pendingProblems.add(new PendingProblem(problem, fix, actualType.getName()));
					}

					currentMethod = null;
					currentReturnTypes = new ArrayList<>();
				}
				super.endVisit(method);
			}

			@Override
			public boolean visit(ReturnStatement node) {
				Expression expression = node.getExpression();
				if (expression == null) {
					return super.visit(node);
				}

				ITypeBinding type = expression.resolveTypeBinding();
				if (type == null) {
					return super.visit(node);
				}

				if (currentReturnTypes.isEmpty()) {
					currentReturnTypes.add(type);
				} else {
					for (ListIterator<ITypeBinding> itr = currentReturnTypes.listIterator(); itr.hasNext();) {
						ITypeBinding t = itr.next();
						if (t.isAssignmentCompatible(type)) {
							itr.remove();
						}
					}
					currentReturnTypes.add(type);
				}
				return super.visit(node);
			}

			@Override
			public void endVisit(CompilationUnit node) {
				List<PreciseBeanTypeRefactoring.Fix> allFixes = pendingProblems.stream()
						.map(PendingProblem::fix)
						.filter(Objects::nonNull)
						.toList();

				if (!allFixes.isEmpty() && registry != null) {
					QuickfixType quickfixType = registry.getQuickfixType(JdtRefactorings.JDT_QUICKFIX);
					if (quickfixType != null) {
						String uri = docUri.toASCIIString();

						// Only worth offering as a separate fix when it would do more than the single-method fix already does.
						JdtFixDescriptor fileDescriptor = allFixes.size() > 1
								? new JdtFixDescriptor(new PreciseBeanTypeRefactoring(allFixes), List.of(uri), LABEL + " in file")
								: null;

						for (PendingProblem p : pendingProblems) {
							if (p.fix() == null) {
								continue;
							}
							String nodeLabel = "Replace return type with '" + p.replacementTypeDisplayName() + "'";
							JdtFixDescriptor nodeDescriptor = new JdtFixDescriptor(
									new PreciseBeanTypeRefactoring(p.fix()), List.of(uri), nodeLabel);
							p.problem().addQuickfix(new QuickfixData<>(quickfixType, nodeDescriptor, nodeLabel));
							if (fileDescriptor != null) {
								p.problem().addQuickfix(new QuickfixData<>(quickfixType, fileDescriptor, fileDescriptor.label()));
							}
						}
					}
				}

				for (PendingProblem p : pendingProblems) {
					context.getProblemCollector().accept(p.problem());
				}

				super.endVisit(node);
			}

		});
	}

}
