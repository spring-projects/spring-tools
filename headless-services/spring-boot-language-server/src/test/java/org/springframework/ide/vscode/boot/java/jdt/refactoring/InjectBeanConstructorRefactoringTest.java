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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link InjectBeanConstructorRefactoring}.
 * <p>
 * These tests are pure JDT-level: source text in, apply edit, assert result.
 * No Spring context, LSP harness, or mock beans required.
 */
class InjectBeanConstructorRefactoringTest {

	private static Map<String, String> defaultFormatterOptions() {
		Map<String, String> options = JavaCore.getOptions();
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.TAB);
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
		return options;
	}

	private static CompilationUnit parseSource(String source) {
		ASTParser parser = ASTParser.newParser(AST.JLS21);
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(false);

		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_SOURCE, "17");
		options.put(JavaCore.COMPILER_COMPLIANCE, "17");
		parser.setCompilerOptions(options);

		return (CompilationUnit) parser.createAST(null);
	}

	private static String applyRefactoring(String source, String beanType, String fieldName,
			String targetClass, boolean addAssignment) throws Exception {
		CompilationUnit cu = parseSource(source);
		InjectBeanConstructorRefactoring refactoring = new InjectBeanConstructorRefactoring(
				cu, source, beanType, fieldName, targetClass, addAssignment, defaultFormatterOptions());
		TextEdit edit = refactoring.computeEdit();
		assertNotNull(edit, "Expected non-null edit");
		Document doc = new Document(source);
		edit.apply(doc);
		return doc.get();
	}

	// ========== Basic scenarios ==========

	@Test
	void injectIntoEmptyClass() throws Exception {
		String source = """
				package com.example;
				
				public class MyService {
				
					public void doWork() {
					}
				}
				""";

		String result = applyRefactoring(source,
				"com.example.repo.MyRepository", "myRepository",
				"com.example.MyService", true);

		assertEquals("""
				package com.example;
				
				import com.example.repo.MyRepository;
				
				public class MyService {
				
					private final MyRepository myRepository;
				
					MyService(MyRepository myRepository) {
						this.myRepository = myRepository;
					}
				
					public void doWork() {
					}
				}
				""", result);
	}

	@Test
	void injectIntoClassWithExistingConstructor() throws Exception {
		String source = """
				package com.example;
				
				public class MyService {
					private final String name;
				
					MyService(String name) {
						this.name = name;
					}
				}
				""";

		String result = applyRefactoring(source,
				"com.example.repo.MyRepository", "myRepository",
				"com.example.MyService", true);

		assertEquals("""
				package com.example;
				
				import com.example.repo.MyRepository;
				
				public class MyService {
					private final MyRepository myRepository;
					private final String name;
				
					MyService(String name, MyRepository myRepository) {
						this.name = name;
						this.myRepository = myRepository;
					}
				}
				""", result);
	}

	@Test
	void injectWithoutAssignment() throws Exception {
		String source = """
				package com.example;
				
				public class MyService {
					MyService() {
						super();
					}
				}
				""";

		String result = applyRefactoring(source,
				"com.example.repo.MyRepository", "myRepository",
				"com.example.MyService", false);

		assertEquals("""
				package com.example;
				
				import com.example.repo.MyRepository;
				
				public class MyService {
					private final MyRepository myRepository;
				
					MyService(MyRepository myRepository) {
						super();
					}
				}
				""", result);
	}

	// ========== Import handling ==========

	@Test
	void noImportForJavaLangType() throws Exception {
		String source = """
				package com.example;
				
				public class MyService {
				
					public void doWork() {
					}
				}
				""";

		String result = applyRefactoring(source,
				"java.lang.Integer", "count",
				"com.example.MyService", true);

		assertEquals("""
				package com.example;
				
				public class MyService {
				
					private final Integer count;
				
					MyService(Integer count) {
						this.count = count;
					}
				
					public void doWork() {
					}
				}
				""", result);
	}

	@Test
	void noImportForSamePackageType() throws Exception {
		String source = """
				package com.example;
				
				public class MyService {
				
					public void doWork() {
					}
				}
				""";

		String result = applyRefactoring(source,
				"com.example.MyRepository", "myRepository",
				"com.example.MyService", true);

		assertEquals("""
				package com.example;
				
				public class MyService {
				
					private final MyRepository myRepository;
				
					MyService(MyRepository myRepository) {
						this.myRepository = myRepository;
					}
				
					public void doWork() {
					}
				}
				""", result);
	}

	@Test
	void importInsertedInSortedOrder() throws Exception {
		String source = """
				package com.example;
				
				import com.example.alpha.AlphaService;
				import com.example.zeta.ZetaService;
				
				public class MyService {
					private final AlphaService alpha;
					private final ZetaService zeta;
				
					MyService(AlphaService alpha, ZetaService zeta) {
						this.alpha = alpha;
						this.zeta = zeta;
					}
				}
				""";

		String result = applyRefactoring(source,
				"com.example.mid.MiddleService", "middle",
				"com.example.MyService", true);

		assertEquals("""
				package com.example;
				
				import com.example.alpha.AlphaService;
				import com.example.mid.MiddleService;
				import com.example.zeta.ZetaService;
				
				public class MyService {
					private final MiddleService middle;
					private final AlphaService alpha;
					private final ZetaService zeta;
				
					MyService(AlphaService alpha, ZetaService zeta, MiddleService middle) {
						this.alpha = alpha;
						this.zeta = zeta;
						this.middle = middle;
					}
				}
				""", result);
	}

	@Test
	void existingImportNotDuplicated() throws Exception {
		String source = """
				package com.example;
				
				import com.example.repo.MyRepository;
				
				public class MyService {
				
					public void doWork() {
					}
				}
				""";

		String result = applyRefactoring(source,
				"com.example.repo.MyRepository", "myRepository",
				"com.example.MyService", true);

		assertEquals("""
				package com.example;
				
				import com.example.repo.MyRepository;
				
				public class MyService {
				
					private final MyRepository myRepository;
				
					MyService(MyRepository myRepository) {
						this.myRepository = myRepository;
					}
				
					public void doWork() {
					}
				}
				""", result);
	}

	@Test
	void noImportWhenWildcardImportCoversPackage() throws Exception {
		String source = """
				package com.example;
				
				import java.util.*;
				
				public class MyService {
				
					public void doWork() {
					}
				}
				""";

		String result = applyRefactoring(source,
				"java.util.List<java.lang.String>", "names",
				"com.example.MyService", true);

		assertEquals("""
				package com.example;
				
				import java.util.*;
				
				public class MyService {
				
					private final List<String> names;
				
					MyService(List<String> names) {
						this.names = names;
					}
				
					public void doWork() {
					}
				}
				""", result);
	}

	@Test
	void wildcardImportDoesNotCoverDifferentPackage() throws Exception {
		String source = """
				package com.example;
				
				import java.util.*;
				
				public class MyService {
				
					public void doWork() {
					}
				}
				""";

		String result = applyRefactoring(source,
				"java.util.concurrent.ConcurrentMap<java.lang.String, java.lang.Integer>",
				"cache",
				"com.example.MyService", true);

		assertEquals("""
				package com.example;
				
				import java.util.*;
				import java.util.concurrent.ConcurrentMap;
				
				public class MyService {
				
					private final ConcurrentMap<String, Integer> cache;
				
					MyService(ConcurrentMap<String, Integer> cache) {
						this.cache = cache;
					}
				
					public void doWork() {
					}
				}
				""", result);
	}

	// ========== Inner classes ==========

	@Test
	void injectInnerClassType() throws Exception {
		String source = """
				package com.example;
				
				public class MyService {
				
					public void doWork() {
					}
				}
				""";

		String result = applyRefactoring(source,
				"com.example.pet.Inner$PetService", "petService",
				"com.example.MyService", true);

		assertEquals("""
				package com.example;
				
				import com.example.pet.Inner.PetService;
				
				public class MyService {
				
					private final Inner.PetService petService;
				
					MyService(Inner.PetService petService) {
						this.petService = petService;
					}
				
					public void doWork() {
					}
				}
				""", result);
	}

	// ========== Nested type declarations ==========

	@Test
	void injectIntoNestedClass() throws Exception {
		String source = """
				package com.example;
				
				public class Outer {
					public class Inner {
				
						public void doWork() {
						}
					}
				}
				""";

		String result = applyRefactoring(source,
				"com.example.repo.MyRepository", "myRepository",
				"com.example.Inner", true);

		assertEquals("""
				package com.example;
				
				import com.example.repo.MyRepository;
				
				public class Outer {
					public class Inner {
				
						private final MyRepository myRepository;
				
						Inner(MyRepository myRepository) {
							this.myRepository = myRepository;
						}
				
						public void doWork() {
						}
					}
				}
				""", result);
	}

	// ========== Idempotency ==========

	@Test
	void existingFieldNotDuplicated() throws Exception {
		String source = """
				package com.example;
				
				import com.example.repo.MyRepository;
				
				public class MyService {
					private final MyRepository myRepository;
				
					MyService(MyRepository myRepository) {
						this.myRepository = myRepository;
					}
				}
				""";

		String result = applyRefactoring(source,
				"com.example.repo.MyRepository", "myRepository",
				"com.example.MyService", true);

		// No changes — field, constructor param, assignment, and import all exist
		assertEquals(source, result);
	}

	// ========== Target class not found ==========

	@Test
	void returnsNullWhenClassNotFound() throws Exception {
		String source = """
				package com.example;
				
				public class MyService {
				}
				""";

		CompilationUnit cu = parseSource(source);
		InjectBeanConstructorRefactoring refactoring = new InjectBeanConstructorRefactoring(
				cu, source, "com.example.Repo", "repo",
				"com.example.NonExistent", true, defaultFormatterOptions());

		assertNull(refactoring.computeEdit());
	}

	// ========== Multiple classes in same file ==========

	@Test
	void injectIntoCorrectClassWhenMultiplePresent() throws Exception {
		String source = """
				package com.example;
				
				public class First {
					public void doWork() {
					}
				}
				
				class Second {
					public void doWork() {
					}
				}
				""";

		String result = applyRefactoring(source,
				"com.example.repo.MyRepository", "myRepository",
				"com.example.Second", true);

		assertEquals("""
				package com.example;
				
				import com.example.repo.MyRepository;
				
				public class First {
					public void doWork() {
					}
				}
				
				class Second {
					private final MyRepository myRepository;
				
					Second(MyRepository myRepository) {
						this.myRepository = myRepository;
					}
				
					public void doWork() {
					}
				}
				""", result);
	}

	// ========== Parameterized types ==========

	@Test
	void injectParameterizedType() throws Exception {
		String source = """
				package com.example;
				
				public class MyService {
				
					public void doWork() {
					}
				}
				""";

		String result = applyRefactoring(source,
				"java.util.Map<java.lang.String, java.util.List<java.util.Map$Entry<java.lang.String, ?>>>",
				"lookupMap",
				"com.example.MyService", true);

		assertEquals("""
				package com.example;
				
				import java.util.List;
				import java.util.Map;
				import java.util.Map.Entry;
				
				public class MyService {
				
					private final Map<String, List<Map.Entry<String, ?>>> lookupMap;
				
					MyService(Map<String, List<Map.Entry<String, ?>>> lookupMap) {
						this.lookupMap = lookupMap;
					}
				
					public void doWork() {
					}
				}
				""", result);
	}

	@Test
	void injectSimpleParameterizedType() throws Exception {
		String source = """
				package com.example;
				
				public class MyService {
				
					public void doWork() {
					}
				}
				""";

		String result = applyRefactoring(source,
				"java.util.List<com.example.dto.MyDto>",
				"items",
				"com.example.MyService", true);

		assertEquals("""
				package com.example;
				
				import com.example.dto.MyDto;
				import java.util.List;
				
				public class MyService {
				
					private final List<MyDto> items;
				
					MyService(List<MyDto> items) {
						this.items = items;
					}
				
					public void doWork() {
					}
				}
				""", result);
	}

	@Test
	void injectParameterizedTypeWithExistingImports() throws Exception {
		String source = """
				package com.example;
				
				import java.util.Map;
				
				public class MyService {
				
					public void doWork() {
					}
				}
				""";

		String result = applyRefactoring(source,
				"java.util.Map<java.lang.String, java.lang.Integer>",
				"counts",
				"com.example.MyService", true);

		assertEquals("""
				package com.example;
				
				import java.util.Map;
				
				public class MyService {
				
					private final Map<String, Integer> counts;
				
					MyService(Map<String, Integer> counts) {
						this.counts = counts;
					}
				
					public void doWork() {
					}
				}
				""", result);
	}

	@Test
	void injectWildcardType() throws Exception {
		String source = """
				package com.example;
				
				public class MyService {
				
					public void doWork() {
					}
				}
				""";

		String result = applyRefactoring(source,
				"java.util.List<? extends com.example.dto.BaseDto>",
				"items",
				"com.example.MyService", true);

		assertEquals("""
				package com.example;
				
				import com.example.dto.BaseDto;
				import java.util.List;
				
				public class MyService {
				
					private final List<? extends BaseDto> items;
				
					MyService(List<? extends BaseDto> items) {
						this.items = items;
					}
				
					public void doWork() {
					}
				}
				""", result);
	}

	// ========== Type name utilities (via FullyQualifiedName) ==========

	@Test
	void getFieldTypeName_simpleClass() {
		ClassType cn = (ClassType) JavaType.parse("com.example.MyRepository");
		assertEquals("MyRepository", cn.getFieldTypeName());
	}

	@Test
	void getFieldTypeName_innerClass() {
		ClassType cn = (ClassType) JavaType.parse("com.example.pet.Inner$PetService");
		assertEquals("Inner.PetService", cn.getFieldTypeName());
	}

	@Test
	void getFieldTypeName_noPackage() {
		ClassType cn = (ClassType) JavaType.parse("MyRepository");
		assertEquals("MyRepository", cn.getFieldTypeName());
	}

	@Test
	void getSimpleTypeName_fqn() {
		assertEquals("MyRepository", ((FullyQualifiedType) JavaType.parse("com.example.MyRepository")).getSimpleName());
	}

	@Test
	void getSimpleTypeName_simple() {
		assertEquals("MyRepository", ((FullyQualifiedType) JavaType.parse("MyRepository")).getSimpleName());
	}

}
