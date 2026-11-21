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
package org.springframework.ide.vscode.boot.java.conditionals;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationAttributeCompletionProvider;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationAttributeProposal;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IType;

import reactor.util.function.Tuple2;

/**
 * Proposes fully qualified class names for the {@code name} attribute of {@code @ConditionalOnClass}.
 * The types are looked up via a fuzzy search on the project classpath (project sources and libraries,
 * without the JRE), using the text typed so far as the search term. Nothing is proposed for an empty
 * search term, to avoid listing the entire classpath.
 *
 * @author viniciusmachak
 */
public class ConditionalOnClassCompletionProcessor implements AnnotationAttributeCompletionProvider {

	private static final Logger log = LoggerFactory.getLogger(ConditionalOnClassCompletionProcessor.class);

	private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(2);

	/** binary names of anonymous and local classes, e.g. {@code com.example.Foo$1} or {@code com.example.Foo$1Local} */
	private static final Pattern ANONYMOUS_OR_LOCAL_CLASS = Pattern.compile(".*\\$\\d.*");

	@Override
	public List<AnnotationAttributeProposal> getCompletionCandidates(IJavaProject project, ASTNode node) {
		String searchTerm = getSearchTerm(node);
		if (searchTerm.isBlank()) {
			return Collections.emptyList();
		}

		try {
			List<Tuple2<IType, Double>> types = project.getIndex()
					.fuzzySearchTypes(searchTerm, true, false)
					.filter(match -> match.getT1() != null && match.getT1().getFullyQualifiedName() != null)
					.filter(match -> !ANONYMOUS_OR_LOCAL_CLASS.matcher(match.getT1().getFullyQualifiedName()).matches())
					// prefix matches all get the same score, sort them alphabetically for a stable order
					.collectSortedList(Comparator.comparing((Tuple2<IType, Double> match) -> match.getT2()).reversed()
							.thenComparing(match -> match.getT1().getFullyQualifiedName()))
					.block(SEARCH_TIMEOUT);

			if (types == null) {
				return Collections.emptyList();
			}

			return types.stream()
					.map(match -> createProposal(match.getT1().getFullyQualifiedName()))
					.distinct()
					.toList();
		}
		catch (Exception e) {
			log.warn("Searching types for '{}' failed: {}", searchTerm, e.getMessage());
			return Collections.emptyList();
		}
	}

	/**
	 * The completion provider interface does not pass along the prefix, so it is extracted from the node
	 * the completion was invoked on: either a string literal (e.g. {@code name = "Foo"}) or a plain name
	 * (e.g. {@code name = Foo}, {@code name = com.example.Foo}).
	 */
	private String getSearchTerm(ASTNode node) {
		if (node instanceof StringLiteral literal) {
			String value = ASTUtils.getLiteralValue(literal);
			return value == null ? "" : value;
		}
		else if (node instanceof SimpleName simpleName && "$missing$".equals(simpleName.getIdentifier())) {
			return "";
		}
		else if (node instanceof Name name) {
			return name.getFullyQualifiedName();
		}
		return "";
	}

	private AnnotationAttributeProposal createProposal(String fqName) {
		int lastDot = fqName.lastIndexOf('.');
		String simpleName = lastDot >= 0 ? fqName.substring(lastDot + 1) : fqName;
		String packageName = lastDot >= 0 ? fqName.substring(0, lastDot) : "";
		return new AnnotationAttributeProposal(simpleName, packageName, fqName);
	}

}
