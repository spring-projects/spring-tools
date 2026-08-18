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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtFixDescriptor;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtRefactorUtils;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtRefactorings;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.ReplaceScopeAnnotationRefactoring;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.quickfix.Quickfix.QuickfixData;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;

/**
 * Reconciler that detects {@code @Scope("request"/"session"/"application")} annotations and
 * offers a quickfix to replace them with the equivalent, more specific {@code @RequestScope},
 * {@code @SessionScope}, or {@code @ApplicationScope} annotation.
 * <p>
 * {@code @RequestScope}/{@code @SessionScope}/{@code @ApplicationScope} default their
 * {@code proxyMode} attribute to {@code ScopedProxyMode.TARGET_CLASS}, whereas plain
 * {@code @Scope} defaults it to {@code ScopedProxyMode.DEFAULT} (effectively no proxy). So:
 * <ul>
 *   <li>when the {@code @Scope} annotation already explicitly sets
 *       {@code proxyMode = ScopedProxyMode.TARGET_CLASS}, a single, behavior-preserving fix is
 *       offered that drops to the bare form (e.g. {@code @RequestScope});</li>
 *   <li>otherwise (no explicit {@code proxyMode}, or a different one), two fixes are offered:
 *       one that carries the current proxy mode over explicitly to preserve behavior exactly,
 *       and one that switches to the bare form - which changes the effective proxy mode to
 *       {@code TARGET_CLASS}.</li>
 * </ul>
 * If the scope name isn't a literal {@code "request"}/{@code "session"}/{@code "application"},
 * or an explicitly set {@code proxyMode} can't be resolved to a known constant, the annotation
 * is left alone entirely.
 * <p>
 * Only applicable when {@code spring-web} is on the project's classpath, since that is where
 * {@code @RequestScope}/{@code @SessionScope}/{@code @ApplicationScope} live.
 *
 * @author Martin Lippert
 */
public class ScopeAnnotationReconciler implements JdtAstReconciler {

	private static final String TARGET_CLASS_PROXY_MODE = "TARGET_CLASS";

	private static final Map<String, String> SCOPE_NAME_TO_ANNOTATION_FQN = Map.of(
			"request", Annotations.SPRING_REQUEST_SCOPE,
			"session", Annotations.SPRING_SESSION_SCOPE,
			"application", Annotations.SPRING_APPLICATION_SCOPE);

	private final QuickfixRegistry registry;

	public ScopeAnnotationReconciler(QuickfixRegistry registry) {
		this.registry = registry;
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		// @RequestScope/@SessionScope/@ApplicationScope live in spring-web - without it on the
		// classpath there is nothing more specific to offer than plain @Scope
		return springBootVersionGreaterOrEqual(2, 0, 0).test(project)
				&& SpringProjectUtil.hasDependencyStartingWith(project, SpringProjectUtil.SPRING_WEB, null);
	}

	@Override
	public ProblemType getProblemType() {
		return Boot2JavaProblemType.JAVA_PRECISE_SCOPE;
	}

	@Override
	public Optional<ASTVisitor> createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {

		return Optional.of(new ASTVisitor() {

			@Override
			public boolean visit(MarkerAnnotation node) {
				processAnnotation(node);
				return false;
			}

			@Override
			public boolean visit(NormalAnnotation node) {
				processAnnotation(node);
				return false;
			}

			@Override
			public boolean visit(SingleMemberAnnotation node) {
				processAnnotation(node);
				return false;
			}

			private void processAnnotation(Annotation a) {
				ASTNode parent = a.getParent();
				if (!(parent instanceof TypeDeclaration) && !(parent instanceof MethodDeclaration)) {
					return;
				}

				ITypeBinding type = a.resolveTypeBinding();
				if (type == null || !Annotations.SCOPE.equals(type.getQualifiedName())) {
					return;
				}

				String scopeName = ASTUtils.getAttribute(a, "value")
						.or(() -> ASTUtils.getAttribute(a, "scopeName"))
						.map(expr -> ASTUtils.getExpressionValueAsString(expr, dep -> {}))
						.orElse(null);

				String targetFqn = scopeName == null ? null : SCOPE_NAME_TO_ANNOTATION_FQN.get(scopeName);
				if (targetFqn == null) {
					return;
				}

				boolean proxyModeSpecified = ASTUtils.getAttribute(a, "proxyMode").isPresent();
				String explicitProxyMode = ASTUtils.getAttribute(a, "proxyMode")
						.map(expr -> ASTUtils.getExpressionValueAsString(expr, dep -> {}))
						.orElse(null);
				if (proxyModeSpecified && explicitProxyMode == null) {
					// proxyMode is set to something we can't resolve to a known constant - too risky to touch
					return;
				}

				String targetSimpleName = JdtRefactorUtils.extractSimpleName(targetFqn);
				String uri = docUri.toASCIIString();
				String message = "Use the more specific `@%s` annotation instead of `@Scope(\"%s\")`".formatted(targetSimpleName, scopeName);
				ReconcileProblemImpl problem = new ReconcileProblemImpl(getProblemType(), message, a.getStartPosition(), a.getLength());

				int declarationOffset = parent.getStartPosition();
				QuickfixType quickfixType = registry.getQuickfixType(JdtRefactorings.JDT_QUICKFIX);
				if (quickfixType != null) {
					if (TARGET_CLASS_PROXY_MODE.equals(explicitProxyMode)) {
						addFix(problem, quickfixType, uri, "Replace with `@%s`".formatted(targetSimpleName),
								new ReplaceScopeAnnotationRefactoring(declarationOffset, targetFqn, null), true);
					} else {
						String preservedProxyMode = explicitProxyMode != null ? explicitProxyMode : "DEFAULT";
						addFix(problem, quickfixType, uri,
								"Replace with `@%s`, keeping its current proxy mode (`%s`)".formatted(targetSimpleName, preservedProxyMode),
								new ReplaceScopeAnnotationRefactoring(declarationOffset, targetFqn, preservedProxyMode), true);
						addFix(problem, quickfixType, uri,
								"Replace with `@%s` (changes its proxy mode to the default, `TARGET_CLASS`)".formatted(targetSimpleName),
								new ReplaceScopeAnnotationRefactoring(declarationOffset, targetFqn, null), false);
					}
				}

				context.getProblemCollector().accept(problem);
			}

			private void addFix(ReconcileProblemImpl problem, QuickfixType quickfixType, String uri, String label,
					ReplaceScopeAnnotationRefactoring refactoring, boolean preferred) {
				JdtFixDescriptor fix = new JdtFixDescriptor(refactoring, List.of(uri), label);
				problem.addQuickfix(new QuickfixData<>(quickfixType, fix, label, preferred));
			}

		});
	}

}
