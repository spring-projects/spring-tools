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
import org.springframework.ide.vscode.boot.java.data.SpringDataPropertyUtils;
import org.springframework.ide.vscode.boot.java.data.SpringDataPropertyUtils.ResolvedChain;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtFixDescriptor;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtRefactorings;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.TypeSafePropertyReferenceRefactoring;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.TypeSafePropertyReferenceRefactoring.PropertyReferenceDescriptor;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.TypeSafePropertyReferenceRefactoring.PropertySegment;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.quickfix.Quickfix.QuickfixData;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;

/**
 * Detects string-based property references in Spring Data code ({@code Sort.by("prop")},
 * {@code Criteria.where("prop")}, etc.) and reports diagnostics using a two-tier
 * domain type resolution strategy, with quick fixes to convert to type-safe references.
 * <p>
 * <b>Tier 1 — Exact resolution:</b> If the string property reference is structurally
 * nested inside a repository/template call that directly reveals the domain type, we
 * report an INFO diagnostic mentioning the domain type name.
 * <p>
 * <b>Tier 2 — Contextual resolution:</b> If exact resolution fails, we scan the
 * enclosing code block for any repository/template calls that reveal domain types.
 * If exactly one candidate is found, we report INFO with that type name; otherwise
 * we report a generic INFO.
 * <p>
 * <b>Quick fixes:</b> When domain types are known, the reconciler attaches quick fixes
 * to replace the string literal with type-safe method references or {@code PropertyPath}
 * chains. Exact property matches have priority; fuzzy (Jaro-Winkler) matches are offered
 * as fallback.
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
	private final QuickfixRegistry quickfixRegistry;

	public SpringDataStringPropertyReferenceReconciler(QuickfixRegistry quickfixRegistry) {
		this.quickfixRegistry = quickfixRegistry;
	}

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
					processMethodInvocation(node, docURI, context);
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

	private void processMethodInvocation(MethodInvocation node, URI docURI, ReconcilingContext context) {
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
			processSortByCall(node, docURI, context);
		} else if (isSortOrderCall(node, erasedFqn)) {
			processSortOrderCall(node, docURI, context);
		} else if (isCriteriaWhereCall(node, erasedFqn)) {
			processCriteriaWhereCall(node, docURI, context);
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

	private void processSortByCall(MethodInvocation node, URI docURI, ReconcilingContext context) {
		@SuppressWarnings("unchecked")
		List<Expression> args = node.arguments();

		boolean hasStringArgs = args.stream().anyMatch(StringLiteral.class::isInstance);
		if (!hasStringArgs) {
			return;
		}

		for (Expression arg : args) {
			if (arg instanceof StringLiteral stringLiteral) {
				reportProblem(node, stringLiteral, docURI, context);
			}
		}
	}

	private void processSortOrderCall(MethodInvocation node, URI docURI, ReconcilingContext context) {
		@SuppressWarnings("unchecked")
		List<Expression> args = node.arguments();

		for (Expression arg : args) {
			if (arg instanceof StringLiteral stringLiteral) {
				reportProblem(node, stringLiteral, docURI, context);
				return;
			}
		}
	}

	private void processCriteriaWhereCall(MethodInvocation node, URI docURI, ReconcilingContext context) {
		@SuppressWarnings("unchecked")
		List<Expression> args = node.arguments();

		if (args.size() == 1 && args.get(0) instanceof StringLiteral stringLiteral) {
			reportProblem(node, stringLiteral, docURI, context);
		}
	}

	/**
	 * Two-tier domain type resolution, problem reporting, and quick fix attachment.
	 */
	private void reportProblem(MethodInvocation callSite, StringLiteral stringLiteral, URI docURI, ReconcilingContext context) {
		// Tier 1: Exact resolution — walk the parent chain
		ITypeBinding exactType = domainTypeResolver.determineDomainTypeExact(callSite);

		List<ITypeBinding> domainTypes;
		String domainTypeName;

		if (exactType != null) {
			domainTypes = List.of(exactType);
			domainTypeName = exactType.getName();
		} else {
			// Tier 2: Contextual resolution — scan the enclosing block
			List<ITypeBinding> candidates = domainTypeResolver.determineDomainTypesFromBlock(callSite);
			domainTypes = candidates;
			domainTypeName = candidates.size() == 1 ? candidates.get(0).getName() : null;
		}

		String message;
		if (domainTypeName != null) {
			message = "Non type-safe property reference for domain type '" + domainTypeName + "'";
		} else {
			message = "Non type-safe property reference";
		}
		ReconcileProblemImpl problem = new ReconcileProblemImpl(
				Boot4JavaProblemType.SPRING_DATA_STRING_PROPERTY_REFERENCE,
				message, stringLiteral.getStartPosition(), stringLiteral.getLength());

		attachQuickFixes(problem, stringLiteral, domainTypes, docURI);

		context.getProblemCollector().accept(problem);
	}

	// =====================================================================
	// Quick fix attachment
	// =====================================================================

	private void attachQuickFixes(ReconcileProblemImpl problem, StringLiteral stringLiteral,
			List<ITypeBinding> domainTypes, URI docURI) {
		if (domainTypes.isEmpty() || quickfixRegistry == null) {
			return;
		}

		QuickfixType quickfixType = quickfixRegistry.getQuickfixType(JdtRefactorings.JDT_QUICKFIX);
		if (quickfixType == null) {
			return;
		}

		String propertyValue = stringLiteral.getLiteralValue();
		int offset = stringLiteral.getStartPosition();
		String docUri = docURI.toASCIIString();

		// resolvePropertyChain already prefers exact (score=1.0) chains
		// over fuzzy ones — just collect results across all domain types
		boolean first = true;
		for (ITypeBinding domainType : domainTypes) {
			List<ResolvedChain> chains = SpringDataPropertyUtils.resolvePropertyChain(domainType, propertyValue);
			for (ResolvedChain chain : chains) {
				String label = buildQuickFixLabel(chain.segments());
				JdtFixDescriptor descriptor = new JdtFixDescriptor(
						new TypeSafePropertyReferenceRefactoring(
								new PropertyReferenceDescriptor(offset, chain.segments())),
						List.of(docUri), label);
				problem.addQuickfix(new QuickfixData<>(quickfixType, descriptor, label, first));
				first = false;
			}
		}
	}

	/**
	 * Build a human-readable label for the quick fix menu.
	 * Single segment: {@code "Replace with Customer::getFirstName"}
	 * Multi-segment: {@code "Replace with PropertyPath.of(Customer::getAddress).then(Address::getCountry)"}
	 */
	static String buildQuickFixLabel(List<PropertySegment> segments) {
		if (segments.size() == 1) {
			PropertySegment s = segments.get(0);
			return "Replace with " + extractSimpleName(s.domainTypeFqn()) + "::" + s.methodName();
		}

		StringBuilder sb = new StringBuilder("Replace with PropertyPath.of(");
		PropertySegment first = segments.get(0);
		sb.append(extractSimpleName(first.domainTypeFqn())).append("::").append(first.methodName());
		sb.append(")");
		for (int i = 1; i < segments.size(); i++) {
			PropertySegment s = segments.get(i);
			sb.append(".then(").append(extractSimpleName(s.domainTypeFqn())).append("::").append(s.methodName()).append(")");
		}
		return sb.toString();
	}

	private static String extractSimpleName(String fqn) {
		int lastDot = fqn.lastIndexOf('.');
		return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
	}

	private String getErasedFqn(ITypeBinding type) {
		if (type.isParameterizedType()) {
			return type.getErasure().getQualifiedName();
		}
		return type.getQualifiedName();
	}

}
