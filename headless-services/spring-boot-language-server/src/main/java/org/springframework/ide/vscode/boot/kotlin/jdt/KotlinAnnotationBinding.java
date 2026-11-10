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
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedAnnotation;

/**
 * A JDT annotation binding for an annotation of the Kotlin model: one written in a Kotlin file,
 * or one of a class from the classpath.
 *
 * @author Martin Lippert
 */
final class KotlinAnnotationBinding implements IAnnotationBinding {

	private final KotlinJdtBindings bindings;
	private final ResolvedAnnotation annotation;

	KotlinAnnotationBinding(KotlinJdtBindings bindings, ResolvedAnnotation annotation) {
		this.bindings = bindings;
		this.annotation = annotation;
	}

	@Override
	public int getKind() {
		return IBinding.ANNOTATION;
	}

	@Override
	public String getName() {
		return annotation.getName();
	}

	@Override
	public String getKey() {
		return annotation.getAnnotationType().getKey() + "@";
	}

	@Override
	public ITypeBinding getAnnotationType() {
		return bindings.type(annotation.getAnnotationType());
	}

	@Override
	public IMemberValuePairBinding[] getDeclaredMemberValuePairs() {
		return annotation.getDeclaredMemberValuePairs().stream()
				.map(pair -> new KotlinMemberValuePairBinding(bindings, pair))
				.toArray(IMemberValuePairBinding[]::new);
	}

	@Override
	public IMemberValuePairBinding[] getAllMemberValuePairs() {
		KotlinJdtBindings.gap("IAnnotationBinding.getAllMemberValuePairs (without the defaults)");
		return getDeclaredMemberValuePairs();
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
		return "@" + annotation.getAnnotationType().getQualifiedName();
	}

}
