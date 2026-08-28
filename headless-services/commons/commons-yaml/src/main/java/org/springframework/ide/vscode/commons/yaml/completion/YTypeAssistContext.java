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

import static org.springframework.ide.vscode.commons.languageserver.completion.AbstractScoreableProposal.DEEMP_DASH_PROPOSAL;
import static org.springframework.ide.vscode.commons.languageserver.completion.AbstractScoreableProposal.DEEMP_DEPRECATION;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.languageserver.completion.DocumentEdits;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposal;
import org.springframework.ide.vscode.commons.languageserver.completion.AbstractScoreableProposal;
import org.springframework.ide.vscode.commons.languageserver.completion.TransformedCompletion;
import org.springframework.ide.vscode.commons.languageserver.util.PlaceHolderString;
import org.springframework.ide.vscode.commons.util.CollectionUtil;
import org.springframework.ide.vscode.commons.util.ExceptionUtil;
import org.springframework.ide.vscode.commons.util.FuzzyMatcher;
import org.springframework.ide.vscode.commons.util.PartialCollection;
import org.springframework.ide.vscode.commons.util.Renderable;
import org.springframework.ide.vscode.commons.util.ValueParseException;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.yaml.completion.DefaultCompletionFactory.ValueProposal;
import org.springframework.ide.vscode.commons.yaml.hover.YPropertyInfoTemplates;
import org.springframework.ide.vscode.commons.yaml.path.YamlPath;
import org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment;
import org.springframework.ide.vscode.commons.yaml.path.YamlPathSegment.YamlPathSegmentType;
import org.springframework.ide.vscode.commons.yaml.schema.DynamicSchemaContext;
import org.springframework.ide.vscode.commons.yaml.schema.ISubCompletionEngine;
import org.springframework.ide.vscode.commons.yaml.schema.SNodeDynamicSchemaContext;
import org.springframework.ide.vscode.commons.yaml.schema.YType;
import org.springframework.ide.vscode.commons.yaml.schema.YTypeUtil;
import org.springframework.ide.vscode.commons.yaml.schema.YTypedProperty;
import org.springframework.ide.vscode.commons.yaml.schema.YValueHint;
import org.springframework.ide.vscode.commons.yaml.snippet.Snippet;
import org.springframework.ide.vscode.commons.yaml.snippet.TypeBasedSnippetProvider;
import org.springframework.ide.vscode.commons.yaml.structure.YamlDocument;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SNode;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SNodeType;
import org.springframework.ide.vscode.commons.yaml.util.YamlIndentUtil;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class YTypeAssistContext extends AbstractYamlAssistContext {

	final static Logger logger = LoggerFactory.getLogger(YTypeAssistContext.class);

	final private YTypeUtil typeUtil;
	final private YType type;
	final private YamlAssistContext parent;

	/**
	 * Create a 'relaxed' {@link YTypeAssistContext} that pretends the type expected in this context
	 * is actually something else than the schema sugests.
	 */
	public YTypeAssistContext(YTypeAssistContext relaxationTarget, YType relaxedType) {
		super(relaxationTarget.getDocument(), relaxationTarget.documentSelector, relaxationTarget.contextPath);
		this.parent = relaxationTarget.parent;
		this.typeUtil = relaxationTarget.typeUtil;
		this.type = relaxedType;
	}

	public YTypeAssistContext(YTypeAssistContext parent, YamlPath contextPath, YType type, YTypeUtil typeUtil) {
		super(parent.getDocument(), parent.documentSelector, contextPath);
		this.parent = parent;
		this.typeUtil = typeUtil;
		this.type = typeUtil.inferMoreSpecificType(type, getSchemaContext());
	}

	public YTypeAssistContext(TopLevelAssistContext parent, int documentSelector, YType type, YTypeUtil typeUtil) {
		super(parent.getDocument(), documentSelector, YamlPath.EMPTY);
		this.parent = parent;
		this.typeUtil = typeUtil;
		this.type = typeUtil.inferMoreSpecificType(type, getSchemaContext());
	}

	@Override
	public Collection<ICompletionProposal> getCompletions(YamlDocument doc, SNode node, int offset) throws Exception {
		ISubCompletionEngine customContentAssistant = typeUtil.getCustomContentAssistant(type);
		if (customContentAssistant != null) {
			DocumentRegion region = getCustomAssistRegion(doc, node, offset);
			if (region != null) {
				return customContentAssistant.getCompletions(completionFactory(), region, region.toRelative(offset));
			}
		}
		if (typeUtil.isTrueUnion(type)) {
			Collection<YType> unionSubTypes = typeUtil.getUnionSubTypes(type);
			//When a union type was not inferred to one of its subtypes...
			//Then suggest completions for all of its subtypes since, presumably they are
			//all valid in this context at the moment.
			List<ICompletionProposal> completions = new ArrayList<>();
			for (YType unionSubType : unionSubTypes) {
				YTypeAssistContext unionContext = new YTypeAssistContext(this, unionSubType);
				completions.addAll(unionContext.getCompletions(doc, node, offset));
			}
			return completions;
		} else {
			String query = getPrefix(doc, node, offset);
			List<ICompletionProposal> completions = getValueCompletions(doc, node, offset, query);
			if (completions.isEmpty()) {
				TypeBasedSnippetProvider snippetProvider = typeUtil.getSnippetProvider();
				if (snippetProvider != null) {
					Collection<Snippet> snippets = snippetProvider.getSnippets(type);
					YamlIndentUtil indenter = new YamlIndentUtil(doc);
					for (Snippet snippet : snippets) {
						String snippetName = snippet.getName();
						double score = FuzzyMatcher.matchScore(query, snippetName);
						if (score != 0.0 && snippet.isApplicable(getSchemaContext())) {
							DocumentEdits edits = createEditFromSnippet(doc, node, offset, query, indenter, snippet);
							completions.add(completionFactory().valueProposal(snippetName, query, snippetName, type, null, score, edits, typeUtil));
						}
					}
				}
				completions.addAll(getKeyCompletions(doc, node, offset, query));
			}
			if (typeUtil.isSequencable(type)) {
				completions = new ArrayList<>(completions);
				completions.addAll(getDashedCompletions(doc, node, offset));
			}
			return completions;
		}
	}

	private DocumentEdits createEditFromSnippet(YamlDocument doc, SNode node, int offset, String query, YamlIndentUtil indenter,
			Snippet _snippet) throws Exception {
		DocumentEdits edits = new DocumentEdits(doc.getDocument(), true);
		int start = offset - query.length();
		edits.delete(start, query);
		int referenceIndent = doc.getColumn(start);

		boolean needNewline = node.getNodeType() == SNodeType.KEY;
		String snippet = _snippet.getSnippet();
		if (needNewline) {
			snippet = "\n"+snippet;
			referenceIndent = YamlIndentUtil.getNewChildKeyIndent(node);
		} else if (start > 0 && !Character.isWhitespace(doc.getChar(start-1))) {
			referenceIndent++;
			edits.insert(start, " ");
		}
		edits.insert(start, indenter.applyIndentation(snippet, referenceIndent));
		return edits;
	}

	public List<ICompletionProposal> getKeyCompletions(YamlDocument doc, SNode node, int offset, String query) throws Exception {
		int queryOffset = offset - query.length();
		DynamicSchemaContext dynamicCtxt = getSchemaContext();
		List<YTypedProperty> allProperties = typeUtil.getProperties(type);
		if (CollectionUtil.hasElements(allProperties)) {
			List<List<YTypedProperty>> tieredProperties = sortIntoTiers(allProperties);
			Set<String> definedProps = dynamicCtxt.getDefinedProperties();
			List<ICompletionProposal> proposals = new ArrayList<>();
			boolean suggestDeprecated = typeUtil.suggestDeprecatedProperties();
			YamlIndentUtil indenter = new YamlIndentUtil(doc);
			for (List<YTypedProperty> thisTier : tieredProperties) {
				List<YTypedProperty> undefinedProps = thisTier.stream()
						.filter(p -> !definedProps.contains(p.getName()) && (suggestDeprecated || !p.isDeprecated()))
						.collect(Collectors.toList());
				if (!undefinedProps.isEmpty()) {
					for (YTypedProperty p : undefinedProps) {
						String name = p.getName();
						double score = FuzzyMatcher.matchScore(query, name);
						if (score != 0) {
							TypeBasedSnippetProvider snippetProvider = typeUtil.getSnippetProvider();
							DocumentEdits edits;
							if (snippetProvider != null) {
								// Generate edits from snippet
								Snippet snippet = snippetProvider.getSnippet(p);
								edits = createEditFromSnippet(doc, node, offset, query, indenter, snippet);
							} else {
								//Generate edits the old-fashioned way
								edits = new DocumentEdits(doc.getDocument(), false);
								YType YType = p.getType();
								edits.delete(queryOffset, query);
								int referenceIndent = doc.getColumn(queryOffset);
								boolean needNewline = node.getNodeType() == SNodeType.KEY;
								StringBuilder snippet = new StringBuilder();
								if (needNewline) {
									snippet.append("\n");
									referenceIndent = YamlIndentUtil.getNewChildKeyIndent(node);
								} else if (queryOffset > 0 && !Character.isWhitespace(doc.getChar(queryOffset-1))) {
									//See https://www.pivotaltracker.com/story/show/137722057
									snippet.append(" ");
									referenceIndent++;
								}
								snippet.append(p.getName());
								snippet.append(":");
								snippet.append(appendTextFor(YType, doc));
								edits.insert(queryOffset, indenter.applyIndentation(snippet.toString(), referenceIndent));
							}
							ICompletionProposal completion = completionFactory().beanProperty(doc.getDocument(),
									contextPath.toPropString(), getType(),
									query, p, score, edits, typeUtil);
							if (p.isDeprecated() && completion instanceof AbstractScoreableProposal) {
								completion.deemphasize(DEEMP_DEPRECATION);
							}
							proposals.add(completion);
						}
					}
				}
				//We should only move on to the next tier if all required properties in this tier are defined.
				if (undefinedProps.stream().anyMatch(p -> p.isRequired())) {
					return proposals; //stop here, take no more from next tier!
				}
			}
			return proposals;
		}
		return Collections.emptyList();
	}

	/**
	 * Divides a given list of properties into tiers of decreasing significance. Property tiering
	 * is a mechanism to reduce 'noise' in content assist proposals. Only properties of the
	 * first X tiers will be used, until those tiers no longer have undefined required properties.
	 * <p>
	 * This allows, for example, to only suggest a 'name' property when starting to define
	 * a new named entity. This is what a sane user would probably want, even though
	 * in theory they would be free to define the properties in any order they want.
	 */
	protected List<List<YTypedProperty>> sortIntoTiers(List<YTypedProperty> properties) {
		boolean tieredOptionals = typeUtil.tieredOptionalPropertyProposals();
		if (properties.isEmpty()) {
			//Nothing to sort
			return ImmutableList.of();
		} else {
			ImmutableList.Builder<YTypedProperty> primary = ImmutableList.builder();
			ImmutableList.Builder<YTypedProperty> tier2 = ImmutableList.builder();
			ImmutableList.Builder<YTypedProperty> tier3 = ImmutableList.builder();
			for (YTypedProperty p : properties) {
				if (p.isPrimary()) {
					primary.add(p);
				} else if (!tieredOptionals || p.isRequired()) {
					tier2.add(p);
				} else {
					tier3.add(p);
				}
			}
			if (tieredOptionals) {
				return ImmutableList.of(primary.build(), tier2.build(), tier3.build());
			} else {
				return ImmutableList.of(primary.build(), tier2.build());
			}
		}
	}

	/**
	 * Computes the text that should be appended at the end of a completion
	 * proposal depending on what type of value is expected.
	 * @param doc 
	 */
	protected String appendTextFor(YType type, YamlDocument doc) {
		return new AppendTextBuilder(typeUtil).buildFor(type, doc);
	}

	private List<ICompletionProposal> getValueCompletions(YamlDocument doc, SNode node, int offset, String query) {
		PartialCollection<YValueHint> _values = typeUtil.getHintValues(type, getSchemaContext());
		if (_values.getExplanation() != null && _values.getElements().isEmpty() && !Boolean.getBoolean("lsp.yaml.completions.errors.disable")) {
			return ImmutableList.of(completionFactory().errorMessage(query, getMessage(_values.getExplanation())));
		}
		Collection<YValueHint> values = _values.getElements();
		if (values != null) {
			ArrayList<ICompletionProposal> completions = new ArrayList<>();
			YamlIndentUtil indenter = new YamlIndentUtil(doc);
			int referenceIndent;
			try {
				referenceIndent = getContextNode().getIndent();
			} catch (Exception e) {
				//Getting it from the node isn't always correct, but more often than not it is.
				//So this fallback is better than nothing.
				referenceIndent = node.getIndent();
			}
			for (YValueHint value : values) {
				double score = FuzzyMatcher.matchScore(query, value.getValue());
				if (score != 0 && value != null && !query.equals(value.getValue())) {
					int queryStart = offset-query.length();
					DocumentEdits edits = new DocumentEdits(doc.getDocument(), false);
					edits.delete(queryStart, offset);
					if (!Character.isWhitespace(doc.getChar(queryStart-1))) {
						edits.insert(offset, " ");
					}
					edits.insert(offset, value.getValue());
					PlaceHolderString extraInsertion = value.getExtraInsertion();
					if (extraInsertion != null) {
						String insertText = indenter.applyIndentation(extraInsertion.toString(), referenceIndent);
						if (extraInsertion.hasPlaceHolders()) {
							edits.insertSnippet(offset, insertText);
						} else {
							edits.insert(offset, insertText);
						}
					}
					completions.add(completionFactory().valueProposal(
							value.getValue(), query, value.getLabel(), type,
							value.getDocumentation(), score, edits, typeUtil
					));
				}
			}
			return completions;
		}
		return Collections.emptyList();
	}

	private String getMessage(Throwable _e) {
		Throwable e = ExceptionUtil.getDeepestCause(_e);

		// If value parse exception, do not append any additional information
		if (e instanceof ValueParseException) {
			return ExceptionUtil.getMessageNoAppendedInformation(e);
		} else {
			return ExceptionUtil.getMessage(e);
		}
	}

	@Override
	public YamlAssistContext traverse(YamlPathSegment s) throws Exception {
		YamlAssistContext result = _traverse(s);
		logger.debug("Traversing {} with {} => {}", this, s, result);
		return result;
	}

	protected YamlAssistContext _traverse(YamlPathSegment s) {
		if (s.getType() == YamlPathSegmentType.VAL_AT_KEY) {
			if (typeUtil.isSequencable(type) || typeUtil.isMap(type)) {
				return contextWith(s, typeUtil.getDomainType(type));
			}
			String key = s.toPropString();
			Map<String, YTypedProperty> subproperties = typeUtil.getPropertiesMap(type);
			if (subproperties != null) {
				return contextWith(s, getType(subproperties.get(key)));
			}
		} else if (s.getType() == YamlPathSegmentType.VAL_AT_INDEX) {
			if (typeUtil.isSequencable(type)) {
				return contextWith(s, typeUtil.getDomainType(type));
			}
		}
		return null;
	}

	private YType getType(YTypedProperty prop) {
		if (prop != null) {
			return prop.getType();
		}
		return null;
	}

	private YamlAssistContext contextWith(YamlPathSegment s, YType nextType) {
		if (nextType != null) {
			return new YTypeAssistContext(this, contextPath.append(s), nextType, typeUtil);
		}
		return null;
	}


	@Override
	public String toString() {
		return "TypeContext("+contextPath.toPropString()+"::"+type+")";
	}

	@Override
	public Renderable getHoverInfo() {
		if (parent != null) {
			return parent.getHoverInfo(contextPath.getLastSegment());
		}
		return null;
	}

	public YType getType() {
		return type;
	}

	@Override
	public Renderable getHoverInfo(YamlPathSegment lastSegment) {
		//Hoverinfo is only attached to YTypedProperties so...
		switch (lastSegment.getType()) {
		case VAL_AT_KEY:
		case KEY_AT_KEY:
			YTypedProperty prop = getProperty(lastSegment.toPropString());
			if (prop != null) {
				return YPropertyInfoTemplates.createHover(contextPath.toPropString(), getType(), prop);
			}
			break;
		default:
		}
		return null;
	}

	protected DynamicSchemaContext getSchemaContext() {
		try {
			SNode contextNode = getContextNode();
			YamlPath fullContextPath = contextPath.prepend(YamlPathSegment.valueAt(documentSelector));
			return new SNodeDynamicSchemaContext(contextNode, fullContextPath);
		} catch (Exception e) {
			logger.error("", e);
			return DynamicSchemaContext.NULL;
		}
	}

	@Override
	public Renderable getValueHoverInfo(YamlDocument doc, DocumentRegion documentRegion) {
		//By default we don't provide value-specific hover, so just show the same hover
		// as the assistContext the value is in. This is likely more interesting than showing nothing at all.
		return getHoverInfo();
	}

	private YTypedProperty getProperty(String name) {
		return typeUtil.getPropertiesMap(getType()).get(name);
	}

	protected YamlAssistContext relaxForDashes() {
		try {
			if (typeUtil.isSequencable(type)) {
				YType itemType = typeUtil.getDomainType(type);
				if (itemType != null) {
					return new YTypeAssistContext(this, itemType) {
						@Override
						public Collection<ICompletionProposal> getCompletions(YamlDocument doc, SNode node, int offset) throws Exception {
							Collection<ICompletionProposal> basicCompletions = super.getCompletions(doc, node, offset);
							return addDashes(basicCompletions, doc, node);
						}
					};
				}
			}
		} catch (Exception e) {
			logger.error("", e);
		}
		return null;
	}

	protected Collection<ICompletionProposal> getDashedCompletions(YamlDocument doc, SNode current, int offset) {
		try {
			YamlAssistContext relaxed = relaxForDashes();
			if (relaxed != null) {
				return relaxed.getCompletions(doc, current, offset);
			}
		} catch (Exception e) {
			logger.error("", e);
		}
		return ImmutableList.of();
	}

	private Collection<ICompletionProposal> addDashes(Collection<ICompletionProposal> basicCompletions, YamlDocument doc, SNode node) {
		if (!basicCompletions.isEmpty()) {
			List<ICompletionProposal> dashedCompletions = new ArrayList<>(basicCompletions.size());
			for (ICompletionProposal c : basicCompletions) {
				dashedCompletions.add(
					new TransformedCompletion(c) {
						@Override
						protected DocumentEdits transformEdit(DocumentEdits textEdit) {
							textEdit.transformFirstNonWhitespaceEdit((Integer offset, String insertText) -> {
								YamlIndentUtil indenter = new YamlIndentUtil(doc);
								if (needNewline(textEdit)) {
									return insertText.substring(0,  offset)
											+ "\n" +Strings.repeat(" ", node.getIndent())+"- "
											+ indenter.applyIndentation(insertText.substring(offset), YamlIndentUtil.INDENT_BY);
								} else if (offset > 2) {
									String prefix = insertText.substring(offset-2, offset);
									if ("  ".equals(prefix)) {
										//special case don't add the "- " in front, but replace the inserted spaces instead.
										return insertText.substring(0, offset-2)
												+ "- "+ insertText.substring(offset);
									}
								}
								return insertText.substring(0, offset) + "- "+indenter.applyIndentation(insertText.substring(offset), YamlIndentUtil.INDENT_BY);
							});
							return textEdit;
						}

						private boolean needNewline(DocumentEdits textEdit) {
							//value proposals which are inserted right after a key will not automatically include a newline, as
							// its not required for them. So we should add it along with the dash.
							try {
								Integer insertAt = textEdit.getFirstEditStart();
								if (insertAt != null && c instanceof ValueProposal) {
									//Value proposals don't automatically get newline added (because they don't require it), so we dmust add it here.
									return !"".equals(doc.getLineTextBefore(insertAt).trim());
								}
							} catch (Exception e) {
								logger.error("", e);
							}
							return false;
						}

						@Override
						protected String tranformLabel(String originalLabel) {
							return "- "+originalLabel;
						}
					}.deemphasize(DEEMP_DASH_PROPOSAL)
				);
			}
			return dashedCompletions;
		}
		return basicCompletions;
	}

}
