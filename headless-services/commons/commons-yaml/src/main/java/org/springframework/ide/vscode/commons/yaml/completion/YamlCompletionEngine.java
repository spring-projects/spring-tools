/*******************************************************************************
 * Copyright (c) 2016, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.yaml.completion;

import static org.springframework.ide.vscode.commons.languageserver.completion.AbstractScoreableProposal.DEEMP_DEDENTED_PROPOSAL;
import static org.springframework.ide.vscode.commons.languageserver.completion.AbstractScoreableProposal.DEEMP_INDENTED_PROPOSAL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.languageserver.completion.DocumentEdits;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionEngine;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposal;
import org.springframework.ide.vscode.commons.languageserver.completion.InternalCompletionList;
import org.springframework.ide.vscode.commons.languageserver.completion.AbstractScoreableProposal;
import org.springframework.ide.vscode.commons.languageserver.completion.TransformedCompletion;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.Unicodes;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.structure.YamlDocument;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SChildBearingNode;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SKeyNode;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SNode;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SNodeType;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SRootNode;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SSeqNode;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureProvider;
import org.springframework.ide.vscode.commons.yaml.util.YamlIndentUtil;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

/**
 * Implements {@link ICompletionEngine} for .yml file, based on a YamlAssistContextProvider
 * which has to to be injected into engine via its constructor.
 *
 * @author Kris De Volder
 */
public class YamlCompletionEngine implements ICompletionEngine {

	Pattern SPACES = Pattern.compile("[ ]+");

	final static Logger logger = LoggerFactory.getLogger(YamlCompletionEngine.class);


	private final YamlAssistContextProvider contextProvider;
	protected final YamlStructureProvider structureProvider;
	private YamlCompletionEngineOptions options;


