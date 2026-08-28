/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.yaml.completion;

import org.springframework.ide.vscode.commons.languageserver.util.PrefixFinder;
import org.springframework.ide.vscode.commons.util.Renderable;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.structure.YamlDocument;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SDocNode;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SKeyNode;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SNode;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SNodeType;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SRootNode;

/**
 * @author Kris De Volder
 */
public abstract class AbstractYamlAssistContext implements YamlAssistContext {

	/**
	 * Delete a content assist query from the document, and also the line of
	 * text in the document that contains it, if that line of text contains just the
	 * query surrounded by whitespace.
	 */
	public static void deleteQueryAndLine(YamlDocument doc, String query, int queryOffset, YamlPathEdits edits) throws Exception {
		edits.delete(queryOffset, query);
		String wholeLine = doc.getLineTextAtOffset(queryOffset);
		if (wholeLine.trim().equals(query.trim())) {
			edits.deleteLineBackwardAtOffset(queryOffset);
		}
	}

	public final int documentSelector;
	public final YamlPath contextPath;
	private final YamlDocument doc;


	@Override
	public YamlDocument getDocument() {
		return doc;
	}

	protected DocumentRegion getCustomAssistRegion(YamlDocument doc, SNode node, int offset) {
		if (node.getNodeType() == SNodeType.KEY) {
			SKeyNode keyNode = (SKeyNode) node;
			if (keyNode.isInValue(offset)) {
				int valueStart = keyNode.getColonOffset()+1;
				int valueEnd = keyNode.getNodeEnd(); // assumes we only look at the current line, good enough for now
				DocumentRegion region = new DocumentRegion(doc.getDocument(), valueStart, valueEnd);
				if (region.startsWith(" ")) {
					region = region.subSequence(1);
				}
				return region;
			}
		}
		return null; // TODO Reaching here might mean support for calling the custom assistant isn't
					// implemented for this kind of context yet. It will have to be expanded upon
					// as the need for it arises in real use-cases.
	}

	private static PrefixFinder prefixfinder = new PrefixFinder() {
		@Override
		protected boolean isPrefixChar(char c) {
			return !(Character.isWhitespace(c) || c == ',');
		}
	};

	protected String getPrefix(YamlDocument doc, SNode node, int offset) {
		//For value completions... in general we would like to determine the whole text
		// corresponding to the value, so a simplistic backwards scan isn't good enough.
		// instead we should use offset in current node / structure to determine the
		// the start of the current value.
		if (node.getNodeType() == SNodeType.KEY) {
			SKeyNode keyNode = (SKeyNode) node;
			if (keyNode.isInValue(offset)) {
				int valueStart = keyNode.getColonOffset()+1;
				while (valueStart <= offset && Character.isWhitespace(doc.getChar(valueStart))) {
					valueStart++;
				}
				if (offset >= valueStart) {
					return prefixfinder.getPrefix(doc.getDocument(), offset, valueStart);
				} else {
					//only whitespace, or nothing found upto the cursor
					return "";
				}
			}
		} else if (node.getNodeType() == SNodeType.SEQ) {
			//Careful, don't include the '-' at start of the node as part of the prefix.
			return prefixfinder.getPrefix(doc.getDocument(), offset, node.getStart()+1);
//		} else if (node.getNodeType() == SNodeType.RAW) {
//			TODO: Handle this as we could be in a value that's on the next line instead of right behind the node
		}

		//If not one of the special cases where we try to be more precise...
		// we use simplistic backward scan to determine 'CA query'.
		return prefixfinder.getPrefix(doc.getDocument(), offset);
	}


	public AbstractYamlAssistContext(YamlDocument doc, int documentSelector, YamlPath contextPath) {
		this.doc = doc;
		this.documentSelector = documentSelector;
		this.contextPath = contextPath;
	}

	protected SNode getContextNode() throws Exception {
		SNode root = getContextRoot(getDocument());
		return contextPath.traverse(root);
	}

	protected SDocNode getContextRoot(YamlDocument file) throws Exception {
		SRootNode root = file.getStructure();
		return (SDocNode) root.getChildren().get(documentSelector);
	}

	protected CompletionFactory completionFactory() {
		return CompletionFactory.DEFAULT;
	}

	@Override
	public Renderable getHoverInfo() {
		return null;
	}

	@Override
	public Renderable getValueHoverInfo(YamlDocument doc, DocumentRegion documentRegion) {
		//By default we don't provide value-specific hover, so just show the same hover
		// as the assistContext the value is in. This is likely more interesting than showing nothing at all.
		return getHoverInfo();
	}

}
