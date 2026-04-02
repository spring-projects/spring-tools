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
package org.springframework.ide.vscode.boot.java.reconcilers;

import java.net.URI;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;

/**
 * Base class for reconcilers that validate Spring AI method-level annotations
 * ({@code @Tool}, {@code @McpTool}, {@code @McpPrompt}, etc.).
 * <p>
 * Handles annotation detection and description extraction; subclasses implement
 * {@link #validateDescription(String, Annotation, IProblemCollector)} to apply
 * their specific check.
 */
abstract class AbstractSpringAiAnnotationReconciler implements JdtAstReconciler {

	private static final Logger log = LoggerFactory.getLogger(AbstractSpringAiAnnotationReconciler.class);

	private static final Set<String> SPRING_AI_ANNOTATION_FQNS = Set.of(
			Annotations.SPRING_AI_TOOL,
			Annotations.SPRING_AI_MCP_TOOL,
			Annotations.SPRING_AI_MCP_PROMPT,
			Annotations.SPRING_AI_MCP_RESOURCE
	);

	@Override
	public boolean isApplicable(IJavaProject project) {
		return SpringProjectUtil.hasDependencyStartingWith(project, "spring-ai-", null);
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		return new ASTVisitor() {

			@Override
			public boolean visit(NormalAnnotation node) {
				try {
					checkAnnotation(node, context.getProblemCollector());
				} catch (Exception e) {
					log.error("", e);
				}
				return super.visit(node);
			}

			@Override
			public boolean visit(SingleMemberAnnotation node) {
				try {
					checkAnnotation(node, context.getProblemCollector());
				} catch (Exception e) {
					log.error("", e);
				}
				return super.visit(node);
			}

			@Override
			public boolean visit(MarkerAnnotation node) {
				try {
					checkAnnotation(node, context.getProblemCollector());
				} catch (Exception e) {
					log.error("", e);
				}
				return super.visit(node);
			}

		};
	}

	private void checkAnnotation(Annotation node, IProblemCollector problemCollector) {
		ITypeBinding typeBinding = node.resolveTypeBinding();
		if (typeBinding == null) {
			return;
		}

		String fqn = typeBinding.getQualifiedName();
		if (!SPRING_AI_ANNOTATION_FQNS.contains(fqn)) {
			return;
		}

		validateDescription(ASTUtils.getAnnotationAttributeAsString(node, "description"), node, problemCollector);
	}

	/**
	 * Applies the subclass-specific validation rule.
	 *
	 * @param description the resolved description value, or {@code null} if absent
	 * @param node the annotation AST node (for location and source range)
	 * @param problemCollector sink for any emitted problems
	 */
	protected abstract void validateDescription(String description, Annotation node, IProblemCollector problemCollector);

}
