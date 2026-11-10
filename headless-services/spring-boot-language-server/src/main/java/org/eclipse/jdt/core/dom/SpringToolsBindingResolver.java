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
package org.eclipse.jdt.core.dom;

import org.springframework.ide.vscode.boot.kotlin.jdt.JdtBindingResolverBridge;
import org.springframework.ide.vscode.boot.kotlin.jdt.JdtBindingSource;

/**
 * A JDT {@link BindingResolver} that forwards to a {@link JdtBindingSource}, so that the nodes of
 * an AST that was built by hand (from Kotlin sources) resolve to bindings we provide.
 * <p>
 * <b>Never refer to this class from code.</b> It lives in a JDT package because
 * {@code BindingResolver} and {@code AST.setBindingResolver} are package-private, and the JDT jar
 * is signed: if the class loader loaded this class from our (unsigned) jar, it would fail with a
 * {@code SecurityException}, since all classes of a package have to be signed by the same signer.
 * {@link JdtBindingResolverBridge} reads the bytes of this class as a resource instead and defines
 * it with a lookup on {@link AST}, which gives it the protection domain of the JDT classes.
 * <p>
 * The overridden methods are package-private API of JDT. They are compiled against the JDT version
 * we use, so a signature that changes with a JDT update breaks the build instead of silently
 * leaving a method without an override.
 *
 * @author Martin Lippert
 */
public class SpringToolsBindingResolver extends BindingResolver {

	private final JdtBindingSource source;

	private SpringToolsBindingResolver(JdtBindingSource source) {
		this.source = source;
	}

	/** called reflectively by {@link JdtBindingResolverBridge} */
	public static void install(AST ast, JdtBindingSource source) {
		ast.setBindingResolver(new SpringToolsBindingResolver(source));
	}

	@Override
	ITypeBinding resolveType(TypeDeclaration type) {
		return source.resolveType(type);
	}

	@Override
	ITypeBinding resolveType(AnnotationTypeDeclaration type) {
		return source.resolveType(type);
	}

	@Override
	ITypeBinding resolveType(EnumDeclaration type) {
		return source.resolveType(type);
	}

	@Override
	ITypeBinding resolveType(RecordDeclaration type) {
		return source.resolveType(type);
	}

	@Override
	ITypeBinding resolveType(Type type) {
		return source.resolveType(type);
	}

	@Override
	IMethodBinding resolveMethod(MethodDeclaration method) {
		return source.resolveMethod(method);
	}

	@Override
	IVariableBinding resolveVariable(VariableDeclaration variable) {
		return source.resolveVariable(variable);
	}

	@Override
	IAnnotationBinding resolveAnnotation(Annotation annotation) {
		return source.resolveAnnotation(annotation);
	}

	@Override
	IMemberValuePairBinding resolveMemberValuePair(MemberValuePair memberValuePair) {
		return source.resolveMemberValuePair(memberValuePair);
	}

	@Override
	IPackageBinding resolvePackage(PackageDeclaration pkg) {
		return source.resolvePackage(pkg);
	}

	@Override
	IBinding resolveName(Name name) {
		return source.resolveName(name);
	}

	@Override
	ITypeBinding resolveExpressionType(Expression expression) {
		return source.resolveExpressionType(expression);
	}

	@Override
	Object resolveConstantExpressionValue(Expression expression) {
		return source.resolveConstantExpressionValue(expression);
	}

	@Override
	ITypeBinding resolveWellKnownType(String name) {
		return source.resolveWellKnownType(name);
	}

	@Override
	ASTNode findDeclaringNode(IBinding binding) {
		return source.findDeclaringNode(binding);
	}

	@Override
	ASTNode findDeclaringNode(String bindingKey) {
		return source.findDeclaringNode(bindingKey);
	}

}
