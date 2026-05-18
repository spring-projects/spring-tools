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
package org.springframework.ide.vscode.boot.java.requestmapping;

import java.util.List;

import org.eclipse.jdt.core.dom.ITypeBinding;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;

/**
 * Represents a predicate that controls which handler types a path prefix applies to.
 * Corresponds to the second argument of {@code PathMatchConfigurer.addPathPrefix(String, Predicate)}.
 *
 * <p>Implementations are sealed records, making the full set of predicate shapes
 * explicit and enabling exhaustive pattern-matching switch expressions in consumers
 * such as {@link WebConfigCodeLensProvider}.</p>
 */
public sealed interface PathPrefixPredicate
		permits PathPrefixPredicate.AnnotationPredicate,
				PathPrefixPredicate.BasePackagePredicate,
				PathPrefixPredicate.AssignableTypePredicate,
				PathPrefixPredicate.AndPredicate,
				PathPrefixPredicate.OrPredicate,
				PathPrefixPredicate.NegatePredicate,
				PathPrefixPredicate.AnyPredicate,
				PathPrefixPredicate.UnknownPredicate {

	/**
	 * Returns {@code true} if the given type matches this predicate.
	 *
	 * @param typeBinding           the type to test
	 * @param annotationHierarchies the annotation hierarchy context for the containing compilation unit
	 */
	boolean matches(ITypeBinding typeBinding, AnnotationHierarchies annotationHierarchies);

	/**
	 * Matches classes annotated with any of the specified annotations
	 * (meta-annotation hierarchies are respected).
	 * Corresponds to {@code HandlerTypePredicate.forAnnotation(Class...)} and
	 * {@code HandlerTypePredicate.forMappedAnnotation(Class...)}.
	 */
	record AnnotationPredicate(List<String> annotationTypes) implements PathPrefixPredicate {
		@Override
		public boolean matches(ITypeBinding typeBinding, AnnotationHierarchies annotationHierarchies) {
			return annotationTypes.stream()
					.anyMatch(ann -> annotationHierarchies.isAnnotatedWith(typeBinding, ann));
		}
	}

	/**
	 * Matches classes whose package name starts with any of the given package prefixes.
	 * Corresponds to {@code HandlerTypePredicate.forBasePackage(String...)}.
	 */
	record BasePackagePredicate(List<String> packages) implements PathPrefixPredicate {
		@Override
		public boolean matches(ITypeBinding typeBinding, AnnotationHierarchies annotationHierarchies) {
			ITypeBinding outermost = outermostType(typeBinding);
			String qualifiedName = outermost.getQualifiedName();
			return packages.stream().anyMatch(pkg ->
					qualifiedName.startsWith(pkg + ".") || qualifiedName.equals(pkg));
		}

		private static ITypeBinding outermostType(ITypeBinding tb) {
			ITypeBinding result = tb;
			while (result.getDeclaringClass() != null) {
				result = result.getDeclaringClass();
			}
			return result;
		}
	}

	/**
	 * Matches classes that are assignable to any of the given type names.
	 * Corresponds to {@code HandlerTypePredicate.forAssignableType(Class...)}.
	 */
	record AssignableTypePredicate(List<String> typeNames) implements PathPrefixPredicate {
		@Override
		public boolean matches(ITypeBinding typeBinding, AnnotationHierarchies annotationHierarchies) {
			return typeNames.stream().anyMatch(typeName -> isAssignableTo(typeBinding, typeName));
		}

		private static boolean isAssignableTo(ITypeBinding tb, String targetFqn) {
			if (tb == null) return false;
			if (targetFqn.equals(tb.getQualifiedName())) return true;
			ITypeBinding superclass = tb.getSuperclass();
			if (isAssignableTo(superclass, targetFqn)) return true;
			for (ITypeBinding iface : tb.getInterfaces()) {
				if (isAssignableTo(iface, targetFqn)) return true;
			}
			return false;
		}
	}

	/**
	 * Logical AND of two predicates. Matches only when both sub-predicates match.
	 * Corresponds to {@code predicate.and(other)}.
	 */
	record AndPredicate(PathPrefixPredicate left, PathPrefixPredicate right) implements PathPrefixPredicate {
		@Override
		public boolean matches(ITypeBinding typeBinding, AnnotationHierarchies annotationHierarchies) {
			return left.matches(typeBinding, annotationHierarchies)
					&& right.matches(typeBinding, annotationHierarchies);
		}
	}

	/**
	 * Logical OR of two predicates. Matches when either sub-predicate matches.
	 * Corresponds to {@code predicate.or(other)}.
	 */
	record OrPredicate(PathPrefixPredicate left, PathPrefixPredicate right) implements PathPrefixPredicate {
		@Override
		public boolean matches(ITypeBinding typeBinding, AnnotationHierarchies annotationHierarchies) {
			return left.matches(typeBinding, annotationHierarchies)
					|| right.matches(typeBinding, annotationHierarchies);
		}
	}

	/**
	 * Logical negation of a predicate. Matches when the inner predicate does not match.
	 * Corresponds to {@code predicate.negate()}.
	 */
	record NegatePredicate(PathPrefixPredicate inner) implements PathPrefixPredicate {
		@Override
		public boolean matches(ITypeBinding typeBinding, AnnotationHierarchies annotationHierarchies) {
			return !inner.matches(typeBinding, annotationHierarchies);
		}
	}

	/**
	 * Matches every class unconditionally.
	 * Used when the source code supplies a lambda predicate or no predicate at all.
	 */
	record AnyPredicate() implements PathPrefixPredicate {
		@Override
		public boolean matches(ITypeBinding typeBinding, AnnotationHierarchies annotationHierarchies) {
			return true;
		}
	}

	/**
	 * Fallback for predicate expressions that could not be parsed statically.
	 * Treated as matching every class so that code lenses are not silently suppressed.
	 */
	record UnknownPredicate() implements PathPrefixPredicate {
		@Override
		public boolean matches(ITypeBinding typeBinding, AnnotationHierarchies annotationHierarchies) {
			return true;
		}
	}
}
