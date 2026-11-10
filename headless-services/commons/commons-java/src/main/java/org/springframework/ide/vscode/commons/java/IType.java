/*******************************************************************************
 * Copyright (c) 2000, 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     IBM Corporation - added J2SE 1.5 support
 *     Stephan Herrmann - Contribution for
 *								Bug 463533 - Signature.getSignatureSimpleName() returns different results for resolved and unresolved extends
 *******************************************************************************/
package org.springframework.ide.vscode.commons.java;

import java.util.stream.Stream;

/**
 * Replaces eclipse JDT IType.
 */
public interface IType extends IMember {

	boolean isClass();
	boolean isEnum();
	boolean isInterface();
	boolean isAnnotation();
	boolean isRecord();

	/**
	 * Returns the fully qualified name of this type,
	 * including qualification for any containing types and packages.
	 * This is the name of the package, followed by <code>'.'</code>,
	 * followed by the type-qualified name.
	 * <p>
	 * <b>Note</b>: The enclosing type separator used in the type-qualified
	 * name is <code>'$'</code>, not <code>'.'</code>.
	 * </p>
	 * This method is fully equivalent to <code>getFullyQualifiedName('$')</code>.
	 * This is a handle-only method.
	 *
	 * @see IType#getTypeQualifiedName()
	 * @see IType#getFullyQualifiedName(char)
	 * @return the fully qualified name of this type
	 */
	String getFullyQualifiedName();

	/**
	 * Returns the field with the specified name
	 * in this type (for example, <code>"bar"</code>).
	 * This is a handle-only method.  The field may or may not exist.
	 *
	 * @param name the given name
	 * @return the field with the specified name in this type
	 */
	IField getField(String name);

	/**
	 * Returns the fields declared by this type in the order in which they appear
	 * in the source or class file. For binary types, this includes synthetic fields.
	 *
	 * @return the fields declared by this type
	 */
	Stream<IField> getFields();

	/**
	 * Returns the method with the specified name and parameter types
	 * in this type (for example, <code>"foo", {"I", "QString;"}</code>).
	 * To get the handle for a constructor, the name specified must be the
	 * simple name of the enclosing type.
	 * This is a handle-only method.  The method may or may not be present.
	 * <p>
	 * The type signatures may be either unresolved (for source types)
	 * or resolved (for binary types), and either basic (for basic types)
	 * or rich (for parameterized types). See {@link Signature} for details.
	 * Note that the parameter type signatures for binary methods are expected
	 * to be dot-based.
	 * </p>
	 *
	 * @param name the given name
	 * @param parameterTypeSignatures the given parameter types
	 * @return the method with the specified name and parameter types in this type
	 */
	IMethod getMethod(String name, Stream<IJavaType> parameters);

	/**
	 * Returns the methods and constructors declared by this type.
	 * For binary types, this may include the special <code>&lt;clinit&gt;</code> method
	 * and synthetic methods.
	 * <p>
	 * The results are listed in the order in which they appear in the source or class file.
	 * </p>
	 *
	 * @return the methods and constructors declared by this type
	 */
	Stream<IMethod> getMethods();

	String getSuperclassName();

	String[] getSuperInterfaceNames();

	/**
	 * Returns the annotations of the type declaration itself. {@link #getAnnotations()} may return
	 * more than that for some implementations (the Jandex based one includes the annotations of the
	 * type's members, too), which is not what is wanted when walking up the annotation hierarchy
	 * of a type. The default is {@link #getAnnotations()}.
	 *
	 * @return the annotations declared directly on this type
	 */
	default Stream<IAnnotation> getDeclaredAnnotations() {
		return getAnnotations();
	}

//	/**
//	 * Resolves the given type name within the context of this type (depending on the type hierarchy
//	 * and its imports).
//	 * <p>
//	 * Multiple answers might be found in case there are ambiguous matches.
//	 * </p>
//	 * <p>
//	 * Each matching type name is decomposed as an array of two strings, the first denoting the package
//	 * name (dot-separated) and the second being the type name. The package name is empty if it is the
//	 * default package. The type name is the type qualified name using a '.' enclosing type separator.
//	 * </p>
//	 * <p>
//	 * Returns <code>null</code> if unable to find any matching type.
//	 * </p>
//	 *<p>
//	 * For example, resolution of <code>"Object"</code> would typically return
//	 * <code>{{"java.lang", "Object"}}</code>. Another resolution that returns
//	 * <code>{{"", "X.Inner"}}</code> represents the inner type Inner defined in type X in the
//	 * default package.
//	 * </p>
//	 *
//	 * @param typeName the given type name
//	 * @return the resolved type names or <code>null</code> if unable to find any matching type
//	 * @see #getTypeQualifiedName(char)
//	 */
//	String[][] resolveType(String typeName);

}
