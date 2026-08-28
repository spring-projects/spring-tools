/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.yaml.completions;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.IRegion;
import org.springframework.ide.eclipse.editor.support.completions.DocumentEdits;
import org.springframework.ide.eclipse.editor.support.util.YamlIndentUtil;
import org.springframework.ide.eclipse.editor.support.yaml.YamlDocument;
import org.springframework.ide.eclipse.editor.support.yaml.path.YamlPath;
import org.springframework.ide.eclipse.editor.support.yaml.path.YamlPathSegment;
import org.springframework.ide.eclipse.editor.support.yaml.path.YamlPathSegment.YamlPathSegmentType;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureParser.SChildBearingNode;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureParser.SKeyNode;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureParser.SNode;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureParser.SNodeType;

/**
 * Helper class that provides methods for creating the edits in a YamlDocument that
 * insert new 'property paths' into the document.
 *
 * @author Kris De Volder
 */
public class YamlPathEdits extends DocumentEdits {

	private YamlDocument doc;
	private YamlIndentUtil indentUtil;

	public YamlPathEdits(YamlDocument doc) {
		super(doc.getDocument());
		this.doc = doc;
		this.indentUtil = new YamlIndentUtil(doc);
	}

	/**
	 * Create the necessary edits to ensure that a given property
	 * path exists, placing cursor in the right place also to start
	 * start typing the property value.
	 * <p>
	 * This also handles cases where all or some of the path already
	 * exists. In the former case no edits are performed only cursor
	 * movement. In the latter case, the right place to start inserting
	 * the 'missing' portion of the path is found and the edits
	 * are created there.
	 */
	public void createPath(SChildBearingNode node, YamlPath path, String appendText) throws Exception {
		//This code doesn't handle selection of subddocuments
		// or creation of new subdocuments so must not call it on
		//ROOT node but start at an appropriate SDocNode (or below)
		Assert.isLegal(node.getNodeType() != SNodeType.ROOT);
		if (!path.isEmpty()) {
			YamlPathSegment s = path.getSegment(0);
			if (s.getType() == YamlPathSegmentType.VAL_AT_KEY) {
				String key = s.toPropString();
				SKeyNode existing = node.getChildWithKey(key);
				if (existing == null) {
					createNewPath(node, path, appendText);
				} else {
					createPath(existing, path.tail(), appendText);
				}
			}
		} else {
			//whole path already exists. Just try to move cursor somewhere
			// sensible in the existing tail-end-node of the path.
			SNode child = node.getFirstRealChild();
			if (child != null) {
				moveCursorTo(child.getStart());
			} else if (node.getNodeType() == SNodeType.KEY) {
				SKeyNode keyNode = (SKeyNode) node;
				int colonOffset = keyNode.getColonOffset();
				char c = doc.getChar(colonOffset+1);
				if (c == ' ') {
					moveCursorTo(colonOffset+2); //cursor after the ": "
				} else {
					moveCursorTo(colonOffset+1); //cursor after the ":"
				}
			}
		}
	}

	private void createNewPath(SChildBearingNode parent, YamlPath path, String appendText) throws Exception {
		int indent = getChildIndent(parent);
		int insertionPoint = getNewPathInsertionOffset(parent);
		boolean startOnNewLine = true;
		insert(insertionPoint, createPathInsertionText(path, indent, startOnNewLine, appendText));
	}

	protected String createPathInsertionText(YamlPath path, int indent, boolean startOnNewLine, String appendText) {
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < path.size(); i++) {
			if (startOnNewLine||i > 0) {
				indentUtil.addNewlineWithIndent(indent, buf);
			}
			String key = path.getSegment(i).toPropString();
			buf.append(YamlUtil.stringEscape(key));
			buf.append(":");
			if (i < path.size()-1) {
				indent += YamlIndentUtil.INDENT_BY;
			}
		}
		buf.append(indentUtil.applyIndentation(appendText, indent));
		return buf.toString();
	}

	private int getChildIndent(SNode parent) {
		if (parent.getNodeType() == SNodeType.DOC) {
			return parent.getIndent();
		} else {
			return parent.getIndent()+YamlIndentUtil.INDENT_BY;
		}
	}

	private int getNewPathInsertionOffset(SChildBearingNode parent) throws Exception {
		int insertAfterLine = doc.getLineOfOffset(parent.getTreeEnd());
		while (insertAfterLine >= 0 && doc.getLineIndentation(insertAfterLine) == -1) {
			insertAfterLine--;
		}
		if (insertAfterLine < 0) {
			//This code is probably 'dead' because:
			//   - it can only occur if all lines in the 'parent' are empty
			//   - if parent is any other node than SRootNode then it must have at least one
			//     non-emtpy line
			//  => parent must be SRootNode and only contain comment or empty lines
			//  But in that case we will never need to compute a 'new path insertion offset'
			//  since we will always be in the case where completions are to be inserted
			//  in place (i.e. at the current cursor).
			return 0; //insert at beginning of document
		} else {
			IRegion r = doc.getLineInformation(insertAfterLine);
			return r.getOffset() + r.getLength();
		}
	}

	public void createPathInPlace(SNode contextNode, YamlPath relativePath, int insertionPoint, String appendText) throws Exception {
		int indent = getChildIndent(contextNode);
		insert(insertionPoint, createPathInsertionText(relativePath, indent, needNewline(contextNode, insertionPoint), appendText));
	}

	private boolean needNewline(SNode contextNode, int insertionPoint) throws Exception {
		if (contextNode.getNodeType() == SNodeType.SEQ) {
			// after a '- ' its okay to put key on same line
			return false;
		} else {
			return lineHasTextBefore(insertionPoint);
		}
	}

	private boolean lineHasTextBefore(int insertionPoint) throws Exception {
		String textBefore = doc.getLineTextBefore(insertionPoint);
		return !textBefore.trim().isEmpty();
	}

	/**
	 * Deletes this node, and all of its children.
	 */
	public void deleteNode(SNode node) throws Exception {
		delete(node.getStart(), node.getTreeEnd());
		deleteLineBackwardAtOffset(node.getStart());
	}

}
