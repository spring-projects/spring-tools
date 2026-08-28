/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java;

import java.util.List;
import java.util.function.Function;

import org.eclipse.lsp4j.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.ImmutableList;

/**
 * Sample implementation of a 'highlighter' that can be used with {@link SimpleLanguageServer}.highlightWith.
 * <p>
 * Finds every occurence of a given word and highlights it.
 *
 * @author Kris De Volder
 */
public class WordHighlighter implements Function<TextDocument, List<Range>> {
	
	private static final Logger log = LoggerFactory.getLogger(WordHighlighter.class);

	private final String word;

	public WordHighlighter(String word) {
		super();
		this.word = word;
	}

	@Override
	public List<Range> apply(TextDocument doc) {
		String text = doc.get();
		int wordStart = text.indexOf(word);
		ImmutableList.Builder<Range> highlights = ImmutableList.builder();
		while (wordStart >= 0) {
			try {
				highlights.add(doc.toRange(wordStart, word.length()));
			} catch (BadLocationException e) {
				log.error("", e);
			}
			wordStart = text.indexOf(word, wordStart+word.length());
		}
		return highlights.build();
	}
}
