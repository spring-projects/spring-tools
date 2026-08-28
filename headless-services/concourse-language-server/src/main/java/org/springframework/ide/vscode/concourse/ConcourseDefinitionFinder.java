/*******************************************************************************
 * Copyright (c) 2017, 2021 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.concourse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.languageserver.definition.SimpleDefinitionFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.commons.yaml.ast.NodeUtil;
import org.springframework.ide.vscode.commons.yaml.ast.YamlAstCache;
import org.springframework.ide.vscode.commons.yaml.ast.YamlFileAST;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.reconcile.ASTTypeCache;
import org.springframework.ide.vscode.commons.yaml.schema.YType;
import org.yaml.snakeyaml.nodes.Node;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

public class ConcourseDefinitionFinder extends SimpleDefinitionFinder {
		
	private static final Logger log = LoggerFactory.getLogger(ConcourseDefinitionFinder.class);

	@FunctionalInterface
	private interface Handler {
		List<Location> handle(Node refNode, TextDocument doc, YamlFileAST ast);
	}

	private final ASTTypeCache astTypes;
	private Map<YType, Handler> handlers = new HashMap<>();
	private final YamlAstCache asts;

	public ConcourseDefinitionFinder(SimpleLanguageServer server, ConcourseModel models, PipelineYmlSchema schema, ASTTypeCache astTypeCache) {
		super(server);
		this.astTypes = astTypeCache;
		this.asts = models.getAstCache();
		findByPath(schema.t_resource_name, ConcourseModel.RESOURCE_NAMES_PATH);
		findByPath(schema.t_maybe_resource_name, ConcourseModel.RESOURCE_NAMES_PATH);
		findByPath(schema.t_job_name, ConcourseModel.JOB_NAMES_PATH);
		findByPath(schema.t_resource_type_name, ConcourseModel.RESOURCE_TYPE_NAMES_PATH);
	}

	/**
	 * Add a handler that finds the definitions for a target node within the same document
	 * by following a {@link YamlPath} to find candidate nodes.
	 *
	 * @param refType the type inferred by the reconciler for the target node.
	 * @param definitionsPath Path that points to all nodes within the same file corresponding
	 *            to definitions of nodes of the given type.
	 */
	protected void findByPath(YType refType, YamlPath definitionsPath) {
		astTypes.addInterestingType(refType);
		Handler handler = (Node refNode, TextDocument doc, YamlFileAST ast) -> {
			String name = NodeUtil.asScalar(refNode);
			if (name != null) {
				Builder<Location> definitions = ImmutableList.builder();
				definitionsPath.traverseAmbiguously(ast).forEach(node -> {
					if (name.equals(NodeUtil.asScalar(node))) {
						Optional<Location> loc = toLocation(doc, node);
						if (loc.isPresent()) {
							definitions.add(loc.get());
						}
					}
				});
				return definitions.build();
			}
			return ImmutableList.of();
		};
		handlers.put(refType, handler);
	}

	@Override
	public List<LocationLink> handle(CancelChecker cancelToken, DefinitionParams params) {
		try {
			TextDocument doc = server.getTextDocumentService().getLatestSnapshot(params);
			if (doc != null) {
				
				cancelToken.checkCanceled();

				YamlFileAST ast = asts.getSafeAst(doc, false);
				if (ast != null) {

					Node refNode = ast.findNode(doc.toOffset(params.getPosition()));
					if (refNode != null) {
						
						cancelToken.checkCanceled();

						YType type = astTypes.getType(ast, refNode);
						if (type != null) {
							
							cancelToken.checkCanceled();

							Handler handler = handlers.get(type);
							if (handler != null) {
								int start = refNode.getStartMark().getIndex();
								int end = refNode.getEndMark().getIndex();
								Range originalRange = doc.toRange(start, end - start);
								return handler.handle(refNode, doc, ast).stream()
										.map(l -> new LocationLink(l.getUri(), l.getRange(), l.getRange(), originalRange))
										.collect(Collectors.toList());
							}
						}
					}
				}
			}
		} catch (CancellationException e) {
			throw e;
		} catch (Exception e) {
			log.error("", e);;
		}
		return ImmutableList.of();
	}

	Optional<Location> toLocation(TextDocument doc, Node node) {
		int start = node.getStartMark().getIndex();
		int end = node.getEndMark().getIndex();
		try {
			return Optional.of(new Location(doc.getUri(), doc.toRange(start, end-start)));
		} catch (BadLocationException e) {
			log.error("", e);;
			return Optional.empty();
		}
	}
}
