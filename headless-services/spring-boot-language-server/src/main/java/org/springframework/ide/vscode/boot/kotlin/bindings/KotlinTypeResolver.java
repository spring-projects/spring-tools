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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinBuiltins.Primitive;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypes.ArrayType;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypes.ClasspathType;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypes.KotlinVariable;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypes.SourceAnnotation;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypes.ParameterizedType;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypes.PrimitiveType;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypes.SourceType;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypes.UnresolvedType;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.AnnotationUse;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Import;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.TypeRef;
import org.springframework.ide.vscode.commons.java.ClasspathIndex;
import org.springframework.ide.vscode.commons.java.IType;

/**
 * Resolves the names that are written in Kotlin sources to the types they stand for, the way the
 * JVM sees them.
 * <p>
 * Names are looked up the way Kotlin does it: classes in the scope of the place the name is used
 * come first, then explicit imports, the package of the file, star imports and finally the
 * packages that every Kotlin file imports. What a name can stand for is found in the classes
 * declared in the Kotlin sources of the project, in the types Kotlin has built in (which are mapped
 * to what they are on the JVM) and on the classpath of the project.
 * <p>
 * This is not a type checker: it only resolves the names of types and annotations, nothing is
 * inferred.
 *
 * @author Martin Lippert
 */
public class KotlinTypeResolver {

	private static final Logger log = LoggerFactory.getLogger(KotlinTypeResolver.class);

	private final ClasspathIndex classpath;
	private final KotlinDeclarationIndex declarations;

	private final KotlinConstantResolver constants;

	private final Map<String, Optional<ResolvedType>> typesByBinaryName = new ConcurrentHashMap<>();
	private final Map<String, Boolean> existingClasses = new ConcurrentHashMap<>();

	/**
	 * @param classpath the classpath of the project, null if there is none to look types up in
	 */
	public KotlinTypeResolver(ClasspathIndex classpath, KotlinDeclarationIndex declarations) {
		this.classpath = classpath;
		this.declarations = declarations;
		this.constants = new KotlinConstantResolver(this, declarations);
	}

	/** evaluates the constants that are referred to in annotation arguments */
	public KotlinConstantResolver constants() {
		return constants;
	}

	/** forgets what was resolved so far, to be called when the sources changed */
	public void clearCaches() {
		typesByBinaryName.clear();
		existingClasses.clear();
		constants.clearCaches();
	}

	/**
	 * @param dotted the name of a class, with '.' between the names of nested classes as well
	 * @return the JVM name of the class, null if there is no such class
	 */
	String findClassName(String dotted) {
		return findClass(dotted, null);
	}

	/** @return null if there is no such class on the classpath of the project */
	IType classpathType(String binaryName) {
		return findOnClasspath(binaryName);
	}

	/**
	 * @param binaryName the JVM name, with '$' between the names of nested classes
	 * @return the type, null if there is no such type in the sources of the project or on its classpath
	 */
	public ResolvedType resolveBinaryName(String binaryName) {
		return typesByBinaryName.computeIfAbsent(binaryName, name -> Optional.ofNullable(lookup(name))).orElse(null);
	}

	private ResolvedType lookup(String binaryName) {
		KotlinSourceClass source = declarations.find(binaryName);
		if (source != null) {
			return new SourceType(this, source);
		}

		IType type = findOnClasspath(binaryName);
		return type != null ? new ClasspathType(this, type) : null;
	}

	private IType findOnClasspath(String binaryName) {
		if (classpath == null) {
			return null;
		}
		try {
			return classpath.findType(binaryName);
		}
		catch (Exception e) {
			log.debug("error looking up type {} on the classpath", binaryName, e);
			return null;
		}
	}

	/** the type of a class that is declared in Kotlin source */
	public ResolvedType sourceType(KotlinSourceClass source) {
		ResolvedType type = resolveBinaryName(source.binaryName());
		if (type instanceof SourceType sourceType && sourceType.source().equals(source)) {
			return type;
		}
		return new SourceType(this, source);
	}

	/** whether the type is a class that is declared in Kotlin source, rather than one from the classpath */
	public static boolean isSourceType(ResolvedType type) {
		return type instanceof SourceType;
	}

