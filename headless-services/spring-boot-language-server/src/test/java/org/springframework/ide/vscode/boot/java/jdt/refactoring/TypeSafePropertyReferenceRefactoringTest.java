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

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.TypeSafePropertyReferenceRefactoring.PropertyReferenceDescriptor;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.TypeSafePropertyReferenceRefactoring.PropertySegment;

/**
 * Unit tests for {@link TypeSafePropertyReferenceRefactoring}.
 * <p>
 * Pure JDT-level: source text in, apply edit, assert result.
 * No Spring context, LSP harness, or mock beans required.
 */
class TypeSafePropertyReferenceRefactoringTest {

	private static Map<String, String> defaultFormatterOptions() {
		Map<String, String> options = JavaCore.getOptions();
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.TAB);
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
		return options;
	}

	private static CompilationUnit parseSource(String source) {
		ASTParser parser = ASTParser.newParser(AST.JLS25);
		parser.setSource(source.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(false);

		Map<String, String> options = JavaCore.getOptions();
		String apiLevel = JavaCore.VERSION_21;
		JavaCore.setComplianceOptions(apiLevel, options);
		parser.setCompilerOptions(options);

		return (CompilationUnit) parser.createAST(null);
	}

	private static String applyRefactoring(String source, PropertyReferenceDescriptor... descriptors)
			throws Exception {
		CompilationUnit cu = parseSource(source);
		ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
		new TypeSafePropertyReferenceRefactoring(descriptors).apply(rewrite, cu);
		Document doc = new Document(source);
		TextEdit edit = rewrite.rewriteAST(doc, defaultFormatterOptions());
		edit.apply(doc);
		return doc.get();
	}

	private static int offsetOf(String source, String literalValue) {
		return source.indexOf("\"" + literalValue + "\"");
	}

	private static PropertyReferenceDescriptor singleSegment(int offset, String domainTypeFqn, String property) {
		return new PropertyReferenceDescriptor(offset,
				List.of(new PropertySegment(domainTypeFqn, property)));
	}

	// ========== Single-segment: basic replacement ==========

	@Test
	void basicReplacement_addsImportAndMethodReference() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.domain.Sort;

				class TestClass {
					void test() {
						Sort.by("firstName");
					}
				}
				""";

		String result = applyRefactoring(source,
				singleSegment(offsetOf(source, "firstName"), "com.example.domain.Customer", "firstName"));

		assertEquals("""
				package com.example;

				import com.example.domain.Customer;
				import org.springframework.data.domain.Sort;

				class TestClass {
					void test() {
						Sort.by(Customer::getFirstName);
					}
				}
				""", result);
	}

	// ========== Single-segment: import already present ==========

	@Test
	void importAlreadyPresent_noDuplicate() throws Exception {
		String source = """
				package com.example;

				import com.example.domain.Customer;
				import org.springframework.data.domain.Sort;

				class TestClass {
					void test() {
						Sort.by("lastName");
					}
				}
				""";

		String result = applyRefactoring(source,
				singleSegment(offsetOf(source, "lastName"), "com.example.domain.Customer", "lastName"));

		assertEquals("""
				package com.example;

				import com.example.domain.Customer;
				import org.springframework.data.domain.Sort;

				class TestClass {
					void test() {
						Sort.by(Customer::getLastName);
					}
				}
				""", result);
	}

	// ========== Single-segment: same package — no import needed ==========

	@Test
	void samePackageType_noImportAdded() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.domain.Sort;

				class TestClass {
					void test() {
						Sort.by("firstName");
					}
				}
				""";

		String result = applyRefactoring(source,
				singleSegment(offsetOf(source, "firstName"), "com.example.Customer", "firstName"));

		assertEquals("""
				package com.example;

				import org.springframework.data.domain.Sort;

				class TestClass {
					void test() {
						Sort.by(Customer::getFirstName);
					}
				}
				""", result);
	}

	// ========== Single-segment: batch (two descriptors, same domain type) ==========

	@Test
	void batchReplacement_twoLiterals() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.domain.Sort;

				class TestClass {
					void test() {
						Sort.by("firstName", "lastName");
					}
				}
				""";

		String result = applyRefactoring(source,
				singleSegment(offsetOf(source, "firstName"), "com.example.domain.Customer", "firstName"),
				singleSegment(offsetOf(source, "lastName"), "com.example.domain.Customer", "lastName"));

		assertEquals("""
				package com.example;

				import com.example.domain.Customer;
				import org.springframework.data.domain.Sort;

				class TestClass {
					void test() {
						Sort.by(Customer::getFirstName, Customer::getLastName);
					}
				}
				""", result);
	}

	// ========== Single-segment: Criteria.where ==========

	@Test
	void criteriaWhereReplacement() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.mongodb.core.query.Criteria;

				class TestClass {
					void test() {
						Criteria.where("lastName");
					}
				}
				""";

		String result = applyRefactoring(source,
				singleSegment(offsetOf(source, "lastName"), "com.example.domain.Customer", "lastName"));

		assertEquals("""
				package com.example;

				import com.example.domain.Customer;
				import org.springframework.data.mongodb.core.query.Criteria;

				class TestClass {
					void test() {
						Criteria.where(Customer::getLastName);
					}
				}
				""", result);
	}

	// ========== Single-segment: wildcard import covers package ==========

	@Test
	void wildcardImportCoversPackage_noExtraImport() throws Exception {
		String source = """
				package com.example;

				import com.example.domain.*;
				import org.springframework.data.domain.Sort;

				class TestClass {
					void test() {
						Sort.by("firstName");
					}
				}
				""";

		String result = applyRefactoring(source,
				singleSegment(offsetOf(source, "firstName"), "com.example.domain.Customer", "firstName"));

		assertEquals("""
				package com.example;

				import com.example.domain.*;
				import org.springframework.data.domain.Sort;

				class TestClass {
					void test() {
						Sort.by(Customer::getFirstName);
					}
				}
				""", result);
	}

	// ========== Single-segment: replace only targeted literal ==========

	@Test
	void replaceOnlyTargetedLiteral_otherUntouched() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.domain.Sort;

				class TestClass {
					void test() {
						Sort.by("firstName", "lastName");
					}
				}
				""";

		String result = applyRefactoring(source,
				singleSegment(offsetOf(source, "firstName"), "com.example.domain.Customer", "firstName"));

		assertEquals("""
				package com.example;

				import com.example.domain.Customer;
				import org.springframework.data.domain.Sort;

				class TestClass {
					void test() {
						Sort.by(Customer::getFirstName, "lastName");
					}
				}
				""", result);
	}

	// ========== Multi-segment: two-level property path ==========

	@Test
	void multiSegment_twoLevels_propertyPathOfThen() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.mongodb.core.query.Criteria;

				class TestClass {
					void test() {
						Criteria.where("address.country");
					}
				}
				""";

		String result = applyRefactoring(source,
				new PropertyReferenceDescriptor(offsetOf(source, "address.country"), List.of(
						new PropertySegment("com.example.domain.Person", "address"),
						new PropertySegment("com.example.domain.Address", "country"))));

		assertEquals("""
				package com.example;

				import com.example.domain.Address;
				import com.example.domain.Person;
				import org.springframework.data.core.PropertyPath;
				import org.springframework.data.mongodb.core.query.Criteria;

				class TestClass {
					void test() {
						Criteria.where(PropertyPath.of(Person::getAddress).then(Address::getCountry));
					}
				}
				""", result);
	}

	// ========== Multi-segment: three-level property path ==========

	@Test
	void multiSegment_threeLevels_chainedThen() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.domain.Sort;

				class TestClass {
					void test() {
						Sort.by("address.city.name");
					}
				}
				""";

		String result = applyRefactoring(source,
				new PropertyReferenceDescriptor(offsetOf(source, "address.city.name"), List.of(
						new PropertySegment("com.example.domain.Employee", "address"),
						new PropertySegment("com.example.domain.Address", "city"),
						new PropertySegment("com.example.domain.City", "name"))));

		assertEquals("""
				package com.example;

				import com.example.domain.Address;
				import com.example.domain.City;
				import com.example.domain.Employee;
				import org.springframework.data.core.PropertyPath;
				import org.springframework.data.domain.Sort;

				class TestClass {
					void test() {
						Sort.by(PropertyPath.of(Employee::getAddress).then(Address::getCity).then(City::getName));
					}
				}
				""", result);
	}

	// ========== Multi-segment: domain types share same package — no duplicate imports ==========

	@Test
	void multiSegment_sharedImports_noDuplicates() throws Exception {
		String source = """
				package com.example;

				import com.example.domain.Person;
				import org.springframework.data.mongodb.core.query.Criteria;

				class TestClass {
					void test() {
						Criteria.where("address.street");
					}
				}
				""";

		String result = applyRefactoring(source,
				new PropertyReferenceDescriptor(offsetOf(source, "address.street"), List.of(
						new PropertySegment("com.example.domain.Person", "address"),
						new PropertySegment("com.example.domain.Address", "street"))));

		assertEquals("""
				package com.example;

				import com.example.domain.Address;
				import com.example.domain.Person;
				import org.springframework.data.core.PropertyPath;
				import org.springframework.data.mongodb.core.query.Criteria;

				class TestClass {
					void test() {
						Criteria.where(PropertyPath.of(Person::getAddress).then(Address::getStreet));
					}
				}
				""", result);
	}

	// ========== Mixed batch: single-segment + multi-segment in one refactoring ==========

	@Test
	void mixedBatch_singleAndMultiSegment() throws Exception {
		String source = """
				package com.example;

				import org.springframework.data.domain.Sort;
				import org.springframework.data.mongodb.core.query.Criteria;

				class TestClass {
					void test() {
						Sort.by("firstName");
						Criteria.where("address.country");
					}
				}
				""";

		String result = applyRefactoring(source,
				singleSegment(offsetOf(source, "firstName"), "com.example.domain.Person", "firstName"),
				new PropertyReferenceDescriptor(offsetOf(source, "address.country"), List.of(
						new PropertySegment("com.example.domain.Person", "address"),
						new PropertySegment("com.example.domain.Address", "country"))));

		assertEquals("""
				package com.example;

				import com.example.domain.Address;
				import com.example.domain.Person;
				import org.springframework.data.core.PropertyPath;
				import org.springframework.data.domain.Sort;
				import org.springframework.data.mongodb.core.query.Criteria;

				class TestClass {
					void test() {
						Sort.by(Person::getFirstName);
						Criteria.where(PropertyPath.of(Person::getAddress).then(Address::getCountry));
					}
				}
				""", result);
	}

}
