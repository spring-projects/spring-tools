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
 * Abstract base for Spring Data string property reference reconcilers.
 * <p>
 * Provides the shared visitor logic, two-tier domain type resolution, problem reporting,
 * quick fix attachment, and the {@code TypedPropertyPath} overload check. Concrete
 * subclasses supply module-specific behavior:
 * <ul>
 *   <li>{@link #isApplicable} — dependency version check</li>
 *   <li>{@link #getRelevantTypesFqn} — import fast-path for {@code RequiredCompleteAstException}</li>
 *   <li>{@link #isPropertyReferenceCall} — declaring class identification (receives {@code ITypeBinding})</li>
 *   <li>{@link #extractStringLiteralGroups} — string literal extraction with overload validation</li>
 *   <li>{@link #getDomainTypeResolver} — module-specific domain type resolver</li>
 * </ul>
 */
public abstract class AbstractSpringDataPropertyReferenceReconciler implements JdtAstReconciler {

	private static final String TYPED_PROPERTY_PATH_FQN = "org.springframework.data.core.TypedPropertyPath";

	private final QuickfixRegistry quickfixRegistry;

	protected AbstractSpringDataPropertyReferenceReconciler(QuickfixRegistry quickfixRegistry) {
		this.quickfixRegistry = quickfixRegistry;
	}

	@Override
	public ProblemType getProblemType() {
		return Boot4JavaProblemType.SPRING_DATA_STRING_PROPERTY_REFERENCE;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docURI, CompilationUnit cu, ReconcilingContext context) {
		if (context.isCompleteAst()) {
			return new PropertyReferenceVisitor(docURI, context);
		} else {
			if (ReconcileUtils.isAnyTypeUsed(cu, getRelevantTypesFqn())) {
				throw new RequiredCompleteAstException();
			}
			return null;
		}
	}

	// =====================================================================
	// Abstract methods — implemented by each module-specific subclass
	// =====================================================================

	/**
	 * Returns the FQNs of types whose presence in imports should trigger a
	 * full AST re-parse. Used for the {@code RequiredCompleteAstException} fast path.
	 */
	protected abstract List<String> getRelevantTypesFqn();

	/**
	 * Checks whether the declaring class of this method invocation is one that
	 * this reconciler cares about (e.g. {@code Sort}, {@code Criteria}, {@code Update}).
	 *
	 * @param declaringType the resolved type binding of the method's declaring class
	 */
	protected abstract boolean isPropertyReferenceCall(MethodInvocation node, ITypeBinding declaringType);

	/**
	 * Extracts groups of {@link StringLiteral} arguments from the method invocation that
	 * represent property names with a type-safe alternative available.
	 * <p>
	 * Each inner list is a group of literals that must be replaced together (e.g., all
	 * varargs string arguments to {@code Sort.by("a", "b")}). Non-varargs calls return
	 * a single-element inner list per detected literal.
	 * <p>
	 * Implementations should use {@link #hasTypedPropertyPathOverload} to verify
	 * that a {@code TypedPropertyPath} overload exists before including a string literal.
	 */
	protected abstract List<List<StringLiteral>> extractStringLiteralGroups(MethodInvocation node);

	/**
	 * Returns the module-specific domain type resolver.
	 */
	protected abstract AbstractSpringDataDomainTypeResolver getDomainTypeResolver();

	// =====================================================================
	// Visitor — collects problems during the walk, attaches "fix all" at the end
	// =====================================================================

	private static final String FIX_ALL_LABEL = "Replace all with type-safe property references in file";

	private class PropertyReferenceVisitor extends ASTVisitor {

		private final URI docURI;
		private final ReconcilingContext context;
		private final List<PropertyReferenceDescriptor> allExactDescriptors = new ArrayList<>();
		private final List<ReconcileProblemImpl> allProblems = new ArrayList<>();

		PropertyReferenceVisitor(URI docURI, ReconcilingContext context) {
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

			if (isPropertyReferenceCall(node, declaringClass)) {
				List<List<StringLiteral>> groups = extractStringLiteralGroups(node);
				for (List<StringLiteral> group : groups) {
					if (!group.isEmpty()) {
						collectProblem(node, group);
					}
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

		private void collectProblem(MethodInvocation callSite, List<StringLiteral> literals) {
			AbstractSpringDataDomainTypeResolver resolver = getDomainTypeResolver();

			ITypeBinding exactType = resolver.determineDomainTypeExact(callSite);

			List<ITypeBinding> domainTypes;
			String domainTypeName;

			if (exactType != null) {
				domainTypes = List.of(exactType);
				domainTypeName = exactType.getName();
			} else {
				List<ITypeBinding> candidates = resolver.determineDomainTypesFromBlock(callSite);
				domainTypes = candidates;
				domainTypeName = candidates.size() == 1 ? candidates.get(0).getName() : null;
			}

			int startOffset = literals.get(0).getStartPosition();
			StringLiteral lastLiteral = literals.get(literals.size() - 1);
			int endOffset = lastLiteral.getStartPosition() + lastLiteral.getLength();
			int length = endOffset - startOffset;

			List<QuickfixData<?>> quickfixes = computeQuickFixes(literals, domainTypes);

			String message = (domainTypeName != null && !quickfixes.isEmpty())
					? "Non type-safe property reference for domain type '" + domainTypeName + "'"
					: "Non type-safe property reference";

			ReconcileProblemImpl problem = new ReconcileProblemImpl(
					Boot4JavaProblemType.SPRING_DATA_STRING_PROPERTY_REFERENCE,
					message, startOffset, length);

			for (QuickfixData<?> qf : quickfixes) {
				problem.addQuickfix(qf);
			}

			allProblems.add(problem);
		}

		private List<QuickfixData<?>> computeQuickFixes(List<StringLiteral> literals,
				List<ITypeBinding> domainTypes) {
			if (domainTypes.isEmpty() || quickfixRegistry == null) {
				return List.of();
			}

			QuickfixType quickfixType = quickfixRegistry.getQuickfixType(JdtRefactorings.JDT_QUICKFIX);
			if (quickfixType == null) {
				return List.of();
			}

			String docUri = docURI.toASCIIString();
			List<QuickfixData<?>> result = new ArrayList<>();

			for (ITypeBinding domainType : domainTypes) {
				List<PropertyReferenceDescriptor> descriptors = new ArrayList<>();
				List<List<PropertySegment>> segments = new ArrayList<>();
				boolean allResolved = true;
				boolean allExact = true;

				for (StringLiteral literal : literals) {
					List<ResolvedChain> chains = SpringDataPropertyUtils.resolvePropertyChain(domainType, literal.getLiteralValue());
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
					String label = buildQuickFixLabel(segments);
					JdtFixDescriptor descriptor = new JdtFixDescriptor(
							new TypeSafePropertyReferenceRefactoring(
									descriptors.toArray(PropertyReferenceDescriptor[]::new)),
							List.of(docUri), label);
					result.add(new QuickfixData<>(quickfixType, descriptor, label, result.isEmpty()));

					if (allExact) {
						allExactDescriptors.addAll(descriptors);
					}
				}
			}

			return result;
		}

		private void attachFixAllInFile() {
			if (allExactDescriptors.isEmpty() || quickfixRegistry == null) {
				return;
			}

			List<ReconcileProblemImpl> problemsWithFixes = allProblems.stream()
					.filter(p -> !p.getQuickfixes().isEmpty())
					.toList();

			if (problemsWithFixes.size() < 2) {
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

			for (ReconcileProblemImpl problem : problemsWithFixes) {
				problem.addQuickfix(new QuickfixData<>(quickfixType, fixAllDescriptor, FIX_ALL_LABEL, false));
			}
		}
	}

	static String buildQuickFixLabel(List<List<PropertySegment>> segmentGroups) {
		if (segmentGroups.size() == 1) {
			return buildSingleLabel(segmentGroups.get(0));
		}
		return "Replace with type-safe property references";
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

	// =====================================================================
	// TypedPropertyPath overload check
	// =====================================================================

	/**
	 * Checks whether the declaring class of the given method has an overload with
	 * the same name where the parameter at {@code argIndex} is {@code TypedPropertyPath}
	 * instead of {@code String}. Handles varargs: for a varargs parameter like
	 * {@code TypedPropertyPath...}, JDT represents it as {@code TypedPropertyPath[]},
	 * so we unwrap the array component type before comparing.
	 */
	protected static boolean hasTypedPropertyPathOverload(IMethodBinding methodBinding, int argIndex) {
		ITypeBinding declaringClass = methodBinding.getDeclaringClass();
		if (declaringClass == null) {
			return false;
		}

		String methodName = methodBinding.getName();
		ITypeBinding[] paramTypes = methodBinding.getParameterTypes();
		int effectiveIndex = methodBinding.isVarargs()
				? Math.min(argIndex, paramTypes.length - 1)
				: argIndex;

		for (IMethodBinding candidate : declaringClass.getDeclaredMethods()) {
			if (!methodName.equals(candidate.getName())) {
				continue;
			}
			ITypeBinding[] candidateParams = candidate.getParameterTypes();
			if (candidateParams.length != paramTypes.length) {
				continue;
			}
			if (effectiveIndex >= candidateParams.length) {
				continue;
			}
			ITypeBinding candidateParam = candidateParams[effectiveIndex].getErasure();
			if (candidateParam.isArray()) {
				candidateParam = candidateParam.getComponentType().getErasure();
			}
			if (TYPED_PROPERTY_PATH_FQN.equals(candidateParam.getQualifiedName())) {
				return true;
			}
		}
		return false;
	}

	// =====================================================================
	// Utilities
	// =====================================================================

	private static String extractSimpleName(String fqn) {
		int lastDot = fqn.lastIndexOf('.');
		return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
	}

	protected static String getErasedFqn(ITypeBinding type) {
		if (type.isParameterizedType()) {
			return type.getErasure().getQualifiedName();
		}
		return type.getQualifiedName();
	}

}
