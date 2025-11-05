/*******************************************************************************
 * Copyright (c) 2019, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.yaml.reconcile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.languageserver.util.DocumentSymbolHandler;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.yaml.ast.NodeUtil;
import org.springframework.ide.vscode.commons.yaml.ast.YamlFileAST;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment;
import org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment.YamlPathSegmentType;
import org.springframework.ide.vscode.commons.yaml.schema.YType;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

public class TypeBasedYamlHierarchicalSymbolHandler implements DocumentSymbolHandler, ITypeCollector {

	private static final Logger log = LoggerFactory.getLogger(TypeBasedYamlHierarchicalSymbolHandler.class);

	public static class HierarchicalDefType {
		/**
		 * A yaml node of this type constitutes a definion. This should identify the 'whole' definition not just the
		 * part of the node that contains the name of the defined entity.
		 */
		public final YType defType;

		/**
		 * A yaml optional path that points to the part of the node where the defined entity's name can be found.
		 */
		public final YamlPath namePath;

		public final SymbolKind kind;

		public final String detail;

		public HierarchicalDefType(YType defType, YamlPath namePath, SymbolKind kind, String detail) {
			super();
			this.defType = defType;
			this.namePath = namePath;
			this.kind = kind;
			this.detail = detail;
		}
		@Override
		public String toString() {
			return "HierarchicalDefType [defType=" + defType + ", namePath=" + namePath + "]";
		}
		public DocumentSymbol createSymbol(YamlFileAST currentAst, Node node, YType type, YamlPath path) {
			try {
				IDocument doc = currentAst.getDocument();
				if (namePath!=null) {
					Node nameNode = namePath.traverseNode(node);
					// VSCode throws for DocumentSymbols having null or empty name hence do not create the symbol
					if (nameNode!=null && !NodeUtil.asScalar(nameNode).isEmpty()) {
						DocumentRegion nodeRegion = NodeUtil.region(doc, node);
						DocumentRegion nameRegion = NodeUtil.region(doc, nameNode);
						if (!nodeRegion.contains(nameRegion)) {
							//This violates the expectation that most clients have. 
							//vscode, for example drops the entire symbols hierarchy as 'invalid' if this happens
							nodeRegion = nameRegion;
						}
						return new DocumentSymbol(NodeUtil.asScalar(nameNode), kind,
								nodeRegion.asRange(),
								nameRegion.asRange(),
								detail
						);
					}
				} else {
					//If there's no 'namePath' then we will assume the node we found is the value of a map entry...
					//and use the map's key as the symbol's name
					MappingNode map = NodeUtil.asMapping(path.dropLast().traverseToNode(currentAst));
					if (map!=null) {
						YamlPathSegment segment = path.getLastSegment();
						if (segment.getType()==YamlPathSegmentType.VAL_AT_KEY) {
							String key = segment.toPropString();
							for (NodeTuple entry : map.getValue()) {
								if (key.equals(NodeUtil.asScalar(entry.getKeyNode()))) {
									return new DocumentSymbol(key, kind,
											NodeUtil.region(doc, entry).asRange(),
											NodeUtil.region(doc, entry.getKeyNode()).asRange(),
											detail
									);
								}
							}
						}
					}
				}
			} catch (Exception e) {
				log.error("", e);
			}
			return null;
		}
	}

	private static class Item {
		YamlPath path;
		DocumentSymbol symbol;
		Item(YamlPath path, DocumentSymbol symbol) {
			super();
			this.path = path;
			this.symbol = symbol;
		}
		public void addChild(DocumentSymbol sym) {
			if (symbol.getChildren()==null) {
				symbol.setChildren(new ArrayList<DocumentSymbol>());
			}
			symbol.getChildren().add(sym);
		}
	}

	private TypeBasedYamlSymbolHandler baseHandler;
	Map<YType, HierarchicalDefType> hierarchicalDefinitionTypes;

	Map<String, List<DocumentSymbol>> outlineByUri = new HashMap<>();
	private YamlFileAST currentAst;

	private Stack<Item> stack = new Stack<>();
	private ImmutableList.Builder<DocumentSymbol> rootSymbols;
	private SimpleLanguageServer server;

	public TypeBasedYamlHierarchicalSymbolHandler(TypeBasedYamlSymbolHandler baseHandler, List<HierarchicalDefType> hierarchicalDefinitionTypes, SimpleLanguageServer server) {
		this.baseHandler = baseHandler;
		Builder<YType, HierarchicalDefType> builder = ImmutableMap.builder();
		for (HierarchicalDefType hdt : hierarchicalDefinitionTypes) {
			builder.put(hdt.defType, hdt);
		}
		this.hierarchicalDefinitionTypes = builder.build();
		this.server = server;
	}

	@Override
	public List<? extends DocumentSymbol> handle(DocumentSymbolParams params) {
		if (server.hasHierarchicalDocumentSymbolSupport()) {
			return outlineByUri.get(params.getTextDocument().getUri());
		}
		else {
			return this.baseHandler.handle(params);
		}
		
	}

	@Override
	public void beginCollecting(YamlFileAST ast) {
		Assert.isNull("Session already active", currentAst);
		this.currentAst = ast;
		this.rootSymbols = ImmutableList.builder();
		this.stack = new Stack<>();
	}

	@Override
	public void accept(Node node, YType type, YamlPath path) {
		HierarchicalDefType def = hierarchicalDefinitionTypes.get(type);
		if (def!=null) {
			Item parent = findParent(path);
			DocumentSymbol sym = def.createSymbol(currentAst, node, type, path);
			if (sym!=null) {
				if (parent!=null) {
					parent.addChild(sym);
				} else {
					rootSymbols.add(sym);
				}
			}
			stack.push(new Item(path, sym));
		}
	}

	private Item findParent(YamlPath path) {
		if (stack.isEmpty()) {
			return null;
		}
		Item item = stack.peek();
		while (!path.startsWith(item.path)) {
			stack.pop();
			if (stack.isEmpty()) {
				return null;
			}
			item = stack.peek();
		}
		return item;
	}

	@Override
	public void endCollecting(YamlFileAST ast) {
		Assert.isLegal(this.currentAst == ast);
		String uri = currentAst.getDocument().getUri();
		this.outlineByUri.put(uri, rootSymbols.build());
		this.rootSymbols = null;
		this.currentAst = null;
		this.stack = null;
	}
}
