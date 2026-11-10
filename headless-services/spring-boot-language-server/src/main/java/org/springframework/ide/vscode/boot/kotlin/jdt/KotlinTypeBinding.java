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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedType;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypeResolver;

/**
 * A JDT type binding for a type that the Kotlin type resolution resolved: a class of a Kotlin
 * source file, a class from the classpath, a primitive, an array, a parameterized type.
 *
 * @author Martin Lippert
 */
final class KotlinTypeBinding implements ITypeBinding {

	private static final String PRIMITIVE_KEYS = "ZBCSIJFDV";

	private final KotlinJdtBindings bindings;
	private final ResolvedType type;

	KotlinTypeBinding(KotlinJdtBindings bindings, ResolvedType type) {
		this.bindings = bindings;
		this.type = type;
	}

	ResolvedType resolved() {
		return type;
	}

	// --- IBinding

	@Override
	public int getKind() {
		return IBinding.TYPE;
	}

	@Override
	public String getName() {
		return type.getName();
	}

	@Override
	public String getKey() {
		return type.getKey();
	}

	@Override
	public IAnnotationBinding[] getAnnotations() {
		return bindings.annotations(type.getAnnotations());
	}

	@Override
	public int getModifiers() {
		// Kotlin classes are public unless said otherwise. That they are final unless open is left
		// out: the kotlin-spring compiler plugin opens the Spring components, and nothing indexed is
		// about finality
		return isPrimitive() || isArray() ? Modifier.NONE : Modifier.PUBLIC;
	}

	@Override
	public int getDeclaredModifiers() {
		return getModifiers();
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
		return binding != null && binding.getKind() == IBinding.TYPE && getKey().equals(binding.getKey());
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof KotlinTypeBinding other && getKey().equals(other.getKey());
	}

	@Override
	public int hashCode() {
		return getKey().hashCode();
	}

	@Override
	public String toString() {
		return getQualifiedName();
	}

	// --- names and kinds

	@Override
	public String getQualifiedName() {
		return type.getQualifiedName();
	}

	@Override
	public String getBinaryName() {
		return type.getBinaryName();
	}

	@Override
	public IPackageBinding getPackage() {
		if (isPrimitive() || isArray()) {
			return null;
		}
		String binaryName = type.getBinaryName();
		if (binaryName == null) {
			return null;
		}
		int index = binaryName.lastIndexOf('.');
		return new KotlinPackageBinding(index >= 0 ? binaryName.substring(0, index) : "");
	}

	@Override
	public boolean isPrimitive() {
		String key = getKey();
		return key.length() == 1 && PRIMITIVE_KEYS.indexOf(key.charAt(0)) >= 0;
	}

	@Override
	public boolean isArray() {
		return getKey().startsWith("[");
	}

	@Override
	public boolean isAnnotation() {
		return type.isAnnotation();
	}

	@Override
	public boolean isInterface() {
		return type.isInterface();
	}

	@Override
	public boolean isClass() {
		return !isInterface() && !isPrimitive() && !isArray() && !isNullType();
	}

	@Override
	public boolean isEnum() {
		return false; // enum classes are not built yet
	}

	@Override
	public boolean isRecord() {
		return false;
	}

	@Override
	public boolean isFromSource() {
		return KotlinTypeResolver.isSourceType(type);
	}

	@Override
	public boolean isNullType() {
		return false;
	}

	@Override
	public boolean isNested() {
		String binaryName = type.getBinaryName();
		return binaryName != null && binaryName.indexOf('$') >= 0;
	}

	@Override
	public boolean isMember() {
		return isNested();
	}

	@Override
	public boolean isTopLevel() {
		return !isNested() && !isPrimitive() && !isArray();
	}

	@Override
	public boolean isLocal() {
		return false;
	}

	@Override
	public boolean isAnonymous() {
		return false;
	}

	@Override
	public ITypeBinding getDeclaringClass() {
		if (!isNested()) {
			return null;
		}
		String binaryName = type.getBinaryName();
		return bindings.type(bindings.resolver().resolveBinaryName(binaryName.substring(0, binaryName.lastIndexOf('$'))));
	}

	@Override
	public IMethodBinding getDeclaringMethod() {
		return null;
	}

	@Override
	public IBinding getDeclaringMember() {
		return null;
	}

	// --- the hierarchy

	@Override
	public ITypeBinding getSuperclass() {
		return bindings.type(type.getSuperclass());
	}

	@Override
	public ITypeBinding[] getInterfaces() {
		return bindings.types(type.getInterfaces());
	}

