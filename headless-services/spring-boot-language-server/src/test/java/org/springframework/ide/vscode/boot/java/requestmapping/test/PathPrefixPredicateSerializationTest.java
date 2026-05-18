/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.requestmapping.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.index.cache.IndexCacheOnDiscDeltaBased;
import org.springframework.ide.vscode.boot.java.requestmapping.PathPrefixPredicate;

import com.google.gson.Gson;

/**
 * Verifies that every {@link PathPrefixPredicate} variant round-trips cleanly
 * through the Gson instance produced by {@link IndexCacheOnDiscDeltaBased#createGson()}.
 */
public class PathPrefixPredicateSerializationTest {

	private Gson gson;

	@BeforeEach
	void setup() {
		gson = IndexCacheOnDiscDeltaBased.createGson();
	}

	// -----------------------------------------------------------------------
	// Leaf predicates
	// -----------------------------------------------------------------------

	@Test
	void anyPredicateRoundTrips() {
		PathPrefixPredicate original = new PathPrefixPredicate.AnyPredicate();
		PathPrefixPredicate result = roundTrip(original);
		assertInstanceOf(PathPrefixPredicate.AnyPredicate.class, result);
		assertEquals(original, result);
	}

	@Test
	void unknownPredicateRoundTrips() {
		PathPrefixPredicate original = new PathPrefixPredicate.UnknownPredicate();
		PathPrefixPredicate result = roundTrip(original);
		assertInstanceOf(PathPrefixPredicate.UnknownPredicate.class, result);
		assertEquals(original, result);
	}

	@Test
	void annotationPredicateSingleTypeRoundTrips() {
		PathPrefixPredicate original = new PathPrefixPredicate.AnnotationPredicate(
				List.of("org.springframework.web.bind.annotation.RestController"));
		PathPrefixPredicate result = roundTrip(original);
		assertInstanceOf(PathPrefixPredicate.AnnotationPredicate.class, result);
		PathPrefixPredicate.AnnotationPredicate ap = (PathPrefixPredicate.AnnotationPredicate) result;
		assertEquals(1, ap.annotationTypes().size());
		assertEquals("org.springframework.web.bind.annotation.RestController", ap.annotationTypes().get(0));
	}

	@Test
	void annotationPredicateMultipleTypesRoundTrips() {
		PathPrefixPredicate original = new PathPrefixPredicate.AnnotationPredicate(
				List.of("org.springframework.stereotype.Controller",
						"org.springframework.web.bind.annotation.RestController"));
		PathPrefixPredicate result = roundTrip(original);
		assertInstanceOf(PathPrefixPredicate.AnnotationPredicate.class, result);
		PathPrefixPredicate.AnnotationPredicate ap = (PathPrefixPredicate.AnnotationPredicate) result;
		assertEquals(2, ap.annotationTypes().size());
		assertTrue(ap.annotationTypes().contains("org.springframework.stereotype.Controller"));
		assertTrue(ap.annotationTypes().contains("org.springframework.web.bind.annotation.RestController"));
	}

	@Test
	void basePackagePredicateSinglePackageRoundTrips() {
		PathPrefixPredicate original = new PathPrefixPredicate.BasePackagePredicate(
				List.of("com.example.api"));
		PathPrefixPredicate result = roundTrip(original);
		assertInstanceOf(PathPrefixPredicate.BasePackagePredicate.class, result);
		PathPrefixPredicate.BasePackagePredicate bp = (PathPrefixPredicate.BasePackagePredicate) result;
		assertEquals(1, bp.packages().size());
		assertEquals("com.example.api", bp.packages().get(0));
	}

	@Test
	void basePackagePredicateMultiplePackagesRoundTrips() {
		PathPrefixPredicate original = new PathPrefixPredicate.BasePackagePredicate(
				List.of("com.example.api", "com.example.web"));
		PathPrefixPredicate result = roundTrip(original);
		assertInstanceOf(PathPrefixPredicate.BasePackagePredicate.class, result);
		PathPrefixPredicate.BasePackagePredicate bp = (PathPrefixPredicate.BasePackagePredicate) result;
		assertEquals(2, bp.packages().size());
		assertTrue(bp.packages().contains("com.example.api"));
		assertTrue(bp.packages().contains("com.example.web"));
	}

	@Test
	void assignableTypePredicateRoundTrips() {
		PathPrefixPredicate original = new PathPrefixPredicate.AssignableTypePredicate(
				List.of("com.example.BaseController"));
		PathPrefixPredicate result = roundTrip(original);
		assertInstanceOf(PathPrefixPredicate.AssignableTypePredicate.class, result);
		PathPrefixPredicate.AssignableTypePredicate at = (PathPrefixPredicate.AssignableTypePredicate) result;
		assertEquals(1, at.typeNames().size());
		assertEquals("com.example.BaseController", at.typeNames().get(0));
	}

	// -----------------------------------------------------------------------
	// Combinator predicates
	// -----------------------------------------------------------------------

	@Test
	void negatePredicateRoundTrips() {
		PathPrefixPredicate inner = new PathPrefixPredicate.BasePackagePredicate(List.of("org.test"));
		PathPrefixPredicate original = new PathPrefixPredicate.NegatePredicate(inner);

		PathPrefixPredicate result = roundTrip(original);
		assertInstanceOf(PathPrefixPredicate.NegatePredicate.class, result);
		PathPrefixPredicate.NegatePredicate neg = (PathPrefixPredicate.NegatePredicate) result;
		assertInstanceOf(PathPrefixPredicate.BasePackagePredicate.class, neg.inner());
		assertEquals("org.test",
				((PathPrefixPredicate.BasePackagePredicate) neg.inner()).packages().get(0));
	}

