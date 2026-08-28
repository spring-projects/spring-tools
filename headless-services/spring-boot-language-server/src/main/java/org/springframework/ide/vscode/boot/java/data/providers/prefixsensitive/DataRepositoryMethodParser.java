/*******************************************************************************
 * Copyright (c) 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data.providers.prefixsensitive;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.ide.vscode.boot.java.data.DataRepositoryDefinition;
import org.springframework.ide.vscode.boot.java.data.DomainProperty;
import org.springframework.ide.vscode.boot.java.data.providers.QueryMethodSubject;

/**
 * Class responsible for parsing Spring JPA Repository query methods.
 * @author danthe1st
 */
class DataRepositoryMethodParser {

	private static final Map<String, List<QueryPredicateKeywordInfo>> PREDICATE_KEYWORDS_GROUPED_BY_FIRST_WORD = QueryPredicateKeywordInfo.PREDICATE_KEYWORDS
			.stream()
			.collect(Collectors.groupingBy(info -> findFirstWord(info.keyword())));

	private final String prefix;
	private final Map<String, List<DomainProperty>> propertiesGroupedByFirstWord;
	private String expectedNextType = null;//the expected type as string if a type is expected, if the type cannot be found, the user should supply it
	private boolean performFullCompletion = true;//if some invalid text is detected, do not complete the whole method
	private String previousExpression = null;

	public DataRepositoryMethodParser(String localPrefix, DataRepositoryDefinition repoDef) {
		prefix = localPrefix;
		propertiesGroupedByFirstWord = groupPropertiesByFirstWord(repoDef);
	}

	private Map<String, List<DomainProperty>> groupPropertiesByFirstWord(DataRepositoryDefinition repoDef) {
		Map<String, List<DomainProperty>> grouped = new HashMap<>();
		for(DomainProperty property : repoDef.getDomainType().getProperties()){
			String firstWord = findFirstWord(property.getName());
			grouped.putIfAbsent(firstWord, new ArrayList<>());
			grouped.get(firstWord).add(property);
		}
		return grouped;
	}

	DataRepositoryMethodNameParseResult parseLocalPrefixForCompletion() {
		int subjectPredicateSplitIndex = prefix.indexOf("By");
		if (subjectPredicateSplitIndex == -1) {
			return null;
		}
		QueryMethodSubject subjectType = parseSubject(subjectPredicateSplitIndex);
		if (subjectType == null) {
			return null;
		}
		String predicate = prefix.substring(subjectPredicateSplitIndex + 2);
		List<String> parameters=new ArrayList<>();

		parsePredicate(predicate, parameters);

		EnumSet<DataRepositoryMethodKeywordType> allowedKeywordTypes = findAllowedKeywordTypesAtEnd();
		return new DataRepositoryMethodNameParseResult(subjectType, parameters, performFullCompletion, previousExpression, allowedKeywordTypes);
	}

	private void parsePredicate(String predicate, List<String> parameters) {
		int lastWordEnd = 0;

		for (int i = 1; i <= predicate.length(); i++) {
			if (i == predicate.length() || Character.isUpperCase(predicate.charAt(i))) {//word ends on uppercase letter or end of string
				String word = predicate.substring(lastWordEnd, i);
				QueryPredicateKeywordInfo keyword = findByLargestFirstWord(PREDICATE_KEYWORDS_GROUPED_BY_FIRST_WORD, QueryPredicateKeywordInfo::keyword, predicate, lastWordEnd, word);
				if (keyword == null){
					DomainProperty preferredWord = findByLargestFirstWord(propertiesGroupedByFirstWord, DomainProperty::getName, predicate, lastWordEnd, word);
					if (preferredWord != null) {
						i += preferredWord.getName().length() - word.length();
						word=preferredWord.getName();
					}
					parseNonKeyword(word);
				} else {
					i += keyword.keyword().length() - word.length();
					parseKeyword(parameters, keyword);
				}
				lastWordEnd = i;
			}
		}
		if (expectedNextType != null) {
			parameters.add(expectedNextType);
		}
	}

