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
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedMemberValuePair;

/**
 * A JDT binding for an attribute of an annotation of the Kotlin model. The value is converted to
 * what JDT has there: type bindings for class literals, variable bindings for enum constants,
 * annotation bindings for nested annotations, arrays for arrays, boxed values and strings as is.
 *
 * @author Martin Lippert
 */
final class KotlinMemberValuePairBinding implements IMemberValuePairBinding {

	private final KotlinJdtBindings bindings;
	private final ResolvedMemberValuePair pair;

	KotlinMemberValuePairBinding(KotlinJdtBindings bindings, ResolvedMemberValuePair pair) {
		this.bindings = bindings;
		this.pair = pair;
	}

	@Override
	public int getKind() {
		return IBinding.MEMBER_VALUE_PAIR;
	}

	@Override
	public String getName() {
		return pair.getName();
	}

	@Override
	public Object getValue() {
		return bindings.value(pair.getValue());
	}

	@Override
	public boolean isDefault() {
		return false;
	}

	@Override
	public IMethodBinding getMethodBinding() {
		KotlinJdtBindings.gap("IMemberValuePairBinding.getMethodBinding");
		return null;
	}

	@Override
	public String getKey() {
		return null; // as for JDT
	}

	@Override
	public IAnnotationBinding[] getAnnotations() {
		return KotlinJdtBindings.NO_ANNOTATIONS;
	}

	@Override
	public int getModifiers() {
		return Modifier.NONE;
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
		return binding == this;
	}

	@Override
	public String toString() {
		return pair.toString();
	}

}
