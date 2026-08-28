/*******************************************************************************
 * Copyright (c) 2016, 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.boot.metadata;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.metadata.ValueProviderRegistry.ValueProviderStrategy;
import org.springframework.ide.vscode.boot.metadata.hints.StsValueHint;
import org.springframework.ide.vscode.commons.java.Flags;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IType;
import org.springframework.ide.vscode.commons.util.FuzzyMatcher;
import org.springframework.ide.vscode.commons.util.StringUtil;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * Provides the algorithm for 'class-reference' valueProvider.
 * <p>
 * See: https://github.com/spring-projects/spring-boot/blob/master/spring-boot-docs/src/main/asciidoc/appendix-configuration-metadata.adoc
 *
 * @author Kris De Volder
 * @author Alex Boyko
 */
public class ClassReferenceProvider extends CachingValueProvider {

	private static final Logger log = LoggerFactory.getLogger(ClassReferenceProvider.class);

	/**
	 * Default value for the 'concrete' parameter.
	 */
	private static final boolean DEFAULT_CONCRETE = true;

	private static final ClassReferenceProvider UNTARGETTED_INSTANCE = new ClassReferenceProvider(null, DEFAULT_CONCRETE, null);

	public static final Function<Map<String, Object>, ValueProviderStrategy> factory(SourceLinks sourceLinks) {
		long duration = 1;
		TimeUnit unit = TimeUnit.MINUTES;
		Cache<Map<String, Object>, ValueProviderStrategy> cache = CacheBuilder.newBuilder().expireAfterAccess(duration, unit).expireAfterWrite(duration, unit).build();
		return (params) -> {
			try {
				return cache.get(params, () -> {
					String target = getTarget(params);
					Boolean concrete = getConcrete(params);
					if (target != null || concrete != null) {
						if (concrete == null) {
							concrete = DEFAULT_CONCRETE;
						}
						return new ClassReferenceProvider(target, concrete, sourceLinks);
					}
					return UNTARGETTED_INSTANCE;
				});
			} catch (ExecutionException e) {
				log.error("", e);
				return null;
			}
		};
	}

	private static String getTarget(Map<String, Object> params) {
		if (params != null) {
			Object obj = params.get("target");
			if (obj instanceof String) {
				String target = (String) obj;
				if (StringUtil.hasText(target)) {
					return target;
				}
			}
		}
		return null;
	}

	private static boolean isAbstract(IType type) {
		try {
			return type.isInterface() || Flags.isAbstract(type.getFlags());
		} catch (Exception e) {
			log.error("", e);
			return false;
		}
	}

	private static Boolean getConcrete(Map<String, Object> params) {
		try {
			if (params != null) {
				Object obj = params.get("concrete");
				if (obj instanceof String) {
					String concrete = (String) obj;
					return Boolean.valueOf(concrete);
				} else if (obj instanceof Boolean) {
					return (Boolean) obj;
				}
			}
		} catch (Exception e) {
			log.error("", e);
		}
		return null;
	}

	/**
	 * Optional, fully qualified name of the 'target' type. Suggested hints should be a subtype of this type.
	 */
	private String target;

	/**
	 * Optional parameter, whether only concrete types should be suggested. Default value is true.
	 */
	private boolean concrete;

	private SourceLinks sourceLinks;

	private ClassReferenceProvider(String target, boolean concrete, SourceLinks sourceLinks) {
		this.target = target;
		this.concrete = concrete;
		this.sourceLinks = sourceLinks;
	}

	@Override
	protected Flux<StsValueHint> getValuesAsync(IJavaProject javaProject, String query) {
		Flux<Tuple2<IType, Double>> typesWithScoresFlux = Flux.empty();

		if (target == null) {
			typesWithScoresFlux = javaProject.getIndex().fuzzySearchTypes(query, true, false);
		} else {
			typesWithScoresFlux = javaProject.getIndex().allSubtypesOf(target, true, false)
					.filter(t -> Flags.isPublic(t.getFlags()) && !concrete || !isAbstract(t))
					.map(type -> Tuples.of(type, FuzzyMatcher.matchScore(query, type.getFullyQualifiedName())));
		}
		return typesWithScoresFlux.collectSortedList((o1, o2) -> o2.getT2().compareTo(o1.getT2()))
				.flatMapIterable(l -> l).map(t -> StsValueHint.create(sourceLinks, javaProject, t.getT1()));
	}

}
