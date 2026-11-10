/*******************************************************************************
 * Copyright (c) 2016, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.commons.jandex;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.springframework.ide.vscode.commons.java.Flags;
import org.springframework.ide.vscode.commons.java.IAnnotation;
import org.springframework.ide.vscode.commons.java.IField;
import org.springframework.ide.vscode.commons.java.IJavaModuleData;
import org.springframework.ide.vscode.commons.java.IJavaType;
import org.springframework.ide.vscode.commons.java.IJavadocProvider;
import org.springframework.ide.vscode.commons.java.IMethod;
import org.springframework.ide.vscode.commons.java.IType;
import org.springframework.ide.vscode.commons.javadoc.IJavadoc;

class TypeImpl implements IType {

	private ClassInfo info;
	private JandexIndex index;
	private IJavadocProvider javadocProvider;
	private IJavaModuleData classpathContainer;

	TypeImpl(JandexIndex index, IJavaModuleData classpathContainer, ClassInfo info, IJavadocProvider javadocProvider) {
		this.info = info;
		this.index = index;
		this.classpathContainer = classpathContainer;
		this.javadocProvider = javadocProvider;
	}

	@Override
	public int getFlags() {
		return info.flags();
	}

	@Override
	public IType getDeclaringType() {
		DotName enclosingClass = info.enclosingClass();
		return enclosingClass == null ? this : index.findType(enclosingClass.toString());
	}

	@Override
	public String getElementName() {
		return info.simpleName() == null ? info.name().local() : info.simpleName();
	}

	@Override
	public IJavadoc getJavaDoc() {
		return javadocProvider == null ? null : javadocProvider.getJavadoc(this);
	}

	@Override
	public boolean exists() {
		return true;
	}

	@Override
	public Stream<IAnnotation> getAnnotations() {
		List<AnnotationInstance> annotations = info.annotations();
		return annotations == null ? Stream.empty() : annotations.stream().map(a -> Wrappers.wrap(a, javadocProvider));
	}

	@Override
	public Stream<IAnnotation> getDeclaredAnnotations() {
		List<AnnotationInstance> annotations = info.declaredAnnotations();
		return annotations == null ? Stream.empty() : annotations.stream().map(a -> Wrappers.wrap(a, javadocProvider));
	}

	@Override
	public boolean isClass() {
		return true;
	}

	@Override
	public boolean isEnum() {
		return Flags.isEnum(info.flags());
	}

	@Override
	public boolean isInterface() {
		return Flags.isInterface(info.flags());
	}

	@Override
	public boolean isAnnotation() {
		return Flags.isAnnotation(info.flags());
	}
	
	public boolean isRecord() {
		return info.isRecord();
	}

	@Override
	public String getFullyQualifiedName() {
		return info.name().toString();
	}

	@Override
	public IField getField(String name) {
		FieldInfo fieldInfo = info.field(name);
		return fieldInfo == null ? null : Wrappers.wrap(this, fieldInfo, javadocProvider);
	}

	@Override
	public Stream<IField> getFields() {
		if (info.isRecord()) {
			return info.recordComponents().stream().map(rc -> {
				return Wrappers.wrap(this, rc.field(), javadocProvider);
			});
		} else {
			return info.fields().stream().map(f -> {
				return Wrappers.wrap(this, f, javadocProvider);
			});
		}
	}

	@Override
	public IMethod getMethod(String name, Stream<IJavaType> parameters) {
		List<Type> typeParameters = parameters.map(Wrappers::from).collect(Collectors.toList());
		MethodInfo methodInfo = info.method(name, typeParameters.toArray(new Type[typeParameters.size()]));
		return methodInfo == null ? null : Wrappers.wrap(this, methodInfo, javadocProvider);
	}

	@Override
	public Stream<IMethod> getMethods() {
		return info.methods().stream().map(m -> {
			return Wrappers.wrap(this, m, javadocProvider);
		});
	}

	@Override
	public String toString() {
		return info.toString();
	}

	@Override
	public int hashCode() {
		return info.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TypeImpl) {
			return info.equals(((TypeImpl)obj).info);
		}
		return super.equals(obj);
	}

	@Override
	public String getBindingKey() {
		return BindingKeyUtils.getBindingKey(info);
	}

	@Override
	public IJavaModuleData classpathContainer() {
		return classpathContainer;
	}

	@Override
	public String signature() {
		return info.toString();
	}

	@Override
	public String getSuperclassName() {
		DotName name = info.superName();
		return name == null ? null : name.toString();
	}

	@Override
	public String[] getSuperInterfaceNames() {
		return info.interfaceNames().stream().map(DotName::toString).toArray(String[]::new);
	}

}
