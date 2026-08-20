/*******************************************************************************
 * Copyright (c) 2016, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.properties.completions;

import static org.springframework.ide.vscode.boot.common.CommonLanguageTools.SPACES;
import static org.springframework.ide.vscode.boot.common.CommonLanguageTools.findLongestValidProperty;
import static org.springframework.ide.vscode.boot.common.CommonLanguageTools.getValueHints;
import static org.springframework.ide.vscode.boot.common.CommonLanguageTools.getValueType;
import static org.springframework.ide.vscode.boot.common.CommonLanguageTools.isValuePrefixChar;
import static org.springframework.ide.vscode.commons.util.StringUtil.camelCaseToHyphens;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.common.PropertyCompletionFactory;
import org.springframework.ide.vscode.boot.metadata.PropertyInfo;
import org.springframework.ide.vscode.boot.metadata.hints.HintProvider;
import org.springframework.ide.vscode.boot.metadata.hints.HintProviders;
import org.springframework.ide.vscode.boot.metadata.hints.StsValueHint;
import org.springframework.ide.vscode.boot.metadata.hints.ValueHintHoverInfo;
import org.springframework.ide.vscode.boot.metadata.types.Type;
import org.springframework.ide.vscode.boot.metadata.types.TypeParser;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil.BeanPropertyNameMode;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil.EnumCaseMode;
import org.springframework.ide.vscode.boot.metadata.types.TypedProperty;
import org.springframework.ide.vscode.boot.properties.reconcile.PropertyNavigator;
import org.springframework.ide.vscode.commons.languageserver.completion.DocumentEdits;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposal;
import org.springframework.ide.vscode.commons.languageserver.completion.LazyProposalApplier;
import org.springframework.ide.vscode.commons.languageserver.util.PrefixFinder;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.CollectionUtil;
import org.springframework.ide.vscode.commons.util.FuzzyMap;
import org.springframework.ide.vscode.commons.util.FuzzyMap.Match;
import org.springframework.ide.vscode.commons.util.FuzzyMatcher;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.java.properties.antlr.parser.AntlrParser;
import org.springframework.ide.vscode.java.properties.parser.ParseResults;
import org.springframework.ide.vscode.java.properties.parser.PropertiesAst.EmptyLine;
import org.springframework.ide.vscode.java.properties.parser.PropertiesAst.Key;
import org.springframework.ide.vscode.java.properties.parser.PropertiesAst.Node;
import org.springframework.ide.vscode.java.properties.parser.PropertiesAst.Value;

import com.google.common.collect.ImmutableList;

public class PropertiesCompletionProposalsCalculator {

	private static final Logger log = LoggerFactory.getLogger(PropertiesCompletionProposalsCalculator.class);

	private static final PrefixFinder valuePrefixFinder = new PrefixFinder() {
		@Override
		protected boolean isPrefixChar(char c) {
			return isValuePrefixChar(c);
		}

	};

	private static final PrefixFinder fuzzySearchPrefix = new PrefixFinder() {
		@Override
		protected boolean isPrefixChar(char c) {
			return !Character.isWhitespace(c);
		}
	};

	private static final PrefixFinder navigationPrefixFinder = new PrefixFinder() {
		@Override
		public String getPrefix(IDocument doc, int offset) {
			String prefix = super.getPrefix(doc, offset);
			//Check if character before looks like 'navigation'.. otherwise don't
			// return a navigationPrefix.
			char charBefore = getCharBefore(doc, prefix, offset);
			if (charBefore=='.' || charBefore==']') {
				return prefix;
			}
			return null;
		}
		private char getCharBefore(IDocument doc, String prefix, int offset) {
			try {
				if (prefix!=null) {
					int offsetBefore = offset-prefix.length()-1;
					if (offsetBefore>=0) {
						return doc.getChar(offsetBefore);
					}
				}
			} catch (BadLocationException e) {
				//ignore
			}
			return 0;
		}
		@Override
		protected boolean isPrefixChar(char c) {
			return !Character.isWhitespace(c) && c!=']' && c!=']' && c!='.';
		}
	};

	private FuzzyMap<PropertyInfo> index;
	private TypeUtil typeUtil;
	private PropertyCompletionFactory completionFactory;
	private IDocument doc;
	private int offset;
	private boolean preferLowerCaseEnums;
	private AntlrParser parser;
	private final PropertyCompletionSettings propertyCompletionSettings;

	public PropertiesCompletionProposalsCalculator(FuzzyMap<PropertyInfo> index, PropertyCompletionSettings propertyCompletionSettings,
			TypeUtil typeUtil, PropertyCompletionFactory completionFactory, IDocument doc, int offset, boolean preferLowerCaseEnums) {
		this.index = index;
		this.propertyCompletionSettings = propertyCompletionSettings;
		this.typeUtil = typeUtil;
		this.completionFactory = completionFactory;
		this.doc = doc;
		this.offset = offset;
		this.preferLowerCaseEnums = preferLowerCaseEnums;
		this.parser = new AntlrParser();
	}

	/**
	 * Create completions proposals in the context of a properties text editor.
	 */
	public Collection<ICompletionProposal> calculate() throws BadLocationException {
		ParseResults parseResults = parser.parse(doc.get());
		Node node = parseResults.ast.findNode(offset);
		if (node instanceof Value) {
			return getValueCompletions((Value)node);
		} else if (node instanceof Key || node instanceof EmptyLine || node == null) {
			return getPropertyCompletions();
		}
		return Collections.emptyList();
	}

	private Collection<ICompletionProposal> getNavigationProposals() {
		String navPrefix = navigationPrefixFinder.getPrefix(doc, offset);
		try {
			if (navPrefix!=null) {
				int navOffset = offset-navPrefix.length()-1; //offset of 'nav' operator char (i.e. '.' or ']').
				navPrefix = fuzzySearchPrefix.getPrefix(doc, navOffset);
				if (navPrefix!=null && !navPrefix.isEmpty()) {
					PropertyInfo prop = findLongestValidProperty(index, navPrefix);
					if (prop!=null) {
						int regionStart = navOffset-navPrefix.length();
						Collection<ICompletionProposal> hintProposals = getKeyHintProposals(prop, regionStart + prop.getId().length());
						if (CollectionUtil.hasElements(hintProposals)) {
							return hintProposals;
						}
						PropertyNavigator navigator = new PropertyNavigator(doc, null, typeUtil, new DocumentRegion(doc, regionStart, navOffset));
						Type type = navigator.navigate(regionStart+prop.getId().length(), TypeParser.parse(prop.getType()));
						if (type!=null) {
							return getNavigationProposals(type, navOffset);
						}
					}
				}
			}
		} catch (Exception e) {
			log.error("{}", e);
		}
		return Collections.emptyList();
	}

	private Collection<ICompletionProposal> getKeyHintProposals(PropertyInfo prop, int navOffset) {
		HintProvider hintProvider = prop.getHints(typeUtil);
		if (!HintProviders.isNull(hintProvider)) {
			String query = textBetween(doc, navOffset+1, offset);
			List<TypedProperty> hintProperties = hintProvider.getPropertyHints(query);
			if (CollectionUtil.hasElements(hintProperties)) {
				return createPropertyProposals(TypeParser.parse(prop.getType()), navOffset, query, hintProperties);
			}
		}
		return ImmutableList.of();
	}

	private String textBetween(IDocument doc, int start, int end) {
		if (end > doc.getLength()) {
			end = doc.getLength();
		}
		if (start>doc.getLength()) {
			start = doc.getLength();
		}
		if (start<0) {
			start = 0;
		}
		if (end < 0) {
			end = 0;
		}
		if (start<end) {
			try {
				return doc.get(start, end-start);
			} catch (BadLocationException e) {
			}
 		}
		return "";
	}

	/**
	 * @param type Type of the expression leading upto the 'nav' operator
	 * @param navOffset Offset of the nav operator (either ']' or '.'
	 * @param offset Offset of the cursor where CA was requested.
	 */
	private Collection<ICompletionProposal> getNavigationProposals(Type type, int navOffset) {
		try {
			char navOp = doc.getChar(navOffset);
			if (navOp=='.') {
				String prefix = doc.get(navOffset+1, offset-(navOffset+1));
				EnumCaseMode caseMode = caseMode(prefix);
				List<TypedProperty> objectProperties = typeUtil.getProperties(type, caseMode, BeanPropertyNameMode.HYPHENATED);
				   //Note: properties editor itself deals with relaxed names. So it expects the properties here to be returned in hyphenated form only.
				if (objectProperties!=null && !objectProperties.isEmpty()) {
					return createPropertyProposals(type, navOffset, prefix, objectProperties);
				}
			} else {
				//TODO: other cases ']' or '[' ?
			}
		} catch (Exception e) {
			log.error("{}", e);
		}
		return Collections.emptyList();
	}

	protected Collection<ICompletionProposal> createPropertyProposals(Type type, int navOffset,
			String prefix, List<TypedProperty> objectProperties) {
		ArrayList<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
		for (TypedProperty prop : objectProperties) {
			double score = FuzzyMatcher.matchScore(prefix, prop.getName());
			if (score!=0) {
				DocumentEdits edits = new DocumentEdits(doc, false);
				edits.delete(navOffset+1, offset);
				edits.lazyInsert(offset, prop.getName(), () -> prop.getName() + propertyCompletionPostfix(typeUtil, prop.getType()));
				proposals.add(
					completionFactory.beanProperty(doc, null, type, prefix, prop, score, edits, typeUtil)
				);
			}
		}
		return proposals;
	}

	/**
	 * Determines the EnumCaseMode used to generate completion candidates based on prefix.
	 */
	protected EnumCaseMode caseMode(String prefix) {
		EnumCaseMode caseMode;
		if (prefix == null || prefix.isEmpty()) {
			caseMode = preferLowerCaseEnums?EnumCaseMode.LOWER_CASE:EnumCaseMode.ORIGNAL;
		} else {
			caseMode = Character.isLowerCase(prefix.charAt(0))?EnumCaseMode.LOWER_CASE:EnumCaseMode.ORIGNAL;
		}
		return caseMode;
	}

	protected static String propertyCompletionPostfix(TypeUtil typeUtil, Type type) {
		String postfix = "";
		if (type!=null) {
			if (typeUtil.isAssignableType(type)) {
				postfix = "=";
			} else if (typeUtil.isIndexable(type)) {
				postfix = "[";
			} else if (typeUtil.isDotable(type)) {
				postfix = ".";
			}
		}
		return postfix;
	}

	private Collection<ICompletionProposal> getValueCompletions(Value value) {
		DocumentRegion valueRegion = createRegion(doc, value).trimStart(SPACES).trimEnd(SPACES);
		String query = valuePrefixFinder.getPrefix(doc, offset, valueRegion.getStart());
		int startOfValue = offset - query.length();
		EnumCaseMode caseMode = caseMode(query);

		// note: no need to skip whitespace backwards.
		String propertyName = /*fuzzySearchPrefix.getPrefix(doc, pair.getOffset())*/value.getParent().getKey().decode();
		// because value partition includes whitespace around the assignment
		if (propertyName != null) {
			Collection<StsValueHint> valueCompletions = getValueHints(index, typeUtil, query, propertyName, caseMode);
			if (valueCompletions != null && !valueCompletions.isEmpty()) {
				ArrayList<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
				for (StsValueHint hint : valueCompletions) {
					String valueCandidate = hint.getValue();
					double score = FuzzyMatcher.matchScore(query, valueCandidate);
					if (score != 0) {
						DocumentEdits edits = new DocumentEdits(doc, false);
						edits.delete(startOfValue, offset);
						edits.insert(offset, valueCandidate);
						String valueTypeName = typeUtil.niceTypeName(getValueType(index, typeUtil, propertyName));
						proposals.add(completionFactory.valueProposal(valueCandidate, query, valueTypeName,
								score, edits, ValueHintHoverInfo.create(hint))
						// new ValueProposal(startOfValue, valuePrefix,
						// valueCandidate, i)
						);
					}
				}
				return proposals;
			}
		}
		return Collections.emptyList();
	}

	private DocumentRegion createRegion(IDocument doc, Node value) {
		// Trim trailing spaces (there is no leading white space already)
		int length = value.getLength();
		try {
			length = doc.get(value.getOffset(), value.getLength()).trim().length();
		} catch (BadLocationException e) {
			// ignore
		}
		return new DocumentRegion(doc, value.getOffset(), value.getOffset() + length);
	}

	private List<Match<PropertyInfo>> findMatches(String prefix) {
		List<Match<PropertyInfo>> matches = index.find(camelCaseToHyphens(prefix));
		return matches;
	}

	private Collection<ICompletionProposal> getPropertyCompletions() throws BadLocationException {
		Collection<ICompletionProposal> navProposals = getNavigationProposals();
		if (!navProposals.isEmpty()) {
			return navProposals;
		}
		return getFuzzyCompletions();
	}

	protected Collection<ICompletionProposal> getFuzzyCompletions() {
		final String prefix = fuzzySearchPrefix.getPrefix(doc, offset);
		if (prefix != null) {
			Collection<Match<PropertyInfo>> matches = findMatches(prefix);
			if (matches!=null && !matches.isEmpty()) {
				ArrayList<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>(matches.size());
				matches.stream().forEach(match -> {
					DocumentEdits docEdits;
					try {
						docEdits = LazyProposalApplier.from(() -> {
								try {
									DocumentEdits edits = new DocumentEdits(doc, false);
									edits.delete(offset-prefix.length(), offset);
									String key = match.data.getId();
									edits.lazyInsert(offset, key, () -> key + propertyCompletionPostfix(typeUtil, TypeParser.parse(match.data.getType())));
									return edits;
								} catch (Throwable t) {
									log.error("{}", t);
									return new DocumentEdits(doc, false);
								}
						});
						synchronized (proposals) {
							proposals.add(completionFactory.property(doc, docEdits, match, typeUtil));
						}
					} catch (Throwable e) {
						log.error("{}", e);
					}
				});
				return elideCommonPrefix(prefix, proposals);
			}
		}
		return Collections.emptyList();
	}

	private Collection<ICompletionProposal> elideCommonPrefix(String basePrefix, ArrayList<ICompletionProposal> proposals) {
		if (propertyCompletionSettings.elidePrefix()) {
			String prefix = StringUtil.commonPrefix(Stream.concat(Stream.of(basePrefix), proposals.stream().map(ICompletionProposal::getLabel)));
			int lastDot = prefix.lastIndexOf('.');
			if (lastDot>=0) {
				for (int i = 0; i < proposals.size(); i++) {
					ICompletionProposal p = proposals.get(i);
					proposals.set(i, p.dropLabelPrefix(lastDot+1));
				}
			}
		}
		return proposals;
	}


}
