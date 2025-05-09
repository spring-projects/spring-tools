/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
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
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.IJavaLocationLinksProvider;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.semantic.tokens.SemanticTokenData;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.Collector;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

public class DataQueryParameterDefinitionProvider implements IJavaLocationLinksProvider {

	private static final Logger log = LoggerFactory.getLogger(DataQueryParameterDefinitionProvider.class);

	private final JdtDataQuerySemanticTokensProvider semanticTokensProvider;
	private final SimpleTextDocumentService documents;

	public DataQueryParameterDefinitionProvider(SimpleTextDocumentService documents, JdtDataQuerySemanticTokensProvider semanticTokensProvider) {
		this.documents = documents;
		this.semanticTokensProvider = semanticTokensProvider;
	}

	@Override
	public List<LocationLink> getLocationLinks(CancelChecker cancelToken, IJavaProject project,
			TextDocumentIdentifier docId, CompilationUnit cu, ASTNode n, int offset) {
		if (n instanceof StringLiteral || n instanceof TextBlock) {
			AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);
			
			ASTNode a = JdtQueryDocHighlightsProvider.findQueryAnnotation(annotationHierarchies, n);
			
			TextDocument doc = documents.getLatestSnapshot(docId.getUri());
				
				if (a != null && a.getParent() instanceof MethodDeclaration m && !m.parameters().isEmpty()) {
					Collector<SemanticTokenData> collector = new Collector<>();
					a.accept(semanticTokensProvider.getTokensComputer(project, doc, cu, collector));
					for (SemanticTokenData t : collector.get()) {
						if ("parameter".equals(t.type()) && t.range().getOffset() <= offset && offset <= t.range().getEnd()) {
							try {
								String parameterDescriptor = doc.get(t.range().getOffset(), t.range().getLength());
								SimpleName paramName = JdtQueryDocHighlightsProvider.findParameter(m, parameterDescriptor);
								if (paramName != null) {
									LocationLink link = new LocationLink();
									link.setTargetUri(docId.getUri());
									link.setOriginSelectionRange(doc.toRange(t.range().getOffset(), t.range().getLength()));
									link.setTargetSelectionRange(doc.toRange(paramName.getStartPosition(), paramName.getLength()));
									link.setTargetRange(link.getTargetSelectionRange());
									return List.of(link);
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
	
}
