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

import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedAnnotation;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedMemberValuePair;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedMethod;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedPackage;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinNodeBindings;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypeResolver;

/**
 * The JDT bindings of an AST that was built from a Kotlin file: what the nodes stand for is known
 * from building the AST ({@link KotlinNodeBindings}), and it is handed out as JDT bindings, so the
 * {@code resolveBinding()} methods of the nodes work as they do for an AST of the JDT parser.
 *
 * @author Martin Lippert
 */
public final class KotlinJdtBindingSource implements JdtBindingSource {

	private final KotlinNodeBindings nodes;
	private final KotlinTypeResolver resolver;
	private final KotlinJdtBindings bindings;

	public KotlinJdtBindingSource(KotlinNodeBindings nodes, KotlinTypeResolver resolver) {
		this.nodes = nodes;
		this.resolver = resolver;
		this.bindings = new KotlinJdtBindings(resolver);
	}

	@Override
	public ITypeBinding resolveType(AbstractTypeDeclaration declaration) {
		return bindings.type(nodes.resolveType(declaration));
	}

	/**
	 * The return type of a method is not built as written in the Kotlin source (it is a placeholder
	 * node), so its node resolves to the return type of the method.
	 */
	@Override
	public ITypeBinding resolveType(Type type) {
		if (type.getParent() instanceof MethodDeclaration method && method.getReturnType2() == type) {
			ResolvedMethod resolved = nodes.resolveMethod(method);
			return resolved != null ? bindings.type(resolved.getReturnType()) : null;
		}
		return bindings.type(nodes.resolveTypeReference(type));
	}

	@Override
	public IMethodBinding resolveMethod(MethodDeclaration declaration) {
		ResolvedMethod method = nodes.resolveMethod(declaration);
		if (method == null) {
			return null;
		}

		IAnnotationBinding[] annotations = ((List<?>) declaration.modifiers()).stream()
				.filter(Annotation.class::isInstance)
				.map(modifier -> resolveAnnotation((Annotation) modifier))
				.filter(annotation -> annotation != null)
				.toArray(IAnnotationBinding[]::new);

		return new KotlinMethodBinding(bindings, method, annotations);
	}

	@Override
	public IAnnotationBinding resolveAnnotation(Annotation annotation) {
		return bindings.annotation(nodes.resolveAnnotation(annotation));
	}

	@Override
	public IMemberValuePairBinding resolveMemberValuePair(MemberValuePair pair) {
		if (pair.getParent() instanceof NormalAnnotation annotation) {
			ResolvedAnnotation resolved = nodes.resolveAnnotation(annotation);
			if (resolved != null) {
				String name = pair.getName().getIdentifier();
				for (ResolvedMemberValuePair resolvedPair : resolved.getDeclaredMemberValuePairs()) {
					if (name.equals(resolvedPair.getName())) {
						return new KotlinMemberValuePairBinding(bindings, resolvedPair);
					}
				}
			}
		}
		return null;
	}

	@Override
	public IPackageBinding resolvePackage(PackageDeclaration declaration) {
		ResolvedPackage resolved = nodes.resolvePackage(declaration);
		return resolved != null ? new KotlinPackageBinding(resolved.getName()) : null;
	}

	/**
	 * The names that are resolved are the ones of types: the type names of annotations and the
	 * names in type references. The other names are enum constants (the constants are evaluated when
	 * the AST is built) and stay unresolved: code that works on JDT bindings falls back to the
	 * identifier for them.
	 */
	@Override
	public IBinding resolveName(Name name) {
		ASTNode node = name;
		while (node.getParent() instanceof QualifiedName qualified && qualified.getName() == node) {
			node = qualified;
		}

		if (node.getParent() instanceof Annotation annotation && annotation.getTypeName() == node) {
			IAnnotationBinding binding = resolveAnnotation(annotation);
			return binding != null ? binding.getAnnotationType() : null;
		}
		if (node.getParent() instanceof SimpleType type) {
			return resolveType(type);
		}
		return null;
	}

	@Override
	public ITypeBinding resolveExpressionType(Expression expression) {
		if (expression instanceof Annotation annotation) {
			IAnnotationBinding binding = resolveAnnotation(annotation);
			return binding != null ? binding.getAnnotationType() : null;
		}
		if (expression instanceof StringLiteral) {
			return resolveWellKnownType("java.lang.String");
		}
		if (expression instanceof TypeLiteral) {
			KotlinJdtBindings.gap("Expression.resolveTypeBinding (class literal)");
		}
		return null;
	}

	@Override
	public Object resolveConstantExpressionValue(Expression expression) {
		if (expression instanceof StringLiteral string) {
			return string.getLiteralValue();
		}
		if (expression instanceof BooleanLiteral bool) {
			return bool.booleanValue();
		}
		if (expression instanceof CharacterLiteral character) {
			return character.charValue();
		}
		if (expression instanceof NumberLiteral number) {
			return numberValue(number.getToken());
		}
		return null;
	}

	private static Object numberValue(String token) {
		try {
			String text = token.replace("_", "");
			if (text.endsWith("L") || text.endsWith("l")) {
				return Long.decode(text.substring(0, text.length() - 1));
			}
			if (text.endsWith("f") || text.endsWith("F")) {
				return Float.valueOf(text);
			}
			if (text.contains(".") || text.endsWith("d") || text.endsWith("D")) {
				return Double.valueOf(text);
			}
			return Integer.decode(text);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	@Override
	public ITypeBinding resolveWellKnownType(String name) {
		return bindings.type(resolver.resolveBinaryName(name));
	}

}
