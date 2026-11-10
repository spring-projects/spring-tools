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
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Modifier;

/**
 * A JDT package binding for the package of a Kotlin file or of a type.
 *
 * @author Martin Lippert
 */
final class KotlinPackageBinding implements IPackageBinding {

	private final String name;

	KotlinPackageBinding(String name) {
		this.name = name;
	}

	@Override
	public int getKind() {
		return IBinding.PACKAGE;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public boolean isUnnamed() {
		return name.isEmpty();
	}

	@Override
	public String[] getNameComponents() {
		return name.isEmpty() ? new String[0] : name.split("\\.");
	}

	@Override
	public String getKey() {
		return name.replace('.', '/');
	}

	@Override
	public ITypeBinding findTypeBinding(String name) {
		KotlinJdtBindings.gap("IPackageBinding.findTypeBinding");
		return null;
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
		return binding != null && binding.getKind() == IBinding.PACKAGE && getKey().equals(binding.getKey());
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof KotlinPackageBinding other && name.equals(other.name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public String toString() {
		return "package " + name;
	}

}
