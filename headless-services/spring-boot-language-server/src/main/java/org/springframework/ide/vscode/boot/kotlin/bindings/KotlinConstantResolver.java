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

import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.BooleanValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ClassKind;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ConcatValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Constant;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Import;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.NumberValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.ReferenceValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.StringValue;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax.Value;
import org.springframework.ide.vscode.commons.java.IType;

/**
 * Evaluates the constant values that annotation arguments are written as: literals, references
 * to constants ({@code const val} in Kotlin sources, {@code static final} fields of Java
 * classes on the classpath) and their concatenation, with strings in templates as well.
 * <p>
 * This is the Kotlin counterpart of what JDT does for Java with the constant values of its
 * bindings.
 *
 * @author Martin Lippert
 */
public class KotlinConstantResolver {

	private static final int MAX_DEPTH = 16; // constants that refer to each other in a circle

	private final KotlinTypeResolver resolver;
	private final KotlinDeclarationIndex declarations;
	private final ClasspathConstants classpathConstants = new ClasspathConstants();

	public KotlinConstantResolver(KotlinTypeResolver resolver, KotlinDeclarationIndex declarations) {
		this.resolver = resolver;
		this.declarations = declarations;
	}

	public void clearCaches() {
		classpathConstants.clear();
	}

	/**
	 * @param file the file the value is written in
	 * @param scope the class the value is written in, null for the top level
	 * @return the value (a String, Integer, Long, Double, Boolean or Character), null if it is not a constant that is known
	 */
	public Object evaluate(Value value, KotlinSyntax.File file, KotlinSourceClass scope) {
		return evaluate(value, file, scope, 0);
	}

	private Object evaluate(Value value, KotlinSyntax.File file, KotlinSourceClass scope, int depth) {
		if (depth > MAX_DEPTH || value == null) {
			return null;
		}

		if (value instanceof StringValue string) {
			return string.value();
		}
		else if (value instanceof BooleanValue bool) {
			return bool.value();
		}
		else if (value instanceof NumberValue number) {
			return parseNumber(number.token());
		}
		else if (value instanceof ConcatValue concat) {
			return concatenate(concat, file, scope, depth);
		}
		else if (value instanceof ReferenceValue reference) {
			return lookup(reference.name(), file, scope, depth);
		}
		return null;
	}

	private Object concatenate(ConcatValue concat, KotlinSyntax.File file, KotlinSourceClass scope, int depth) {
		StringBuilder result = new StringBuilder();
		boolean hasString = false;

		for (Value part : concat.parts()) {
			Object evaluated = evaluate(part, file, scope, depth + 1);
			if (evaluated == null) {
				return null;
			}
			hasString |= evaluated instanceof String;
			result.append(evaluated);
		}

		// adding numbers is not done here
		return hasString ? result.toString() : null;
	}

	private static Object parseNumber(String token) {
		String text = token.replace("_", "");
		try {
			if (text.endsWith("L") || text.endsWith("l")) {
				return Long.decode(text.substring(0, text.length() - 1));
			}
			if (text.startsWith("0x") || text.startsWith("0X") || text.startsWith("0b") || text.startsWith("0B")) {
				return text.startsWith("0b") || text.startsWith("0B") ? Integer.parseInt(text.substring(2), 2) : Integer.decode(text);
			}
			if (text.endsWith("f") || text.endsWith("F")) {
				return Double.parseDouble(text.substring(0, text.length() - 1));
			}
			if (text.contains(".") || text.contains("e") || text.contains("E")) {
				return Double.parseDouble(text);
			}
			long number = Long.parseLong(text);
			return number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE ? (Object) (int) number : (Object) number;
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	private Object lookup(String name, KotlinSyntax.File file, KotlinSourceClass scope, int depth) {
		int dot = name.lastIndexOf('.');
		if (dot < 0) {
			return lookupSimpleName(name, file, scope, depth);
		}

		// the part in front of the constant is a class, written the way types are
		String className = resolver.resolveName(name.substring(0, dot), file, scope);
		return className != null ? lookupInClass(className, name.substring(dot + 1), depth) : null;
	}

	private Object lookupSimpleName(String name, KotlinSyntax.File file, KotlinSourceClass scope, int depth) {
		// the constants of the classes the name is used in
		for (KotlinSourceClass current = scope; current != null; current = current.outer()) {
			Object value = lookupInClass(current.binaryName(), name, depth);
			if (value != null) {
				return value;
			}
		}

		// explicit imports
		for (Import imported : file.imports()) {
			if (!imported.allUnder() && name.equals(importedName(imported))) {
				int dot = imported.name().lastIndexOf('.');
				if (dot > 0) {
					Object value = lookupInPackageOrClass(imported.name().substring(0, dot), imported.name().substring(dot + 1), depth);
					if (value != null) {
						return value;
					}
				}
			}
		}

		// the package of the file
		Object value = lookupTopLevel(file.packageName(), name, depth);
		if (value != null) {
			return value;
		}

		// star imports
		for (Import imported : file.imports()) {
			if (imported.allUnder()) {
				value = lookupInPackageOrClass(imported.name(), name, depth);
				if (value != null) {
					return value;
				}
			}
		}

		return null;
	}

	private static String importedName(Import imported) {
		if (imported.alias() != null) {
			return imported.alias();
		}
		int dot = imported.name().lastIndexOf('.');
		return dot >= 0 ? imported.name().substring(dot + 1) : imported.name();
	}

	/** the owner of the constant is a package (a constant on the top level) or a class */
	private Object lookupInPackageOrClass(String owner, String name, int depth) {
		Object value = lookupTopLevel(owner, name, depth);
		if (value != null) {
			return value;
		}

		String className = resolver.findClassName(owner);
		return className != null ? lookupInClass(className, name, depth) : null;
	}

	private Object lookupTopLevel(String packageName, String name, int depth) {
		KotlinDeclarationIndex.TopLevelConstant constant = declarations.findTopLevelConstant(packageName, name);
		return constant != null ? evaluate(constant.constant().value(), constant.file(), null, depth + 1) : null;
	}

	private Object lookupInClass(String binaryName, String name, int depth) {
		KotlinSourceClass source = declarations.find(binaryName);
		if (source != null) {
			Object value = lookupInDeclaration(source, name, depth);
			if (value != null) {
				return value;
			}

			// the constants of the companion object can be used as if they were the ones of the class
			for (KotlinSyntax.ClassDeclaration nested : source.declaration().nestedClasses()) {
				if (nested.kind() == ClassKind.COMPANION) {
					value = lookupInDeclaration(source.nested(nested), name, depth);
					if (value != null) {
						return value;
					}
				}
			}
			return null;
		}

		IType type = resolver.classpathType(binaryName);
		return type != null ? classpathConstants.get(type, name) : null;
	}

	private Object lookupInDeclaration(KotlinSourceClass source, String name, int depth) {
		for (Constant constant : source.declaration().constants()) {
			if (constant.name().equals(name)) {
				return evaluate(constant.value(), source.file(), source, depth + 1);
			}
		}
		return null;
	}

}