	public YamlCompletionEngine(YamlStructureProvider structureProvider, YamlAssistContextProvider contextProvider, YamlCompletionEngineOptions options) {
		this.options = options;
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
	public InternalCompletionList getCompletions(TextDocument _doc, int offset) throws Exception {
		YamlDocument doc = new YamlDocument(_doc, structureProvider);
		if (!doc.isCommented(offset)) {
			SRootNode root = doc.getStructure();
			SNode current = root.find(offset);
			int cursorIndent = doc.getColumn(offset);
			int nodeIndent = current.getIndent();
			int baseIndent = YamlIndentUtil.minIndent(cursorIndent, nodeIndent);
			List<SNode> contextNodes = getContextNodes(doc, current, offset, baseIndent);
			if (current.getNodeType() == SNodeType.RAW) {
				//relaxed indentation
				List<ICompletionProposal> completions = new ArrayList<>();
				double deempasizeBy = 0.0;
				for (SNode contextNode : contextNodes) {
					completions.addAll(getRelaxedCompletions(offset, doc, current, contextNode, baseIndent, deempasizeBy));
					deempasizeBy += AbstractScoreableProposal.DEEMP_NEXT_CONTEXT;
				}
				return new InternalCompletionList(completions, false);
			} else {
				//precise indentation only
				Assert.isLegal(contextNodes.size() <= 1);
				for (SNode contextNode : contextNodes) {
					return new InternalCompletionList(getBaseCompletions(offset, doc, current, contextNode), false);
				}
			}
		}
		return new InternalCompletionList(Collections.emptyList(), false);
	}

	protected Collection<? extends ICompletionProposal> getRelaxedCompletions(int offset, YamlDocument doc, SNode current, SNode contextNode, int baseIndent, double deempasizeBy) {
		try {
			return fixIndentations(getBaseCompletions(offset, doc, current, contextNode),
					current, contextNode, baseIndent, deempasizeBy, doc);
		} catch (Exception e) {
			logger.error("", e);
		}
		return ImmutableList.of();
	}

	protected Collection<? extends ICompletionProposal> fixIndentations(Collection<ICompletionProposal> completions, SNode currentNode,
			SNode contextNode, int baseIndent, double deempasizeBy, YamlDocument doc) {
		if (!completions.isEmpty()) {
			int dashyIndent = getTargetIndent(contextNode, currentNode, true);
			int plainIndent = getTargetIndent(contextNode, currentNode, false);
			List<ICompletionProposal> transformed = new ArrayList<>();
			for (ICompletionProposal p : completions) {
				int targetIndent = p.getLabel().startsWith("- ") ? dashyIndent : plainIndent;
				AbstractScoreableProposal p_fixed = indentFix((AbstractScoreableProposal)p, targetIndent - baseIndent, currentNode, contextNode, doc);
				if (p_fixed != null) {
					p_fixed.deemphasize(deempasizeBy);
					transformed.add(p_fixed);
				}
			}
			return transformed;
		}
		return Collections.emptyList();
	}

	protected AbstractScoreableProposal indentFix(AbstractScoreableProposal p, int fixIndentBy, SNode currentNode, SNode contextNode, YamlDocument doc) {
		if (fixIndentBy == 0) {
			return p;
		} else if (fixIndentBy > 0) {
			if (isExtraIndentRelaxable(contextNode, fixIndentBy)) {
				return indented(p, Strings.repeat(" ", fixIndentBy), doc);
			}
		} else { // fixIndentBy < 0
			if (isLesserIndentRelaxable(currentNode, contextNode)) {
				return dedented(p, -fixIndentBy, contextNode.getDocument());
			}
		}
		return null;
	}

	protected final boolean isLesserIndentRelaxable(final SNode currentNode, final SNode contextNode) {
		if (!options.includeDeindentedProposals()) {
			return false;
		}
		SChildBearingNode parent = currentNode.getParent();
		while (parent != null && parent != contextNode) {
			SNode lastChild = parent.getLastRealChild();
			if (lastChild != null && lastChild.getStart() >= currentNode.getNodeEnd()) {
				return false;
			}
			parent = parent.getParent();
		}
		return true;
	}

	/**
	 * Determine the indentation level needed to line up with other contextNode children.
	 * If the contextNode has no children, then compute a proper default indentation where
	 * a new child could be added.
	 */
	private int getTargetIndent(SNode contextNode, SNode currentNode, boolean dashy) {
		Optional<SNode> child = Optional.empty();
		if (contextNode instanceof SChildBearingNode) {
			child = ((SChildBearingNode)contextNode).getChildren().stream()
					.filter(c -> c != currentNode && c.getIndent() >= 0)
					.max((c1, c2) -> Integer.compare(c1.getIndent(), c2.getIndent()));
		}
		if (child.isPresent()) {
			return child.get().getIndent();
		}
		return (dashy || contextNode.getNodeType() == SNodeType.DOC)
				? contextNode.getIndent()
				: contextNode.getIndent() + YamlIndentUtil.INDENT_BY;
	}

	public AbstractScoreableProposal dedented(ICompletionProposal proposal, int numSpacesToRemove, IDocument doc) {
		Assert.isLegal(numSpacesToRemove > 0);
		int spacesEnd = proposal.getTextEdit().getFirstEditStart();
		int spacesStart = spacesEnd-numSpacesToRemove;
		int numArrows = numSpacesToRemove / YamlIndentUtil.INDENT_BY;
		String spaces = new DocumentRegion(doc, spacesStart, spacesEnd).toString();
		YamlIndentUtil indenter = new YamlIndentUtil(doc);
		if (spaces.length() == numSpacesToRemove && SPACES.matcher(spaces).matches()) {
			AbstractScoreableProposal transformed = new TransformedCompletion(proposal) {
				@Override public String tranformLabel(String originalLabel) {
					return Strings.repeat(Unicodes.LEFT_ARROW+" ", numArrows)  + originalLabel;
				}
				@Override public DocumentEdits transformEdit(DocumentEdits originalEdit) {
					originalEdit.firstDelete(spacesStart, spacesEnd);
					originalEdit.transformFirstNonWhitespaceEdit((offset, insertText) -> {
						String prefix = insertText.substring(0, offset);
						String dedented = indenter.applyIndentation(insertText.substring(offset), -numSpacesToRemove);
						return prefix + dedented;
					});
					return originalEdit;
				}
				@Override
				public String getFilterText() {
					//If we don't add the spaces, vscode won't show the completions.
					// Presumably this is because it matches the filtter text to the text it thinks its going
					// to replace. Since we are replacing these removed spaces, they must be part of the filtertext
					return spaces + super.getFilterText();
				}
			};
			transformed.deemphasize(DEEMP_DEDENTED_PROPOSAL*numArrows);
			return transformed;
		}
		// we can't dedent the proposal by the requested amount of space. So err on the safe
		// side and ignore the proposal. (Otherwise me might end up deleting non-space chars
		// in our attempt to de-dent.)
		return null;
	}

	public AbstractScoreableProposal indented(ICompletionProposal proposal, String indentStr, YamlDocument doc) {
		int numArrows = (indentStr.length()+1)/2;
		AbstractScoreableProposal transformed = new TransformedCompletion(proposal) {
			@Override public String tranformLabel(String originalLabel) {
				return Strings.repeat(Unicodes.RIGHT_ARROW+" ", numArrows) + originalLabel;
			}
			@Override public DocumentEdits transformEdit(DocumentEdits originalEdit) {
//				originalEdit.indentFirstEdit(indentStr);
				YamlIndentUtil indenter = new YamlIndentUtil(doc);
				if (originalEdit.hasRelativeIndents()) {
					originalEdit.transformFirstNonWhitespaceEdit((Integer offset, String insertText) -> {
						String prefix = insertText.substring(0, offset);
						String target = insertText.substring(offset);
						return prefix + indentStr + indenter.applyIndentation(target, indentStr);
					});
				} else {
					originalEdit.transformFirstNonWhitespaceEdit((Integer offset, String insertText) -> {
						String prefix = insertText.substring(0, offset);
						String target = insertText.substring(offset);
						return prefix + indentStr + target;
					});
				}
				return originalEdit;
			}
		};
		transformed.deemphasize(numArrows * DEEMP_INDENTED_PROPOSAL);
		return transformed;
	}

	private boolean isExtraIndentRelaxable(SNode contextNode, int fixIndentBy) {
		return contextNode != null && /* fixIndentBy <= 2 && */ (
				isBarrenKey(contextNode) ||
				isBarrenSeq(contextNode)
		);
	}

	private boolean isBarrenSeq(SNode node) {
		try {
			if (node.getNodeType() == SNodeType.SEQ) {
				SSeqNode seqNode = (SSeqNode) node;
				String value = seqNode.getTextWithoutChildren();
				return "-".equals(value.trim());
			}
		} catch (Exception e) {
			logger.error("", e);
		}
		return false;
	}

	private boolean isBarrenKey(SNode node) {
		try {
			if (node.getNodeType() == SNodeType.KEY) {
				SKeyNode keyNode = (SKeyNode) node;
				String value = keyNode.getSimpleValue();
				return value.trim().isEmpty();
			}
		} catch (Exception e) {
			logger.error("", e);
		}
		return false;
	}

	private Collection<ICompletionProposal> getBaseCompletions(int offset, YamlDocument doc, SNode current, SNode contextNode) throws Exception {
		if (contextNode != null) {
			YamlAssistContext context = getContext(doc, contextNode);
			if (context == null && isDubiousKey(contextNode, offset)) {
				current = current.getParent();
				contextNode = contextNode.getParent();
				context = getContext(doc, contextNode);
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

	protected YamlAssistContext getContext(YamlDocument doc, SNode contextNode) {
		try {
			if (contextNode != null) {
				YamlPath contextPath = contextNode.getPath();
				return contextPath.traverse(getGlobalContext(doc));
			}
		} catch (Exception e) {
			logger.error("Error obtaining YamlAssistContext", e);
		}
		return null;
	}

	/**
	 * Get context node candidates taking into account that we want to have a 'relaxed' interpretation
	 * of the context node with respect to the current indentation where we ask for a completion.
	 * To allow for the ambiguity in indentation a list of context nodes is returned instead of a
	 * single node. (Note we may still return a singleton list for cases where relaxed indentation
	 * doesn't seem desirable).
	 * @param baseIndent
	 */
	protected List<SNode> getContextNodes(YamlDocument doc, SNode node, int offset, int baseIndent) {
		if (node == null) {
			return null;
		} else if (node.getNodeType() == SNodeType.KEY) {
			//slight complication. The area in the key and value of a key node represent different
			// contexts for content assistance
			SKeyNode keyNode = (SKeyNode)node;
			if (keyNode.isInValue(offset)) {
				return ImmutableList.of(keyNode);
			} else {
				return ImmutableList.of(keyNode.getParent());
			}
		} else if (node.getNodeType() == SNodeType.SEQ) {
			SSeqNode seqNode = (SSeqNode)node;
			if (seqNode.isInValue(offset)) {
				return ImmutableList.of(seqNode);
			} else {
				return ImmutableList.of(seqNode.getParent());
			}
		} else if (node.getNodeType() == SNodeType.DOC) {
			return ImmutableList.of(node);
		} else if (node.getNodeType() == SNodeType.RAW) {
			//This node has flexibility around indentation. So this is where me need to build a list of candidates!
			ImmutableList.Builder<SNode> contextNodes = ImmutableList.builder();
			while (node != null ) {
				//Any node that represents a 'step' between contexts and is not too deeply nested is kept.
				if (node.getSegment() != null && node.getIndent() <= baseIndent) {
					contextNodes.add(node);
				}
				node = node.getParent();
			}
			return contextNodes.build();
		}
		return ImmutableList.of();
	}

//	protected SNode getContextNode(YamlDocument doc, SNode node, int offset, String adjustIndentStr) throws Exception {
//		if (node == null) {
//			return null;
//		} else if (node.getNodeType() == SNodeType.KEY) {
//			//slight complication. The area in the key and value of a key node represent different
//			// contexts for content assistance
//			SKeyNode keyNode = (SKeyNode)node;
//			if (keyNode.isInValue(offset)) {
//				return keyNode;
//			} else {
//				return keyNode.getParent();
//			}
//		} else if (node.getNodeType() == SNodeType.RAW) {
//			if (adjustIndentStr.startsWith("- ")) {
//				// We are trying to determine context node for a completion that starts with a '- '.
//				// Yaml indentation rules means we have to treat this differently because '-' doesn't
//				// have to be indented to be considered as nested under a key node!
//				int cursorIndent = doc.getColumn(offset);
//				int nodeIndent = node.getIndent();
//				int currentIndent = YamlIndentUtil.minIndent(cursorIndent, nodeIndent);
//				while (node.getNodeType() != SNodeType.DOC && (
//					nodeIndent == -1 ||
//					nodeIndent>currentIndent ||
//					nodeIndent == currentIndent && (node.getNodeType() == SNodeType.SEQ || node.getNodeType() == SNodeType.RAW)
//				)) {
//					node = node.getParent();
//					nodeIndent = node.getIndent();
//				}
//				return node;
//			} else {
//				//Treat raw node as a 'key node'. This is basically assuming that is misclasified
//				// by structure parser because the ':' was not yet typed into the document.
//
//				//Complication: if line with cursor is empty or the cursor is inside the indentation
//				// area then the structure may not reflect correctly the context. This is because
//				// the correct context depends on text the user has not typed yet.(which will change the
//				// indentation level of the current line. So we must use the cursorIndentation
//				// rather than the structure-tree to determine the 'context' node.
//				int adjustIndent = adjustIndentStr == null? 0 : adjustIndentStr.length();
//				int cursorIndent = YamlIndentUtil.add(doc.getColumn(offset), adjustIndent);
//				int nodeIndent = YamlIndentUtil.add(node.getIndent(), adjustIndent);
//				int currentIndent = YamlIndentUtil.minIndent(cursorIndent, nodeIndent);
//				while (nodeIndent == -1 || (nodeIndent >= currentIndent && node.getNodeType() != SNodeType.DOC)) {
//					node = node.getParent();
//					nodeIndent = node.getIndent();
//				}
//				return node;
//			}
//		} else if (node.getNodeType() == SNodeType.SEQ) {
//			SSeqNode seqNode = (SSeqNode)node;
//			if (seqNode.isInValue(offset)) {
//				return seqNode;
//			} else {
//				return seqNode.getParent();
//			}
//		} else if (node.getNodeType() == SNodeType.DOC) {
//			return node;
//		}
//		return null;
//	}
//
//	protected SNode getContextNode(YamlDocument doc, SNode node, int offset) throws Exception {
//		return getContextNode(doc, node, offset, "");
//	}

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
	
	@Override
	public boolean keepCompletionsOrder(IDocument doc) {
		return true;
	}

}
