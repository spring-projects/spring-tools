/*******************************************************************************
 * Copyright (c) 2017, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.handlers;

import java.util.Collection;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;

/**
 * @author Martin Lippert
 * @author Kris De Volder
 */
public interface SymbolProvider {

	default void addSymbols(Annotation node, ITypeBinding typeBinding, Collection<ITypeBinding> metaAnnotations, SpringIndexerJavaContext context) {};
	default void addSymbols(TypeDeclaration typeDeclaration, SpringIndexerJavaContext context) {};
	default void addSymbols(RecordDeclaration typeDeclaration, SpringIndexerJavaContext context) {};
	default void addSymbols(MethodDeclaration methodDeclaration, SpringIndexerJavaContext context) {};
	default void addSymbols(PackageDeclaration packageDeclaration, SpringIndexerJavaContext context) {};

}
