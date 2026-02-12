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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link FullyQualifiedName} type model:
 * {@link ClassName}, {@link ParameterizedClassName}, and {@link WildcardName}.
 * <p>
 * These tests verify parsing from source-style type strings, structural decomposition,
 * field type name generation, and import collection.
 */
class JavaTypeTest {

	// ========== Simple class types ==========

	@Test
	void parseSimpleTopLevelClass() {
		JavaType jt = JavaType.parse("com.example.MyService");

		assertInstanceOf(ClassName.class, jt);
		ClassName cn = (ClassName) jt;

		assertEquals("com.example", cn.getPackageName());
		assertEquals("MyService", cn.getSimpleName());
		assertNull(cn.getDeclaringClass());
		assertEquals("MyService", cn.getFieldTypeName());
		assertEquals("com.example.MyService", cn.getFullyQualifiedName());
		assertEquals("MyService", jt.getDisplayName());
	}

	@Test
	void parseJavaLangType() {
		JavaType jt = JavaType.parse("java.lang.String");

		assertInstanceOf(ClassName.class, jt);
		ClassName cn = (ClassName) jt;

		assertEquals("java.lang", cn.getPackageName());
		assertEquals("String", cn.getSimpleName());
		assertNull(cn.getDeclaringClass());
		assertEquals("String", cn.getFieldTypeName());
		assertEquals("java.lang.String", cn.getFullyQualifiedName());
		assertEquals("String", jt.getDisplayName());
	}

	@Test
	void parseUnqualifiedName() {
		JavaType jt = JavaType.parse("MyService");

		assertInstanceOf(ClassName.class, jt);
		ClassName cn = (ClassName) jt;

		assertEquals("MyService", cn.getSimpleName());
		assertEquals("MyService", cn.getFieldTypeName());
		assertEquals("MyService", cn.getFullyQualifiedName());
		assertEquals("MyService", jt.getDisplayName());
	}

	// ========== Inner class types ==========

	@Test
	void parseInnerClassType() {
		JavaType jt = JavaType.parse("java.util.Map$Entry");

		assertInstanceOf(ClassName.class, jt);
		ClassName cn = (ClassName) jt;

		assertEquals("java.util", cn.getPackageName());
		assertEquals("Entry", cn.getSimpleName());
		assertNotNull(cn.getDeclaringClass());
		assertEquals("Map", cn.getDeclaringClass().getSimpleName());
		assertEquals("Map.Entry", cn.getFieldTypeName());
		assertEquals("java.util.Map.Entry", cn.getFullyQualifiedName());
		assertEquals("Map.Entry", jt.getDisplayName());
	}

	@Test
	void parseDeepInnerClassType() {
		JavaType jt = JavaType.parse("com.example.Outer$Middle$Inner");

		assertInstanceOf(ClassName.class, jt);
		ClassName cn = (ClassName) jt;

		assertEquals("com.example", cn.getPackageName());
		assertEquals("Inner", cn.getSimpleName());
		assertEquals("Outer.Middle.Inner", cn.getFieldTypeName());
		assertEquals("Outer.Middle.Inner", jt.getDisplayName());

		// Check declaring class chain
		ClassName middle = cn.getDeclaringClass();
		assertNotNull(middle);
		assertEquals("Middle", middle.getSimpleName());

		ClassName outer = middle.getDeclaringClass();
		assertNotNull(outer);
		assertEquals("Outer", outer.getSimpleName());
		assertNull(outer.getDeclaringClass());
	}

	// ========== getAllClassNames for simple types ==========

	@Test
	void allClassNamesForSimpleType() {
		JavaType jt = JavaType.parse("com.example.MyService");
		List<ClassName> names = jt.getAllClassNames();

		assertEquals(1, names.size());
		assertEquals("com.example.MyService", names.get(0).getFullyQualifiedName());
	}

