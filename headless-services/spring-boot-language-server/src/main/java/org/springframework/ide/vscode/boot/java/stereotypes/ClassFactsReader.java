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

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

/**
 * Reads {@link ClassFacts} out of the bytes of a class file with ASM: without the method bodies,
 * debug information and frames, and without visiting what has no use here - fields, annotation
 * values, parameter and type annotations.
 *
 * <p>Meant to be given the same class files Jandex gets, and to come to the same answers for what
 * {@link ClassFacts} holds - {@code JarFactsScannerParityTest} compares the two over real JARs. Two things
 * are Jandex's and not ASM's way of seeing a class, and are done to match it: the class's flags are the
 * ones the class file has (ASM adds a synthetic flag for the {@code Synthetic} attribute of class
 * files from before Java 5, which Jandex ignores), and a class that is nameless but has an enclosing
 * class is a member, not an anonymous class.
 *
 * <p>Like Jandex, ASM refuses a class file newer than the Java version it knows.
 *
 * @author Martin Lippert
 */
public class ClassFactsReader {

	private static final String[] NONE = new String[0];

	/**
	 * @param interner shares the strings of one scan - a JAR has thousands of classes with the same
	 *        few supertypes and annotations; called with every name read
	 * @throws IllegalArgumentException if the bytes are not a class file ASM can read
	 */
	public static ClassFacts read(byte[] bytes, UnaryOperator<String> interner) {
		ClassReader reader = new ClassReader(bytes);
		Collector collector = new Collector(interner);
		reader.accept(collector, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		return collector.facts(reader.getAccess());
	}

	private static final class Collector extends ClassVisitor {

		private final UnaryOperator<String> interner;

		private String internalName;
		private String name;
		private String superName;
		private boolean superParameterized;
		private String[] interfaces = NONE;
		private boolean[] interfaceParameterized = new boolean[0];
		private final List<String> annotations = new ArrayList<>(4);
		private boolean anonymous;
		private boolean hasAnnotatedMethod;

		Collector(UnaryOperator<String> interner) {
			super(Opcodes.ASM9);
			this.interner = interner;
		}

		ClassFacts facts(int flags) {
			return new ClassFacts(name, flags, superName, superParameterized, interfaces, interfaceParameterized,
					annotations.isEmpty() ? NONE : annotations.toArray(NONE), anonymous, hasAnnotatedMethod);
		}

		@Override
		public void visit(int version, int access, String internalName, String signature, String superName, String[] interfaces) {
			this.internalName = internalName;
			this.name = binaryName(internalName);
			this.superName = superName == null ? null : binaryName(superName);

			this.interfaces = NONE;
			if (interfaces != null && interfaces.length > 0) {
				this.interfaces = new String[interfaces.length];
				for (int i = 0; i < interfaces.length; i++) {
					this.interfaces[i] = binaryName(interfaces[i]);
				}
			}
			this.interfaceParameterized = new boolean[this.interfaces.length];

			if (signature != null) {
				boolean[] parameterized = parameterizedSupertypes(signature, this.interfaces.length);
				this.superParameterized = parameterized[0];
				System.arraycopy(parameterized, 1, this.interfaceParameterized, 0, this.interfaces.length);
			}
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			annotations.add(interner.apply(descriptor.substring(1, descriptor.length() - 1).replace('/', '.'))); // Lp/A;
			return null; // no use for the values
		}

		@Override
		public void visitInnerClass(String name, String outerName, String innerName, int access) {
			// this class's own entry, with neither a name nor an enclosing class: Jandex's anonymous class
			if (name.equals(internalName) && outerName == null && innerName == null) {
				anonymous = true;
			}
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			// only a method in source counts, and one with an annotation is enough
			if (hasAnnotatedMethod || (access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0 || name.equals("<clinit>")) {
				return null;
			}

			return new MethodVisitor(Opcodes.ASM9) {
				@Override
				public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
					hasAnnotatedMethod = true;
					return null;
				}
			};
		}

		private String binaryName(String internalName) {
			return interner.apply(internalName.replace('/', '.'));
		}
	}

	/**
	 * Which of the superclass and the interfaces a class signature gives are written with type
	 * arguments (also a parameterized owner type, {@code Outer<T>.Inner}) - in that order, the
	 * superclass first.
	 */
	private static boolean[] parameterizedSupertypes(String signature, int interfaceCount) {
		boolean[] result = new boolean[interfaceCount + 1];

		new SignatureReader(signature).accept(new SignatureVisitor(Opcodes.ASM9) {

			// the supertype being visited - none yet while it is the class's own type parameters
			private int supertype = -1;

			@Override
			public SignatureVisitor visitSuperclass() {
				supertype = 0;
				return this;
			}

			@Override
			public SignatureVisitor visitInterface() {
				supertype++;
				return this;
			}

			@Override
			public void visitTypeArgument() {
				argument();
			}

			@Override
			public SignatureVisitor visitTypeArgument(char wildcard) {
				argument();
				return this;
			}

			private void argument() {
				if (supertype >= 0 && supertype < result.length) {
					result[supertype] = true;
				}
			}
		});

		return result;
	}

}
