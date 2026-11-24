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
package org.springframework.ide.vscode.boot.kotlin.bindings;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypes.KotlinDeclaredVariable;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypes.KotlinMethod;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypes.KotlinPackage;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.AnnotationUse;

/**
 * What the nodes of the AST that is built from a Kotlin source file stand for in the Kotlin source.
 * This is told when the AST is built, and resolves the nodes from there with the
 * {@link KotlinTypeResolver}, into the Kotlin model ({@link ResolvedType} and friends). The JDT
 * bindings of the nodes are adapters over these (see {@code KotlinJdtBindingSource}).
 *
 * @author Martin Lippert
 */
public class KotlinNodeBindings {

	private final KotlinTypeResolver resolver;
	private final KotlinSyntax.File file;

	private final Map<AbstractTypeDeclaration, KotlinSourceClass> types = new IdentityHashMap<>();
	private final Map<MethodDeclaration, MethodSource> methods = new IdentityHashMap<>();
	private final Map<Annotation, AnnotationSource> annotations = new IdentityHashMap<>();
	private final Map<Type, TypeSource> typeReferences = new IdentityHashMap<>();
	private final Map<VariableDeclaration, VariableSource> variables = new IdentityHashMap<>();

	/**
	 * @param name the name of the method, the simple name of the class for a constructor
	 * @param returnType the declared return type, null if there is none (and for constructors)
	 */
	private record MethodSource(KotlinSourceClass declaringClass, String name, List<KotlinSyntax.Parameter> parameters, KotlinSyntax.TypeRef returnType,
			boolean constructor) {
	}

	private record AnnotationSource(AnnotationUse use, KotlinSourceClass scope) {
	}

	private record TypeSource(KotlinSyntax.TypeRef type, KotlinSourceClass scope) {
	}

	private record VariableSource(String name, KotlinSyntax.TypeRef type, KotlinSourceClass declaringClass, boolean parameter) {
	}

	public KotlinNodeBindings(KotlinTypeResolver resolver, KotlinSyntax.File file) {
		this.resolver = resolver;
		this.file = file;
	}

	public synchronized void registerType(AbstractTypeDeclaration node, KotlinSourceClass source) {
		types.put(node, source);
	}

	public synchronized void registerMethod(MethodDeclaration node, KotlinSourceClass declaringClass, KotlinSyntax.Function function) {
		methods.put(node, new MethodSource(declaringClass, function.name(), function.parameters(), function.returnType(), false));
	}

	public synchronized void registerConstructor(MethodDeclaration node, KotlinSourceClass declaringClass, KotlinSyntax.Constructor constructor) {
		methods.put(node, new MethodSource(declaringClass, declaringClass.declaration().name(), constructor.parameters(), null, true));
	}

	/**
	 * A parameter of a function or a constructor, or the field of a property.
	 *
	 * @param declaringClass the class the variable is declared in, which is the scope its type is resolved in
	 */
	public synchronized void registerVariable(VariableDeclaration node, String name, KotlinSyntax.TypeRef type, KotlinSourceClass declaringClass, boolean parameter) {
		variables.put(node, new VariableSource(name, type, declaringClass, parameter));
	}

	public ResolvedVariable resolveVariable(VariableDeclaration declaration) {
		VariableSource source;
		synchronized (this) {
			source = variables.get(declaration);
		}
		if (source == null) {
			return null;
		}
		return new KotlinDeclaredVariable(source.name(), resolver.sourceType(source.declaringClass()),
				resolver.resolveTypeReference(source.type(), file, source.declaringClass()), source.parameter());
	}

	/**
	 * @param scope the class the annotation is written in, null for the top level
	 */
	public synchronized void registerAnnotation(Annotation node, AnnotationUse use, KotlinSourceClass scope) {
		annotations.put(node, new AnnotationSource(use, scope));
	}

	public ResolvedType resolveType(AbstractTypeDeclaration declaration) {
		KotlinSourceClass source;
		synchronized (this) {
			source = types.get(declaration);
		}
		return source != null ? resolver.sourceType(source) : null;
	}

	/**
	 * @param name the type as written
	 * @param scope the class the type is written in, null for the top level
	 */
	public void registerTypeReference(Type node, String name, KotlinSourceClass scope) {
		registerTypeReference(node, new KotlinSyntax.TypeRef(name, false, List.of(), null), scope);
	}

	/**
	 * @param type the type as written, with its type arguments and whether it is nullable
	 * @param scope the class the type is written in, null for the top level
	 */
	public synchronized void registerTypeReference(Type node, KotlinSyntax.TypeRef type, KotlinSourceClass scope) {
		typeReferences.put(node, new TypeSource(type, scope));
	}

	public ResolvedType resolveTypeReference(Type type) {
		TypeSource source;
		synchronized (this) {
			source = typeReferences.get(type);
		}
		return source != null ? resolver.resolveTypeReference(source.type(), file, source.scope()) : null;
	}

	/** the file the AST was built from */
	public KotlinSyntax.File file() {
		return file;
	}

	/** evaluates constants that are referred to in the file */
	public KotlinConstantResolver constants() {
		return resolver.constants();
	}

	public ResolvedPackage resolvePackage(PackageDeclaration declaration) {
		return new KotlinPackage(file.packageName());
	}

	public ResolvedAnnotation resolveAnnotation(Annotation annotation) {
		AnnotationSource source;
		synchronized (this) {
			source = annotations.get(annotation);
		}
		return source != null ? resolver.resolveAnnotation(source.use(), file, source.scope()) : null;
	}

	public ResolvedMethod resolveMethod(MethodDeclaration declaration) {
		MethodSource source;
		synchronized (this) {
			source = methods.get(declaration);
		}
		if (source == null) {
			return null;
		}

		List<ResolvedType> parameterTypes = new ArrayList<>();
		for (KotlinSyntax.Parameter parameter : source.parameters()) {
			parameterTypes.add(resolver.resolveTypeReference(parameter.type(), file, source.declaringClass()));
		}

		return new KotlinMethod(source.name(), resolver.sourceType(source.declaringClass()), parameterTypes,
				resolver.resolveReturnType(source.returnType(), file, source.declaringClass()), source.constructor());
	}

}