	@Test
	void allClassNamesForInnerType() {
		JavaType jt = JavaType.parse("java.util.Map$Entry");
		List<ClassName> names = jt.getAllClassNames();

		assertEquals(1, names.size());
		assertEquals("java.util.Map.Entry", names.get(0).getFullyQualifiedName());
	}

	// ========== Simple parameterized types ==========

	@Test
	void parseSimpleParameterizedType() {
		JavaType jt = JavaType.parse("java.util.List<java.lang.String>");

		assertInstanceOf(ParameterizedClassName.class, jt);
		ParameterizedClassName pcn = (ParameterizedClassName) jt;

		assertEquals("java.util", pcn.getErasure().getPackageName());
		assertEquals("List", pcn.getSimpleName());
		assertEquals("List", pcn.getErasure().getFieldTypeName());

		assertEquals(1, pcn.getTypeArguments().size());
		JavaType arg = pcn.getTypeArguments().get(0);
		assertInstanceOf(ClassName.class, arg);
		assertEquals("java.lang.String", ((FullyQualifiedName) arg).getFullyQualifiedName());
		assertEquals("List<String>", jt.getDisplayName());
	}

	@Test
	void parseMapType() {
		JavaType jt = JavaType.parse("java.util.Map<java.lang.String, java.lang.Integer>");

		assertInstanceOf(ParameterizedClassName.class, jt);
		ParameterizedClassName pcn = (ParameterizedClassName) jt;

		assertEquals("java.util", pcn.getErasure().getPackageName());
		assertEquals("Map", pcn.getSimpleName());
		assertEquals(2, pcn.getTypeArguments().size());
		assertEquals("java.lang.String", ((FullyQualifiedName) pcn.getTypeArguments().get(0)).getFullyQualifiedName());
		assertEquals("java.lang.Integer", ((FullyQualifiedName) pcn.getTypeArguments().get(1)).getFullyQualifiedName());
		assertEquals("Map<String, Integer>", jt.getDisplayName());
	}

	@Test
	void parameterizedTypeFullyQualifiedNameIncludesArgs() {
		JavaType jt = JavaType.parse("java.util.Map<java.lang.String, java.lang.Integer>");
		assertEquals("java.util.Map<java.lang.String, java.lang.Integer>", ((FullyQualifiedName) jt).getFullyQualifiedName());
	}

	// ========== Nested parameterized types ==========

	@Test
	void parseNestedParameterizedType() {
		JavaType jt = JavaType.parse(
				"java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>");

		assertInstanceOf(ParameterizedClassName.class, jt);
		ParameterizedClassName pcn = (ParameterizedClassName) jt;

		assertEquals("Map", pcn.getSimpleName());
		assertEquals(2, pcn.getTypeArguments().size());

		// First arg: String
		assertInstanceOf(ClassName.class, pcn.getTypeArguments().get(0));
		assertEquals("String", ((FullyQualifiedName) pcn.getTypeArguments().get(0)).getSimpleName());

		// Second arg: List<Integer>
		assertInstanceOf(ParameterizedClassName.class, pcn.getTypeArguments().get(1));
		ParameterizedClassName listArg = (ParameterizedClassName) pcn.getTypeArguments().get(1);
		assertEquals("List", listArg.getSimpleName());
		assertEquals(1, listArg.getTypeArguments().size());
		assertEquals("java.lang.Integer", ((FullyQualifiedName) listArg.getTypeArguments().get(0)).getFullyQualifiedName());
		assertEquals("Map<String, List<Integer>>", jt.getDisplayName());
	}