	/** whether the type is a name that could not be resolved, and is kept as it was written */
	public static boolean isUnresolved(ResolvedType type) {
		return type instanceof KotlinTypes.UnresolvedType;
	}

	/**
	 * Resolves a type as written in a Kotlin file.
	 *
	 * @param file the file the type is written in
	 * @param scope the class the type is written in, null for the top level
	 * @return the type, never null: a name that cannot be resolved is kept as it was written
	 */
	public ResolvedType resolveTypeReference(TypeRef reference, KotlinSyntax.File file, KotlinSourceClass scope) {
		return resolveType(reference, file, scope, false);
	}

	/**
	 * Resolves the return type of a function, where no type means {@code void}, and so does
	 * {@code Unit}.
	 *
	 * @param reference the declared return type, null if there is none
	 */
	public ResolvedType resolveReturnType(TypeRef reference, KotlinSyntax.File file, KotlinSourceClass scope) {
		if (reference == null) {
			return new PrimitiveType("void");
		}
		if (!reference.nullable() && KotlinBuiltins.UNIT.equals(resolveName(reference.name(), file, scope))) {
			return new PrimitiveType("void");
		}
		return resolveType(reference, file, scope, false);
	}

	private ResolvedType resolveType(TypeRef reference, KotlinSyntax.File file, KotlinSourceClass scope, boolean typeArgument) {
		if ("*".equals(reference.name())) {
			ResolvedType object = resolveBinaryName("java.lang.Object");
			return object != null ? object : new UnresolvedType("java.lang.Object");
		}

		String name = resolveName(reference.name(), file, scope);
		if (name == null) {
			return new UnresolvedType(reference.name());
		}

		Primitive primitive = KotlinBuiltins.primitive(name);
		if (primitive != null) {
			return reference.nullable() || typeArgument ? classOrUnresolved(primitive.boxed()) : new PrimitiveType(primitive.primitive());
		}

		String arrayElement = KotlinBuiltins.primitiveArrayElement(name);
		if (arrayElement != null) {
			return new ArrayType(new PrimitiveType(arrayElement));
		}

		if (KotlinBuiltins.ARRAY.equals(name)) {
			ResolvedType component = reference.arguments().size() == 1
					? resolveType(reference.arguments().get(0), file, scope, true)
					: classOrUnresolved("java.lang.Object");
			return new ArrayType(component);
		}

		String javaClass = KotlinBuiltins.javaClass(name);
		ResolvedType type = classOrUnresolved(javaClass != null ? javaClass : name);

		if (!reference.arguments().isEmpty() && !(type instanceof UnresolvedType)) {
			List<ResolvedType> arguments = new ArrayList<>();
			for (TypeRef argument : reference.arguments()) {
				arguments.add(resolveType(argument, file, scope, true));
			}
			return new ParameterizedType(type, arguments);
		}
		return type;
	}

	private ResolvedType classOrUnresolved(String binaryName) {
		ResolvedType type = resolveBinaryName(binaryName);
		return type != null ? type : new UnresolvedType(binaryName);
	}

	/**
	 * Resolves an annotation as written in a Kotlin file.
	 *
	 * @return the annotation, null if the annotation type cannot be resolved
	 */
	public ResolvedAnnotation resolveAnnotation(AnnotationUse use, KotlinSyntax.File file, KotlinSourceClass scope) {
		String name = resolveName(use.typeName(), file, scope);
		if (name == null) {
			return null;
		}

		String javaClass = KotlinBuiltins.javaClass(name);
		ResolvedType type = resolveBinaryName(javaClass != null ? javaClass : name);
		return type != null ? new SourceAnnotation(this, type, use, file, scope) : null;
	}

