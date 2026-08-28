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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.lsp4j.CompletionItemKind;
import org.springframework.ide.vscode.boot.java.data.DataRepositoryDefinition;
import org.springframework.ide.vscode.boot.java.data.DomainProperty;
import org.springframework.ide.vscode.boot.java.data.DomainType;
import org.springframework.ide.vscode.boot.java.data.FindByCompletionProposal;
import org.springframework.ide.vscode.boot.java.data.providers.DataRepositoryCompletionProvider;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.languageserver.completion.DocumentEdits;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposal;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.util.StringUtils;

/**
 * This class provides content assist proposals for Spring Data repository query methods.
 * @author danthe1st
 */
public class DataRepositoryPrefixSensitiveCompletionProvider implements DataRepositoryCompletionProvider {

	@Override
	public void addProposals(Collection<ICompletionProposal> completions, IDocument doc, int offset, String prefix, DataRepositoryDefinition repoDef, ASTNode node) {
		String localPrefix = findLastJavaIdentifierPart(prefix);
		if (localPrefix == null) {
			return;
		}
		DataRepositoryMethodNameParseResult parseResult = new DataRepositoryMethodParser(localPrefix, repoDef).parseLocalPrefixForCompletion();
		if (parseResult != null && parseResult.performFullCompletion()){
			Map<String, DomainProperty> propertiesByName = repoDef.getDomainType().getPropertiesByName();
			if (isEndingWithProperty(parseResult, propertiesByName) || isEndingWithPredicateKeyWord(localPrefix, propertiesByName)) {
				addMethodCompletionProposal(completions, offset, repoDef, localPrefix, prefix, parseResult, propertiesByName, node, doc);
			}

			if (parseResult.lastWord() == null || !propertiesByName.containsKey(parseResult.lastWord())) {
				addPropertyProposals(completions, offset, propertiesByName, parseResult);
			}
			addPredicateKeywordProposals(completions, offset, prefix, parseResult, propertiesByName);
		}
	}
	
	private boolean isEndingWithPredicateKeyWord(String localPrefix, Map<String, DomainProperty> propertiesByName) {
		for (QueryPredicateKeywordInfo predicate : QueryPredicateKeywordInfo.PREDICATE_KEYWORDS) {
			if (predicate.type() == DataRepositoryMethodKeywordType.TERMINATE_EXPRESSION
					&& localPrefix.endsWith(predicate.keyword())) {
				return true;
			}
		}
		return false;
	}
	
	private boolean isEndingWithProperty(DataRepositoryMethodNameParseResult parseResult, Map<String, DomainProperty> propertiesByName) {
		return parseResult.lastWord() != null 
				&& (propertiesByName.containsKey(parseResult.lastWord()) || findExpressionType(propertiesByName, parseResult.lastWord()) != null);
	}

	private void addPredicateKeywordProposals(Collection<ICompletionProposal> completions, int offset, String prefix, DataRepositoryMethodNameParseResult parseResult, Map<String, DomainProperty> propertiesByName) {
		String lastWord = findLastWordWithoutPrefixingProperty(parseResult, propertiesByName);
		for (QueryPredicateKeywordInfo predicate : QueryPredicateKeywordInfo.PREDICATE_KEYWORDS){
			if (parseResult.allowedKeywordTypes().contains(predicate.type())) {
				createLastWordReplacementCompletion(completions, offset, parseResult, lastWord, predicate.keyword());
			}
		}
	}

	private String findLastWordWithoutPrefixingProperty(DataRepositoryMethodNameParseResult parseResult, Map<String, DomainProperty> propertiesByName) {
		String lastWord = parseResult.lastWord();
		if (lastWord == null) {
			return "";
		}
		if (propertiesByName.containsKey(lastWord)) {
			return "";
		}
		for (int i = lastWord.length() - 1; i >= 0; i--) {
			if (Character.isUpperCase(lastWord.charAt(i))) {
				String substring = lastWord.substring(0,i);
				if (propertiesByName.containsKey(substring)) {
					return lastWord.substring(i);
				}
			}
		}
		return lastWord;
	}

	private void addPropertyProposals(Collection<ICompletionProposal> completions, int offset, Map<String, DomainProperty> propertiesByName, DataRepositoryMethodNameParseResult parseResult) {
		for (Map.Entry<String, DomainProperty> e : propertiesByName.entrySet()) {
			String toReplace = e.getKey();
			createLastWordReplacementCompletion(completions, offset, parseResult,  parseResult.lastWord(), toReplace);
		}
	}