	@Test
	void parseComplexNestedType() {
		String input = "java.util.Map<java.lang.String, java.util.List<java.util.Map$Entry<java.lang.String, ?>>>";
		JavaType jt = JavaType.parse(input);

		assertInstanceOf(ParameterizedClassName.class, jt);
		ParameterizedClassName map = (ParameterizedClassName) jt;
		assertEquals("Map", map.getSimpleName());
		assertEquals(2, map.getTypeArguments().size());

		// Second arg: List<Map.Entry<String, ?>>
		ParameterizedClassName list = assertInstanceOf(ParameterizedClassName.class, map.getTypeArguments().get(1));
		assertEquals("List", list.getSimpleName());

		// List's arg: Map.Entry<String, ?>
		ParameterizedClassName entry = assertInstanceOf(ParameterizedClassName.class, list.getTypeArguments().get(0));
		assertEquals("Entry", entry.getSimpleName());
		assertNotNull(entry.getErasure().getDeclaringClass());
		assertEquals("Map", entry.getErasure().getDeclaringClass().getSimpleName());
		assertEquals("Map.Entry", entry.getErasure().getFieldTypeName());

		// Entry's args: String, ?
		assertEquals(2, entry.getTypeArguments().size());
		assertEquals("java.lang.String", ((FullyQualifiedName) entry.getTypeArguments().get(0)).getFullyQualifiedName());
		assertInstanceOf(WildcardName.class, entry.getTypeArguments().get(1));
		assertEquals("Map<String, List<Map.Entry<String, ?>>>", jt.getDisplayName());
	}

	// ========== getAllClassNames for parameterized types ==========

	@Test
	void allClassNamesForParameterizedType() {
		JavaType jt = JavaType.parse("java.util.List<com.example.dto.MyDto>");

		List<String> fqNames = jt.getAllClassNames().stream()
				.map(ClassName::getFullyQualifiedName)
				.collect(Collectors.toList());

		assertEquals(2, fqNames.size());
		assertTrue(fqNames.contains("java.util.List"));
		assertTrue(fqNames.contains("com.example.dto.MyDto"));
	}

	@Test
	void allClassNamesForComplexType() {
		String input = "java.util.Map<java.lang.String, java.util.List<java.util.Map$Entry<java.lang.String, ?>>>";
		JavaType jt = JavaType.parse(input);

		List<String> fqNames = jt.getAllClassNames().stream()
				.map(ClassName::getFullyQualifiedName)
				.collect(Collectors.toList());

		// Map, String, List, Map.Entry, String (again) — wildcards contribute nothing
		assertTrue(fqNames.contains("java.util.Map"));
		assertTrue(fqNames.contains("java.lang.String"));
		assertTrue(fqNames.contains("java.util.List"));
		assertTrue(fqNames.contains("java.util.Map.Entry"));
	}

	@Test
	void allClassNamesDoesNotIncludeWildcards() {
		JavaType jt = JavaType.parse("java.util.List<?>");

		List<String> fqNames = jt.getAllClassNames().stream()
				.map(ClassName::getFullyQualifiedName)
				.collect(Collectors.toList());

		assertEquals(1, fqNames.size());
		assertEquals("java.util.List", fqNames.get(0));
	}

	// ========== Wildcard types ==========

	@Test
	void parseUnboundedWildcard() {
		JavaType jt = JavaType.parse("?");

		assertInstanceOf(WildcardName.class, jt);
		WildcardName wn = (WildcardName) jt;

		assertNull(wn.getBound());
		assertEquals("?", wn.toString());
		assertEquals("?", jt.getDisplayName());
		assertTrue(wn.getAllClassNames().isEmpty());
	}

	@Test
	void parseUpperBoundedWildcard() {
		JavaType jt = JavaType.parse("? extends java.lang.Number");

		assertInstanceOf(WildcardName.class, jt);
		WildcardName wn = (WildcardName) jt;

		assertNotNull(wn.getBound());
		assertTrue(wn.isUpperBound());
		assertEquals("java.lang.Number", ((FullyQualifiedName) wn.getBound()).getFullyQualifiedName());
		assertEquals("? extends java.lang.Number", wn.toString());
		assertEquals("? extends Number", jt.getDisplayName());
	}

