/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.annotations;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.internal.compiler.problem.AbortCompilation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.JavaUtils;

import com.google.common.collect.ImmutableList;

/**
 * Utility class for working with annotation and discovering / understanding their
 * 'inheritance' structure.
 * <p>
 * Provides methods to ask questions about inheritance between annotations.
 *
 * @author Kris De Volder
 * @author Alex Boyko
 * @author Martin Lippert
 */
public class AnnotationHierarchies {
	
	private static final Logger log = LoggerFactory.getLogger(AnnotationHierarchies.class);
	
	private static final String CU_PROPERTY_KEY = AnnotationHierarchies.class.getName();
	
	private final ConcurrentHashMap<String, AnnotationTypeInformation> cache;
	private final Predicate<ITypeBinding> filter;

	@SuppressWarnings("unchecked")
	public static <T extends AnnotationHierarchies> T get(ASTNode n) {
		return (T) n.getRoot().getProperty(CU_PROPERTY_KEY);
	}
	
	public static boolean set(ASTNode n, AnnotationHierarchies annotations) {
		ASTNode root = n.getRoot();
		AnnotationHierarchies a = get(root);
		if (a == null) {
			root.setProperty(CU_PROPERTY_KEY, annotations);
			return true;
		}
		return false;
	}
	
	public AnnotationHierarchies(Predicate<ITypeBinding> filter) {
		this.cache = new ConcurrentHashMap<>();
		this.filter = filter;
	}
	
	public AnnotationHierarchies() {
		this(tb -> !tb.getKey().startsWith("Ljava/")); //mostly intended to capture java.lang.annotation.* types. But really it should be
		//safe to ignore any type defined by the JRE since it can't possibly be inheriting from a spring annotation.
	}
	
	/**
	 * Computes annotations only annotating this binding.
	 * 
	 * @param binding binding
	 * @return list of annotations
	 */
	// this lock is used to protect multi-threaded access to this helper class
	// due to https://bugs.eclipse.org/bugs/show_bug.cgi?id=571247
	synchronized List<IAnnotationBinding> getDirectSuperAnnotationBindings(IBinding binding) {
		try {
			if (binding != null) {
				IAnnotationBinding[] annotations = binding.getAnnotations();
				if (annotations.length != 0) {
					ImmutableList.Builder<IAnnotationBinding> superAnnotations = ImmutableList.builder();
					for (IAnnotationBinding ab : annotations) {
						ITypeBinding sa = ab.getAnnotationType();
						if (sa != null) {
							if (accept(sa)) {
								superAnnotations.add(ab);
							}
						}
					}
					return superAnnotations.build();
				}
			}
		}
		catch (AbortCompilation e) {
			log.debug("compilation aborted ", e);
			// ignore this, it is most likely caused by broken source code, a broken classpath, or some optional dependencies not being on the classpath
		}
	
		return ImmutableList.of();
	}
	
	private boolean accept(ITypeBinding tb) {
		return filter == null || filter.test(tb);
	}

	/**
	 * Produces the iterator over all annotations hierarchy for the binding. If the
	 * binding happens to be {@link IAnnotationBinding} it'll be included in the
	 * hierarchy
	 * 
	 * @param binding the binding
	 * @return iterator over annotations hierarchy
	 */
	public Iterator<IAnnotationBinding> iterator(IBinding binding) {
		return iterator(binding, new LinkedHashSet<>());
	}
	
	private Iterator<IAnnotationBinding> iterator(IBinding binding, Set<String> seen) {
		return new Iterator<>() {
			
			private Queue<IAnnotationBinding> queue = new ArrayDeque<>(10);

			{
				if (binding instanceof IAnnotationBinding ab) {
					seen.add(keyOf(ab.getAnnotationType()));
					queue.add(ab);
				} else {
					if (binding instanceof ITypeBinding tb && tb.isAnnotation()) {
						seen.add(keyOf(tb));
					}
					for (IAnnotationBinding ab :  getDirectSuperAnnotationBindings(binding)) {
						seen.add(keyOf(ab.getAnnotationType()));
						queue.add(ab);
					}
				}
			}

			@Override
			public boolean hasNext() {
				return !queue.isEmpty();
			}

			@Override
			public IAnnotationBinding next() {
				IAnnotationBinding next = queue.poll();
				for (IAnnotationBinding a : getDirectSuperAnnotationBindings(next.getAnnotationType())) {
					String key = keyOf(a.getAnnotationType());
					if (seen.add(key)) {
						queue.add(a);
					}
				}
				return next;
			}
			
		};
	}
	
