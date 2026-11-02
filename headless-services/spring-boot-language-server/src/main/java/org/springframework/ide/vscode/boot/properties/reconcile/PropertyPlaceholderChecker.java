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
package org.springframework.ide.vscode.boot.properties.reconcile;

import java.util.Set;
import java.util.function.Function;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.springframework.ide.vscode.boot.java.embedded.lang.AntlrReconciler;
import org.springframework.ide.vscode.boot.metadata.ProjectBasedPropertyIndexProvider;
import org.springframework.ide.vscode.boot.metadata.PropertyInfo;
import org.springframework.ide.vscode.boot.metadata.SpringPropertyIndex;
import org.springframework.ide.vscode.boot.metadata.SpringPropertyIndexProvider;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.util.FuzzyMap;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.util.text.IRegion;
import org.springframework.ide.vscode.commons.util.text.Region;
import org.springframework.ide.vscode.parser.placeholder.PropertyPlaceHolderLexer;
import org.springframework.ide.vscode.parser.placeholder.PropertyPlaceHolderParser;

/**
 * Checks <code>${...}</code> placeholders in property values: reports syntax
 * errors and warns when a placeholder without default is not defined anywhere
 * in the project. Shared by the properties, yaml and Java reconcilers.
 *
 * @author Seema Jagadeesh
 */
public class PropertyPlaceholderChecker {

	/** Keys the JVM or Spring Boot itself provides at runtime, so they are never reported. */
	private static final String[] RUNTIME_PREFIXES = {
			"random.", "user.", "java.", "os.", "file.", "line.", "path.",
			"LOG_", "CONSOLE_LOG_", "FILE_LOG_", "LOGGED_APPLICATION_"
	};
	private static final Set<String> RUNTIME_KEYS = Set.of("PID");

	/**
	 * Tells whether a placeholder key is defined anywhere the language server
	 * can see. One instance per document, so each index is fetched at most once.
	 */
	public interface KeyLookup {
		boolean isDefined(String key);
	}

	private final ProblemType syntaxProblemType;
	private final ProblemType unresolvedProblemType;
	private final AntlrReconciler syntaxReconciler;
	private final SpringPropertyIndexProvider indexProvider;
	private final ProjectBasedPropertyIndexProvider adHocIndexProvider;
	private final JavaProjectFinder projectFinder;

	public PropertyPlaceholderChecker(ProblemType syntaxProblemType, ProblemType unresolvedProblemType,
			SpringPropertyIndexProvider indexProvider, ProjectBasedPropertyIndexProvider adHocIndexProvider,
			JavaProjectFinder projectFinder) {
		this.syntaxProblemType = syntaxProblemType;
		this.unresolvedProblemType = unresolvedProblemType;
		this.syntaxReconciler = new AntlrReconciler("Placeholder", PropertyPlaceHolderParser.class,
				PropertyPlaceHolderLexer.class, "start", syntaxProblemType);
		this.indexProvider = indexProvider;
		this.adHocIndexProvider = adHocIndexProvider;
		this.projectFinder = projectFinder;
	}

	/**
	 * Checks every placeholder in the raw value text that starts at
	 * <code>textOffset</code> in <code>doc</code>. <code>localKeys</code> are
	 * the keys defined in the document itself.
	 */
	public void check(IDocument doc, String text, int textOffset, Set<String> localKeys, IProblemCollector problems) {
		check(text, r -> new Region(textOffset + r.getOffset(), r.getLength()), lookupFor(doc, localKeys), problems);
	}

	/**
	 * Checks every placeholder in <code>text</code>. Positions are relative to
	 * the text and go through <code>mapper</code>, so the text may come from a
	 * Java literal with escapes.
	 */
	public void check(String text, Function<IRegion, IRegion> mapper, KeyLookup lookup, IProblemCollector problems) {
		int start = text.indexOf("${");
		while (start >= 0) {
			int bodyStart = start + 2;
			if (start > 0 && text.charAt(start - 1) == '\\') {
				// Escaped placeholder (Boot 3.4+), left as literal text
				start = text.indexOf("${", bodyStart);
				continue;
			}
			int end = findClosingBrace(text, bodyStart);
			if (end < 0) {
				report(problems, syntaxProblemType, "Placeholder is not closed, expected '}'", mapper, start, text.length() - start);
				return;
			}
			String body = text.substring(bodyStart, end);
			if (body.isBlank()) {
				report(problems, syntaxProblemType, "Placeholder is empty", mapper, start, end + 1 - start);
			} else {
				checkPlaceholder(body, bodyStart, mapper, lookup, problems);
			}
			// Continue inside the body so nested placeholders are checked too
			start = text.indexOf("${", bodyStart);
		}
	}

