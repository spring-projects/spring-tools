/*******************************************************************************
 * Copyright (c) 2014, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.properties.reconcile;

import static org.springframework.ide.vscode.boot.properties.reconcile.ApplicationPropertiesProblemType.PROP_DEPRECATED;
import static org.springframework.ide.vscode.boot.properties.reconcile.ApplicationPropertiesProblemType.PROP_SYNTAX_ERROR;
import static org.springframework.ide.vscode.boot.properties.reconcile.ApplicationPropertiesProblemType.PROP_UNKNOWN_PROPERTY;
import static org.springframework.ide.vscode.boot.properties.reconcile.SpringPropertyProblem.problem;
import static org.springframework.ide.vscode.commons.util.StringUtil.commonPrefix;

import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.metadata.PropertyInfo;
import org.springframework.ide.vscode.boot.metadata.SpringPropertyIndex;
import org.springframework.ide.vscode.boot.metadata.SpringPropertyIndexProvider;
import org.springframework.ide.vscode.boot.metadata.types.Type;
import org.springframework.ide.vscode.boot.metadata.types.TypeParser;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtilProvider;
import org.springframework.ide.vscode.boot.properties.quickfix.AppPropertiesQuickFixes;
import org.springframework.ide.vscode.boot.properties.quickfix.CommonQuickfixes;
import org.springframework.ide.vscode.boot.properties.quickfix.DeprecatedPropertyData;
import org.springframework.ide.vscode.boot.properties.quickfix.MissingPropertyData;
import org.springframework.ide.vscode.commons.languageserver.quickfix.Quickfix.QuickfixData;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IReconcileEngine;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.ExceptionUtil;
import org.springframework.ide.vscode.commons.util.FuzzyMap;
import org.springframework.ide.vscode.commons.util.ValueParseException;
import org.springframework.ide.vscode.commons.util.ValueParser;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.java.properties.antlr.parser.AntlrParser;
import org.springframework.ide.vscode.java.properties.parser.ParseResults;
import org.springframework.ide.vscode.java.properties.parser.Parser;
import org.springframework.ide.vscode.java.properties.parser.PropertiesAst.Comment;
import org.springframework.ide.vscode.java.properties.parser.PropertiesAst.KeyValuePair;
import org.springframework.ide.vscode.java.properties.parser.PropertiesAst.Node;
import org.springframework.ide.vscode.java.properties.parser.PropertiesFileEscapes;

/**
 * Implements reconciling algorithm for {@link SpringPropertiesReconcileStrategy}.
 * <p>
 * The code in here could have been also part of the {@link SpringPropertiesReconcileStrategy}
 * itself, however isolating it here allows it to me more easily unit tested (no dependencies
 * on ISourceViewer which is difficult to 'mock' in testing harness.
 *
 * @author Kris De Volder
 */
public class SpringPropertiesReconcileEngine implements IReconcileEngine {

	private static final Logger log = LoggerFactory.getLogger(SpringPropertiesReconcileEngine.class);

	/**
	 * Regexp that matches a ',' surrounded by whitespace, including escaped whitespace / newlines
	 */
//	private static final Pattern COMMA = Pattern.compile(
//			"(\\s|\\\\\\s)*,(\\s|\\\\\\s)*"
//	);

	private static final Pattern SPACES = Pattern.compile(
			"(\\s|\\\\\\s)*"
	);

	private SpringPropertyIndexProvider fIndexProvider;
	private TypeUtilProvider typeUtilProvider;
	private Parser parser = new AntlrParser();
	private AppPropertiesQuickFixes quickFixes;
	private SourceLinks sourceLinks;

	public SpringPropertiesReconcileEngine(SpringPropertyIndexProvider provider, TypeUtilProvider typeUtilProvider, AppPropertiesQuickFixes quickFixes, SourceLinks sourceLinks) {
		this.fIndexProvider = provider;
		this.typeUtilProvider = typeUtilProvider;
		this.quickFixes = quickFixes;
		this.sourceLinks = sourceLinks;
	}

