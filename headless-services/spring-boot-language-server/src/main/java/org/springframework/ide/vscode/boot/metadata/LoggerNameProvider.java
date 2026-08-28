/*******************************************************************************
 * Copyright (c) 2016, 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.boot.metadata;

import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;
import java.util.function.Function;

import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.metadata.ValueProviderRegistry.ValueProviderStrategy;
import org.springframework.ide.vscode.boot.metadata.hints.StsValueHint;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.util.FuzzyMatcher;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;

import reactor.core.publisher.Flux;
import reactor.util.function.Tuples;

/**
 * Provides the algorithm for 'logger-name' valueProvider.
 * <p>
 * See: https://github.com/spring-projects/spring-boot/blob/master/spring-boot-docs/src/main/asciidoc/appendix-configuration-metadata.adoc
 *
 * @author Kris De Volder
 * @author Alex Boyko
 */
public class LoggerNameProvider implements ValueProviderStrategy {

	private static final String LOGGING_GROUPS_PREFIX = "logging.group.";
	private final ProjectBasedPropertyIndexProvider adhocProperties;
	private final boolean includeGroups;
	private final SourceLinks sourceLinks;

	public LoggerNameProvider(ProjectBasedPropertyIndexProvider adhocProperties, boolean includeGroups, SourceLinks sourceLinks) {
		this.adhocProperties = adhocProperties;
		this.includeGroups = includeGroups;
		this.sourceLinks = sourceLinks;
	}

	public static final Function<Map<String, Object>, ValueProviderStrategy> factory(ProjectBasedPropertyIndexProvider adhocProperties, SourceLinks sourceLinks) {
		return (params) -> {
			return new LoggerNameProvider(adhocProperties, (boolean) params.getOrDefault("group", true), sourceLinks);
		};
	}

	@Override
	public Flux<StsValueHint> getValues(IJavaProject javaProject, String query) {
		return getValuesAsync(javaProject, query);
	}

	Collection<String> loggerGroupNames(IJavaProject jp) {
		Builder<String> builder = ImmutableSet.builder();
		if (adhocProperties != null && includeGroups) {
			SortedMap<String, PropertyInfo> index = adhocProperties.getIndex(jp).getTreeMap();
			index = index.subMap(LOGGING_GROUPS_PREFIX, LOGGING_GROUPS_PREFIX+Character.MAX_VALUE);
			for (String prop : index.keySet()) {
				if (prop.startsWith(LOGGING_GROUPS_PREFIX)) {
					String groupName = prop.substring(LOGGING_GROUPS_PREFIX.length());
					int bracket = groupName.indexOf('[');
					if (bracket >= 0) {
						groupName = groupName.substring(0, bracket);
					}
					builder.add(groupName);
				}
			}
		}
		return builder.build();
	}

	private Flux<StsValueHint> getValuesAsync(IJavaProject javaProject, String query) {
		return Flux.concat(
			Flux.fromIterable(loggerGroupNames(javaProject))
				.map(loggerName -> Tuples.of(StsValueHint.create(loggerName), FuzzyMatcher.matchScore(query, loggerName)))
				.filter(t -> t.getT2() != 0.0),
			javaProject.getIndex()
				.fuzzySearchPackages(query, true, false)
				.map(t -> Tuples.of(StsValueHint.create(t.getT1()), t.getT2())),
			javaProject.getIndex()
				.fuzzySearchTypes(query, true, false)
				.map(t -> Tuples.of(StsValueHint.create(sourceLinks, javaProject, t.getT1()), t.getT2()))
			)
		.collectSortedList((o1, o2) -> o2.getT2().compareTo(o1.getT2()))
		.flatMapIterable(l -> l)
		.map(t -> t.getT1())
		.distinct(h -> h.getValue());
	}

}
