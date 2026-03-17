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
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.springframework.ide.vscode.boot.java.Boot4JavaProblemType;
import org.springframework.ide.vscode.boot.java.data.SpringDataDomainTypeResolver;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;

/**
 * Detects string-based property references in Spring Data code ({@code Sort.by("prop")},
 * {@code Criteria.where("prop")}, etc.) and reports diagnostics using a two-tier
 * domain type resolution strategy.
 * <p>
 * <b>Tier 1 — Exact resolution:</b> If the string property reference is structurally
 * nested inside a repository/template call that directly reveals the domain type, we
 * report an INFO diagnostic mentioning the domain type name.
 * <p>
 * <b>Tier 2 — Contextual resolution:</b> If exact resolution fails, we scan the
 * enclosing code block for any repository/template calls that reveal domain types.
 * If exactly one candidate is found, we report INFO with that type name; otherwise
 * we report a generic INFO.
 */
public class SpringDataStringPropertyReferenceReconciler implements JdtAstReconciler {

	private static final Set<String> SORT_FQN_TYPES = Set.of(
			"org.springframework.data.domain.Sort"
	);

	private static final Set<String> SORT_ORDER_FQN_TYPES = Set.of(
			"org.springframework.data.domain.Sort.Order"
	);

	private static final Set<String> CRITERIA_FQN_TYPES = Set.of(
			"org.springframework.data.mongodb.core.query.Criteria",
			"org.springframework.data.relational.core.query.Criteria",
			"org.springframework.data.cassandra.core.query.Criteria"
	);

	private final SpringDataDomainTypeResolver domainTypeResolver = new SpringDataDomainTypeResolver();

	@Override
	public boolean isApplicable(IJavaProject project) {
		Version version = SpringProjectUtil.getDependencyVersionByPrefix(project, "spring-data-commons");
		return version != null && version.compareTo(new Version(4, 1, 0, null)) >= 0;
	}

	@Override
	public ProblemType getProblemType() {
		return Boot4JavaProblemType.SPRING_DATA_STRING_PROPERTY_REFERENCE;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docURI, CompilationUnit cu, ReconcilingContext context) {
		if (context.isCompleteAst()) {
			return new ASTVisitor() {

				@Override
				public boolean visit(MethodInvocation node) {
					processMethodInvocation(node, context);
					return true;
				}
			};
		} else {
			boolean needsFullAst = ReconcileUtils.isAnyTypeUsed(cu, List.of(
					"org.springframework.data.domain.Sort",
					"org.springframework.data.domain.Sort.Order",
					"org.springframework.data.mongodb.core.query.Criteria",
					"org.springframework.data.relational.core.query.Criteria",
					"org.springframework.data.cassandra.core.query.Criteria"
			));

			if (needsFullAst) {
				throw new RequiredCompleteAstException();
			}

			return null;
		}
	}

	private void processMethodInvocation(MethodInvocation node, ReconcilingContext context) {
		IMethodBinding methodBinding = node.resolveMethodBinding();
		if (methodBinding == null) {
			return;
		}

		ITypeBinding declaringClass = methodBinding.getDeclaringClass();
		if (declaringClass == null) {
			return;
		}

		String erasedFqn = getErasedFqn(declaringClass);

		if (isSortByCall(node, erasedFqn)) {
			processSortByCall(node, context);
		} else if (isSortOrderCall(node, erasedFqn)) {
			processSortOrderCall(node, context);
		} else if (isCriteriaWhereCall(node, erasedFqn)) {
			processCriteriaWhereCall(node, context);
		}
	}

	private boolean isSortByCall(MethodInvocation node, String declaringFqn) {
		return "by".equals(node.getName().getIdentifier()) && SORT_FQN_TYPES.contains(declaringFqn);
	}

	private boolean isSortOrderCall(MethodInvocation node, String declaringFqn) {
		String methodName = node.getName().getIdentifier();
		return ("asc".equals(methodName) || "desc".equals(methodName) || "by".equals(methodName))
				&& SORT_ORDER_FQN_TYPES.contains(declaringFqn);
	}

	private boolean isCriteriaWhereCall(MethodInvocation node, String declaringFqn) {
		return "where".equals(node.getName().getIdentifier()) && CRITERIA_FQN_TYPES.contains(declaringFqn);
	}

	private void processSortByCall(MethodInvocation node, ReconcilingContext context) {
		@SuppressWarnings("unchecked")
		List<Expression> args = node.arguments();

		boolean hasStringArgs = args.stream().anyMatch(StringLiteral.class::isInstance);
		if (!hasStringArgs) {
			return;
		}

		for (Expression arg : args) {
			if (arg instanceof StringLiteral stringLiteral) {
				reportProblem(node, stringLiteral, context);
			}
		}
	}

	private void processSortOrderCall(MethodInvocation node, ReconcilingContext context) {
		@SuppressWarnings("unchecked")
		List<Expression> args = node.arguments();

		for (Expression arg : args) {
			if (arg instanceof StringLiteral stringLiteral) {
				reportProblem(node, stringLiteral, context);
				return;
			}
		}
	}

	private void processCriteriaWhereCall(MethodInvocation node, ReconcilingContext context) {
		@SuppressWarnings("unchecked")
		List<Expression> args = node.arguments();

		if (args.size() == 1 && args.get(0) instanceof StringLiteral stringLiteral) {
			reportProblem(node, stringLiteral, context);
		}
	}

	/**
	 * Two-tier domain type resolution and problem reporting.
	 *
	 * @param callSite the Sort.by / Sort.Order.desc / Criteria.where invocation
	 * @param stringLiteral the string literal containing the property name
	 * @param context the reconciling context for collecting problems
	 */
	private void reportProblem(MethodInvocation callSite, StringLiteral stringLiteral, ReconcilingContext context) {
		// Tier 1: Exact resolution — walk the parent chain for a structurally
		// enclosing invocation that directly reveals the domain type.
		ITypeBinding exactType = domainTypeResolver.determineDomainTypeExact(callSite);

		if (exactType != null) {
			reportInfo(stringLiteral, exactType.getName(), context);
			return;
		}

		// Tier 2: Contextual resolution — scan the enclosing block for any
		// repository/template calls that reveal domain types.
		List<ITypeBinding> candidates = domainTypeResolver.determineDomainTypesFromBlock(callSite);

		if (candidates.size() == 1) {
			reportInfo(stringLiteral, candidates.get(0).getName(), context);
		} else {
			reportInfo(stringLiteral, null, context);
		}
	}

	private void reportInfo(StringLiteral stringLiteral, String domainTypeName, ReconcilingContext context) {
		String message;
		if (domainTypeName != null) {
			message = "Non type-safe property reference for domain type '" + domainTypeName + "'";
		} else {
			message = "Non type-safe property reference";
		}
		ReconcileProblemImpl problem = new ReconcileProblemImpl(
				Boot4JavaProblemType.SPRING_DATA_STRING_PROPERTY_REFERENCE,
				message, stringLiteral.getStartPosition(), stringLiteral.getLength());
		context.getProblemCollector().accept(problem);
	}

	private String getErasedFqn(ITypeBinding type) {
		if (type.isParameterizedType()) {
			return type.getErasure().getQualifiedName();
		}
		return type.getQualifiedName();
	}

}