	@Override
	public void reconcile(IDocument doc, IProblemCollector problemCollector) {
		FuzzyMap<PropertyInfo> index = fIndexProvider.getIndex(doc).getProperties();
		problemCollector.beginCollecting();
		try {
			ParseResults results = parser.parse(doc.get());
			DuplicateNameChecker duplicateNameChecker = new DuplicateNameChecker(problemCollector);

			results.syntaxErrors.forEach(syntaxError -> {
				problemCollector.accept(problem(PROP_SYNTAX_ERROR, syntaxError.getMessage(), syntaxError.getOffset(),
						syntaxError.getLength()));
			});

			if (index == null || index.isEmpty()) {
				//don't report errors when index is empty, simply don't check (otherwise we will just reprot
				// all properties as errors, but this not really useful information since the cause is
				// some problem putting information about properties into the index.
				return;
			}
			
			List<Node> nodes = results.ast.getAllNodes();
			for (int i = 0; i < results.ast.getAllNodes().size(); i++) {
				Node node = nodes.get(i);
				try {
					if (node instanceof Comment) {
						if (isDocumentMarker(doc, nodes, i)) {
							duplicateNameChecker.startNewSubDocument();
						}
					} else if (node instanceof KeyValuePair) {
						KeyValuePair pair = (KeyValuePair) node;
						DocumentRegion propertyNameRegion = createRegion(doc, pair.getKey());
						String keyName = PropertiesFileEscapes.unescape(propertyNameRegion.toString());
						duplicateNameChecker.check(propertyNameRegion);
						PropertyInfo validProperty = SpringPropertyIndex.findLongestValidProperty(index, keyName);
						if (validProperty != null) {
							//TODO: Remove last remnants of 'IRegion trimmedRegion' here and replace
							// it all with just passing around 'fullName' DocumentRegion. This may require changes
							// in PropertyNavigator (probably these changes are also for the better making it simpler as well)
							if (validProperty.isDeprecated()) {
								problemCollector.accept(problemDeprecated(propertyNameRegion, validProperty, quickFixes.DEPRECATED_PROPERTY));
							}
							int offset = validProperty.getId().length() + propertyNameRegion.getStart();
							PropertyNavigator navigator = new PropertyNavigator(doc, problemCollector, typeUtilProvider.getTypeUtil(sourceLinks, doc), propertyNameRegion);
							Type valueType = navigator.navigate(offset, TypeParser.parse(validProperty.getType()));
							if (valueType != null) {
								reconcileType(doc, valueType, pair.getValue(), problemCollector);
							}
						} else { //validProperty == null
							//The name is invalid, with no 'prefix' of the name being a valid property name.
							PropertyInfo similarEntry = index.findLongestCommonPrefixEntry(propertyNameRegion.toString());
							CharSequence validPrefix = commonPrefix(similarEntry.getId(), keyName);
							problemCollector.accept(problemUnkownProperty(propertyNameRegion, similarEntry, validPrefix, quickFixes.MISSING_PROPERTY));
						} //end: validProperty == null
					}
				} catch (Exception e) {
					log.error("", e);
				}
			}

		} catch (Throwable e2) {
			log.error("", e2);
		} finally {
			problemCollector.endCollecting();
		}
	}

	private boolean isDocumentMarker(IDocument doc, List<Node> nodes, int idx) {
		Node node = nodes.get(idx);
		if (node instanceof Comment) {
			DocumentRegion region = createRegion(doc, node).trimEnd();
			String t = region.toString();
			if (region.isAtStartOfLine()) {
				switch (t) {
				case "#---":
					// Check if next line starts with the same comment prefix. If yes then not a doc splitter
					if (isNextLineCommentWithPrefix(doc, nodes, idx, "#")) {
						return false;
					}
					return true;
				case "!---":
					// Check if next line starts with the same comment prefix. If yes then not a doc splitter
					if (isNextLineCommentWithPrefix(doc, nodes, idx, "!")) {
						return false;
					}
					return true;
				}
				
			}
		}
		return false;
	}
	
	private boolean isNextLineCommentWithPrefix(IDocument doc, List<Node> nodes, int idx, String prefix) {
		if (idx < nodes.size() - 1) {
			Node nextLine = nodes.get(idx + 1);
			if (nextLine instanceof Comment) {
				DocumentRegion nextRegion = createRegion(doc, nextLine).trimEnd();
				if (nextRegion.toString().startsWith(prefix)) {
					return true;
				}
			}
		}
		return false;
	}

