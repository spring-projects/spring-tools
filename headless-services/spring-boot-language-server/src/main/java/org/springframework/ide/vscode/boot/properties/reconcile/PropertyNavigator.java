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

package org.springframework.ide.vscode.boot.properties.reconcile;

import static org.springframework.ide.vscode.boot.properties.reconcile.SpringPropertyProblem.problem;

import java.util.List;

import org.springframework.ide.vscode.boot.metadata.types.Type;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil.BeanPropertyNameMode;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil.EnumCaseMode;
import org.springframework.ide.vscode.boot.metadata.types.TypedProperty;
import org.springframework.ide.vscode.boot.properties.completions.SpringPropertiesCompletionEngine;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.ValueParser;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.IDocument;

/**
 * Helper class for {@link SpringPropertiesReconcileEngine} and {@link SpringPropertiesCompletionEngine}.
 * <p>
 * This class provides a means to 'navigate' a chain of bracket and dot navigation operations down
 * from a typed property into its value type.
 *
 * @author Kris De Volder
 */
public class PropertyNavigator {

	private static final char EOF = 0;

	/**
	 * If problem collector is not null, then problems detected in the navigation chain are added to the
	 * collector.
	 */
	private IProblemCollector problemCollector;

	/**
	 * Document in which navigation chain text is contained.
	 */
	private IDocument doc;

	private TypeUtil typeUtil;

	private DocumentRegion region;

	private String regionText;

	public PropertyNavigator(IDocument doc, IProblemCollector problemCollector, TypeUtil typeUtil, DocumentRegion region) throws BadLocationException {
		this.doc = doc;
		this.problemCollector = problemCollector==null?IProblemCollector.NULL:problemCollector;
		this.typeUtil = typeUtil;
		this.region = region;
		this.regionText = doc.get(region.getStart(), region.getLength());
	}

	/**
	 * @param offset current position in the nav chain. Text before this offset is already 'processed'.
	 * @param type The type at the end of the already processed nav chain. The next nav op in the chain
	 *             should go deeper into this type.
	 * @param r The entire region of the navchain, including both the already processed portion as well
	 *        as the remaining text.
	 * @return Type at the end of the whole nav chain, or null if the type could not be determined.
	 */
	public Type navigate(int offset, Type type) {
		if (type!=null) {
			if (offset<region.getEnd()) {
				char navOp = getChar(offset);
				if (navOp=='.' && getChar(offset+1)=='[') {
					navOp = '['; // just skip the redundant '.'. See:  https://www.pivotaltracker.com/story/show/175147973
				}
				if (navOp=='.') {
					if (typeUtil.isDotable(type)) {
						return dotNavigate(offset, type);
					} else {
						problemCollector.accept(problem(ApplicationPropertiesProblemType.PROP_INVALID_BEAN_NAVIGATION,
								"Can't use '.' navigation for property '"+textBetween(region.getStart(), offset)+"' of type "+type,
								offset, region.getEnd()-offset));
					}
				} else if (navOp=='[') {
					if (typeUtil.isBracketable(type)) {
						return bracketNavigate(offset, type);
					} else if (!typeUtil.isObjectOrSequence(type)) {
						problemCollector.accept(problem(ApplicationPropertiesProblemType.PROP_INVALID_INDEXED_NAVIGATION,
								"Can't use '[..]' navigation for property '"+textBetween(region.getStart(), offset)+"' of type "+type,
								offset, region.getEnd()-offset));
					}
				} else {
					problemCollector.accept(problem(ApplicationPropertiesProblemType.PROP_EXPECTED_DOT_OR_LBRACK, "Expecting either a '.' or '['", offset, region.getEnd()-offset));
				}
			} else {
				//end of nav chain
				return type;
			}
		}
		//Something we can't handle...
		return null;
	}

	private String textBetween(int start, int end) {
		try {
			if (end>start) {
				return doc.get(start, end-start);
			}
		} catch (BadLocationException e) {
			//ignore
		}
		return "";
	}

	private int indexOf(char c, int from) {
		int offset = region.getStart();
		int found = regionText.indexOf(c, from-offset);
		if (found>=0) {
			return found+offset;
		}
		return -1;
	}

