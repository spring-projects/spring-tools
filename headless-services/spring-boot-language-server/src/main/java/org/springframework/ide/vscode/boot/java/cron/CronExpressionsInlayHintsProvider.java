/*******************************************************************************
 * Copyright (c) 2024, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.cron;

import java.util.Locale;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.JdtInlayHintsProvider;
import org.springframework.ide.vscode.boot.java.embedded.lang.EmbeddedLangAstUtils;
import org.springframework.ide.vscode.boot.java.embedded.lang.EmbeddedLanguageSnippet;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.util.Collector;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.scheduling.support.CronExpression;

import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;

import static com.cronutils.model.CronType.SPRING;

public class CronExpressionsInlayHintsProvider implements JdtInlayHintsProvider {

	protected static Logger logger = LoggerFactory.getLogger(CronExpressionsInlayHintsProvider.class);

	private static final String SCHEDULED = "Scheduled";

	private final BootJavaConfig config;

	public record EmbeddedCronExpression(Expression expression, String text) {
	};

	public CronExpressionsInlayHintsProvider(BootJavaConfig config) {
		this.config = config;
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return config.isCronInlayHintsEnabled();
	}

	@Override
	public ASTVisitor getInlayHintsComputer(IJavaProject project, TextDocument doc, CompilationUnit cu,
			Collector<InlayHint> collector) {
		return new ASTVisitor() {

			@Override
			public boolean visit(NormalAnnotation node) {
				EmbeddedCronExpression cron = extractCronExpression(node);
				if (cron != null) {
					processCron(project, doc, collector, cron, node);
				}
				return super.visit(node);
			}

			@Override
			public boolean visit(SingleMemberAnnotation node) {
				EmbeddedCronExpression cron = extractCronExpression(node);
				if (cron != null) {
					processCron(project, doc, collector, cron, node);
				}
				return super.visit(node);
			}

		};
	}

	private void processCron(IJavaProject project, TextDocument doc, Collector<InlayHint> collector,
			EmbeddedCronExpression cronExp, Annotation node) {
		boolean isValidExpression = CronExpression.isValidExpression(cronExp.text());

		try {
			if (isValidExpression) {
				CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(SPRING);
			    CronParser parser = new CronParser(cronDefinition);
			    CronDescriptor descriptor = CronDescriptor.instance(Locale.US);
			    String cronDescription = descriptor.describe(parser.parse(cronExp.text().toUpperCase()));
			    
				InlayHint hint = new InlayHint();
				hint.setKind(InlayHintKind.Type);
				hint.setLabel(Either.forLeft(cronDescription));
				hint.setTooltip(cronDescription);
				hint.setPaddingLeft(true);
				hint.setPaddingRight(true);
				hint.setPosition(doc.toPosition(node.getStartPosition() + node.getLength()));
				collector.accept(hint);
			}
		} catch (Exception e) {
			// ignore
		}
	}

	public static EmbeddedCronExpression extractCronExpression(SingleMemberAnnotation a) {
		if (isScheduledAnnotation(a)) {
			return extractEmbeddedExpression(a.getValue());
		}
		return null;
	}

	public static EmbeddedCronExpression extractCronExpression(NormalAnnotation a) {
		Expression cronExpression = null;
		if (isScheduledAnnotation(a)) {
			for (Object value : a.values()) {
				if (value instanceof MemberValuePair) {
					MemberValuePair pair = (MemberValuePair) value;
					String name = pair.getName().getFullyQualifiedName();
					if ("cron".equals(name)) {
						cronExpression = pair.getValue();
						break;
					}
				}
			}
		}
		if (cronExpression != null) {
			return extractEmbeddedExpression(cronExpression);
		}
		return null;
	}

	/**
	 * Resolves the cron expression text of an annotation attribute value. Delegates to
	 * {@link EmbeddedLangAstUtils}, so string literals, text blocks, constant references and
	 * concatenations of those are all handled.
	 */
	public static EmbeddedCronExpression extractEmbeddedExpression(Expression valueExp) {
		EmbeddedLanguageSnippet snippet = EmbeddedLangAstUtils.extractEmbeddedExpression(valueExp);
		return snippet == null ? null : new EmbeddedCronExpression(valueExp, snippet.getText().trim());
	}

	static boolean isScheduledAnnotation(Annotation a) {
		return Annotations.SCHEDULED.equals(a.getTypeName().getFullyQualifiedName())
				|| SCHEDULED.equals(a.getTypeName().getFullyQualifiedName());
	}
}
