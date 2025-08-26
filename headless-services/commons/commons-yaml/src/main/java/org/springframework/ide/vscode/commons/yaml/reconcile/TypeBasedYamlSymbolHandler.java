/*******************************************************************************
 * Copyright (c) 2017, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.yaml.reconcile;

import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.languageserver.util.DocumentSymbolHandler;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.commons.yaml.ast.NodeUtil;
import org.springframework.ide.vscode.commons.yaml.reconcile.ASTTypeCache.NodeTypes;
import org.springframework.ide.vscode.commons.yaml.schema.YType;
import org.yaml.snakeyaml.nodes.Node;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSet;

/**
 * Finds symbols in a yaml document based on type information cached
 * during reconcile and stored in the {@link ASTTypeCache}. Because
 * it relies on information computed during reconcile, this handler only
 * works if it is invoked after a reconcile.
 *
 * @author Kris De Volder
 */
public class TypeBasedYamlSymbolHandler implements DocumentSymbolHandler {

	final static Logger logger = LoggerFactory.getLogger(TypeBasedYamlSymbolHandler.class);

	private ASTTypeCache astTypeCache;
	private Set<YType> definitionTypes;
	private SimpleTextDocumentService documents;

	public TypeBasedYamlSymbolHandler(SimpleTextDocumentService documents, ASTTypeCache astTypeCache, Collection<YType> definitionTypes) {
		Assert.isTrue(!definitionTypes.isEmpty()); // If there's no interesting types then you are better of using DocumentSymbolHandler.NO_SYMBOLS
		this.documents = documents;
		this.astTypeCache = astTypeCache;
		this.definitionTypes = ImmutableSet.copyOf(definitionTypes);
		for (YType yType : definitionTypes) {
			astTypeCache.addInterestingType(yType);
		}
	}

	@Override
	public List<? extends DocumentSymbol> handle(DocumentSymbolParams params) {
		Builder<DocumentSymbol> builder = ImmutableList.builder();

		TextDocument doc = documents.getLatestSnapshot(params.getTextDocument().getUri());
		NodeTypes nodeTypes = astTypeCache.getNodeTypes(params.getTextDocument().getUri());
		if (doc != null && nodeTypes != null) {
			for (Entry<Node, YType> entry : nodeTypes.getTypes().entrySet()) {
				if (definitionTypes.contains(entry.getValue())) {
					try {
						builder.add(createSymbol(doc, entry.getKey(), entry.getValue()));
					} catch (Exception e) {
						logger.error("", e);
					}
				}
			}
		}
		return builder.build();
	}

	protected DocumentSymbol createSymbol(TextDocument doc, Node node, YType type) throws BadLocationException {
		DocumentRegion region = NodeUtil.region(doc, node);

		Range range = doc.toRange(region.getStart(), region.getLength());

		DocumentSymbol symbol = new DocumentSymbol();
		symbol.setName(region.toString().isEmpty() ? " " : region.toString());
		symbol.setKind(symbolKind(type));
		symbol.setRange(range);
		symbol.setSelectionRange(range);

		return symbol;
	}

	protected String containerName(YType type) {
		return type.toString().replaceAll("(\\s)*[Nn]ame", "");
	}

	protected SymbolKind symbolKind(YType type) {
		return SymbolKind.String; //TODO: try to return something different for different types of symbols
	}

}
