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
package org.springframework.ide.vscode.boot.java.stereotypes;

/**
 * What the structure scan of a JAR needs from a class file at class level, read straight from its
 * bytes by {@link ClassFactsReader}: much less than a full Jandex {@code ClassInfo}, which is what
 * makes reading every class of a JAR with it cheap. A {@code ClassInfo} is only read for the few
 * classes that need more - see {@link JarFactsScanner}.
 *
 * <p>All class names are binary names with dots ({@code com.example.Outer$Inner}).
 *
 * @param name the class
 * @param flags the class's access flags
 * @param superName the superclass - none for {@code java.lang.Object}, and for a module
 * @param superParameterized whether the superclass is referenced with type arguments
 * @param interfaces the directly implemented interfaces
 * @param interfaceParameterized whether each of them is referenced with type arguments
 * @param annotations the class's own annotations - visible or not, without their values
 * @param anonymous whether it is an anonymous class: no name of its own, and no enclosing class
 *        that declares it as a member
 * @param hasAnnotatedMethod whether any method that is in source (not the static initializer, not
 *        synthetic, not a bridge) has an annotation - the only classes whose methods can make
 *        elements
 *
 * @author Martin Lippert
 */
public record ClassFacts(String name, int flags, String superName, boolean superParameterized, String[] interfaces,
		boolean[] interfaceParameterized, String[] annotations, boolean anonymous, boolean hasAnnotatedMethod) {

	public static final int ACC_INTERFACE = 0x0200;
	public static final int ACC_SYNTHETIC = 0x1000;
	public static final int ACC_ANNOTATION = 0x2000;
	public static final int ACC_MODULE = 0x8000;

	public boolean isInterface() {
		return (flags & ACC_INTERFACE) != 0;
	}

}
