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
package org.springframework.ide.eclipse.editor.support.yaml;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.springframework.ide.eclipse.editor.support.EditorSupportActivator;
import org.springframework.ide.eclipse.editor.support.completions.CompletionFactory;
import org.springframework.ide.eclipse.editor.support.completions.ICompletionEngine;
import org.springframework.ide.eclipse.editor.support.util.YamlIndentUtil;
import org.springframework.ide.eclipse.editor.support.yaml.completions.YamlAssistContext;
import org.springframework.ide.eclipse.editor.support.yaml.path.YamlPath;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureParser.SKeyNode;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureParser.SNode;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureParser.SNodeType;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureParser.SRootNode;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureParser.SSeqNode;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureProvider;

/**
 * Abstract superclass to make it easier to define {@link ICompletionEngine} implementation
 * for .yml file.
 *
 * @author Kris De Volder
 */
public class YamlCompletionEngine implements ICompletionEngine {

	private final YamlAssistContextProvider contextProvider;
	protected final YamlStructureProvider structureProvider;

	public YamlCompletionEngine(YamlStructureProvider structureProvider, YamlAssistContextProvider contextProvider) {
		Assert.isNotNull(structureProvider);
		Assert.isNotNull(contextProvider);
		this.structureProvider= structureProvider;
		this.contextProvider = contextProvider;
	}

	protected final YamlAssistContext getGlobalContext(YamlDocument doc) {
		return contextProvider.getGlobalAssistContext(doc);
	}

	protected CompletionFactory proposalFactory() {
		return CompletionFactory.DEFAULT;
	}

	@Override
	public Collection<ICompletionProposal> getCompletions(IDocument _doc, int offset) throws Exception {
		YamlDocument doc = new YamlDocument(_doc, structureProvider);
		if (!doc.isCommented(offset)) {
			SRootNode root = doc.getStructure();
			SNode current = root.find(offset);
			YamlPath contextPath = getContextPath(doc, current, offset);
			YamlAssistContext context = getContext(doc, offset, current, contextPath);
			if (context == null && isDubiousKey(current, offset)) {
				current = current.getParent();
				contextPath = contextPath.dropLast();
				context = getContext(doc, offset, current, contextPath);
			}
			if (context != null) {
				return context.getCompletions(doc, current, offset);
			}
		}
		return Collections.emptyList();
	}

	/**
	 * A 'dubious' key is when the cursor is positioned right after a key's ':' character.
	 * This key is 'dubious' in that if the user would type a non-whitespace character next...
	 * then that key is no longer a key but parses as a 'value' instead.
	 */
	private boolean isDubiousKey(SNode node, int offset) {
		if (node.getNodeType() == SNodeType.KEY) {
			SKeyNode key = (SKeyNode)node;
			return key.getColonOffset()+1 == offset;
		}
		return false;
	}

	protected YamlAssistContext getContext(YamlDocument doc, int offset, SNode node, YamlPath contextPath) {
		try {
			return contextPath.traverse(getGlobalContext(doc));
		} catch (Exception e) {
			EditorSupportActivator.log(e);
			return null;
		}
	}

	protected YamlPath getContextPath(YamlDocument doc, SNode node, int offset) throws Exception {
		if (node == null) {
			return YamlPath.EMPTY;
		} else if (node.getNodeType() == SNodeType.KEY) {
			//slight complication. The area in the key and value of a key node represent different
			// contexts for content assistance
			SKeyNode keyNode = (SKeyNode)node;
			if (keyNode.isInValue(offset)) {
				return keyNode.getPath();
			} else {
				return keyNode.getParent().getPath();
			}
		} else if (node.getNodeType() == SNodeType.RAW) {
			//Treat raw node as a 'key node'. This is basically assuming that is misclasified
			// by structure parser because the ':' was not yet typed into the document.

			//Complication: if line with cursor is empty or the cursor is inside the indentation
			// area then the structure may not reflect correctly the context. This is because
			// the correct context depends on text the user has not typed yet.(which will change the
			// indentation level of the current line. So we must use the cursorIndentation
			// rather than the structur-tree to determine the 'context' node.
			int cursorIndent = doc.getColumn(offset);
			int nodeIndent = node.getIndent();
			int currentIndent = YamlIndentUtil.minIndent(cursorIndent, nodeIndent);
			while (node.getIndent() == -1 || (node.getIndent() >= currentIndent && node.getNodeType() != SNodeType.DOC)) {
				node = node.getParent();
			}
			return node.getPath();
		} else if (node.getNodeType() == SNodeType.SEQ) {
			SSeqNode seqNode = (SSeqNode)node;
			if (seqNode.isInValue(offset)) {
				return seqNode.getPath();
			} else {
				return seqNode.getParent().getPath();
			}
		} else if (node.getNodeType() == SNodeType.DOC) {
			return node.getPath();
		} else {
			throw new IllegalStateException("Missing case");
		}
	}

}
