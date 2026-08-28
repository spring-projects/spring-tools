/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.yaml.hover;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.languageserver.hover.HoverInfoProvider;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.Renderable;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.util.text.IRegion;
import org.springframework.ide.vscode.commons.util.text.Region;
import org.springframework.ide.vscode.commons.yaml.ast.NodeRef;
import org.springframework.ide.vscode.commons.yaml.ast.YamlASTProvider;
import org.springframework.ide.vscode.commons.yaml.ast.YamlFileAST;
import org.springframework.ide.vscode.commons.yaml.completion.YamlAssistContext;
import org.springframework.ide.vscode.commons.yaml.completion.YamlAssistContextProvider;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment;
import org.springframework.ide.vscode.commons.yaml.structure.YamlDocument;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureProvider;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.parser.ParserException;
import org.yaml.snakeyaml.scanner.ScannerException;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * Implements {@link HoverInfoProvider} for Yaml files based on
 * {@link YamlAssistContext}.
 *
 * @author Kris De Volder
 */
public class YamlHoverInfoProvider implements HoverInfoProvider {

	private static final Logger log = LoggerFactory.getLogger(YamlHoverInfoProvider.class);
	
	private YamlASTProvider astProvider;
	private YamlAssistContextProvider assistContextProvider;
	private YamlStructureProvider structureProvider;

	public YamlHoverInfoProvider(YamlASTProvider astProvider, YamlStructureProvider structureProvider,
			YamlAssistContextProvider assistContextProvider) {
		Assert.isNotNull(astProvider);
		Assert.isNotNull(structureProvider);
		Assert.isNotNull(assistContextProvider);
		this.astProvider = astProvider;
		this.structureProvider = structureProvider;
		this.assistContextProvider = assistContextProvider;
	}

	@Override
	public Tuple2<Renderable, IRegion> getHoverInfo(IDocument doc, int offset) throws Exception {
		log.debug("YamlHoverInfoProvider starting");
		try {
			YamlFileAST ast = getAst(doc);
			if (ast == null) {
				log.debug("No hover because ast is null");
			} else {
				IRegion region = getHoverRegion(ast, offset);
				if (region == null) {
					log.debug("No hover because region is null");
				} else {
					YamlDocument ymlDoc = new YamlDocument(doc, structureProvider);
					YamlAssistContext assistContext = assistContextProvider.getGlobalAssistContext(ymlDoc);
					if (assistContext == null) {
						log.debug("No hover because GLOBAL assistContext is null");
					} else {
						List<NodeRef<?>> astPath = ast.findPath(offset);
						final YamlPath path = YamlPath.fromASTPath(astPath);
						if (path == null) {
							log.debug("No hover because path is null");
						} else {
							YamlPath assistPath = path;
							if (assistPath.pointsAtKey()) {
								// When a path points at a key we must tramsform it to a
								// 'value-terminating path'
								// to be able to reuse the 'getHoverInfo' method on
								// YamlAssistContext (as navigation
								// into 'key' is not defined for YamlAssistContext.
								String key = path.getLastSegment().toPropString();
								assistPath = path.dropLast().append(YamlPathSegment.valueAt(key));
							}
							assistContext = assistPath.traverse(assistContext);
							if (assistContext == null) {
								log.debug("No hover because assistContext for path {} is null", assistPath);
							} else {
								Renderable info = path.pointsAtValue()
										? assistContext.getValueHoverInfo(ymlDoc, new DocumentRegion(doc, region))
										: assistContext.getHoverInfo();
	
							    // Fix for: PT 134914895. If assist context cannot provide an info, then don't return a Tuple.
								if (info == null) {
									log.debug("No hover because assistContext returned no hover", assistPath);
								} else {
									return Tuples.of(info, region);
								}
							}
						}
					}
				}
			}
			return null;
		} finally {
			log.debug("YamlHoverInfoProvider finished");
		}
	}

	private IRegion getHoverRegion(YamlFileAST ast, int offset) {
		if (ast != null) {
			Node n = ast.findNode(offset);
			if (n != null && n.getNodeId() == NodeId.scalar) {
				int start = n.getStartMark().getIndex();
				int end = n.getEndMark().getIndex();
				return new Region(start, end - start);
			}
		}
		return null;
	}

	private YamlFileAST getAst(IDocument doc) throws Exception {
		try {
			return astProvider.getAST(doc);
		} catch (ParserException | ScannerException e) {
			// ignore, the user just typed some crap
		}
		return null;
	}
}
