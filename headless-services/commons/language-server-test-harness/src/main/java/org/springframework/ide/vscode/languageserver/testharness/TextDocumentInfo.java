/*******************************************************************************
 * Copyright (c) 2016-2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.languageserver.testharness;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.springframework.ide.vscode.commons.util.text.LanguageId;

/**
 * Deprecated, we should get rid of this class and use {@link TextDocument}.
 */
@Deprecated
public class TextDocumentInfo {

	Pattern NEWLINE = Pattern.compile("\\r\\n|\\n");

	private final TextDocumentItem document;

	private int[] _lineStarts;

	public TextDocumentInfo(TextDocumentItem document) {
		this.document = document;
	}

	public LanguageId getLanguageId() {
		return LanguageId.of(getDocument().getLanguageId());
	}

	public int getVersion() {
		return getDocument().getVersion();
	}

	public String getText() {
		return getDocument().getText();
	}

	public String getUri() {
		return getDocument().getUri();
	}

	public TextDocumentItem getDocument() {
		return document;
	}

	public String getText(Range rng) {
		String txt = getText();
		int start = Math.max(0, toOffset(rng.getStart()));
		int end = Math.min(txt.length(), toOffset(rng.getEnd()));
		return txt.substring(start, end);
	}

	public int toOffset(Position p) {
		int startOfLine = startOfLine(p.getLine());
		return startOfLine+p.getCharacter();
	}

	private int startOfLine(int line) {
		return lineStarts()[line];
	}

	private int[] lineStarts() {
		if (_lineStarts == null) {
			_lineStarts = parseLines();
		}
		return _lineStarts;
	}

	private int[] parseLines() {
		List<Integer> lineStarts = new ArrayList<>();
		lineStarts.add(0);
		Matcher matcher = NEWLINE.matcher(getText());
		int pos = 0;
		while (matcher.find(pos)) {
			lineStarts.add(pos = matcher.end());
		}
		int[] array = new int[lineStarts.size()];
		for (int i = 0; i < array.length; i++) {
			array[i] = lineStarts.get(i);
		}
		return array;
	}

	/**
	 * Find and return the (first) position of a given text snippet in the
	 * document.
	 *
	 * @return The position, or null if the snippet can't be found.
	 */
	public Position positionOf(String snippet) {
		int offset = getText().indexOf(snippet);
		if (offset >= 0) {
			return toPosition(offset);
		}
		return null;
	}

	public Position toPosition(int offset) {
		int line = lineNumber(offset);
		int startOfLine = startOfLine(line);
		int column = offset - startOfLine;
		Position pos = new Position();
		pos.setCharacter(column);
		pos.setLine(line);
		return pos;
	}

	/**
	 * Determine the line-number a given offset (i.e. what line is the offset inside of?)
	 */
	private int lineNumber(int offset) {
		int[] lineStarts = lineStarts();
		// TODO Could use binary search which is faster
		int lineNumber = 0;
		for (int i = 0; i < lineStarts.length; i++) {
			if (lineStarts[i] <= offset) {
				lineNumber = i;
			} else {
				return lineNumber;
			}
		}
		return lineNumber;
	}

	public TextDocumentIdentifier getId() {
		TextDocumentIdentifier id = new TextDocumentIdentifier();
		id.setUri(getUri());
		return id;
	}

	public String getLineIndentString(int line) {
		int start = startOfLine(line);
		int scan = start;
		char c = getSafeChar(scan);
		StringBuilder indentStr = new StringBuilder();
		while (c == ' '|| c == '\t') {
			indentStr.append(c);
			c = getSafeChar(++scan);
		}
		return indentStr.toString();
	}

	private char getSafeChar(int pos) {
		String text = getText();
		if (pos > 0 && pos < text.length()) {
			return text.charAt(pos);
		}
		return 0;
	}

	public String textBetween(int start, int end) {
		return getText().substring(start, end);
	}

}