	@Override
	public boolean isSubTypeCompatible(ITypeBinding other) {
		if (other == null) {
			return false;
		}
		if (isPrimitive() || other.isPrimitive()) {
			return getKey().equals(other.getKey());
		}
		if ("Ljava/lang/Object;".equals(other.getErasure().getKey())) {
			return true;
		}

		String target = other.getErasure().getKey();
		Set<String> seen = new HashSet<>();
		Deque<ResolvedType> todo = new ArrayDeque<>();
		todo.add(type.getErasure());

		while (!todo.isEmpty()) {
			ResolvedType current = todo.poll();
			if (!seen.add(current.getKey())) {
				continue;
			}
			if (current.getKey().equals(target)) {
				return true;
			}
			if (current.getSuperclass() != null) {
				todo.add(current.getSuperclass().getErasure());
			}
			current.getInterfaces().forEach(i -> todo.add(i.getErasure()));
		}
		return false;
	}

	@Override
	public boolean isAssignmentCompatible(ITypeBinding other) {
		KotlinJdtBindings.gap("ITypeBinding.isAssignmentCompatible (no boxing, widening)");
		return isSubTypeCompatible(other);
	}

	@Override
	public boolean isCastCompatible(ITypeBinding other) {
		KotlinJdtBindings.gap("ITypeBinding.isCastCompatible");
		return isSubTypeCompatible(other) || (other != null && other.isSubTypeCompatible(this));
	}

	// --- generics

	@Override
	public ITypeBinding getErasure() {
		return bindings.type(type.getErasure());
	}

	@Override
	public ITypeBinding getTypeDeclaration() {
		return getErasure();
	}

	@Override
	public boolean isParameterizedType() {
		return type.isParameterizedType();
	}

	@Override
	public ITypeBinding[] getTypeArguments() {
		if (type.isParameterizedType()) {
			KotlinJdtBindings.gap("ITypeBinding.getTypeArguments");
		}
		return KotlinJdtBindings.NO_TYPES;
	}

	@Override
	public boolean isGenericType() {
		return false;
	}

	@Override
	public boolean isRawType() {
		return false;
	}

	@Override
	public ITypeBinding[] getTypeParameters() {
		return KotlinJdtBindings.NO_TYPES;
	}

	@Override
	public boolean isTypeVariable() {
		return false;
	}

	@Override
	public ITypeBinding[] getTypeBounds() {
		return KotlinJdtBindings.NO_TYPES;
	}

	@Override
	public boolean isWildcardType() {
		return false;
	}

	@Override
	public ITypeBinding getBound() {
		return null;
	}

	@Override
	public boolean isUpperbound() {
		return false;
	}

	@Override
	public ITypeBinding getWildcard() {
		return null;
	}

	@Override
	public ITypeBinding getGenericTypeOfWildcardType() {
		return null;
	}

	@Override
	public int getRank() {
		return -1;
	}

	@Override
	public boolean isCapture() {
		return false;
	}

	@Override
	public boolean isIntersectionType() {
		return false;
	}

	// --- arrays

	@Override
	public int getDimensions() {
		if (isArray()) {
			KotlinJdtBindings.gap("ITypeBinding.getDimensions");
		}
		return 0;
	}

	@Override
	public ITypeBinding getElementType() {
		if (isArray()) {
			KotlinJdtBindings.gap("ITypeBinding.getElementType");
		}
		return null;
	}

	@Override
	public ITypeBinding getComponentType() {
		if (isArray()) {
			KotlinJdtBindings.gap("ITypeBinding.getComponentType");
		}
		return null;
	}

	@Override
	public ITypeBinding createArrayType(int dimension) {
		KotlinJdtBindings.gap("ITypeBinding.createArrayType");
		return null;
	}

	// --- members, which the Kotlin model does not have for types yet

	@Override
	public IVariableBinding[] getDeclaredFields() {
		KotlinJdtBindings.gap("ITypeBinding.getDeclaredFields");
		return new IVariableBinding[0];
	}

	@Override
	public IMethodBinding[] getDeclaredMethods() {
		KotlinJdtBindings.gap("ITypeBinding.getDeclaredMethods");
		return new IMethodBinding[0];
	}

	@Override
	public ITypeBinding[] getDeclaredTypes() {
		KotlinJdtBindings.gap("ITypeBinding.getDeclaredTypes");
		return KotlinJdtBindings.NO_TYPES;
	}

	@Override
	public IMethodBinding getFunctionalInterfaceMethod() {
		KotlinJdtBindings.gap("ITypeBinding.getFunctionalInterfaceMethod");
		return null;
	}

	@Override
	public IAnnotationBinding[] getTypeAnnotations() {
		return KotlinJdtBindings.NO_ANNOTATIONS;
	}

}
