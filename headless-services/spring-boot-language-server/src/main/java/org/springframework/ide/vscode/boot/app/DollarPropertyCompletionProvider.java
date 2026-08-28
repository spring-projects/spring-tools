/*******************************************************************************
 * Copyright (c) 2020, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.app;

import java.util.Collection;
import java.util.List;

import org.springframework.ide.vscode.boot.java.value.ValuePropertyKeyProposal;
import org.springframework.ide.vscode.boot.metadata.PropertyInfo;
import org.springframework.ide.vscode.boot.metadata.SpringPropertyIndexProvider;
import org.springframework.ide.vscode.commons.languageserver.completion.DocumentEdits;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionEngine;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposal;
import org.springframework.ide.vscode.commons.languageserver.completion.InternalCompletionList;
import org.springframework.ide.vscode.commons.languageserver.util.LanguageSpecific;
import org.springframework.ide.vscode.commons.languageserver.util.PrefixFinder;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.FuzzyMap.Match;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableList;

@Component
public class DollarPropertyCompletionProvider implements ICompletionEngine, LanguageSpecific {

	private static String DOLLAR = "${";
	
	private static PrefixFinder PREFIX_FINDER = new PrefixFinder() {
		@Override
		protected boolean isPrefixChar(char c) {
			return Character.isJavaIdentifierPart(c) || c == '-' || c == '.';
		}
	};
	private static final Collection<LanguageId> LANGUAGES = ImmutableList.of(
			LanguageId.BOOT_PROPERTIES, 
			LanguageId.BOOT_PROPERTIES_YAML
	);
	private SpringPropertyIndexProvider indexProvider;

	public DollarPropertyCompletionProvider(BootLanguageServerParams params) {
		this.indexProvider = params.indexProvider;
	}
	
	@Override
	public InternalCompletionList getCompletions(TextDocument doc, int offset) {
		ImmutableList.Builder<ICompletionProposal> proposals = ImmutableList.builder();
		String prefix = PREFIX_FINDER.getPrefix(doc, offset);
		int prefixStart = offset-prefix.length();
		try {
			String dollar = doc.textBetween(prefixStart-DOLLAR.length(), prefixStart);
			if (DOLLAR.equals(dollar)) {
				String pattern = prefix.replaceAll("[^a-zA-Z0-9\\s+]", "");
				boolean hasCloseCurly = doc.getSafeChar(offset) == '}';
				List<Match<PropertyInfo>> matches = indexProvider.getIndex(doc).getProperties().find(pattern);
				for (Match<PropertyInfo> match : matches) {
					DocumentEdits edits = new DocumentEdits(doc, false);
					if (hasCloseCurly) {
						edits.replace(prefixStart, offset, match.data.getId());
					} else {
						edits.replace(prefixStart, offset, match.data.getId()+"}");
					}
					proposals.add(new ValuePropertyKeyProposal(edits, match));
				}
			}
		} catch (BadLocationException e) {
			//ignore. Didn't find the '${' 
		}
		return new InternalCompletionList(proposals.build(), false);
	}

	@Override
	public Collection<LanguageId> supportedLanguages() {
		return LANGUAGES;
	}

}
