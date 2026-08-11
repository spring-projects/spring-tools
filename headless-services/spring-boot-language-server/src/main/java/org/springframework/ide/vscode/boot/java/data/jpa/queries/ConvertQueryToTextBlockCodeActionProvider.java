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
package org.springframework.ide.vscode.boot.java.data.jpa.queries;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.codeaction.JdtAstCodeActionProvider;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.JdtQueryVisitorUtils.EmbeddedQueryExpression;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.ConvertQueryToTextBlockRefactoring;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtFixDescriptor;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtRefactorUtils;
import org.springframework.ide.vscode.boot.java.jdt.refactoring.JdtRefactorings;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ICollector;
import org.springframework.ide.vscode.commons.util.text.IRegion;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.parser.hql.HqlQueryFormatter;
import org.springframework.ide.vscode.parser.postgresql.PostgreSqlQueryFormatter;

/**
 * Offers a code action that converts the string literal value of a
 * {@code @Query}, {@code @NativeQuery}, or {@code @NamedQuery} annotation (or
 * an {@code EntityManager.createQuery(...)} call) into a formatted Java text
 * block, the same conversion that is applied automatically when such queries
 * are inserted from AOT-generated Spring Data metadata.
 */
public class ConvertQueryToTextBlockCodeActionProvider implements JdtAstCodeActionProvider {

	private static final String TITLE = "Convert query to text block";

	private final JdtRefactorings refactorings;

	public ConvertQueryToTextBlockCodeActionProvider(JdtRefactorings refactorings) {
		this.refactorings = refactorings;
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return SpringProjectUtil.hasDependencyStartingWith(project, "spring-data-jpa", null)
				|| SpringProjectUtil.hasDependencyStartingWith(project, "spring-data-jdbc", null);
	}

	@Override
	public Optional<ASTVisitor> createVisitor(CancelChecker cancelToken, IJavaProject project, URI docURI,
			CompilationUnit cu, TextDocument doc, IRegion region, ICollector<CodeAction> collector) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);
		if (annotationHierarchies == null) {
			return Optional.empty();
		}

		return Optional.of(new ASTVisitor() {

			@Override
			public boolean visit(NormalAnnotation node) {
				cancelToken.checkCanceled();
				EmbeddedQueryExpression q = JdtQueryVisitorUtils.extractQueryExpression(annotationHierarchies, node);
				offerConversion(q, findValueExpression(node));
				return super.visit(node);
			}

			@Override
			public boolean visit(SingleMemberAnnotation node) {
				cancelToken.checkCanceled();
				EmbeddedQueryExpression q = JdtQueryVisitorUtils.extractQueryExpression(annotationHierarchies, node);
				offerConversion(q, node.getValue());
				return super.visit(node);
			}

			@Override
			public boolean visit(MethodInvocation node) {
				cancelToken.checkCanceled();
				EmbeddedQueryExpression q = JdtQueryVisitorUtils.extractQueryExpression(node);
				Expression valueExpr = node.arguments().isEmpty() ? null : (Expression) node.arguments().get(0);
				offerConversion(q, valueExpr);
				return super.visit(node);
			}

			private Expression findValueExpression(NormalAnnotation node) {
				String attributeName = JdtQueryVisitorUtils.isNamedQueryAnnotation(annotationHierarchies, node)
						? "query"
						: "value";
				for (Object v : node.values()) {
					if (v instanceof MemberValuePair mvp && attributeName.equals(mvp.getName().getIdentifier())) {
						return mvp.getValue();
					}
				}
				return null;
			}

			private void offerConversion(EmbeddedQueryExpression q, Expression valueExpr) {
				if (q == null || !(valueExpr instanceof StringLiteral literal)) {
					return;
				}
				boolean regionWithinLiteral = literal.getStartPosition() <= region.getStart()
						&& literal.getStartPosition() + literal.getLength() >= region.getEnd();
				if (!regionWithinLiteral) {
					return;
				}
				collector.accept(createCodeAction(docURI, literal, q.isNative()));
			}

		});
	}

	private CodeAction createCodeAction(URI docURI, StringLiteral literal, boolean isNative) {
		String rawValue = literal.getLiteralValue();
		String formattedValue = isNative
				? new PostgreSqlQueryFormatter().format(rawValue)
				: new HqlQueryFormatter().format(rawValue);
		String textBlockValue = "\"\"\"\n" + JdtRefactorUtils.escapeForTextBlock(formattedValue) + "\n\"\"\"";

		ConvertQueryToTextBlockRefactoring refactoring = new ConvertQueryToTextBlockRefactoring(
				literal.getStartPosition(), literal.getLength(), textBlockValue);
		JdtFixDescriptor fixDescriptor = new JdtFixDescriptor(refactoring, List.of(docURI.toASCIIString()), TITLE);

		CodeAction ca = new CodeAction();
		ca.setCommand(refactorings.createFixCommand(TITLE, fixDescriptor));
		ca.setTitle(TITLE);
		ca.setKind(CodeActionKind.RefactorRewrite);
		return ca;
	}

}