	/**
	 * The value of an annotation argument, as JDT presents the value of an annotation attribute: a
	 * String or a boxed primitive, a type for a class literal, a variable for an enum constant (or a
	 * constant that is not known), an array of those for an array. Null if the value is not known.
	 */
	Object bindingValue(KotlinSyntax.Value value, KotlinSyntax.File file, KotlinSourceClass scope) {
		if (value instanceof KotlinSyntax.ClassValue classValue) {
			return resolveTypeReference(new TypeRef(classValue.typeName(), false, List.of(), null), file, scope);
		}
		else if (value instanceof KotlinSyntax.ArrayValue array) {
			Object[] values = new Object[array.values().size()];
			for (int i = 0; i < values.length; i++) {
				values[i] = bindingValue(array.values().get(i), file, scope);
			}
			return values;
		}
		else if (value instanceof KotlinSyntax.ReferenceValue reference) {
			Object constant = constants.evaluate(value, file, scope);
			if (constant != null) {
				return constant;
			}

			// an enum constant, or a constant that cannot be evaluated: it is known by its name
			String name = reference.name();
			int dot = name.lastIndexOf('.');
			ResolvedType declaringClass = null;
			if (dot > 0) {
				String className = resolveName(name.substring(0, dot), file, scope);
				declaringClass = className != null ? resolveBinaryName(className) : null;
			}
			return new KotlinVariable(name.substring(dot + 1), declaringClass);
		}
		return constants.evaluate(value, file, scope);
	}

	/**
	 * Finds what a name that is written in a Kotlin file stands for.
	 *
	 * @param name the name as written, for example {@code Foo}, {@code Foo.Bar} or {@code com.example.Foo}
	 * @return the name of the class: its JVM name, or for the types Kotlin has built in the Kotlin name
	 *         of the type. Null if there is no such class.
	 */
	String resolveName(String name, KotlinSyntax.File file, KotlinSourceClass scope) {
		int dot = name.indexOf('.');
		String first = dot >= 0 ? name.substring(0, dot) : name;
		String rest = dot >= 0 ? name.substring(dot + 1) : "";
		String suffix = rest.isEmpty() ? "" : "." + rest;

		// classes the name is used in, and the classes nested in them
		for (KotlinSourceClass current = scope; current != null; current = current.outer()) {
			if (current.declaration().name().equals(first)) {
				String found = findClass(current.binaryName() + suffix, current.binaryName());
				if (found != null) {
					return found;
				}
			}
			for (KotlinSyntax.ClassDeclaration nested : current.declaration().nestedClasses()) {
				if (nested.name().equals(first)) {
					String found = findClass(current.binaryName() + "$" + first + suffix, current.binaryName());
					if (found != null) {
						return found;
					}
				}
			}
		}

		// explicit imports
		for (Import imported : file.imports()) {
			if (!imported.allUnder() && first.equals(importedName(imported))) {
				String found = findClass(imported.name() + suffix, null);
				if (found != null) {
					return found;
				}
			}
		}

		// the package of the file
		String found = findClass((file.packageName().isEmpty() ? first : file.packageName() + "." + first) + suffix, null);
		if (found != null) {
			return found;
		}

		// star imports
		for (Import imported : file.imports()) {
			if (imported.allUnder()) {
				found = findClass(imported.name() + "." + first + suffix, null);
				if (found != null) {
					return found;
				}
			}
		}

		// what every Kotlin file imports
		for (String defaultImport : KotlinBuiltins.DEFAULT_IMPORTS) {
			found = findClass(defaultImport + "." + first + suffix, null);
			if (found != null) {
				return found;
			}
		}

		// fully qualified
		return dot >= 0 ? findClass(name, null) : null;
	}

	private static String importedName(Import imported) {
		if (imported.alias() != null) {
			return imported.alias();
		}
		int dot = imported.name().lastIndexOf('.');
		return dot >= 0 ? imported.name().substring(dot + 1) : imported.name();
	}

	/**
	 * Finds the class a dotted name stands for, where any of the dots can be the one between a
	 * class and a class nested in it.
	 *
	 * @param candidate the name, with '.' or '$' between nested classes
	 * @param protectedPrefix the beginning of the name that is a binary name already (with '$' for nested classes)
	 */
	private String findClass(String candidate, String protectedPrefix) {
		String current = candidate;
		int minimum = protectedPrefix != null ? protectedPrefix.length() : 0;

		while (true) {
			if (exists(current)) {
				return current;
			}
			int dot = current.lastIndexOf('.');
			if (dot < minimum || dot < 0) {
				return null;
			}
			current = current.substring(0, dot) + "$" + current.substring(dot + 1);
		}
	}

	private boolean exists(String binaryName) {
		return existingClasses.computeIfAbsent(binaryName, name ->
				KotlinBuiltins.isBuiltin(name) || KotlinBuiltins.javaClass(name) != null
				|| declarations.find(name) != null || findOnClasspath(name) != null);
	}

}