	@Test
	void andPredicateRoundTrips() {
		PathPrefixPredicate left = new PathPrefixPredicate.AnnotationPredicate(
				List.of("org.springframework.web.bind.annotation.RestController"));
		PathPrefixPredicate right = new PathPrefixPredicate.BasePackagePredicate(
				List.of("com.example"));
		PathPrefixPredicate original = new PathPrefixPredicate.AndPredicate(left, right);

		PathPrefixPredicate result = roundTrip(original);
		assertInstanceOf(PathPrefixPredicate.AndPredicate.class, result);
		PathPrefixPredicate.AndPredicate and = (PathPrefixPredicate.AndPredicate) result;
		assertInstanceOf(PathPrefixPredicate.AnnotationPredicate.class, and.left());
		assertInstanceOf(PathPrefixPredicate.BasePackagePredicate.class, and.right());
		assertEquals(original, result);
	}

	@Test
	void orPredicateRoundTrips() {
		PathPrefixPredicate left = new PathPrefixPredicate.AnnotationPredicate(
				List.of("org.springframework.stereotype.Controller"));
		PathPrefixPredicate right = new PathPrefixPredicate.AnnotationPredicate(
				List.of("org.springframework.web.bind.annotation.RestController"));
		PathPrefixPredicate original = new PathPrefixPredicate.OrPredicate(left, right);

		PathPrefixPredicate result = roundTrip(original);
		assertInstanceOf(PathPrefixPredicate.OrPredicate.class, result);
		assertEquals(original, result);
	}

	// -----------------------------------------------------------------------
	// Real-world complex case
	// -----------------------------------------------------------------------

	@Test
	void chainedAndNegatedPredicateRoundTrips() {
		// Equivalent to:
		//   HandlerTypePredicate.forAnnotation(RestController.class)
		//       .and(HandlerTypePredicate.forBasePackage("org.test.versions").negate())
		PathPrefixPredicate annotation = new PathPrefixPredicate.AnnotationPredicate(
				List.of("org.springframework.web.bind.annotation.RestController"));
		PathPrefixPredicate basePackage = new PathPrefixPredicate.BasePackagePredicate(
				List.of("org.test.versions"));
		PathPrefixPredicate negate = new PathPrefixPredicate.NegatePredicate(basePackage);
		PathPrefixPredicate original = new PathPrefixPredicate.AndPredicate(annotation, negate);

		PathPrefixPredicate result = roundTrip(original);

		assertInstanceOf(PathPrefixPredicate.AndPredicate.class, result);
		PathPrefixPredicate.AndPredicate and = (PathPrefixPredicate.AndPredicate) result;

		assertInstanceOf(PathPrefixPredicate.AnnotationPredicate.class, and.left());
		assertEquals("org.springframework.web.bind.annotation.RestController",
				((PathPrefixPredicate.AnnotationPredicate) and.left()).annotationTypes().get(0));

		assertInstanceOf(PathPrefixPredicate.NegatePredicate.class, and.right());
		PathPrefixPredicate innerRight = ((PathPrefixPredicate.NegatePredicate) and.right()).inner();
		assertInstanceOf(PathPrefixPredicate.BasePackagePredicate.class, innerRight);
		assertEquals("org.test.versions",
				((PathPrefixPredicate.BasePackagePredicate) innerRight).packages().get(0));

		assertEquals(original, result);
	}

	// -----------------------------------------------------------------------
	// Null safety
	// -----------------------------------------------------------------------

	@Test
	void nullPredicateSerializesAsNull() {
		String json = gson.toJson(null, PathPrefixPredicate.class);
		assertEquals("null", json);
	}

	@Test
	void nullJsonDeserializesToNull() {
		PathPrefixPredicate result = gson.fromJson("null", PathPrefixPredicate.class);
		assertNull(result);
	}

	// -----------------------------------------------------------------------
	// JSON structure verification
	// -----------------------------------------------------------------------

	@Test
	void annotationPredicateJsonContainsTypeDiscriminator() {
		PathPrefixPredicate predicate = new PathPrefixPredicate.AnnotationPredicate(
				List.of("com.example.MyAnnotation"));
		String json = gson.toJson(predicate, PathPrefixPredicate.class);
		assertNotNull(json);
		assertTrue(json.contains("_predicate_type"),
				"Serialized JSON must contain the '_predicate_type' discriminator field");
		assertTrue(json.contains("AnnotationPredicate"),
				"Type discriminator must reference 'AnnotationPredicate'");
		assertTrue(json.contains("com.example.MyAnnotation"),
				"Serialized JSON must contain the annotation type name");
	}

	@Test
	void andPredicateJsonContainsNestedTypeDiscriminators() {
		PathPrefixPredicate predicate = new PathPrefixPredicate.AndPredicate(
				new PathPrefixPredicate.AnnotationPredicate(List.of("com.example.A")),
				new PathPrefixPredicate.BasePackagePredicate(List.of("com.example")));
		String json = gson.toJson(predicate, PathPrefixPredicate.class);
		assertTrue(json.contains("AndPredicate"),
				"JSON must identify the outer AndPredicate");
		assertTrue(json.contains("AnnotationPredicate"),
				"JSON must identify the nested AnnotationPredicate");
		assertTrue(json.contains("BasePackagePredicate"),
				"JSON must identify the nested BasePackagePredicate");
	}

	// -----------------------------------------------------------------------
	// Helper
	// -----------------------------------------------------------------------

	private PathPrefixPredicate roundTrip(PathPrefixPredicate predicate) {
		String json = gson.toJson(predicate, PathPrefixPredicate.class);
		assertNotNull(json, "Serialized JSON must not be null");
		return gson.fromJson(json, PathPrefixPredicate.class);
	}
}