	private void createLastWordReplacementCompletion(Collection<ICompletionProposal> completions, int offset, DataRepositoryMethodNameParseResult parseResult, String lastWord, String toReplace) {
		if (lastWord == null) {
			lastWord = "";
		}
		if (toReplace.startsWith(lastWord)) {
			DocumentEdits edits = new DocumentEdits(null, false);
			edits.replace(offset - lastWord.length(), offset, toReplace);
			ICompletionProposal proposal = new FindByCompletionProposal(toReplace, CompletionItemKind.Property, edits, "property " + toReplace, null, null, lastWord, true);
			completions.add(proposal);
		}
	}

	private void addMethodCompletionProposal(Collection<ICompletionProposal> completions, int offset, DataRepositoryDefinition repoDef, String localPrefix, String fullPrefix, DataRepositoryMethodNameParseResult parseResult, Map<String, DomainProperty> propertiesByName, ASTNode node, IDocument doc) {
		String methodName = localPrefix;
		DocumentEdits edits = new DocumentEdits(null, false);
		Set<String> imports = new HashSet<>();
		String signature = buildSignature(methodName, propertiesByName, parseResult, imports, repoDef);
		StringBuilder newText = new StringBuilder();
		newText.append(parseResult.subjectType().returnType());
		if (parseResult.subjectType().fqName() != null && repoDef.getType().shouldImportType(parseResult.subjectType().fqName())) {
			imports.add(parseResult.subjectType().fqName());
		}
		if (parseResult.subjectType().fqName() != null) {
			newText.append("<");
			newText.append(repoDef.getDomainType().getSimpleName());
			newText.append(">");
		}
		String returnType = newText.toString();
		newText.append(" ");
		newText.append(signature);
		newText.append(";");
		int replaceStart = calculateReplaceOffset(offset, localPrefix, fullPrefix, returnType);
		edits.replace(replaceStart, offset, newText.toString());
		Supplier<DocumentEdits> additionalEdits = () -> ASTUtils.getImportsEdit((CompilationUnit) node.getRoot(), imports, doc).orElse(null);
		ICompletionProposal proposal = new FindByCompletionProposal(signature, CompletionItemKind.Method, edits, null, null, additionalEdits, signature, false);
		completions.add(proposal);
	}

	private int calculateReplaceOffset(int offset, String localPrefix, String fullPrefix, String returnType) {
		int replaceStart = offset - localPrefix.length();
		String beforeLocalPrefix = fullPrefix.substring(0, fullPrefix.length()-localPrefix.length());
		String trimmed = beforeLocalPrefix.trim();
		if (trimmed.endsWith(returnType)) {
			replaceStart -= (beforeLocalPrefix.length() - trimmed.length()) + returnType.length();
		}
		return replaceStart;
	}

	private String buildSignature(String methodName, Map<String, DomainProperty> properties, DataRepositoryMethodNameParseResult parseResult, Set<String> imports, DataRepositoryDefinition repoDef) {
		StringBuilder signatureBuilder = new StringBuilder();
		signatureBuilder.append(methodName);
		signatureBuilder.append("(");
		List<String> parameters = parseResult.parameters();
		for(int i = 0; i < parameters.size(); i++){
			String param = parameters.get(i);
			DomainType type = findExpressionType(properties, param);
			if (type == null) {
				signatureBuilder.append("Object");
			} else {
				signatureBuilder.append(type.getSimpleName());
				for (String im : type.getUsedTypes()) {
					if (repoDef.getType().shouldImportType(im)) {
						imports.add(im);
					}
				}
			}
			signatureBuilder.append(" ");
			signatureBuilder.append(StringUtils.uncapitalize(param));
			if (i + 1 < parameters.size()) {
				signatureBuilder.append(", ");
			}
		}
		signatureBuilder.append(")");
		return signatureBuilder.toString();
	}

	private DomainType findExpressionType(Map<String, DomainProperty> properties, String param) {
		String[] splitByUnderscore = param.split("_");
		if (properties.containsKey(splitByUnderscore[0])) {
			DomainType type = properties.get(splitByUnderscore[0]).getType();
			for (int j = 1; j < splitByUnderscore.length && type != null; j++) {
				type = findMatchingParameter(splitByUnderscore[j], type);
			}
			return type;
		}
		return null;
	}

	private DomainType findMatchingParameter(String name, DomainType type) {
		for(DomainProperty prop : type.getProperties()){
			if (prop.getName().equals(name)) {
				return prop.getType();
			}
		}
		return null;
	}

	public static String findLastJavaIdentifierPart(String prefix) {
		if (prefix == null) {
			return null;
		}
		int lastNonIdentifierPartIndex;
		for (lastNonIdentifierPartIndex = prefix.length() - 1; lastNonIdentifierPartIndex >= 0 && Character.isJavaIdentifierPart(prefix.charAt(lastNonIdentifierPartIndex)); lastNonIdentifierPartIndex--) {
			// search done using loop condition
		}
		return prefix.substring(lastNonIdentifierPartIndex + 1);
	}


}
