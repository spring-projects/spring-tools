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
package org.springframework.ide.vscode.boot.java.data.providers;

import java.util.Collection;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.lsp4j.CompletionItemKind;
import org.springframework.ide.vscode.boot.java.data.DataRepositoryDefinition;
import org.springframework.ide.vscode.boot.java.data.FindByCompletionProposal;
import org.springframework.ide.vscode.boot.java.data.providers.prefixsensitive.DataRepositoryPrefixSensitiveCompletionProvider;
import org.springframework.ide.vscode.commons.languageserver.completion.ICompletionProposal;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.IDocument;

/**
 * This class creates text roposals for query method subjects, e.g. {@code countBy}.
 * @author danthe1st
 */
public class DataRepositoryQueryStartCompletionProvider implements DataRepositoryCompletionProvider{

	@Override
	public void addProposals(Collection<ICompletionProposal> completions, IDocument doc, int offset, String prefix, DataRepositoryDefinition repo, ASTNode node) {
		String localPrefix = DataRepositoryPrefixSensitiveCompletionProvider.findLastJavaIdentifierPart(prefix);
		for(QueryMethodSubject queryMethodSubject : QueryMethodSubject.QUERY_METHOD_SUBJECTS){
			String toInsert = queryMethodSubject.key() + "By";
			if (prefix == null || (toInsert.length() > localPrefix.length() && toInsert.startsWith(localPrefix)) || isOffsetAfterWhitespace(doc, offset)) {
				completions.add(FindByCompletionProposal.createProposal(offset, CompletionItemKind.Method, prefix, toInsert, toInsert, true, null));
			}
		}
	}

	private boolean isOffsetAfterWhitespace(IDocument doc, int offset) {
		try {
			return offset > 0 && Character.isWhitespace(doc.getChar(offset-1));
		}catch (BadLocationException e) {
			return false;
		}
	}

}
