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
package org.springframework.ide.vscode.commons.util.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.linetracker.DefaultLineTracker;
import org.springframework.ide.vscode.commons.util.text.linetracker.ILineTracker;

import javolution.text.Text;

public class TextDocument implements IDocument {
	
	private static final Logger log = LoggerFactory.getLogger(TextDocument.class);

	ILineTracker lineTracker = new DefaultLineTracker();
	private static final Pattern NEWLINE = Pattern.compile("\\n|\\r\\n");

	private final LanguageId languageId;
	private final String uri;
	private Text text = new Text("");
	private int version;
	private boolean changedSinceLastSave = false;

	public TextDocument(String uri, LanguageId languageId) {
		this(uri, languageId, 0, "");
	}

	private TextDocument(TextDocument other) {
		this.uri = other.uri;
		this.languageId = other.getLanguageId();
		this.text = other.text;
		this.lineTracker.set(text.toString());
		this.version = other.version;
		this.changedSinceLastSave = other.changedSinceLastSave;
	}

	public TextDocument(String uri, LanguageId languageId, int version, String text) {
		this.uri = uri;
		this.languageId = languageId;
		this.version = version;
		setText(text);
	}

	@Override
	public String getUri() {
		return uri;
	}

	@Override
	public String get() {
		return getText().toString();
	}

	protected synchronized Text getText() {
		return text;
	}

	public synchronized void setText(String text) {
		this.text = new Text(text);
		this.lineTracker.set(text);
	}

	private void apply(TextDocumentContentChangeEvent change) throws BadLocationException {
		Range rng = change.getRange();
		if (rng == null) {
			//full sync mode
			setText(change.getText());
		} else {
			int start = toOffset(rng.getStart());
			int end = toOffset(rng.getEnd());
			replace(start, end-start, change.getText());
		}
	}

	public synchronized void apply(DidChangeTextDocumentParams params) throws BadLocationException {
		int newVersion = params.getTextDocument().getVersion();
		if (version < newVersion) {
			for (TextDocumentContentChangeEvent change : params.getContentChanges()) {
				apply(change);
			}
			this.version = newVersion;
			this.changedSinceLastSave = true;
		} else {
			log.warn("Change event with bad version ignored");
		}
	}

	/**
	 * Convert a simple offset+length pair into a vscode range. This is a method on
	 * TextDocument because it requires splitting document into lines to determine
	 * line numbers from offsets.
	 */
	public Range toRange(int offset, int length) throws BadLocationException {
		int end = Math.min(offset + length, getLength());
		Range range = new Range();
		range.setStart(toPosition(offset));
		range.setEnd(toPosition(end));
		return range;
	}

	/**
	 * Determine the line-number a given offset (i.e. what line is the offset inside of?)
	 */
	private int lineNumber(int offset) throws BadLocationException {
		return lineTracker.getLineNumberOfOffset(offset);
	}


	public Position toPosition(int offset) throws BadLocationException {
		int line = lineNumber(offset);
		int startOfLine = startOfLine(line);
		int column = offset - startOfLine;
		Position pos = new Position();
		pos.setCharacter(column);
		pos.setLine(line);
		return pos;
	}

	private int startOfLine(int line) throws BadLocationException {
		IRegion region = lineTracker.getLineInformation(line);
		return region.getOffset();
	}

	@Override
	public IRegion getLineInformationOfOffset(int offset) {
		try {
			if (offset <= getLength()) {
				int line = lineNumber(offset);
				return getLineInformation(line);
			}
		} catch (BadLocationException e) {
			//outside document.
		}
		return null;
	}

	@Override
	public int getLength() {
		return getText().length();
	}

	@Override
	public String get(int start, int len) throws BadLocationException {
		try {
			return getText().subtext(start, start+len).toString();
		} catch (Exception e) {
			throw new BadLocationException(e);
		}
	}

	@Override
	public int getNumberOfLines() {
		return lineTracker.getNumberOfLines();
	}

	@Override
	public String getDefaultLineDelimiter() {
		Matcher newlineFinder = NEWLINE.matcher(getText());
		if (newlineFinder.find()) {
			return getText().subtext(newlineFinder.start(), newlineFinder.end()).toString();
		}
		return System.getProperty("line.separator");
	}

	@Override
	public char getChar(int offset) throws BadLocationException {
		if (offset >= 0 && offset < getText().length()) {
			return getText().charAt(offset);
		}
		throw new BadLocationException();
	}

	@Override
	public int getLineOfOffset(int offset) throws BadLocationException {
		return lineTracker.getLineNumberOfOffset(offset);
	}

	@Override
	public IRegion getLineInformation(int line) {
		try {
			return lineTracker.getLineInformation(line);
		} catch (BadLocationException e) {
			//line doesn't exist
		}
		return null;
	}

	@Override
	public int getLineOffset(int line) throws BadLocationException {
		return lineTracker.getLineOffset(line);
	}

	public int toOffset(Position position) throws BadLocationException {
		IRegion region = lineTracker.getLineInformation(position.getLine());
		int lineStart = region.getOffset();
		return lineStart + position.getCharacter();
	}

	@Override
	public synchronized void replace(int start, int len, String ins) throws BadLocationException {
		int end = start+len;
		text = getText()
			.delete(start, end)
			.insert(start, new Text(ins));
		lineTracker.replace(start, len, ins);
	}

	public synchronized TextDocument copy() {
		return new TextDocument(this);
	}

	@Override
	public String textBetween(int start, int end) throws BadLocationException {
		return get(start, end-start);
	}

	@Override
	public String toString() {
		return "TextDocument(uri="+uri+"["+version+"],\n"+this.getText()+"\n)";
	}

	/**
	 * Returns the number of leading spaces in front of a line. If the line only contains spaces then
	 * this returns the number of spaces the line contains.
	 * <p>
	 * This may return -1 if, for some reason, a line's indentation cannot be determined (e.g. the line does
	 * not exist in the document)
	 */
	public int getLineIndentation(int line) {
		//TODO: this works fine only if we assume all indentation is done with spaces only.
		// To generalize this it should probably return a String containing exactly the spaces
		// and tabs at the front of the line.
		IRegion r = getLineInformation(line);
		if (r == null) {
			//not a line in the document so it has no indentation
			return -1;
		}
		int len = r.getLength();
		int startOfLine = r.getOffset();
		int leadingSpaces = 0;
		while (leadingSpaces < len) {
			char c = getSafeChar(startOfLine+leadingSpaces);
			if (c == ' ') {
				leadingSpaces++;
			} else if (c != ' ') {
				return leadingSpaces;
			}
			leadingSpaces++;
		}
		return leadingSpaces;
	}

	/**
	 * Like getChar but never throws {@link BadLocationException}. Instead it
	 * return (char)0 for offsets outside the document.
	 */
	public char getSafeChar(int offset) {
		try {
			return getChar(offset);
		} catch (BadLocationException e) {
			return 0;
		}
	}

	@Override
	public LanguageId getLanguageId() {
		return languageId;
	}

	@Override
	public Range toRange(IRegion region) throws BadLocationException {
		return toRange(region.getOffset(), region.getLength());
	}

	@Override
	public int getVersion() {
		return version;
	}

	public TextDocumentIdentifier getId() {
		if (uri != null) {
			return new TextDocumentIdentifier(uri);
		}
		return null;
	}

	public void saved() {
		this.changedSinceLastSave = false;
	}

	public boolean hasChangedSinceLastSave() {
		return this.changedSinceLastSave;
	}
}
