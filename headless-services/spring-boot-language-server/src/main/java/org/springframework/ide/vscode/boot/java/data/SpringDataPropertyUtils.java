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
import java.util.Set;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
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
 * This class is intentionally separate from {@link AbstractSpringDataDomainTypeResolver},
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
		return findSimilarProperties(domainType, propertyName, minSimilarity, Set.of());
	}

	/**
	 * Overload using default minimum similarity threshold.
	 */
	public static List<PropertyMatch> findSimilarProperties(ITypeBinding domainType, String propertyName) {
		return findSimilarProperties(domainType, propertyName, DEFAULT_MIN_SIMILARITY, Set.of());
	}

	/**
	 * Find properties on the given type whose names are similar to
	 * {@code propertyName} but are <em>not</em> exact matches (similarity &lt; 1.0).
	 * Also considers annotated field names from annotations like {@code @Field} or
	 * {@code @Column}.
	 *
	 * @param domainType           the type to introspect
	 * @param propertyName         the (possibly misspelled) property name
	 * @param minSimilarity        minimum Jaro-Winkler similarity to consider (0.0–1.0)
	 * @param fieldAnnotationFqns  FQNs of annotations that map field names (e.g., {@code @Field}, {@code @Column})
	 * @return list of matches sorted by similarity (best first), never {@code null}
	 */
	public static List<PropertyMatch> findSimilarProperties(ITypeBinding domainType, String propertyName,
			double minSimilarity, Set<String> fieldAnnotationFqns) {
		if (propertyName == null || propertyName.isEmpty()) {
			return List.of();
		}
		List<PropertyMatch> matches = collectProperties(domainType, propertyName, minSimilarity, fieldAnnotationFqns);
		matches.removeIf(PropertyMatch::isExact);
		return matches;
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
		return resolvePropertyChain(domainType, dottedProperty, Set.of());
	}

	/**
	 * Resolve a dotted property chain against a root domain type, also considering
	 * annotated field names from the given annotations.
	 *
	 * @param domainType           the root domain type
	 * @param dottedProperty       the property string, possibly containing dots
	 * @param fieldAnnotationFqns  FQNs of annotations that map field names
	 * @return list of resolved chains; empty if no resolution is possible
	 */
	public static List<ResolvedChain> resolvePropertyChain(ITypeBinding domainType, String dottedProperty,
			Set<String> fieldAnnotationFqns) {
		if (dottedProperty == null || dottedProperty.isEmpty()) {
			return List.of();
		}

		String[] segments = dottedProperty.split("\\.");
		if (segments.length == 0) {
			return List.of();
		}

		List<ResolvedChain> results = new ArrayList<>();
		resolveSegments(domainType, segments, 0, new ArrayList<>(), 1.0, results, fieldAnnotationFqns);

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
			List<ResolvedChain> results,
			Set<String> fieldAnnotationFqns
	) {
		if (segmentIndex >= segments.length) {
			results.add(new ResolvedChain(List.copyOf(accumulated), scoreSoFar));
			return;
		}

		String segmentName = segments[segmentIndex];
		String typeFqn = currentType.getQualifiedName();

		List<PropertyMatch> matches = collectProperties(currentType, segmentName, DEFAULT_MIN_SIMILARITY, fieldAnnotationFqns);
		for (PropertyMatch match : matches) {
			accumulated.add(new PropertySegment(typeFqn, match.methodName()));
			resolveSegments(match.returnType(), segments, segmentIndex + 1, accumulated, scoreSoFar * match.similarity(), results, fieldAnnotationFqns);
			accumulated.remove(accumulated.size() - 1);
		}
	}

	/**
	 * Collect all properties on a type (including supertypes via
	 * {@link ASTUtils#getHierarchyTypesBreadthFirstIterator}) that meet the
	 * minimum Jaro-Winkler similarity threshold against the target name.
	 * Includes exact matches (similarity = 1.0). Returned list is sorted
	 * by similarity (best first).
	 * <p>
	 * When {@code fieldAnnotationFqns} is non-empty, also checks fields for
	 * annotations (e.g., {@code @Field("firstname")}, {@code @Column("col_name")})
	 * that map the Java property to a different database name. If the annotated
	 * name matches the target, the corresponding Java accessor is returned.
	 */
	private static List<PropertyMatch> collectProperties(ITypeBinding type, String targetName,
			double minSimilarity, Set<String> fieldAnnotationFqns) {
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

			// Annotation-aware matching: check @Field/@Column annotations on fields
			if (!fieldAnnotationFqns.isEmpty()) {
				for (IVariableBinding field : current.getDeclaredFields()) {
					String annotatedName = extractAnnotatedFieldName(field, fieldAnnotationFqns);
					if (annotatedName != null) {
						double sim = JARO_WINKLER.apply(targetLower, annotatedName.toLowerCase());
						if (sim >= minSimilarity) {
							String accessor = findAccessorForField(current, field);
							if (accessor != null) {
								matches.add(new PropertyMatch(annotatedName, accessor, field.getType(), sim));
							}
						}
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

	/**
	 * Extracts the annotated field name from a field's annotations, checking against
	 * the given set of annotation FQNs. Returns the {@code value()} of the first
	 * matching annotation, or {@code null} if none found.
	 */
	private static String extractAnnotatedFieldName(IVariableBinding field, Set<String> annotationFqns) {
		for (IAnnotationBinding ann : field.getAnnotations()) {
			ITypeBinding annType = ann.getAnnotationType();
			if (annType != null && annotationFqns.contains(annType.getQualifiedName())) {
				// Extract the value() or name() member
				for (IMemberValuePairBinding pair : ann.getAllMemberValuePairs()) {
					String memberName = pair.getName();
					if ("value".equals(memberName) || "name".equals(memberName)) {
						Object val = pair.getValue();
						if (val instanceof String s && !s.isEmpty()) {
							return s;
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Finds the accessor method name for a field. For records, the accessor is
	 * the field name itself. For classes, looks for {@code getXxx()} or {@code isXxx()}.
	 */
	private static String findAccessorForField(ITypeBinding declaringType, IVariableBinding field) {
		String fieldName = field.getName();

		if (declaringType.isRecord()) {
			return fieldName;
		}

		String expectedGetter = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
		String expectedIs = "is" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

		for (IMethodBinding method : declaringType.getDeclaredMethods()) {
			if (method.getParameterTypes().length != 0) {
				continue;
			}
			String mName = method.getName();
			if (mName.equals(expectedGetter) || mName.equals(expectedIs)) {
				return mName;
			}
		}
		return null;
	}

}
