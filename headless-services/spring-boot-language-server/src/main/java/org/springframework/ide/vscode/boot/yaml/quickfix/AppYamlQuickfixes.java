/*******************************************************************************
 * Copyright (c) 2019, 2021 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.yaml.quickfix;

import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.properties.quickfix.CommonQuickfixes;
import org.springframework.ide.vscode.boot.properties.quickfix.DeprecatedPropertyData;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixEdit;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixType;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.protocol.CursorMovement;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.commons.yaml.completion.YamlPathEdits;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.quickfix.YamlQuickfixes;
import org.springframework.ide.vscode.commons.yaml.structure.YamlDocument;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SChildBearingNode;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SDocNode;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SKeyNode;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SNode;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SNodeType;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureProvider;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import reactor.core.publisher.Mono;

/**
 * Boot YAML file properties quick fix code action handlers
 *
 * @author Alex Boyko
 *
 */
public class AppYamlQuickfixes {

	private static final Logger log = LoggerFactory.getLogger(AppYamlQuickfixes.class);

	public final QuickfixType DEPRECATED_PROPERTY;
	public final QuickfixType MISSING_PROPERTY;

	private static final QuickfixEdit NULL_FIX = new QuickfixEdit(
			new WorkspaceEdit(ImmutableMap.of()),
			null
	);

	private final Gson gson = new Gson();

	public AppYamlQuickfixes(QuickfixRegistry r, SimpleTextDocumentService textDocumentService, YamlStructureProvider structureProvider, CommonQuickfixes commonQuickfixes) {
		MISSING_PROPERTY = commonQuickfixes.MISSING_PROPERTY;
		DEPRECATED_PROPERTY = r.register("DEPRECATED_YAML_PROPERTY", (Object _params) -> Mono.fromSupplier(() -> {
			DeprecatedPropertyData params = gson.fromJson((JsonElement)_params, DeprecatedPropertyData.class);
			try {
				TextDocument _doc = textDocumentService.getLatestSnapshot(params.getUri());

				if (_doc != null) {
					YamlDocument doc = new YamlDocument(_doc, structureProvider);
					SNode root = doc.getStructure();
					int offset = _doc.toOffset(params.getRange().getStart());
					SNode node = root.find(offset);
					if (node != null) {
						// Drop the doc root
						YamlPath oldPath = node.getPath().dropFirst(1);
						YamlPath newPath = YamlPath.fromProperty(params.getReplacement());
						YamlPath prefix = newPath.commonPrefix(oldPath);
						if (prefix.size() == newPath.size()-1 && newPath.size() == oldPath.size()) {
							//only the last segment has changed. We can do a simple 'in-place' replace
							// of just the change segment.
							WorkspaceEdit wsEdits = new WorkspaceEdit();
							String replacement = newPath.getLastSegment().toPropString();
							wsEdits.setChanges(ImmutableMap.of(
									params.getUri(),
									ImmutableList.of(new TextEdit(params.getRange(), replacement))
							));
							return new QuickfixEdit(wsEdits, new CursorMovement(params.getUri(), _doc.toPosition(node.getNodeEnd())));
						}
						if (node.getNodeType() == SNodeType.KEY) {
							SKeyNode problemKey = (SKeyNode) node;
							if (problemKey.isInKey(offset)) {
								YamlPathEdits edits = new YamlPathEdits(doc);
								String valueText = problemKey.getValueWithRelativeIndent();
								edits.deleteNode(problemKey);
								int maxParentDeletions = oldPath.size() - prefix.size() - 1; // don't delete bits of the common prefix!
								SChildBearingNode parent = node.getParent();
								while (maxParentDeletions > 0 && parent != null && parent.getChildren().size() == 1) {
									edits.deleteNode(parent);
									parent = parent.getParent();
									maxParentDeletions--;
								}
								SDocNode docRoot = node.getDocNode(); //edits should stay within the same 'document' for yaml file that has multiple documents inside of it.
								edits.createPath(docRoot, YamlPath.fromProperty(params.getReplacement()), valueText);

								return YamlQuickfixes.createReplacementQuickfic(_doc, edits);
							}
						}

					}
				}
			} catch (Exception e) {
				log.error("", e);
			}
			return NULL_FIX;
		}));
	}

}