	protected SpringPropertyProblem problemDeprecated(DocumentRegion region, PropertyInfo property, QuickfixType fixType) {
		SpringPropertyProblem p = problem(PROP_DEPRECATED,
				TypeUtil.deprecatedPropertyMessage(
						property.getId(), null,
						property.getDeprecationReplacement(),
						property.getDeprecationReason()
				),
				region
		);
		p.setPropertyName(property.getId());
		p.setMetadata(property);

		try {
			IDocument doc = region.getDocument();
			p.addQuickfix(new QuickfixData<>(fixType,
					new DeprecatedPropertyData(doc.getUri(), doc.toRange(region), property.getDeprecationReplacement()),
					"Replace with `" + property.getDeprecationReplacement() + "`"));
		} catch (BadLocationException e) {
			log.error("", e);
		}

		return p;
	}

	protected SpringPropertyProblem problemUnkownProperty(DocumentRegion fullNameRegion,
			PropertyInfo similarEntry, CharSequence validPrefix, QuickfixType... fixTypes) {
		String fullName = fullNameRegion.toString();
		SpringPropertyProblem p = problem(PROP_UNKNOWN_PROPERTY,
				"'"+fullName+"' is an unknown property."+suggestSimilar(similarEntry, validPrefix, fullName),
				fullNameRegion.subSequence(validPrefix.length())
		);
		p.setPropertyName(fullName);

		IDocument doc = fullNameRegion.getDocument();
		for (QuickfixType fixType : fixTypes) {
			if (fixType != null) {
				switch (fixType.getId()) {
				case CommonQuickfixes.MISSING_PROPERTY_APP_QF_ID:
					p.addQuickfix(new QuickfixData<>(fixType,
							new MissingPropertyData(new TextDocumentIdentifier(doc.getUri()), fullName),
							"Create metadata for `" + fullName +"`"));
					break;
				}
			}
		}
		return p;
	}

	private void reconcileType(IDocument doc, Type expectType, Node value, IProblemCollector problems) {
		// Trim start and end spaces from the value node
		reconcileType(createRegion(doc, value).trimStart(SPACES).trimEnd(SPACES), expectType,
				problems);
	}

	private DocumentRegion createRegion(IDocument doc, Node value) {
		// Trim trailing spaces (there is no leading white space already)
		int length = value.getLength();
		try {
			length = doc.get(value.getOffset(), value.getLength()).length();
		} catch (BadLocationException e) {
			// ignore
		}
		return new DocumentRegion(doc, value.getOffset(), value.getOffset() + length);
	}

	private void reconcileType(DocumentRegion escapedValue, Type expectType, IProblemCollector problems) {
		TypeUtil typeUtil = typeUtilProvider.getTypeUtil(sourceLinks, escapedValue.getDocument());
		ValueParser parser = typeUtil.getValueParser(expectType);
		if (parser != null) {
			try {
				String valueStr = PropertiesFileEscapes.unescape(escapedValue.toString());
				if (!valueStr.contains("${")) {
					//Don't check strings that look like they use variable substitution.
					parser.parse(valueStr);
				}
			} catch (ValueParseException e) {
				problems.accept(problem(ApplicationPropertiesProblemType.PROP_VALUE_TYPE_MISMATCH,
						ExceptionUtil.getMessage(e),
						e.getHighlightRegion(escapedValue)));

			} catch (Exception e) {
				problems.accept(problem(ApplicationPropertiesProblemType.PROP_VALUE_TYPE_MISMATCH,
						"Expecting '"+typeUtil.niceTypeName(expectType)+"'",
						escapedValue));
			}
		}
	}

	private String suggestSimilar(PropertyInfo similarEntry, CharSequence validPrefix, CharSequence fullName) {
		int matchedChars = validPrefix.length();
		int wrongChars = fullName.length()-matchedChars;
		if (wrongChars < matchedChars) {
			return " Did you mean '"+similarEntry.getId()+"'?";
		} else {
			return "";
		}
	}

}
