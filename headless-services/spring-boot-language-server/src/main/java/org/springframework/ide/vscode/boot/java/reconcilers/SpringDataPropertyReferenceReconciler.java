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
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.springframework.ide.vscode.boot.java.Boot4JavaProblemType;
import org.springframework.ide.vscode.boot.java.data.AbstractSpringDataDomainTypeResolver;
import org.springframework.ide.vscode.boot.java.data.SpringDataPropertyUtils;
import org.springframework.ide.vscode.boot.java.data.SpringDataPropertyUtils.ResolvedChain;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtFixDescriptor;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtRefactorings;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.TypeSafePropertyReferenceRefactoring;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.TypeSafePropertyReferenceRefactoring.PropertyReferenceDescriptor;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.TypeSafePropertyReferenceRefactoring.PropertySegment;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.quickfix.Quickfix.QuickfixData;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;

/**
 * Unified reconciler for Spring Data string-based property references across all modules.
 * Delegates module-specific detection and domain type resolution to a list of
 * {@link SpringDataPropertyReferenceContributor} strategy objects.
 * <p>
 * This single reconciler handles all modules (Commons, MongoDB, Relational, Cassandra)
 * and produces a unified "fix all in file" quick fix that spans all detected problems,
 * regardless of which module's contributor found them.
 */
public class SpringDataPropertyReferenceReconciler implements JdtAstReconciler {

	private static final String FIX_ALL_LABEL = "Replace all exact matches with type-safe property references in file";

	private final QuickfixRegistry quickfixRegistry;
	private final List<SpringDataPropertyReferenceContributor> contributors;

