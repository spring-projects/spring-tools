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
package org.springframework.ide.vscode.boot.java.jdt.refactoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NormalizeUtils#normalizeInnerClasses(String)}.
 */
class NormalizeUtilsTest {

	@Test
	void normalizeNoInnerClass() {
		assertEquals("java.util.Map", NormalizeUtils.normalizeInnerClasses("java.util.Map"));
	}

	@Test
	void normalizeAlreadyDollar() {
		assertEquals("java.util.Map$Entry", NormalizeUtils.normalizeInnerClasses("java.util.Map$Entry"));
	}

	@Test
	void normalizeSimpleInnerClass() {
		assertEquals("java.util.Map$Entry", NormalizeUtils.normalizeInnerClasses("java.util.Map.Entry"));
	}

	@Test
	void normalizeDeepInnerClass() {
		assertEquals("com.example.Outer$Middle$Inner",
				NormalizeUtils.normalizeInnerClasses("com.example.Outer.Middle.Inner"));
	}

	@Test
	void normalizeParameterizedInnerClass() {
		assertEquals("java.util.Map$Entry<java.lang.String, java.lang.Integer>",
				NormalizeUtils.normalizeInnerClasses("java.util.Map.Entry<java.lang.String, java.lang.Integer>"));
	}

	@Test
	void normalizeInnerClassInTypeArgument() {
		assertEquals(
				"java.util.Map<java.lang.String, java.util.List<java.util.Map$Entry<java.lang.String, ?>>>",
				NormalizeUtils.normalizeInnerClasses(
						"java.util.Map<java.lang.String, java.util.List<java.util.Map.Entry<java.lang.String, ?>>>"));
	}

	@Test
	void normalizeInnerClassArray() {
		assertEquals("java.util.Map$Entry[]",
				NormalizeUtils.normalizeInnerClasses("java.util.Map.Entry[]"));
	}

	@Test
	void normalizeTopLevelClassUnchanged() {
		assertEquals("com.example.MyService", NormalizeUtils.normalizeInnerClasses("com.example.MyService"));
	}

	@Test
	void normalizePrimitiveUnchanged() {
		assertEquals("int", NormalizeUtils.normalizeInnerClasses("int"));
	}

	@Test
	void normalizeSimpleParameterizedUnchanged() {
		assertEquals("java.util.List<java.lang.String>",
				NormalizeUtils.normalizeInnerClasses("java.util.List<java.lang.String>"));
	}

	@Test
	void normalizeEmptyString() {
		assertEquals("", NormalizeUtils.normalizeInnerClasses(""));
	}

	@Test
	void normalizeUnqualifiedName() {
		assertEquals("MyService", NormalizeUtils.normalizeInnerClasses("MyService"));
	}

	@Test
	void normalizeUnboundedWildcard() {
		assertEquals("?", NormalizeUtils.normalizeInnerClasses("?"));
	}

	@Test
	void normalizeWildcardExtendsInnerClass() {
		assertEquals("? extends com.example.Outer$Inner",
				NormalizeUtils.normalizeInnerClasses("? extends com.example.Outer.Inner"));
	}

	@Test
	void normalizeWildcardSuperInnerClass() {
		assertEquals("? super com.example.Outer$Inner",
				NormalizeUtils.normalizeInnerClasses("? super com.example.Outer.Inner"));
	}

	@Test
	void normalizeMultiDimensionalInnerClassArray() {
		assertEquals("java.util.Map$Entry[][]",
				NormalizeUtils.normalizeInnerClasses("java.util.Map.Entry[][]"));
	}

	@Test
	void normalizeInnerClassBothOuterAndTypeArgument() {
		assertEquals("com.example.Outer$Inner<com.example.Foo$Bar>",
				NormalizeUtils.normalizeInnerClasses("com.example.Outer.Inner<com.example.Foo.Bar>"));
	}

	@Test
	void normalizeParameterizedInnerClassArray() {
		assertEquals("java.util.Map$Entry<java.lang.String, java.lang.Integer>[]",
				NormalizeUtils.normalizeInnerClasses("java.util.Map.Entry<java.lang.String, java.lang.Integer>[]"));
	}

}