	private QueryMethodSubject parseSubject(int subjectPredicateSplitIndex) {
		String subject = prefix.substring(0, subjectPredicateSplitIndex);
		QueryMethodSubject subjectType = null;
		for(QueryMethodSubject queryMethodSubject : QueryMethodSubject.QUERY_METHOD_SUBJECTS){
			if (subject.startsWith(queryMethodSubject.key())) {
				subjectType = queryMethodSubject;
			}
		}
		return subjectType;
	}

	private EnumSet<DataRepositoryMethodKeywordType> findAllowedKeywordTypesAtEnd() {
		EnumSet<DataRepositoryMethodKeywordType> allowedKeywordTypes = EnumSet.allOf(DataRepositoryMethodKeywordType.class);
		if (expectedNextType == null) {
			allowedKeywordTypes.remove(DataRepositoryMethodKeywordType.TERMINATE_EXPRESSION);
			allowedKeywordTypes.remove(DataRepositoryMethodKeywordType.COMPARE);
		}
		return allowedKeywordTypes;
	}

	private void parseNonKeyword(String word) {
		if (previousExpression == null) {
			previousExpression = word;
			//non-keywords just invert the status
			//if an expression is expected, the word is the expression
			//if not, some expression is required after the word
			if (expectedNextType == null) {
				expectedNextType = word;
			} else {
				expectedNextType = null;
			}
		} else {
			//combine multiple words that are not keywords
			previousExpression += word;
			if (expectedNextType != null) {
				expectedNextType = previousExpression;
			}
		}
	}

	private void parseKeyword(List<String> parameters, QueryPredicateKeywordInfo keyword) {
		switch(keyword.type()) {
			case TERMINATE_EXPRESSION: {//e.g. IsTrue
				if (expectedNextType == null) {
					//if no next type/expression is expected (which should not happen), do not complete the full method (parameters)
					performFullCompletion = false;
				}
				expectedNextType = null;

				break;
			}
			case COMBINE_CONDITIONS: {//e.g. And
				//if an expression is expected, it is added to the parameters
				if (expectedNextType != null) {
					parameters.add(expectedNextType);
				}
				expectedNextType = null;
				break;
			}
			case COMPARE: {//e.g. GreaterThan
				if (expectedNextType == null) {
					//nothing to compare, e.g. And directly followed by GreaterThan
					performFullCompletion = false;
				}
				expectedNextType = previousExpression;
				break;
			}
			case IGNORE:{
				//ignore
				break;
			}
			default:
				throw new IllegalArgumentException("Unexpected value: " + keyword.type());
		}
		previousExpression = null;
	}

	private <T> T findByLargestFirstWord(Map<String, List<T>> toSearch, Function<T, String> expressionExtractor, String predicate, int lastWordEnd, String word) {
		T ret = null;
		if (toSearch.containsKey(word)) {
			for(T possibleKeyword : toSearch.get(word)){
				int endPosition = lastWordEnd + expressionExtractor.apply(possibleKeyword).length();
				if (predicate.length() >= endPosition
						&& expressionExtractor.apply(possibleKeyword).equals(predicate.substring(lastWordEnd, endPosition))
						&& (ret == null || expressionExtractor.apply(possibleKeyword).length() > expressionExtractor.apply(possibleKeyword).length())) {//find largest valid keyword
					ret = possibleKeyword;
				}
			}
		}
		return ret;
	}

	private static String findFirstWord(String expression) {
		int firstWordEnd;
		for (firstWordEnd = 1;
				firstWordEnd < expression.length()
				&& Character.isLowerCase(expression.charAt(firstWordEnd));
				firstWordEnd++) {
			//search is done in loop condition
		}
		return expression.substring(0, firstWordEnd);
	}
}
