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
	// Fuzzy property lookup (Jaro-Winkler similarity)
	// =====================================================================

	/**
	 * Find properties on the given type whose names are similar to
	 * {@code propertyName} but are <em>not</em> exact matches (similarity &lt; 1.0).
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
		List<PropertyMatch> matches = collectProperties(domainType, propertyName, minSimilarity);
		matches.removeIf(PropertyMatch::isExact);
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

		// Unified: collect all matches (exact + fuzzy) sorted best-first.
		// Exact matches have similarity 1.0 and naturally float to the top.
		// The caller (resolvePropertyChain) filters exact-only chains when available.
		List<PropertyMatch> matches = collectProperties(currentType, segmentName, DEFAULT_MIN_SIMILARITY);
		for (PropertyMatch match : matches) {
			accumulated.add(new PropertySegment(typeFqn, match.methodName()));
			resolveSegments(match.returnType(), segments, segmentIndex + 1, accumulated, scoreSoFar * match.similarity(), results);
			accumulated.remove(accumulated.size() - 1);
		}
	}

	/**
	 * Collect all properties on a type (including supertypes via
	 * {@link ASTUtils#getHierarchyTypesBreadthFirstIterator}) that meet the
	 * minimum Jaro-Winkler similarity threshold against the target name.
	 * Includes exact matches (similarity = 1.0). Returned list is sorted
	 * by similarity (best first).
	 */
	private static List<PropertyMatch> collectProperties(ITypeBinding type, String targetName, double minSimilarity) {
		List<PropertyMatch> matches = new ArrayList<>();
		Iterator<ITypeBinding> hierarchy = ASTUtils.getHierarchyTypesBreadthFirstIterator(type);
		String targetLower = targetName.toLowerCase();

		while (hierarchy.hasNext()) {
			ITypeBinding current = hierarchy.next();
			if (current.getQualifiedName().startsWith("java.")) {
				continue;
			}

			if (current.isRecord()) {
				for (IVariableBinding field : current.getDeclaredFields()) {
					String fieldName = field.getName();
					double sim = JARO_WINKLER.apply(targetLower, fieldName.toLowerCase());
					if (sim >= minSimilarity) {
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
					double sim = JARO_WINKLER.apply(targetLower, propName.toLowerCase());
					if (sim >= minSimilarity) {
						matches.add(new PropertyMatch(propName, mName, method.getReturnType(), sim));
					}
				}
			}
		}

		matches.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));
		return matches;
	}

}
