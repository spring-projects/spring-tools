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
package org.springframework.ide.vscode.boot.kotlin.jdt;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedAnnotation;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedMemberValuePair;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedType;
import org.springframework.ide.vscode.boot.kotlin.bindings.ResolvedVariable;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypeResolver;

/**
 * Creates the JDT bindings for what the Kotlin type resolution resolved, one set per Kotlin file.
 * The bindings are thin adapters over the resolved model of the Kotlin side
 * ({@link ResolvedType} and friends), types are cached by their key, so the same type is the same
 * binding object within a file, as with JDT.
 * <p>
 * The JDT binding interfaces are much larger than what the Kotlin model knows. Methods that cannot
 * be answered return what JDT returns when it knows nothing (null, false, empty arrays) and are
 * recorded with {@link #gap(String)}, so it can be seen which parts of the JDT surface the indexers
 * running on Kotlin files really use.
 *
 * @author Martin Lippert
 */
public final class KotlinJdtBindings {

	private static final Logger log = LoggerFactory.getLogger(KotlinJdtBindings.class);

	private static final Set<String> GAPS = ConcurrentHashMap.newKeySet();

	static final ITypeBinding[] NO_TYPES = new ITypeBinding[0];
	static final IAnnotationBinding[] NO_ANNOTATIONS = new IAnnotationBinding[0];

	private final KotlinTypeResolver resolver;
	private final Map<String, KotlinTypeBinding> types = new ConcurrentHashMap<>();

	public KotlinJdtBindings(KotlinTypeResolver resolver) {
		this.resolver = resolver;
	}

	KotlinTypeResolver resolver() {
		return resolver;
	}

	/** the JDT binding for a type of the Kotlin model, null for null */
	public ITypeBinding type(ResolvedType type) {
		return type != null ? types.computeIfAbsent(type.getKey(), key -> new KotlinTypeBinding(this, type)) : null;
	}

	ITypeBinding[] types(List<ResolvedType> types) {
		return types.stream().map(this::type).toArray(ITypeBinding[]::new);
	}

	/** the JDT binding for an annotation of the Kotlin model, null for null */
	public IAnnotationBinding annotation(ResolvedAnnotation annotation) {
		return annotation != null && annotation.getAnnotationType() != null ? new KotlinAnnotationBinding(this, annotation) : null;
	}

	IAnnotationBinding[] annotations(List<ResolvedAnnotation> annotations) {
		return annotations.stream().map(this::annotation).filter(a -> a != null).toArray(IAnnotationBinding[]::new);
	}

	/** the JDT binding for an annotation attribute of the Kotlin model */
	public IMemberValuePairBinding memberValuePair(ResolvedMemberValuePair pair) {
		return new KotlinMemberValuePairBinding(this, pair);
	}

	IVariableBinding variable(ResolvedVariable variable) {
		return variable != null ? new KotlinVariableBinding(this, variable) : null;
	}

	/** an attribute value of the Kotlin model, as JDT represents it in an {@code IMemberValuePairBinding} */
	Object value(Object value) {
		if (value instanceof ResolvedType type) {
			return type(type);
		}
		else if (value instanceof ResolvedVariable variable) {
			return variable(variable);
		}
		else if (value instanceof ResolvedAnnotation annotation) {
			return annotation(annotation);
		}
		else if (value instanceof Object[] values) {
			Object[] result = new Object[values.length];
			for (int i = 0; i < values.length; i++) {
				result[i] = value(values[i]);
			}
			return result;
		}
		return value;
	}

	/**
	 * Records that a JDT binding method was called that the Kotlin bindings cannot answer (yet).
	 */
	static void gap(String method) {
		if (GAPS.add(method)) {
			log.debug("JDT binding method used on Kotlin bindings, without a real answer: {}", method);
		}
	}

	/** the binding methods that were called without a real answer, since the start or the last reset */
	public static Set<String> gaps() {
		return Set.copyOf(GAPS);
	}

	public static void resetGaps() {
		GAPS.clear();
	}

}
