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
package org.springframework.ide.vscode.boot.java.placeholder;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.embedded.lang.EmbeddedLanguageSnippet;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.ReconcilingContext;
import org.springframework.ide.vscode.boot.properties.reconcile.PropertyPlaceholderChecker;
import org.springframework.ide.vscode.boot.properties.reconcile.PropertyPlaceholderChecker.KeyLookup;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.reconcile.DiagnosticSeverityProvider;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;

/**
 * Checks <code>${...}</code> placeholders in the attributes listed in
 * {@link AnnotationParamPlaceholderExtractor}. Reports syntax errors and
 * reminds about a placeholder without default whose key the project does not
 * define. Placeholders inside <code>#{...}</code> are left to the SpEL reconciler.
 * <p>
 * The reconciler keeps no state of its own. A file with placeholders is marked in the
 * {@link ReconcilingContext} as depending on the property files, so the indexer brings
 * it back when an <code>application*</code> file changes.
 *
 * @author Seema Jagadeesh
 */
public class JdtPropertyPlaceholderReconciler implements JdtAstReconciler {

	private final AnnotationParamPlaceholderExtractor[] extractors = AnnotationParamPlaceholderExtractor.PLACEHOLDER_EXTRACTORS;
	private final PropertyPlaceholderChecker checker;
	private final DiagnosticSeverityProvider severityProvider;

	public JdtPropertyPlaceholderReconciler(PropertyPlaceholderChecker checker, DiagnosticSeverityProvider severityProvider) {
		this.checker = checker;
		this.severityProvider = severityProvider;
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return SpringProjectUtil.hasDependencyStartingWith(project, "spring-context", null);
	}

	@Override
	public ProblemType getProblemType() {
		return Boot2JavaProblemType.JAVA_UNRESOLVED_PLACEHOLDER;
	}

	@Override
	public Optional<ASTVisitor> createVisitor(IJavaProject project, URI docURI, CompilationUnit cu, ReconcilingContext context) {
		// one lookup per compilation unit, the indexes are fetched on the first placeholder
		KeyLookup lookup = checker.lookupFor(project);

		return Optional.of(new ASTVisitor() {
			@Override
			public boolean visit(SingleMemberAnnotation node) {
				if (AnnotationParamPlaceholderExtractor.isListed(node)) {
					Arrays.stream(extractors)
						.flatMap(e -> e.getValueSnippets(node).stream())
						.forEach(this::check);
				}
				return super.visit(node);
			}

			@Override
			public boolean visit(NormalAnnotation node) {
				if (AnnotationParamPlaceholderExtractor.isListed(node)) {
					Arrays.stream(extractors)
						.flatMap(e -> e.getValueSnippets(node).stream())
						.forEach(this::check);
				}
				return super.visit(node);
			}

			private void check(EmbeddedLanguageSnippet snippet) {
				String text = snippet.getText();
				if (text == null || text.indexOf("${") < 0) {
					return;
				}
				context.markAsDependingOnPropertyFiles();
				checker.check(maskSpelExpressions(text), snippet::toSingleJavaRange, lookup, context.getProblemCollector());
			}
		});
	}

	/**
	 * Only the unresolved reminder depends on the property files, the syntax check does not.
	 * So nothing is reconciled again while the reminder is set to IGNORE.
	 */
	@Override
	public boolean reconcileOnPropertyFileChange(IJavaProject project) {
		return severityProvider.getDiagnosticSeverity(getProblemType()) != null;
	}

	/**
	 * Blanks every <code>#{...}</code> region but keeps the length, so offsets
	 * stay valid and SpEL placeholders stay with the SpEL reconciler.
	 * An unclosed <code>#{</code> is blanked to the end of the string on purpose,
	 * the SpEL reconciler reports that syntax error.
	 */
	static String maskSpelExpressions(String text) {
		int start = text.indexOf("#{");
		if (start < 0) {
			return text;
		}
		StringBuilder masked = new StringBuilder(text);
		while (start >= 0) {
			int end = findClosingBrace(text, start + 2);
			if (end < 0) {
				end = text.length() - 1;
			}
			for (int i = start; i <= end; i++) {
				masked.setCharAt(i, ' ');
			}
			start = text.indexOf("#{", end + 1);
		}
		return masked.toString();
	}

	/** Index of the brace closing the expression that starts at <code>bodyStart</code>, or -1 if unclosed. */
	private static int findClosingBrace(String text, int bodyStart) {
		int depth = 1;
		for (int i = bodyStart; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\'' || c == '"') {
				i = endOfStringLiteral(text, i);
				if (i < 0) {
					return -1;
				}
			} else if (c == '{') {
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

	private static int endOfStringLiteral(String text, int start) {
		char quote = text.charAt(start);
		for (int i = start + 1; i < text.length(); i++) {
			if (text.charAt(i) == quote) {
				if (i + 1 < text.length() && text.charAt(i + 1) == quote) {
					i++; // a doubled quote is an escaped quote within the literal
				} else {
					return i;
				}
			}
		}
		return -1;
	}

}
