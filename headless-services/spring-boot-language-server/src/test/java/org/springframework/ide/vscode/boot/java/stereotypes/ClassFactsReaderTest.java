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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.function.UnaryOperator;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Index;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * {@link ClassFactsReader} on class files written to have what ASM and Jandex see differently - each
 * compared with Jandex, which is what the facts have to agree with. The rest is covered by
 * {@code JarFactsScannerParityTest} over real JARs.
 *
 * @author Martin Lippert
 */
class ClassFactsReaderTest {

	private static final UnaryOperator<String> NO_SHARING = name -> name;

	/**
	 * A class file from before Java 5 with a {@code Synthetic} attribute: ASM says it is synthetic,
	 * Jandex - and so the facts - does not. It is a class of the source then, as it was for the scan
	 * over Jandex (javac used to make such classes for class literals).
	 */
	@Test
	void theSyntheticAttributeOfAnOldClassFileDoesNotMakeAClassSynthetic() throws Exception {
		ClassWriter writer = new ClassWriter(0);
		writer.visit(Opcodes.V1_4, Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC, "p/Holder", null, "java/lang/Object", null);
		writer.visitEnd();
		byte[] bytes = writer.toByteArray();

		assertFalse(jandex(bytes).isSynthetic());
		assertEquals(0, ClassFactsReader.read(bytes, NO_SHARING).flags() & ClassFacts.ACC_SYNTHETIC);
		assertTrue(JarFactsScanner.isSourceLevelType(ClassFactsReader.read(bytes, NO_SHARING)));
	}

	@Test
	void aSyntheticFlagOfTheClassFileIsKept() throws Exception {
		ClassWriter writer = new ClassWriter(0);
		writer.visit(Opcodes.V17, Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC, "p/Generated", null, "java/lang/Object", null);
		writer.visitEnd();
		byte[] bytes = writer.toByteArray();

		assertTrue(jandex(bytes).isSynthetic());
		assertFalse(JarFactsScanner.isSourceLevelType(ClassFactsReader.read(bytes, NO_SHARING)));
	}

	/**
	 * Nameless and without an enclosing class is anonymous; nameless but with one is a member as
	 * Jandex sees it.
	 */
	@Test
	void aNamelessClassIsAnonymousOnlyWithoutAnEnclosingClass() throws Exception {
		byte[] anonymous = classWithInnerClassEntry("p/Outer$1", null, null);
		assertEquals(ClassInfo.NestingType.ANONYMOUS, jandex(anonymous).nestingType());
		assertTrue(ClassFactsReader.read(anonymous, NO_SHARING).anonymous());

		byte[] member = classWithInnerClassEntry("p/Outer$1", "p/Outer", null);
		assertFalse(jandex(member).nestingType() == ClassInfo.NestingType.ANONYMOUS);
		assertFalse(ClassFactsReader.read(member, NO_SHARING).anonymous());

		byte[] named = classWithInnerClassEntry("p/Outer$Inner", "p/Outer", "Inner");
		assertFalse(ClassFactsReader.read(named, NO_SHARING).anonymous());
	}

	@Test
	void whichSupertypesHaveTypeArgumentsComesFromTheSignature() throws Exception {
		ClassWriter writer = new ClassWriter(0);
		writer.visit(Opcodes.V17, Opcodes.ACC_SUPER, "p/Sub", "<T::Ljava/lang/Comparable<TT;>;>Lp/Base<TT;>;Ljava/io/Serializable;Ljava/util/List<Ljava/lang/String;>;Lp/Outer<TT;>.Inner;",
				"p/Base", new String[] { "java/io/Serializable", "java/util/List", "p/Outer$Inner" });
		writer.visitEnd();

		ClassFacts facts = ClassFactsReader.read(writer.toByteArray(), NO_SHARING);

		assertEquals("p.Base", facts.superName());
		assertTrue(facts.superParameterized());
		assertArrayEquals(new String[] { "java.io.Serializable", "java.util.List", "p.Outer$Inner" }, facts.interfaces());
		assertArrayEquals(new boolean[] { false, true, true }, facts.interfaceParameterized());
	}

	@Test
	void aClassWithoutASignatureHasNoParameterizedSupertypes() throws Exception {
		ClassWriter writer = new ClassWriter(0);
		writer.visit(Opcodes.V17, Opcodes.ACC_SUPER, "p/Plain", null, "p/Base", new String[] { "java.io.Serializable".replace('.', '/') });
		writer.visitEnd();

		ClassFacts facts = ClassFactsReader.read(writer.toByteArray(), NO_SHARING);

		assertFalse(facts.superParameterized());
		assertArrayEquals(new boolean[] { false }, facts.interfaceParameterized());
	}

	@Test
	void onlyAnnotatedMethodsInSourceCount() throws Exception {
		assertFalse(ClassFactsReader.read(classWithMethod(Opcodes.ACC_PUBLIC, "plain", false), NO_SHARING).hasAnnotatedMethod());
		assertTrue(ClassFactsReader.read(classWithMethod(Opcodes.ACC_PUBLIC, "annotated", true), NO_SHARING).hasAnnotatedMethod());
		assertFalse(ClassFactsReader.read(classWithMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, "lambda$0", true), NO_SHARING).hasAnnotatedMethod());
		assertFalse(ClassFactsReader.read(classWithMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_BRIDGE, "bridge", true), NO_SHARING).hasAnnotatedMethod());
		assertFalse(ClassFactsReader.read(classWithMethod(Opcodes.ACC_STATIC, "<clinit>", true), NO_SHARING).hasAnnotatedMethod());
	}

	@Test
	void whatIsNotAClassFileIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> ClassFactsReader.read(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }, NO_SHARING));
	}

	private static byte[] classWithInnerClassEntry(String name, String outerName, String innerName) {
		ClassWriter writer = new ClassWriter(0);
		writer.visit(Opcodes.V17, Opcodes.ACC_SUPER, name, null, "java/lang/Object", null);
		writer.visitInnerClass(name, outerName, innerName, 0);
		writer.visitEnd();
		return writer.toByteArray();
	}

	private static byte[] classWithMethod(int access, String name, boolean annotated) {
		ClassWriter writer = new ClassWriter(0);
		writer.visit(Opcodes.V17, Opcodes.ACC_SUPER, "p/WithMethod", null, "java/lang/Object", null);
		var method = writer.visitMethod(access, name, "()V", null, null);
		if (annotated) {
			method.visitAnnotation("Lp/Marker;", true).visitEnd();
		}
		method.visitEnd();
		writer.visitEnd();
		return writer.toByteArray();
	}

	private static ClassInfo jandex(byte[] bytes) throws IOException {
		return Index.singleClass(new java.io.ByteArrayInputStream(bytes));
	}

}
