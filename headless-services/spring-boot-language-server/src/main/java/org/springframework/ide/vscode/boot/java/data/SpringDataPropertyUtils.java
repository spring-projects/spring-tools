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
package org.springframework.ide.vscode.boot.java.data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.jspecify.annotations.Nullable;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.TypeSafePropertyReferenceRefactoring.PropertySegment;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;

/**
 * Utility for introspecting properties on Spring Data domain types.
 * <p>
 * Supports exact and fuzzy (Jaro-Winkler similarity) property matching, as well as
 * dotted property chain resolution (e.g., {@code "address.country"}).
 * <p>
 * This class is intentionally separate from {@link SpringDataDomainTypeResolver},
 * which handles domain type identification only.
 */
public final class SpringDataPropertyUtils {

	private static final JaroWinklerSimilarity JARO_WINKLER = new JaroWinklerSimilarity();

	static final double DEFAULT_MIN_SIMILARITY = 0.7;

	private SpringDataPropertyUtils() {
	}

	/**
	 * A matched property on a domain type, carrying the property name (for
	 * similarity comparison), the accessor/getter method name (for code
	 * generation), and the return type (for walking nested chains).
	 *
	 * @param propertyName the logical property name (e.g., {@code "firstName"})
	 * @param methodName   the actual accessor method name (e.g., {@code "getFirstName"}
	 *                     for classes, {@code "firstName"} for records)
	 * @param returnType   the return type of the accessor method
	 * @param similarity   Jaro-Winkler similarity score (1.0 = exact match, 0.0 = no similarity)
	 */
	public record PropertyMatch(
			String propertyName,
			String methodName,
			ITypeBinding returnType,
			double similarity
	) {
		public boolean isExact() {
			return similarity == 1.0;
		}
	}

	/**
	 * A fully resolved property chain from root domain type through nested types.
	 *
	 * @param segments  the resolved property segments
	 * @param score     composite Jaro-Winkler similarity score — product of per-segment
	 *                  similarities (1.0 = all exact, lower = fuzzier match)
	 */
	public record ResolvedChain(
			List<PropertySegment> segments,
			double score
	) {
		public boolean allExact() {
			return score == 1.0;
		}
	}

	// =====================================================================
	// Exact property lookup
	// =====================================================================

	/**
	 * Find an exact property match on the given type (including supertypes).
	 * Looks for {@code get<PropertyName>()} or {@code is<PropertyName>()} methods.
	 *
	 * @param domainType   the type to introspect
	 * @param propertyName lower-case-first property name (e.g., {@code "firstName"})
	 * @return the match, or {@code null} if not found
	 */
	public static @Nullable PropertyMatch findExactProperty(ITypeBinding domainType, String propertyName) {
		if (propertyName == null || propertyName.isEmpty()) {
			return null;
		}
		return findPropertyOnType(domainType, propertyName, true);
	}

	// =====================================================================
	// Fuzzy property lookup (Jaro-Winkler similarity)
	// =====================================================================

