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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclaration;

/**
 * What the nodes of an AST that was not produced by the JDT parser resolve to, in terms of the JDT
 * binding interfaces. It is installed on the {@code AST} with {@link JdtBindingResolverBridge}, and
 * from then on the {@code resolveBinding()} methods of the nodes answer from here, so code that
 * works with JDT bindings runs on that AST unchanged.
 * <p>
 * This is the public face of the (package-private) JDT {@code BindingResolver}: one method for
 * each of the resolver methods that the bridge forwards. Everything returns null by default, as
 * the JDT resolver of an AST without bindings does.
 *
 * @author Martin Lippert
 */
public interface JdtBindingSource {

	/** for type, annotation type, enum and record declarations */
	default ITypeBinding resolveType(AbstractTypeDeclaration declaration) { return null; }

	/** for a type as it is written in the source, for example in a class literal */
	default ITypeBinding resolveType(Type type) { return null; }

	default IMethodBinding resolveMethod(MethodDeclaration declaration) { return null; }

	default IVariableBinding resolveVariable(VariableDeclaration declaration) { return null; }

	default IAnnotationBinding resolveAnnotation(Annotation annotation) { return null; }

	default IMemberValuePairBinding resolveMemberValuePair(MemberValuePair pair) { return null; }

	default IPackageBinding resolvePackage(PackageDeclaration declaration) { return null; }

	default IBinding resolveName(Name name) { return null; }

	/** the type of an expression, an annotation is an expression as well */
	default ITypeBinding resolveExpressionType(Expression expression) { return null; }

	default Object resolveConstantExpressionValue(Expression expression) { return null; }

	default ITypeBinding resolveWellKnownType(String name) { return null; }

	default ASTNode findDeclaringNode(IBinding binding) { return null; }

	default ASTNode findDeclaringNode(String bindingKey) { return null; }

}
