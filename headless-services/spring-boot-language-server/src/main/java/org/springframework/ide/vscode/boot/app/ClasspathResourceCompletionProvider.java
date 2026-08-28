/*******************************************************************************
 * Copyright (c) 2020, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.app;

import java.util.Collection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ide.vscode.boot.common.PropertyCompletionFactory;
import org.springframework.ide.vscode.boot.metadata.CachingValueProvider;
import org.springframework.ide.vscode.boot.metadata.hints.StsValueHint;
import org.springframework.ide.vscode.boot.metadata.hints.ValueHintHoverInfo;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.completion.DocumentEdits;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionEngine;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposal;
import org.springframework.ide.vscode.commons.languageserver.completion.InternalCompletionList;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.LanguageSpecific;
import org.springframework.ide.vscode.commons.languageserver.util.PrefixFinder;
import org.springframework.ide.vscode.commons.util.FuzzyMatcher;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;

@Component
public class ClasspathResourceCompletionProvider implements ICompletionEngine, LanguageSpecific {

	private static String[] CLASSPATH_PREFIXES = {
			"classpath:",
			"classpath*:"
	};
	
	private static PrefixFinder PREFIX_FINDER = new PrefixFinder() {
		@Override
		protected boolean isPrefixChar(char c) {
			return Character.isJavaIdentifierPart(c) || c == '-' || c == '.' || c == '/' || c == ':' || c == '*';
		}
	};
	private static final Collection<LanguageId> LANGUAGES = ImmutableList.of(
			LanguageId.BOOT_PROPERTIES, 
			LanguageId.BOOT_PROPERTIES_YAML
	);

	@Autowired JavaProjectFinder projectFinder;

	public ClasspathResourceCompletionProvider(BootLanguageServerParams params) {
	}
	
	private static class ClasspathHints extends CachingValueProvider {
		@Override
		protected Flux<StsValueHint> getValuesAsync(IJavaProject javaProject, String query) {
			return Flux.fromStream(
				IClasspathUtil.getClasspathResources(javaProject.getClasspath()).stream()
				.distinct().map(r -> r.replaceAll("\\\\", "/"))
				.map(StsValueHint::create)
			);
		}
	}

	private ClasspathHints classpathHints = new ClasspathHints();
	private PropertyCompletionFactory completionFactory = new PropertyCompletionFactory();
	
	@Override
	public InternalCompletionList getCompletions(TextDocument doc, int offset) {
		ImmutableList.Builder<ICompletionProposal> proposals = ImmutableList.builder();
		IJavaProject jp = projectFinder.find(doc.getId()).orElse(null);
		if (jp != null) {
			String prefix = PREFIX_FINDER.getPrefix(doc, offset);
			for (String CLASSPATH : CLASSPATH_PREFIXES) {
				if (prefix.startsWith(CLASSPATH)) {
					String query = prefix.substring(CLASSPATH.length());
					Flux<StsValueHint> valueHints = classpathHints.getValues(jp, query);
					valueHints.toStream().forEach(hint -> {
						String valueCandidate = hint.getValue();
						int startOfValue = offset - query.length();
						double score = FuzzyMatcher.matchScore(query, valueCandidate);
						if (score != 0) {
							DocumentEdits edits = new DocumentEdits(doc, false);
							edits.delete(startOfValue, offset);
							edits.insert(offset, valueCandidate);
							proposals.add(completionFactory.valueProposal(valueCandidate, query, "String",
									score, edits, ValueHintHoverInfo.create(hint))
							);
						}
					});
				}
			}
		}
		return new InternalCompletionList(proposals.build(), false);
	}

	@Override
	public Collection<LanguageId> supportedLanguages() {
		return LANGUAGES;
	}

}