	public SpringDataPropertyReferenceReconciler(QuickfixRegistry quickfixRegistry) {
		this.quickfixRegistry = quickfixRegistry;
		this.contributors = List.of(
				new SpringDataCommonsContributor(),
				new SpringDataMongoDbContributor(),
				new SpringDataRelationalContributor(),
				new SpringDataCassandraContributor()
		);
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		for (SpringDataPropertyReferenceContributor contributor : contributors) {
			if (contributor.isApplicable(project)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public ProblemType getProblemType() {
		return Boot4JavaProblemType.SPRING_DATA_STRING_PROPERTY_REFERENCE;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docURI, CompilationUnit cu, ReconcilingContext context) {
		List<SpringDataPropertyReferenceContributor> applicable = contributors.stream()
				.filter(c -> c.isApplicable(project))
				.toList();

		if (applicable.isEmpty()) {
			return null;
		}

		if (context.isCompleteAst()) {
			return new PropertyReferenceVisitor(applicable, docURI, context);
		} else {
			List<String> allRelevantTypes = applicable.stream()
					.flatMap(c -> c.getRelevantTypesFqn().stream())
					.collect(Collectors.toList());
			if (ReconcileUtils.isAnyTypeUsed(cu, allRelevantTypes)) {
				throw new RequiredCompleteAstException();
			}
			return null;
		}
	}

	// =====================================================================
	// Visitor — collects problems during the walk, attaches "fix all" at the end
	// =====================================================================

	private class PropertyReferenceVisitor extends ASTVisitor {

		private final List<SpringDataPropertyReferenceContributor> applicable;
		private final URI docURI;
		private final ReconcilingContext context;
		private final List<PropertyReferenceDescriptor> allExactDescriptors = new ArrayList<>();
		private final List<ReconcileProblemImpl> allProblems = new ArrayList<>();
		private final List<ReconcileProblemImpl> fixAllEligibleProblems = new ArrayList<>();

		PropertyReferenceVisitor(List<SpringDataPropertyReferenceContributor> applicable,
				URI docURI, ReconcilingContext context) {
			this.applicable = applicable;
			this.docURI = docURI;
			this.context = context;
		}

		@Override
		public boolean visit(MethodInvocation node) {
			IMethodBinding methodBinding = node.resolveMethodBinding();
			if (methodBinding == null) {
				return true;
			}

			ITypeBinding declaringClass = methodBinding.getDeclaringClass();
			if (declaringClass == null) {
				return true;
			}

			for (SpringDataPropertyReferenceContributor contributor : applicable) {
				if (contributor.isPropertyReferenceCall(node, declaringClass)) {
					List<List<StringLiteral>> groups = contributor.extractStringLiteralGroups(node);
					for (List<StringLiteral> group : groups) {
						if (!group.isEmpty()) {
							collectProblem(contributor, node, group);
						}
					}
					break;
				}
			}
			return true;
		}

		@Override
		public void endVisit(CompilationUnit node) {
			attachFixAllInFile();
			for (ReconcileProblemImpl problem : allProblems) {
				context.getProblemCollector().accept(problem);
			}
		}

		private void collectProblem(SpringDataPropertyReferenceContributor contributor,
				MethodInvocation callSite, List<StringLiteral> literals) {
			AbstractSpringDataDomainTypeResolver resolver = contributor.getDomainTypeResolver();

			ITypeBinding exactType = resolver.determineDomainTypeExact(callSite);
			boolean exactDomainType = exactType != null;

			List<ITypeBinding> domainTypes;
			if (exactDomainType) {
				domainTypes = List.of(exactType);
			} else {
				domainTypes = resolver.determineDomainTypesFromBlock(callSite);
			}

			int startOffset = literals.get(0).getStartPosition();
			StringLiteral lastLiteral = literals.get(literals.size() - 1);
			int endOffset = lastLiteral.getStartPosition() + lastLiteral.getLength();
			int length = endOffset - startOffset;

			QuickFixResult qfResult = computeQuickFixes(
					contributor, literals, domainTypes, !exactDomainType);

			boolean plural = literals.size() > 1;
			String refWord = plural ? "references" : "reference";

			String message;
			if (exactDomainType && !qfResult.quickfixes().isEmpty()) {
				message = "Non type-safe property " + refWord
						+ " for domain type '" + exactType.getName() + "'";
			} else {
				message = "Non type-safe property " + refWord;
			}

			ReconcileProblemImpl problem = new ReconcileProblemImpl(
					Boot4JavaProblemType.SPRING_DATA_STRING_PROPERTY_REFERENCE,
					message, startOffset, length);

			for (QuickfixData<?> qf : qfResult.quickfixes()) {
				problem.addQuickfix(qf);
			}

			allProblems.add(problem);
			if (qfResult.exactMatch()) {
				fixAllEligibleProblems.add(problem);
			}
		}

		private QuickFixResult computeQuickFixes(SpringDataPropertyReferenceContributor contributor,
				List<StringLiteral> literals, List<ITypeBinding> domainTypes,
				boolean inferredDomainType) {
			if (domainTypes.isEmpty() || quickfixRegistry == null) {
				return QuickFixResult.EMPTY;
			}

			QuickfixType quickfixType = quickfixRegistry.getQuickfixType(JdtRefactorings.JDT_QUICKFIX);
			if (quickfixType == null) {
				return QuickFixResult.EMPTY;
			}

			String docUri = docURI.toASCIIString();
			Set<String> annotationFqns = contributor.getFieldAnnotationFqns();
			List<QuickfixData<?>> result = new ArrayList<>();
			boolean hasExactSingleDomainMatch = false;

			for (ITypeBinding domainType : domainTypes) {
				List<PropertyReferenceDescriptor> descriptors = new ArrayList<>();
				List<List<PropertySegment>> segments = new ArrayList<>();
				boolean allResolved = true;
				boolean allExact = true;

				for (StringLiteral literal : literals) {
					List<ResolvedChain> chains = SpringDataPropertyUtils.resolvePropertyChain(
							domainType, literal.getLiteralValue(), annotationFqns);
					if (chains.isEmpty()) {
						allResolved = false;
						break;
					}
					ResolvedChain bestChain = chains.get(0);
					descriptors.add(new PropertyReferenceDescriptor(literal.getStartPosition(), bestChain.segments()));
					segments.add(bestChain.segments());
					if (!bestChain.allExact()) {
						allExact = false;
					}
				}

				if (allResolved && !descriptors.isEmpty()) {
					boolean similarProperty = !allExact;
					String label = buildQuickFixLabel(segments, inferredDomainType, similarProperty);
					JdtFixDescriptor descriptor = new JdtFixDescriptor(
							new TypeSafePropertyReferenceRefactoring(
									descriptors.toArray(PropertyReferenceDescriptor[]::new)),
							List.of(docUri), label);
					result.add(new QuickfixData<>(quickfixType, descriptor, label, result.isEmpty()));

					if (allExact) {
						allExactDescriptors.addAll(descriptors);
						hasExactSingleDomainMatch = true;
					}
				}
			}

			boolean exactMatch = domainTypes.size() == 1 && hasExactSingleDomainMatch;
			return new QuickFixResult(result, exactMatch);
		}

		private void attachFixAllInFile() {
			if (fixAllEligibleProblems.size() < 2 || allExactDescriptors.isEmpty()
					|| quickfixRegistry == null) {
				return;
			}

			QuickfixType quickfixType = quickfixRegistry.getQuickfixType(JdtRefactorings.JDT_QUICKFIX);
			if (quickfixType == null) {
				return;
			}

			String docUri = docURI.toASCIIString();
			JdtFixDescriptor fixAllDescriptor = new JdtFixDescriptor(
					new TypeSafePropertyReferenceRefactoring(
							allExactDescriptors.toArray(PropertyReferenceDescriptor[]::new)),
					List.of(docUri), FIX_ALL_LABEL);

			for (ReconcileProblemImpl problem : fixAllEligibleProblems) {
				problem.addQuickfix(new QuickfixData<>(quickfixType, fixAllDescriptor, FIX_ALL_LABEL, false));
			}
		}
	}

	private record QuickFixResult(List<QuickfixData<?>> quickfixes, boolean exactMatch) {
		static final QuickFixResult EMPTY = new QuickFixResult(List.of(), false);
	}

	// =====================================================================
	// Label building — shared by all contributors
	// =====================================================================

	static String buildQuickFixLabel(List<List<PropertySegment>> segmentGroups,
			boolean inferred, boolean similarProperty) {
		String suffix = buildQualifierSuffix(inferred, similarProperty);
		if (segmentGroups.size() == 1) {
			return buildSingleLabel(segmentGroups.get(0)) + suffix;
		}
		return "Replace with type-safe property references" + suffix;
	}

	private static String buildQualifierSuffix(boolean inferred, boolean similarProperty) {
		if (!inferred && !similarProperty) {
			return "";
		}
		List<String> qualifiers = new ArrayList<>();
		if (inferred) {
			qualifiers.add("inferred");
		}
		if (similarProperty) {
			qualifiers.add("similar match");
		}
		return " (" + String.join(", ", qualifiers) + ")";
	}

	private static String buildSingleLabel(List<PropertySegment> segments) {
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

}
