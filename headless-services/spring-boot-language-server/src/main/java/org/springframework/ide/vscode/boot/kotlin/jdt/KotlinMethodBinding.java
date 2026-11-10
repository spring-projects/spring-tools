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
package org.springframework.ide.vscode.boot.kotlin.jdt;

import java.util.stream.Collectors;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedMethod;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedType;

/**
 * A JDT method binding for a Kotlin function. The annotations come from the annotation nodes of
 * the method declaration, the Kotlin model of a method does not carry them.
 *
 * @author Martin Lippert
 */
final class KotlinMethodBinding implements IMethodBinding {

	private final KotlinJdtBindings bindings;
	private final ResolvedMethod method;
	private final IAnnotationBinding[] annotations;

	KotlinMethodBinding(KotlinJdtBindings bindings, ResolvedMethod method, IAnnotationBinding[] annotations) {
		this.bindings = bindings;
		this.method = method;
		this.annotations = annotations;
	}

	@Override
	public int getKind() {
		return IBinding.METHOD;
	}

	@Override
	public String getName() {
		return method.getName();
	}

	/** in the syntax of JDT method keys: {@code Lpkg/Type;.name(Lpkg/Param;)Lpkg/Return;} */
	@Override
	public String getKey() {
		ResolvedType declaringClass = method.getDeclaringClass();
		ResolvedType returnType = method.getReturnType();
		return (declaringClass != null ? declaringClass.getKey() : "") + "." + method.getName()
				+ "(" + method.getParameterTypes().stream().map(t -> t.getErasure().getKey()).collect(Collectors.joining()) + ")"
				+ (returnType != null ? returnType.getErasure().getKey() : "V");
	}

	@Override
	public IAnnotationBinding[] getAnnotations() {
		return annotations;
	}

	@Override
	public int getModifiers() {
		return Modifier.PUBLIC;
	}

	@Override
	public boolean isDeprecated() {
		return false;
	}

	@Override
	public boolean isRecovered() {
		return false;
	}

	@Override
	public boolean isSynthetic() {
		return false;
	}

	@Override
	public IJavaElement getJavaElement() {
		return null;
	}

	@Override
	public boolean isEqualTo(IBinding binding) {
		return binding != null && binding.getKind() == IBinding.METHOD && getKey().equals(binding.getKey());
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof KotlinMethodBinding other && getKey().equals(other.getKey());
	}

	@Override
	public int hashCode() {
		return getKey().hashCode();
	}

	@Override
	public String toString() {
		return getKey();
	}

	@Override
	public ITypeBinding getDeclaringClass() {
		return bindings.type(method.getDeclaringClass());
	}

	@Override
	public ITypeBinding[] getParameterTypes() {
		return bindings.types(method.getParameterTypes());
	}

	@Override
	public ITypeBinding getReturnType() {
		return bindings.type(method.getReturnType()); // the Kotlin model maps Unit to void already
	}

	@Override
	public boolean isConstructor() {
		return false; // the primary constructor is not built yet
	}

	@Override
	public boolean isCompactConstructor() {
		return false;
	}

	@Override
	public boolean isCanonicalConstructor() {
		return false;
	}

	@Override
	public boolean isDefaultConstructor() {
		return false;
	}

	@Override
	public IBinding getDeclaringMember() {
		return null;
	}

	@Override
	public Object getDefaultValue() {
		return null;
	}

	@Override
	public IAnnotationBinding[] getParameterAnnotations(int paramIndex) {
		KotlinJdtBindings.gap("IMethodBinding.getParameterAnnotations");
		return KotlinJdtBindings.NO_ANNOTATIONS;
	}

	@Override
	public String[] getParameterNames() {
		KotlinJdtBindings.gap("IMethodBinding.getParameterNames");
		return new String[0];
	}

	@Override
	public ITypeBinding getDeclaredReceiverType() {
		return null;
	}

	@Override
	public ITypeBinding[] getExceptionTypes() {
		return KotlinJdtBindings.NO_TYPES;
	}

	@Override
	public ITypeBinding[] getTypeParameters() {
		return KotlinJdtBindings.NO_TYPES;
	}

	@Override
	public boolean isAnnotationMember() {
		return false;
	}

	@Override
	public boolean isGenericMethod() {
		return false;
	}

	@Override
	public boolean isParameterizedMethod() {
		return false;
	}

	@Override
	public ITypeBinding[] getTypeArguments() {
		return KotlinJdtBindings.NO_TYPES;
	}

	@Override
	public IMethodBinding getMethodDeclaration() {
		return this;
	}

	@Override
	public boolean isRawMethod() {
		return false;
	}

	@Override
	public boolean isSubsignature(IMethodBinding otherMethod) {
		KotlinJdtBindings.gap("IMethodBinding.isSubsignature");
		return false;
	}

	@Override
	public boolean isVarargs() {
		return false;
	}

	@Override
	public boolean overrides(IMethodBinding method) {
		KotlinJdtBindings.gap("IMethodBinding.overrides");
		return false;
	}

	@Override
	public IVariableBinding[] getSyntheticOuterLocals() {
		return new IVariableBinding[0];
	}

	@Override
	public boolean isSyntheticRecordMethod() {
		return false;
	}

}
