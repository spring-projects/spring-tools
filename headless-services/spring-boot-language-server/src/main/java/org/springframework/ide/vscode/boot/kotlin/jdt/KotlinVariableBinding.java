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

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedType;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedVariable;

/**
 * A JDT variable binding for a variable of the Kotlin model: enum constants and constants that are
 * not evaluated (both as values of annotation attributes), the fields of properties, and the
 * parameters of functions and constructors. The annotations come from the nodes of the
 * declaration, the Kotlin model of a variable does not carry them.
 *
 * @author Martin Lippert
 */
final class KotlinVariableBinding implements IVariableBinding {

	private final KotlinJdtBindings bindings;
	private final ResolvedVariable variable;
	private final IAnnotationBinding[] annotations;

	KotlinVariableBinding(KotlinJdtBindings bindings, ResolvedVariable variable, IAnnotationBinding[] annotations) {
		this.bindings = bindings;
		this.variable = variable;
		this.annotations = annotations;
	}

	/** a variable that is declared in the source: the field of a property, or a parameter */
	private boolean isDeclared() {
		return variable.getType() != null;
	}

	@Override
	public int getKind() {
		return IBinding.VARIABLE;
	}

	@Override
	public String getName() {
		return variable.getName();
	}

	@Override
	public String getKey() {
		ResolvedType declaringClass = variable.getDeclaringClass();
		return (declaringClass != null ? declaringClass.getKey() : "") + "." + variable.getName() + ")";
	}

	@Override
	public ITypeBinding getDeclaringClass() {
		return bindings.type(variable.getDeclaringClass());
	}

	@Override
	public boolean isField() {
		return !variable.isParameter();
	}

	@Override
	public boolean isEnumConstant() {
		if (isDeclared()) {
			return false;
		}
		// the Kotlin model does not tell enum constants from constants that were not evaluated
		KotlinJdtBindings.gap("IVariableBinding.isEnumConstant");
		return false;
	}

	@Override
	public boolean isParameter() {
		return variable.isParameter();
	}

	@Override
	public ITypeBinding getType() {
		if (!isDeclared()) {
			KotlinJdtBindings.gap("IVariableBinding.getType");
			return null;
		}
		return bindings.type(variable.getType());
	}

	@Override
	public Object getConstantValue() {
		return null; // constants are evaluated when the AST is built already
	}

	@Override
	public int getVariableId() {
		return 0;
	}

	@Override
	public IMethodBinding getDeclaringMethod() {
		return null;
	}

	@Override
	public IVariableBinding getVariableDeclaration() {
		return this;
	}

	@Override
	public boolean isEffectivelyFinal() {
		return true;
	}

	@Override
	public IAnnotationBinding[] getAnnotations() {
		return annotations;
	}

	@Override
	public int getModifiers() {
		if (variable.isParameter()) {
			return Modifier.NONE;
		}
		// the field of a property is private on the JVM, it is accessed through the getter and setter
		return isDeclared() ? Modifier.PRIVATE : Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL;
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
		return binding != null && binding.getKind() == IBinding.VARIABLE && getKey().equals(binding.getKey());
	}

	@Override
	public String toString() {
		return variable.toString();
	}

}