	/**
	 * Handle bracket navigation into given type, after a bracket at
	 * was found at given offset. Assumes the type has already been checked to
	 * be 'bracketable'.
	 */
	private Type bracketNavigate(int offset, Type type) {
		int lbrack = offset;
		int rbrack = indexOf(']', lbrack);
		if (rbrack<0) {
			problemCollector.accept(problem(ApplicationPropertiesProblemType.PROP_NO_MATCHING_RBRACK,
					"No matching ']'",
					offset, 1));
		} else {
			String indexStr = textBetween(lbrack+1, rbrack);
			if (!indexStr.contains("${")) {
				Type keytype = typeUtil.getKeyType(type);
				ValueParser parser = typeUtil.getValueParser(keytype);
				if (parser!=null) {
					try {
						parser.parse(indexStr);
					} catch (Exception e) {
						problemCollector.accept(problem(ApplicationPropertiesProblemType.PROP_VALUE_TYPE_MISMATCH,
							"Expecting '"+typeUtil.niceTypeName(keytype)+"' for '[...]' notation '"+textBetween(region.getStart(), lbrack)+"'",
							lbrack+1, rbrack-lbrack-1
						));
					}
				}
			}
			Type domainType = TypeUtil.getDomainType(type);
			return navigate(rbrack+1, domainType);
		}
		return null;
	}


	/**
	 * Handle dot navigation into given type, after a '.' was
	 * was found at given offset. Assumes the type has already been
	 * checked to be 'dotable'.
	 */
	private Type dotNavigate(int offset, Type type) {
		if (typeUtil.isMap(type)) {
			int keyStart = offset+1;
			Type domainType = TypeUtil.getDomainType(type);
			int keyEnd = -1;
			if (typeUtil.isDotable(domainType)) {
				//'.' should be interpreted as navigation.
				keyEnd = nextNavOp(".[", offset+1);
			} else {
				//'.' should *not* be interpreted as navigation.
				keyEnd = nextNavOp("[", offset+1);
			}
			String key = textBetween(keyStart, keyEnd);
			Type keyType = typeUtil.getKeyType(type);
			if (keyType!=null) {
				ValueParser keyParser = typeUtil.getValueParser(keyType);
				if (keyParser!=null) {
					try {
						keyParser.parse(key);
					} catch (Exception e) {
						problemCollector.accept(problem(ApplicationPropertiesProblemType.PROP_VALUE_TYPE_MISMATCH,
								"Expecting "+typeUtil.niceTypeName(keyType),
								keyStart, keyEnd-keyStart));
					}
				}
			}
			return navigate(keyEnd, domainType);
		} else {
			// dot navigation into object properties
			int keyStart = offset+1;
			int	keyEnd = nextNavOp(".[", offset+1);
			if (keyEnd<0) {
				keyEnd = region.getEnd();
			}
			String key = StringUtil.camelCaseToHyphens(textBetween(keyStart, keyEnd));

			List<TypedProperty> properties = typeUtil.getProperties(type, EnumCaseMode.ALIASED, BeanPropertyNameMode.ALIASED);
			if (properties!=null) {
				TypedProperty prop = null;
				for (TypedProperty p : properties) {
					if (p.getName().equals(key)) {
						prop = p;
						break;
					}
				}
				if (prop==null) {
					problemCollector.accept(problem(ApplicationPropertiesProblemType.PROP_INVALID_BEAN_PROPERTY,
							"Type '"+typeUtil.niceTypeName(type)+"' has no property '"+key+"'",
							keyStart, keyEnd-keyStart));
				} else {
					if (prop.isDeprecated()) {
						problemCollector.accept(problemDeprecated(type, prop, keyStart, keyEnd-keyStart));
					}
					return navigate(keyEnd, prop.getType());
				}
			}
		}
		return null;
	}

	private ReconcileProblem problemDeprecated(Type contextType, TypedProperty prop, int offset, int len) {
		SpringPropertyProblem p = problem(ApplicationPropertiesProblemType.PROP_DEPRECATED,
				TypeUtil.deprecatedPropertyMessage(
						prop.getName(), typeUtil.niceTypeName(contextType),
						prop.getDeprecationReplacement(), prop.getDeprecationReason()
				),
				offset, len
		);
		p.setPropertyName(prop.getName());
		return p;
	}

	/**
	 * Skip ahead from give position until reaching the next 'navigation' operator (or the end
	 * of the navigation chain region).
	 *
	 * @param navops Each character in this string is considered a 'navigation operator'.
	 * @param pos current position in the document.
	 * @return position of next navop if found, or the position at the end of the region if not found.
	 */
	private int nextNavOp(String navops, int pos) {
		int end = region.getEnd();
		while (pos < end && navops.indexOf(getChar(pos))<0) {
			pos++;
		}
		return Math.min(pos, end); //ensure never past the end
	}

	private char getChar(int offset) {
		try {
			return doc.getChar(offset);
		} catch (BadLocationException e) {
			//outside doc, return something anyways.
			return EOF;
		}
	}

}
