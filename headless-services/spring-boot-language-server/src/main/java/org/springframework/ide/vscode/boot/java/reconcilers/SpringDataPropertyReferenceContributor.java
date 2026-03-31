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

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.springframework.ide.vscode.boot.java.data.AbstractSpringDataDomainTypeResolver;
import org.springframework.ide.vscode.commons.java.IJavaProject;

/**
 * Module-specific strategy for detecting string-based property references
 * in Spring Data code. Each implementation covers one Spring Data module
 * (Commons, MongoDB, Relational, Cassandra) and provides:
 * <ul>
 *   <li>Applicability check (dependency version)</li>
 *   <li>Import type FQNs for fast-path AST re-parse triggering</li>
 *   <li>Method invocation identification and string literal extraction</li>
 *   <li>Domain type resolution</li>
 *   <li>Field/column annotation FQNs for annotation-aware property matching</li>
 * </ul>
 */
interface SpringDataPropertyReferenceContributor {

	boolean isApplicable(IJavaProject project);

	List<String> getRelevantTypesFqn();

	boolean isPropertyReferenceCall(MethodInvocation node, ITypeBinding declaringType);

	List<List<StringLiteral>> extractStringLiteralGroups(MethodInvocation node);

	AbstractSpringDataDomainTypeResolver getDomainTypeResolver();

	Set<String> getFieldAnnotationFqns();

	// =====================================================================
	// Shared utilities available to all contributors
	// =====================================================================

	static String getErasedFqn(ITypeBinding type) {
		if (type.isParameterizedType()) {
			return type.getErasure().getQualifiedName();
		}
		return type.getQualifiedName();
	}

	static boolean hasTypedPropertyPathOverload(IMethodBinding methodBinding, int argIndex) {
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
			if ("org.springframework.data.core.TypedPropertyPath".equals(candidateParam.getQualifiedName())) {
				return true;
			}
		}
		return false;
	}

}