	@Test
	void parseLowerBoundedWildcard() {
		JavaType jt = JavaType.parse("? super java.lang.Integer");

		assertInstanceOf(WildcardName.class, jt);
		WildcardName wn = (WildcardName) jt;

		assertNotNull(wn.getBound());
		assertTrue(!wn.isUpperBound());
		assertEquals("java.lang.Integer", ((FullyQualifiedName) wn.getBound()).getFullyQualifiedName());
		assertEquals("? super java.lang.Integer", wn.toString());
		assertEquals("? super Integer", jt.getDisplayName());
	}

	@Test
	void allClassNamesForBoundedWildcard() {
		JavaType jt = JavaType.parse("? extends com.example.BaseDto");

		List<String> fqNames = jt.getAllClassNames().stream()
				.map(ClassName::getFullyQualifiedName)
				.collect(Collectors.toList());

		assertEquals(1, fqNames.size());
		assertEquals("com.example.BaseDto", fqNames.get(0));
	}

	// ========== Wildcard inside parameterized type ==========

	@Test
	void parseParameterizedWithWildcardExtends() {
		JavaType jt = JavaType.parse("java.util.List<? extends com.example.dto.BaseDto>");

		assertInstanceOf(ParameterizedClassName.class, jt);
		ParameterizedClassName pcn = (ParameterizedClassName) jt;

		assertEquals(1, pcn.getTypeArguments().size());
		WildcardName wn = assertInstanceOf(WildcardName.class, pcn.getTypeArguments().get(0));
		assertTrue(wn.isUpperBound());
		assertEquals("com.example.dto.BaseDto", ((FullyQualifiedName) wn.getBound()).getFullyQualifiedName());
		assertEquals("List<? extends BaseDto>", jt.getDisplayName());
	}

	@Test
	void parseParameterizedWithWildcardSuper() {
		JavaType jt = JavaType.parse("java.util.Comparator<? super java.lang.String>");

		assertInstanceOf(ParameterizedClassName.class, jt);
		ParameterizedClassName pcn = (ParameterizedClassName) jt;

		assertEquals(1, pcn.getTypeArguments().size());
		WildcardName wn = assertInstanceOf(WildcardName.class, pcn.getTypeArguments().get(0));
		assertTrue(!wn.isUpperBound());
		assertEquals("java.lang.String", ((FullyQualifiedName) wn.getBound()).getFullyQualifiedName());
	}

	@Test
	void parseParameterizedWithUnboundedWildcard() {
		JavaType jt = JavaType.parse("java.util.List<?>");

		assertInstanceOf(ParameterizedClassName.class, jt);
		ParameterizedClassName pcn = (ParameterizedClassName) jt;

		assertEquals(1, pcn.getTypeArguments().size());
		WildcardName wn = assertInstanceOf(WildcardName.class, pcn.getTypeArguments().get(0));
		assertNull(wn.getBound());
	}

	// ========== ClassName field type name ==========

	@Test
	void fieldTypeNameForTopLevelClass() {
		ClassName cn = new ClassName("com.example", "MyService");
		assertEquals("MyService", cn.getFieldTypeName());
	}

	@Test
	void fieldTypeNameForInnerClass() {
		ClassName outer = new ClassName("java.util", "Map");
		ClassName inner = new ClassName(outer, "Entry");
		assertEquals("Map.Entry", inner.getFieldTypeName());
	}

	@Test
	void fieldTypeNameForDeepInnerClass() {
		ClassName outer = new ClassName("com.example", "Outer");
		ClassName middle = new ClassName(outer, "Middle");
		ClassName inner = new ClassName(middle, "Inner");
		assertEquals("Outer.Middle.Inner", inner.getFieldTypeName());
	}

	// ========== Round-trip: parse then getFullyQualifiedName ==========

	@Test
	void roundTripSimpleType() {
		String input = "com.example.MyService";
		assertEquals(input, JavaType.parse(input).toString());
	}

