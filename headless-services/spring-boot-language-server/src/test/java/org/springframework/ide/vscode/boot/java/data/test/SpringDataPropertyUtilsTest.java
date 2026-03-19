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
package org.springframework.ide.vscode.boot.java.data.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.data.SpringDataPropertyUtils;
import org.springframework.ide.vscode.boot.java.data.SpringDataPropertyUtils.PropertyMatch;
import org.springframework.ide.vscode.boot.java.data.SpringDataPropertyUtils.ResolvedChain;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.TypeSafePropertyReferenceRefactoring.PropertySegment;

/**
 * Unit tests for {@link SpringDataPropertyUtils}.
 * <p>
 * Uses JDT parsing with binding resolution to get real {@link ITypeBinding}s
 * for test domain types. All types are declared in a single compilation unit
 * (no package) to avoid temp files on disk.
 */
class SpringDataPropertyUtilsTest {

	private static final String ALL_TYPES_SOURCE = """
			class Address {
				private String city;
				private String country;
				public String getCity() { return city; }
				public String getCountry() { return country; }
			}

			class Customer {
				private String id;
				private String firstName;
				private String lastName;
				private Address address;
				public String getId() { return id; }
				public String getFirstName() { return firstName; }
				public String getLastName() { return lastName; }
				public Address getAddress() { return address; }
			}

			class Order {
				private String id;
				private String firstName;
				private String orderDate;
				private Customer customer;
				public String getId() { return id; }
				public String getFirstName() { return firstName; }
				public String getOrderDate() { return orderDate; }
				public Customer getCustomer() { return customer; }
			}

			record PersonRecord(String firstName, String lastName, Address address) {}
			""";

	private static ITypeBinding customerBinding;
	private static ITypeBinding addressBinding;
	private static ITypeBinding orderBinding;
	private static ITypeBinding personRecordBinding;

	@BeforeAll
	static void setupBindings() {
		ASTParser parser = ASTParser.newParser(AST.JLS25);
		parser.setSource(ALL_TYPES_SOURCE.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setUnitName("Test.java");
		parser.setEnvironment(new String[0], new String[0], null, true);

		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);

		CompilationUnit cu = (CompilationUnit) parser.createAST(null);

		for (Object type : cu.types()) {
			if (type instanceof AbstractTypeDeclaration td) {
				String name = td.getName().getIdentifier();
				switch (name) {
					case "Customer" -> customerBinding = td.resolveBinding();
					case "Address" -> addressBinding = td.resolveBinding();
					case "Order" -> orderBinding = td.resolveBinding();
					case "PersonRecord" -> personRecordBinding = td.resolveBinding();
				}
			}
		}

		assertNotNull(customerBinding, "Customer binding must be resolved");
		assertNotNull(addressBinding, "Address binding must be resolved");
		assertNotNull(orderBinding, "Order binding must be resolved");
		assertNotNull(personRecordBinding, "PersonRecord binding must be resolved");
	}

	// =====================================================================
	// findSimilarProperties (fuzzy / Jaro-Winkler)
	// =====================================================================

	@Test
	void fuzzyMatch_closeSimilarity() {
		List<PropertyMatch> matches = SpringDataPropertyUtils.findSimilarProperties(customerBinding, "firstNam");
		assertFalse(matches.isEmpty());
		assertEquals("firstName", matches.get(0).propertyName());
		assertEquals("getFirstName", matches.get(0).methodName());
		assertTrue(matches.get(0).similarity() < 1.0);
		assertTrue(matches.get(0).similarity() >= 0.7);
	}

	@Test
	void fuzzyMatch_tooLowSimilarity() {
		List<PropertyMatch> matches = SpringDataPropertyUtils.findSimilarProperties(customerBinding, "xyz", 0.95);
		assertTrue(matches.isEmpty());
	}

	@Test
	void fuzzyMatch_sortedBySimilarity() {
		List<PropertyMatch> matches = SpringDataPropertyUtils.findSimilarProperties(customerBinding, "lastNam");
		assertFalse(matches.isEmpty());
		assertEquals("lastName", matches.get(0).propertyName());
		for (int i = 1; i < matches.size(); i++) {
			assertTrue(matches.get(i - 1).similarity() >= matches.get(i).similarity());
		}
	}

	@Test
	void fuzzyMatch_doesNotIncludeExact() {
		List<PropertyMatch> matches = SpringDataPropertyUtils.findSimilarProperties(customerBinding, "firstName");
		assertTrue(matches.stream().noneMatch(m -> m.similarity() == 1.0),
				"Fuzzy matches should not include exact matches (similarity 1.0)");
	}

	// =====================================================================
	// resolvePropertyChain — exact
	// =====================================================================

	@Test
	void chainExact_singleSegment() {
		List<ResolvedChain> chains = SpringDataPropertyUtils.resolvePropertyChain(customerBinding, "firstName");
		assertEquals(1, chains.size());
		ResolvedChain chain = chains.get(0);
		assertTrue(chain.allExact());
		assertEquals(1, chain.segments().size());
		assertEquals("getFirstName", chain.segments().get(0).methodName());
		assertEquals("Customer", chain.segments().get(0).domainTypeFqn());
	}

