/*******************************************************************************
 * Copyright (c) 2016, 2023 Pivotal, Inc.
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
import java.util.stream.Stream;

import org.jboss.jandex.MethodInfo;
import org.springframework.ide.vscode.commons.java.IAnnotation;
import org.springframework.ide.vscode.commons.java.IJavaType;
import org.springframework.ide.vscode.commons.java.IJavadocProvider;
import org.springframework.ide.vscode.commons.java.IMethod;
import org.springframework.ide.vscode.commons.java.IType;
import org.springframework.ide.vscode.commons.javadoc.IJavadoc;

public class MethodImpl implements IMethod {

	/**
	 * Test code may set this to manually inject test data to make up for some missing 
	 * capabilities of Jandex (e.g. discovering the names of method parameters).
	 */
	public static TestDataProvider testDataProvider = null;

	private static final String JANDEX_CONTRUCTOR_NAME = "<init>";

	private IType declaringType;
	private MethodInfo method;
	private IJavadocProvider javadocProvider;

	MethodImpl(IType declaringType, MethodInfo method, IJavadocProvider javadocProvider) {
		this.declaringType = declaringType;
		this.method = method;
		this.javadocProvider =javadocProvider;
	}

	@Override
	public int getFlags() {
		return method.flags();
	}

	@Override
	public boolean isConstructor() {
		return method.name().equals(JANDEX_CONTRUCTOR_NAME);
	}

	@Override
	public IType getDeclaringType() {
		return declaringType;
	}

	@Override
	public String getElementName() {
		return isConstructor() ? getDeclaringType().getElementName() : method.name();
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
		return method.annotations().stream().map(a -> Wrappers.wrap(a, javadocProvider));
	}

	@Override
	public IJavaType getReturnType() {
		return Wrappers.wrap(method.returnType());
	}

	@Override
	public String toString() {
		return method.toString();
	}

	@Override
	public Stream<IJavaType> parameters() {
		return method.parameters().stream().map(p -> Wrappers.wrap(p.type()));
	}

	@Override
	public int hashCode() {
		return method.toString().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof MethodImpl) {
			return method.toString().equals(((MethodImpl)obj).method.toString());
		}
		return super.equals(obj);
	}

	@Override
	public String getBindingKey() {
		return BindingKeyUtils.getBindingKey(method);
	}

	@Override
	public String signature() {
		//Return Jandex signature for now
		return method.toString();
	}

	@Override
	public List<String> getParameterNames() {
		if (testDataProvider != null) {
			return testDataProvider.getParameterNames(this);
		} else {
			throw new UnsupportedOperationException("Not supported with jandex");
		}
	}

}