	@Test
	void roundTripInnerClassType() {
		assertEquals("java.util.Map.Entry", JavaType.parse("java.util.Map$Entry").toString());
	}

	@Test
	void roundTripParameterizedType() {
		String input = "java.util.Map<java.lang.String, java.lang.Integer>";
		assertEquals(input, JavaType.parse(input).toString());
	}

	@Test
	void roundTripNestedParameterizedType() {
		String input = "java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>";
		assertEquals(input, JavaType.parse(input).toString());
	}

	@Test
	void roundTripWildcardExtends() {
		String input = "? extends java.lang.Number";
		assertEquals(input, JavaType.parse(input).toString());
	}

	@Test
	void roundTripWildcardSuper() {
		String input = "? super java.lang.Integer";
		assertEquals(input, JavaType.parse(input).toString());
	}

	@Test
	void roundTripUnboundedWildcard() {
		assertEquals("?", JavaType.parse("?").toString());
	}

	@Test
	void roundTripParameterizedWithWildcard() {
		String input = "java.util.List<? extends com.example.dto.BaseDto>";
		assertEquals(input, JavaType.parse(input).toString());
	}

	@Test
	void roundTripComplexType() {
		String input = "java.util.Map<java.lang.String, java.util.List<java.util.Map$Entry<java.lang.String, ?>>>";
		String expected = "java.util.Map<java.lang.String, java.util.List<java.util.Map.Entry<java.lang.String, ?>>>";
		assertEquals(expected, JavaType.parse(input).toString());
	}

	// ========== Primitive types ==========

	@Test
	void parsePrimitiveInt() {
		JavaType jt = JavaType.parse("int");

		assertInstanceOf(PrimitiveTypeName.class, jt);
		PrimitiveTypeName ptn = (PrimitiveTypeName) jt;

		assertEquals("int", ptn.getKeyword());
		assertEquals("int", ptn.toString());
		assertEquals("int", jt.getDisplayName());
		assertTrue(ptn.getAllClassNames().isEmpty());
	}

	@Test
	void parsePrimitiveBoolean() {
		JavaType jt = JavaType.parse("boolean");

		assertInstanceOf(PrimitiveTypeName.class, jt);
		PrimitiveTypeName ptn = (PrimitiveTypeName) jt;
		assertEquals("boolean", ptn.getKeyword());
		assertEquals("boolean", ptn.toString());
	}

	@Test
	void parsePrimitiveDouble() {
		JavaType jt = JavaType.parse("double");

		assertInstanceOf(PrimitiveTypeName.class, jt);
		PrimitiveTypeName ptn = (PrimitiveTypeName) jt;
		assertEquals("double", ptn.getKeyword());
		assertEquals("double", ptn.toString());
	}

	@Test
	void parsePrimitiveVoid() {
		JavaType jt = JavaType.parse("void");

		assertInstanceOf(PrimitiveTypeName.class, jt);
		PrimitiveTypeName ptn = (PrimitiveTypeName) jt;
		assertEquals("void", ptn.getKeyword());
	}

	// ========== Array types ==========

	@Test
	void parsePrimitiveArray() {
		JavaType jt = JavaType.parse("int[]");

		assertInstanceOf(ArrayTypeName.class, jt);
		ArrayTypeName atn = (ArrayTypeName) jt;

		assertEquals(1, atn.getDimensions());
		assertInstanceOf(PrimitiveTypeName.class, atn.getComponentType());
		assertEquals("int", atn.getComponentType().toString());
		assertEquals("int[]", atn.toString());
		assertEquals("int[]", jt.getDisplayName());
		assertTrue(atn.getAllClassNames().isEmpty());
	}