	/** Lookup for a properties or yaml document. <code>localKeys</code> are the keys the document defines itself. */
	public KeyLookup lookupFor(IDocument doc, Set<String> localKeys) {
		return new IndexLookup(localKeys) {
			@Override
			FuzzyMap<PropertyInfo> metadata() {
				return indexProvider.getIndex(doc).getProperties();
			}

			@Override
			FuzzyMap<PropertyInfo> adHoc() {
				IJavaProject project = projectFinder.find(new TextDocumentIdentifier(doc.getUri())).orElse(null);
				return adHocIndexProvider.getIndex(project);
			}
		};
	}

	/** Lookup for a Java source of <code>project</code>, which defines no keys of its own. */
	public KeyLookup lookupFor(IJavaProject project) {
		return new IndexLookup(Set.of()) {
			@Override
			FuzzyMap<PropertyInfo> metadata() {
				return indexProvider.getIndex(project).getProperties();
			}

			@Override
			FuzzyMap<PropertyInfo> adHoc() {
				return adHocIndexProvider.getIndex(project);
			}
		};
	}

	/** Index of the matching '}', skipping nested braces, or -1 if unclosed. */
	private int findClosingBrace(String text, int bodyStart) {
		int depth = 1;
		for (int i = bodyStart; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '{') {
				depth++;
			} else if (c == '}') {
				depth--;
				if (depth == 0) {
					return i;
				}
			}
		}
		return -1;
	}

	private void checkPlaceholder(String body, int bodyOffset, Function<IRegion, IRegion> mapper, KeyLookup lookup, IProblemCollector problems) {
		ProblemCounter counter = new ProblemCounter(problems);
		syntaxReconciler.reconcile(body, r -> mapper.apply(new Region(bodyOffset + r.getOffset(), r.getLength())), counter);
		if (counter.count > 0) {
			return;
		}
		if (body.indexOf(':') >= 0) {
			return; // has a default value, resolves at runtime
		}
		String key = body.trim();
		if (key.contains("${") || isRuntimeKey(key) || lookup.isDefined(key)) {
			return;
		}
		report(problems, unresolvedProblemType,
				"Placeholder '" + key + "' has no default value and could not be found in the project's configuration. Make sure it is defined at runtime.",
				mapper, bodyOffset + body.indexOf(key), key.length());
	}

	private static void report(IProblemCollector problems, ProblemType type, String message, Function<IRegion, IRegion> mapper, int offset, int length) {
		IRegion region = mapper.apply(new Region(offset, length));
		problems.accept(new ReconcileProblemImpl(type, message, region.getOffset(), region.getLength()));
	}

	private boolean isRuntimeKey(String key) {
		if (RUNTIME_KEYS.contains(key)) {
			return true;
		}
		for (String prefix : RUNTIME_PREFIXES) {
			if (key.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Looks in the document itself, the configuration metadata and the project's
	 * application files. Both indexes are fetched on first use and kept.
	 */
	private static abstract class IndexLookup implements KeyLookup {

		private final Set<String> localKeys;
		private FuzzyMap<PropertyInfo> metadata;
		private FuzzyMap<PropertyInfo> adHoc;
		private boolean resolved;

		IndexLookup(Set<String> localKeys) {
			this.localKeys = localKeys;
		}

		abstract FuzzyMap<PropertyInfo> metadata();

		abstract FuzzyMap<PropertyInfo> adHoc();

		@Override
		public boolean isDefined(String key) {
			String canonical = StringUtil.camelCaseToHyphens(key);
			if (localKeys.contains(key) || localKeys.contains(canonical)) {
				return true;
			}
			if (!resolved) {
				metadata = metadata();
				adHoc = adHoc();
				resolved = true;
			}
			if (metadata != null && SpringPropertyIndex.findLongestValidProperty(metadata, key) != null) {
				return true;
			}
			return adHoc != null && (adHoc.get(key) != null || SpringPropertyIndex.findLongestValidProperty(adHoc, key) != null);
		}
	}

	/** Forwards problems to the real collector and counts them. */
	private static class ProblemCounter implements IProblemCollector {

		private final IProblemCollector delegate;
		int count = 0;

		ProblemCounter(IProblemCollector delegate) {
			this.delegate = delegate;
		}

		@Override
		public void beginCollecting() {
		}

		@Override
		public void endCollecting() {
		}

		@Override
		public void accept(ReconcileProblem problem) {
			count++;
			delegate.accept(problem);
		}
	}

}