	/**
	 * Find properties on the given type whose names have at least the specified
	 * Jaro-Winkler similarity to {@code propertyName}.
	 *
	 * @param domainType     the type to introspect
	 * @param propertyName   the (possibly misspelled) property name
	 * @param minSimilarity  minimum Jaro-Winkler similarity to consider (0.0–1.0)
	 * @return list of matches sorted by similarity (best first), never {@code null}
	 */
	public static List<PropertyMatch> findSimilarProperties(ITypeBinding domainType, String propertyName, double minSimilarity) {
		if (propertyName == null || propertyName.isEmpty()) {
			return List.of();
		}
		List<PropertyMatch> matches = new ArrayList<>();
		collectProperties(domainType, propertyName, minSimilarity, matches);
		matches.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));
		return matches;
	}

	/**
	 * Overload using default minimum similarity threshold.
	 */
	public static List<PropertyMatch> findSimilarProperties(ITypeBinding domainType, String propertyName) {
		return findSimilarProperties(domainType, propertyName, DEFAULT_MIN_SIMILARITY);
	}

	// =====================================================================
	// Property chain resolution
	// =====================================================================

	/**
	 * Resolve a dotted property chain against a root domain type.
	 * <p>
	 * Each segment is tried as an exact match first; if no exact match exists,
	 * fuzzy (Jaro-Winkler) alternatives are explored. The returned list is
	 * filtered so that if any chain achieves a perfect score (1.0 — all segments
	 * exact), only those perfect chains are returned. Otherwise, all fuzzy
	 * chains above the similarity threshold are returned, sorted best-first.
	 *
	 * @param domainType     the root domain type
	 * @param dottedProperty the property string, possibly containing dots
	 *                       (e.g., {@code "address.country"})
	 * @return list of resolved chains; empty if no resolution is possible
	 */
	public static List<ResolvedChain> resolvePropertyChain(ITypeBinding domainType, String dottedProperty) {
		if (dottedProperty == null || dottedProperty.isEmpty()) {
			return List.of();
		}

		String[] segments = dottedProperty.split("\\.");
		if (segments.length == 0) {
			return List.of();
		}

		List<ResolvedChain> results = new ArrayList<>();
		resolveSegments(domainType, segments, 0, new ArrayList<>(), 1.0, results);

		List<ResolvedChain> exact = results.stream().filter(ResolvedChain::allExact).toList();
		if (!exact.isEmpty()) {
			return exact;
		}

		results.sort((a, b) -> Double.compare(b.score(), a.score()));
		return results;
	}

	// =====================================================================
	// Internal helpers
	// =====================================================================

	private static void resolveSegments(
			ITypeBinding currentType,
			String[] segments,
			int segmentIndex,
			List<PropertySegment> accumulated,
			double scoreSoFar,
			List<ResolvedChain> results
	) {
		if (segmentIndex >= segments.length) {
			results.add(new ResolvedChain(List.copyOf(accumulated), scoreSoFar));
			return;
		}

		String segmentName = segments[segmentIndex];
		String typeFqn = currentType.getQualifiedName();

		// Try exact first (similarity = 1.0, doesn't change the composite score)
		PropertyMatch exact = findExactProperty(currentType, segmentName);
		if (exact != null) {
			accumulated.add(new PropertySegment(typeFqn, exact.methodName()));
			resolveSegments(exact.returnType(), segments, segmentIndex + 1, accumulated, scoreSoFar, results);
			accumulated.remove(accumulated.size() - 1);
			return;
		}

		// Fuzzy fallback — multiply per-segment similarity into composite score
		List<PropertyMatch> similar = findSimilarProperties(currentType, segmentName);
		for (PropertyMatch match : similar) {
			accumulated.add(new PropertySegment(typeFqn, match.methodName()));
			resolveSegments(match.returnType(), segments, segmentIndex + 1, accumulated, scoreSoFar * match.similarity(), results);
			accumulated.remove(accumulated.size() - 1);
		}
	}

	/**
	 * Find a property by name on a type, walking the type hierarchy via
	 * {@link ASTUtils#getHierarchyTypesBreadthFirstIterator}.
	 * <p>
	 * For records: matches field names; method name = field name (record accessor).
	 * For classes: matches {@code get/is}-prefixed getters and plain accessor methods
	 * with the same name as the property; method name = actual method name.
	 */
	private static @Nullable PropertyMatch findPropertyOnType(ITypeBinding type, String propertyName, boolean walkSuperTypes) {
		Iterator<ITypeBinding> hierarchy = walkSuperTypes
				? ASTUtils.getHierarchyTypesBreadthFirstIterator(type)
				: List.of(type).iterator();

		String getterName = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
		String isName = "is" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);

		while (hierarchy.hasNext()) {
			ITypeBinding current = hierarchy.next();
			if (current.getQualifiedName().startsWith("java.")) {
				continue;
			}

			PropertyMatch match = findPropertyOnSingleType(current, propertyName, getterName, isName);
			if (match != null) {
				return match;
			}
		}

		return null;
	}

	/**
	 * Check a single type (no supertype walking) for a property match.
	 */
	private static @Nullable PropertyMatch findPropertyOnSingleType(
			ITypeBinding type, String propertyName, String getterName, String isName) {

		if (type.isRecord()) {
			for (IVariableBinding field : type.getDeclaredFields()) {
				if (field.getName().equals(propertyName)) {
					return new PropertyMatch(propertyName, propertyName, field.getType(), 1.0);
				}
			}
		}

		for (IMethodBinding method : type.getDeclaredMethods()) {
			if (method.getParameterTypes().length != 0) {
				continue;
			}
			String name = method.getName();
			if (getterName.equals(name) || isName.equals(name) || propertyName.equals(name)) {
				return new PropertyMatch(propertyName, name, method.getReturnType(), 1.0);
			}
		}

		return null;
	}

	/**
	 * Collect all properties on a type (including supertypes via
	 * {@link ASTUtils#getHierarchyTypesBreadthFirstIterator}) that meet the
	 * minimum Jaro-Winkler similarity threshold against the target name.
	 * Exact matches (similarity = 1.0) are excluded — use {@link #findExactProperty} instead.
	 */
	private static void collectProperties(ITypeBinding type, String targetName, double minSimilarity, List<PropertyMatch> matches) {
		Iterator<ITypeBinding> hierarchy = ASTUtils.getHierarchyTypesBreadthFirstIterator(type);

		while (hierarchy.hasNext()) {
			ITypeBinding current = hierarchy.next();
			if (current.getQualifiedName().startsWith("java.")) {
				continue;
			}

			if (current.isRecord()) {
				for (IVariableBinding field : current.getDeclaredFields()) {
					String fieldName = field.getName();
					double sim = JARO_WINKLER.apply(targetName.toLowerCase(), fieldName.toLowerCase());
					if (sim < 1.0 && sim >= minSimilarity) {
						matches.add(new PropertyMatch(fieldName, fieldName, field.getType(), sim));
					}
				}
			}

			for (IMethodBinding method : current.getDeclaredMethods()) {
				String mName = method.getName();
				if (method.getParameterTypes().length != 0) {
					continue;
				}
				String propName = null;
				if (mName.startsWith("get") && mName.length() > 3) {
					propName = Character.toLowerCase(mName.charAt(3)) + mName.substring(4);
				}
				else if (mName.startsWith("is") && mName.length() > 2) {
					propName = Character.toLowerCase(mName.charAt(2)) + mName.substring(3);
				}
				if (propName != null) {
					double sim = JARO_WINKLER.apply(targetName.toLowerCase(), propName.toLowerCase());
					if (sim < 1.0 && sim >= minSimilarity) {
						matches.add(new PropertyMatch(propName, mName, method.getReturnType(), sim));
					}
				}
			}
		}
	}

}
