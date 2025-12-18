/*******************************************************************************
 * Copyright (c) 2024, 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data.jpa.queries;

import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.JdtAstDocHighlightsProvider;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.semantic.tokens.SemanticTokenData;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.Collector;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

public class JdtQueryDocHighlightsProvider implements JdtAstDocHighlightsProvider {
	
	private static final Logger log = LoggerFactory.getLogger(JdtQueryDocHighlightsProvider.class);
		
	private JdtDataQuerySemanticTokensProvider semanticTokensProvider;

	public JdtQueryDocHighlightsProvider(JdtDataQuerySemanticTokensProvider semanticTokensProvider) {
		this.semanticTokensProvider = semanticTokensProvider;
	}

	@Override
	public List<DocumentHighlight> getDocHighlights(IJavaProject project, TextDocument doc, CompilationUnit cu,
			ASTNode node, int offset) {
		if (node instanceof StringLiteral || node instanceof TextBlock) {
			AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);
			Annotation a = findQueryAnnotation(annotationHierarchies, node);
			if (a != null && a.getParent() instanceof MethodDeclaration m && !m.parameters().isEmpty()) {
				Collector<SemanticTokenData> collector = new Collector<>();
				a.accept(semanticTokensProvider.getTokensComputer(project, doc, cu, collector));
				for (SemanticTokenData t : collector.get()) {
					if ("parameter".equals(t.type()) && t.range().getStart() <= offset && offset <= t.range().getEnd()) {
						try {
							String parameterDescriptor = doc.get(t.range().getOffset(), t.range().getLength());
							SimpleName paramName = findParameter(m, parameterDescriptor);
							if (paramName != null) {
								DocumentHighlight highlight = new DocumentHighlight();
								highlight.setKind(DocumentHighlightKind.Write);
								highlight.setRange(doc.toRange(paramName.getStartPosition(), paramName.getLength()));
								return List.of(highlight);
							}
						} catch (BadLocationException e) {
							log.error("", e);
						}
					}
				}
			}
		}
		return Collections.emptyList();
	}
	
	static Annotation findQueryAnnotation(AnnotationHierarchies annotationHierarchies, ASTNode node) {
		if (node.getParent() instanceof MemberValuePair pair
				&& node.getParent().getParent() instanceof NormalAnnotation na && "value".equals(pair.getName().getIdentifier())
				&& (JdtQueryVisitorUtils.isQueryAnnotation(annotationHierarchies, na)
						|| JdtQueryVisitorUtils.isQueryJdbcAnnotation(annotationHierarchies, na))) {
			return na;
		} else if (node.getParent() instanceof SingleMemberAnnotation sm
				&& (JdtQueryVisitorUtils.isQueryAnnotation(annotationHierarchies, sm)
						|| JdtQueryVisitorUtils.isQueryJdbcAnnotation(annotationHierarchies, sm))) {
			return sm;
		}
		return null;
	}
	
	static SimpleName findParameter(MethodDeclaration m, String p) {
		try {
			int paramNumber = Integer.parseInt(p);
			if (paramNumber > 0 && paramNumber <= m.parameters().size()) {
				Object o = m.parameters().get(paramNumber - 1);
				if (o instanceof VariableDeclaration vd) {
					return vd.getName();
				}
			}
		} catch (NumberFormatException e) {
			for (Object o : m.parameters()) {
				if (o instanceof VariableDeclaration vd) {
					SimpleName simpleName = vd.getName();
					if (simpleName != null && p.equals(simpleName.getIdentifier())) {
						return simpleName;
					}
				}
			}
		}
		return null;
	}

}
