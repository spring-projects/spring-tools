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
package org.springframework.ide.eclipse.editor.support.yaml.hover;

import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.springframework.ide.eclipse.editor.support.hover.HoverInfo;
import org.springframework.ide.eclipse.editor.support.hover.HoverInfoProvider;
import org.springframework.ide.eclipse.editor.support.util.DocumentRegion;
import org.springframework.ide.eclipse.editor.support.yaml.YamlAssistContextProvider;
import org.springframework.ide.eclipse.editor.support.yaml.YamlDocument;
import org.springframework.ide.eclipse.editor.support.yaml.ast.NodeRef;
import org.springframework.ide.eclipse.editor.support.yaml.ast.YamlASTProvider;
import org.springframework.ide.eclipse.editor.support.yaml.ast.YamlFileAST;
import org.springframework.ide.eclipse.editor.support.yaml.completions.YamlAssistContext;
import org.springframework.ide.eclipse.editor.support.yaml.path.YamlPath;
import org.springframework.ide.eclipse.editor.support.yaml.path.YamlPathSegment;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureProvider;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.parser.ParserException;
import org.yaml.snakeyaml.scanner.ScannerException;

/**
 * Implements {@link HoverInfoProvider} for Yaml files based on {@link YamlAssistContext}.
 *
 * @author Kris De Volder
 */
public class YamlHoverInfoProvider implements HoverInfoProvider {

	private YamlASTProvider astProvider;
	private YamlAssistContextProvider assistContextProvider;
	private YamlStructureProvider structureProvider;

	public YamlHoverInfoProvider	(
			YamlASTProvider astProvider,
			YamlStructureProvider structureProvider,
			YamlAssistContextProvider assistContextProvider
	) {
		Assert.isNotNull(astProvider);
		Assert.isNotNull(structureProvider);
		Assert.isNotNull(assistContextProvider);
		this.astProvider = astProvider;
		this.structureProvider = structureProvider;
		this.assistContextProvider = assistContextProvider;
	}

	@Override
	public HoverInfo getHoverInfo(IDocument doc, IRegion r) {
		YamlFileAST ast = getAst(doc);
		if (ast != null) {
			YamlDocument ymlDoc = new YamlDocument(doc, structureProvider);
			YamlAssistContext assistContext = assistContextProvider.getGlobalAssistContext(ymlDoc);
			if (assistContext != null) {
				List<NodeRef<?>> astPath = ast.findPath(r.getOffset());
				final YamlPath path = YamlPath.fromASTPath(astPath);
				if (path != null) {
					YamlPath assistPath = path;
					if (assistPath.pointsAtKey()) {
						//When a path points at a key we must tramsform it to a 'value-terminating path'
						// to be able to reuse the 'getHoverInfo' method on YamlAssistContext (as navigation
						// into 'key' is not defined for YamlAssistContext.
						String key = path.getLastSegment().toPropString();
						assistPath = path.dropLast().append(YamlPathSegment.valueAt(key));
					}
					assistContext = assistPath.traverse(assistContext);
					if (assistContext != null) {
						if (path.pointsAtValue()) {
							return assistContext.getValueHoverInfo(ymlDoc, new DocumentRegion(doc, r));
						}
						return assistContext.getHoverInfo();
					}
				}
			}
		}
		return null;
	}

	@Override
	public IRegion getHoverRegion(IDocument document, int offset) {
		YamlFileAST ast = getAst(document);
		if (ast != null) {
			Node n = ast.findNode(offset);
			if (n != null && n.getNodeId() == NodeId.scalar) {
				int start = n.getStartMark().getIndex();
				int end = n.getEndMark().getIndex();
				return new Region(start, end-start);
			}
		}
		return null;
	}

	private YamlFileAST getAst(IDocument doc) {
		try {
			return astProvider.getAST(doc);
		} catch (ParserException|ScannerException e) {
			//ignore, the user just typed some crap
		}
		return null;
	}
}