	@Test
	void parseMultiDimensionalPrimitiveArray() {
		JavaType jt = JavaType.parse("byte[][]");

		assertInstanceOf(ArrayTypeName.class, jt);
		ArrayTypeName atn = (ArrayTypeName) jt;

		assertEquals(2, atn.getDimensions());
		assertInstanceOf(PrimitiveTypeName.class, atn.getComponentType());
		assertEquals("byte", atn.getComponentType().toString());
		assertEquals("byte[][]", atn.toString());
	}

	@Test
	void parseReferenceArray() {
		JavaType jt = JavaType.parse("java.lang.String[]");

		assertInstanceOf(ArrayTypeName.class, jt);
		ArrayTypeName atn = (ArrayTypeName) jt;

		assertEquals(1, atn.getDimensions());
		assertInstanceOf(ClassName.class, atn.getComponentType());
		assertEquals("String", ((ClassName) atn.getComponentType()).getSimpleName());
		assertEquals("java.lang.String[]", atn.toString());
		assertEquals("String[]", jt.getDisplayName());

		List<ClassName> classNames = atn.getAllClassNames();
		assertEquals(1, classNames.size());
		assertEquals("java.lang.String", classNames.get(0).getFullyQualifiedName());
	}

	@Test
	void parseMultiDimensionalReferenceArray() {
		JavaType jt = JavaType.parse("com.example.Foo[][]");

		assertInstanceOf(ArrayTypeName.class, jt);
		ArrayTypeName atn = (ArrayTypeName) jt;

		assertEquals(2, atn.getDimensions());
		assertInstanceOf(ClassName.class, atn.getComponentType());
		assertEquals("com.example.Foo[][]", atn.toString());
		assertEquals("Foo[][]", jt.getDisplayName());
	}

	@Test
	void parseParameterizedArray() {
		JavaType jt = JavaType.parse("java.util.List<java.lang.String>[]");

		assertInstanceOf(ArrayTypeName.class, jt);
		ArrayTypeName atn = (ArrayTypeName) jt;

		assertEquals(1, atn.getDimensions());
		assertInstanceOf(ParameterizedClassName.class, atn.getComponentType());
		assertEquals("List", ((ParameterizedClassName) atn.getComponentType()).getSimpleName());
		assertEquals("java.util.List<java.lang.String>[]", atn.toString());
		assertEquals("List<String>[]", jt.getDisplayName());

		List<ClassName> classNames = atn.getAllClassNames();
		assertEquals(2, classNames.size());
		assertTrue(classNames.stream().anyMatch(cn -> cn.getFullyQualifiedName().equals("java.util.List")));
		assertTrue(classNames.stream().anyMatch(cn -> cn.getFullyQualifiedName().equals("java.lang.String")));
	}

	@Test
	void parseInnerClassArray() {
		JavaType jt = JavaType.parse("java.util.Map$Entry[]");

		assertInstanceOf(ArrayTypeName.class, jt);
		ArrayTypeName atn = (ArrayTypeName) jt;

		assertEquals(1, atn.getDimensions());
		assertInstanceOf(ClassName.class, atn.getComponentType());
		ClassName cn = (ClassName) atn.getComponentType();
		assertEquals("Entry", cn.getSimpleName());
		assertNotNull(cn.getDeclaringClass());
		assertEquals("java.util.Map.Entry[]", atn.toString());
		assertEquals("Map.Entry[]", jt.getDisplayName());
	}

	// ========== Round-trip for arrays and primitives ==========

	@Test
	void roundTripPrimitive() {
		assertEquals("int", JavaType.parse("int").toString());
	}

	@Test
	void roundTripPrimitiveArray() {
		assertEquals("int[]", JavaType.parse("int[]").toString());
	}

	@Test
	void roundTripMultiDimensionalArray() {
		assertEquals("java.lang.String[][]", JavaType.parse("java.lang.String[][]").toString());
	}

	@Test
	void roundTripParameterizedArray() {
		assertEquals("java.util.List<java.lang.String>[]",
				JavaType.parse("java.util.List<java.lang.String>[]").toString());
	}

}