	@Test
	void chainExact_multiSegment() {
		List<ResolvedChain> chains = SpringDataPropertyUtils.resolvePropertyChain(customerBinding, "address.country");
		assertEquals(1, chains.size());
		ResolvedChain chain = chains.get(0);
		assertTrue(chain.allExact());
		assertEquals(2, chain.segments().size());

		PropertySegment seg0 = chain.segments().get(0);
		assertEquals("Customer", seg0.domainTypeFqn());
		assertEquals("getAddress", seg0.methodName());

		PropertySegment seg1 = chain.segments().get(1);
		assertEquals("Address", seg1.domainTypeFqn());
		assertEquals("getCountry", seg1.methodName());
	}

	@Test
	void chainExact_notFound() {
		List<ResolvedChain> chains = SpringDataPropertyUtils.resolvePropertyChain(customerBinding, "zzzzz");
		assertTrue(chains.isEmpty());
	}

	@Test
	void chainExact_partialChainNotFound() {
		List<ResolvedChain> chains = SpringDataPropertyUtils.resolvePropertyChain(customerBinding, "address.zzzzz");
		assertTrue(chains.isEmpty());
	}

	// =====================================================================
	// resolvePropertyChain — fuzzy fallback
	// =====================================================================

	@Test
	void chainFuzzy_singleSegment() {
		List<ResolvedChain> chains = SpringDataPropertyUtils.resolvePropertyChain(customerBinding, "firstNam");
		assertFalse(chains.isEmpty());
		assertFalse(chains.get(0).allExact());
		assertTrue(chains.get(0).score() < 1.0);
		assertEquals("getFirstName", chains.get(0).segments().get(0).methodName());
	}

	@Test
	void chainFuzzy_perSegmentInChain() {
		List<ResolvedChain> chains = SpringDataPropertyUtils.resolvePropertyChain(customerBinding, "adress.country");
		assertFalse(chains.isEmpty());
		ResolvedChain chain = chains.get(0);
		assertFalse(chain.allExact());
		assertTrue(chain.score() < 1.0);
		assertEquals(2, chain.segments().size());
		assertEquals("getAddress", chain.segments().get(0).methodName());
		assertEquals("getCountry", chain.segments().get(1).methodName());
	}

	@Test
	void chainFuzzy_resultsSortedByScore() {
		List<ResolvedChain> chains = SpringDataPropertyUtils.resolvePropertyChain(customerBinding, "firstNam");
		for (int i = 1; i < chains.size(); i++) {
			assertTrue(chains.get(i - 1).score() >= chains.get(i).score(),
					"Chains should be sorted by score (best first)");
		}
	}

	@Test
	void chainExact_scoreIsOne() {
		List<ResolvedChain> chains = SpringDataPropertyUtils.resolvePropertyChain(customerBinding, "firstName");
		assertEquals(1, chains.size());
		assertEquals(1.0, chains.get(0).score());
		assertTrue(chains.get(0).allExact());
	}

	@Test
	void chain_exactPrefersOverFuzzy() {
		List<ResolvedChain> chains = SpringDataPropertyUtils.resolvePropertyChain(customerBinding, "firstName");
		assertEquals(1, chains.size());
		assertTrue(chains.get(0).allExact());
	}

	@Test
	void chainFuzzy_noMatch() {
		List<ResolvedChain> chains = SpringDataPropertyUtils.resolvePropertyChain(customerBinding, "xyzxyz");
		assertTrue(chains.isEmpty());
	}

	// =====================================================================
	// Edge cases
	// =====================================================================

	@Test
	void emptyProperty() {
		List<ResolvedChain> chains = SpringDataPropertyUtils.resolvePropertyChain(customerBinding, "");
		assertTrue(chains.isEmpty());
	}

	@Test
	void nullProperty() {
		List<ResolvedChain> chains = SpringDataPropertyUtils.resolvePropertyChain(customerBinding, null);
		assertTrue(chains.isEmpty());
	}

	// =====================================================================
	// Record types
	// =====================================================================

	@Test
	void record_fuzzyMatch_methodNameIsFieldName() {
		List<PropertyMatch> matches = SpringDataPropertyUtils.findSimilarProperties(personRecordBinding, "firstNam");
		assertFalse(matches.isEmpty());
		assertEquals("firstName", matches.get(0).propertyName());
		assertEquals("firstName", matches.get(0).methodName());
	}

	@Test
	void record_chainExact_singleSegment() {
		List<ResolvedChain> chains = SpringDataPropertyUtils.resolvePropertyChain(personRecordBinding, "firstName");
		assertEquals(1, chains.size());
		assertTrue(chains.get(0).allExact());
		assertEquals("firstName", chains.get(0).segments().get(0).methodName());
		assertEquals("PersonRecord", chains.get(0).segments().get(0).domainTypeFqn());
	}

	@Test
	void record_chainExact_nestedIntoClass() {
		List<ResolvedChain> chains = SpringDataPropertyUtils.resolvePropertyChain(personRecordBinding, "address.country");
		assertEquals(1, chains.size());
		assertTrue(chains.get(0).allExact());
		assertEquals(2, chains.get(0).segments().size());
		assertEquals("address", chains.get(0).segments().get(0).methodName());
		assertEquals("getCountry", chains.get(0).segments().get(1).methodName());
	}

}