	/**
	 * The list of all annotations within the hierarchy.
	 * Note: for searching purposes it is best to use {@link #iterator(IBinding)}
	 * 
	 * @param binding
	 * @return the list of annotations in the hierarchy
	 */
	public List<IAnnotationBinding> getAllAnnotations(IBinding binding) {
		if (binding != null) {
			List<IAnnotationBinding> annotations = new ArrayList<>();
			for (Iterator<IAnnotationBinding> itr = iterator(binding); itr.hasNext();) {
				annotations.add(itr.next());
			}
			return annotations;
		}
		return Collections.emptyList();
	}
	
	private Optional<AnnotationTypeInformation> annotationInfo(ITypeBinding typeBinding) {
		if (accept(typeBinding)) {
			return Optional.of(cache.computeIfAbsent(keyOf(typeBinding), s -> compute(typeBinding)));
		}
		return Optional.empty();
	}
	
	private AnnotationTypeInformation compute(ITypeBinding typeBinding) {
		Set<String> inherited = new LinkedHashSet<>();
		// Iterating over all inherited annotations must fill in the `inherited` set with annotation binding keys
		for (Iterator<IAnnotationBinding> itr = iterator(typeBinding, inherited); itr.hasNext(); itr.next()) {
			// nothing to do
		} 
		// remove itself from `inherited` set as we'd like to leave only inherited annotations binding keys in the set 
		String key = keyOf(typeBinding);
		inherited.remove(key);
		return new AnnotationTypeInformation(key, inherited);
	}
	
	public boolean isAnnotatedWithAnnotationByBindingKey(IBinding binding, Predicate<String> bindingKeyTest) {
		if (binding instanceof IAnnotationBinding ab) {
			return annotationInfo(ab.getAnnotationType()).map(info -> info.inherits(bindingKeyTest)).orElse(false);
		} else if (binding instanceof ITypeBinding tb && tb.isAnnotation()) {
			return annotationInfo(tb).map(info -> info.inherits(bindingKeyTest)).orElse(false);
		} else {
			for (IAnnotationBinding ab : getDirectSuperAnnotationBindings(binding)) {
				if (isAnnotatedWithAnnotationByBindingKey(ab.getAnnotationType(), bindingKeyTest)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean isAnnotatedWithAnnotationByBindingKey(IBinding binding, String annotationBindingKey) {
		if (binding instanceof IAnnotationBinding ab) {
			return annotationInfo(ab.getAnnotationType()).map(info -> info.inherits(annotationBindingKey)).orElse(false);
		} else if (binding instanceof ITypeBinding tb && tb.isAnnotation()) {
			return annotationInfo(tb).map(info -> info.inherits(annotationBindingKey)).orElse(false);
		} else {
			for (IAnnotationBinding ab : getDirectSuperAnnotationBindings(binding)) {
				if (isAnnotatedWithAnnotationByBindingKey(ab.getAnnotationType(), annotationBindingKey)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * The key of an annotation type, as {@link JavaUtils#typeFqNameToBindingKey(String)} builds it
	 * from the type's name. JDT's own key for a type read from source cannot be compared against
	 * that on Windows: it carries the source file's path ({@code Lp/C:\...\p\A~A;}), as JDT
	 * looks for the file's main type name after the last '/' while the batch compiler hands it a
	 * '\'-separated path.
	 */
	public static String keyOf(ITypeBinding type) {
		String binaryName = type.getErasure().getBinaryName();
		return binaryName != null ? JavaUtils.typeFqNameToBindingKey(binaryName) : type.getKey();
	}

	public boolean isAnnotatedWith(IBinding binding, String annotationTypeFqn) {
		return isAnnotatedWithAnnotationByBindingKey(binding, JavaUtils.typeFqNameToBindingKey(annotationTypeFqn));
	}
	
}
