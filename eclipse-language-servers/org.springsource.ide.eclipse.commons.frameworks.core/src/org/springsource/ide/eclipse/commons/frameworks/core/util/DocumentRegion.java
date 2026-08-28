/*******************************************************************************
 * Copyright (c) 2020 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.frameworks.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TypedRegion;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

/**
 * A non-sucky alternative to {@link IRegion}. Represents a region of text in a document.
 * <p>
 * Caution: assumes the underlying document is not mutated during the lifetime of the
 * region object (otherwise start/end positions may no longer be valid).
 * <p>
 * Implements {@link CharSequence} for convenience (e.g you can use {@link DocumentRegion} as
 * input to a {@link Pattern} and other standard JRE functions which expect a {@link CharSequence}.
 *
 * @author Kris De Volder
 */
public class DocumentRegion implements CharSequence {
	final IDocument doc;
	final int start;
	final int end;
	public DocumentRegion(IDocument doc, IRegion r) {
		this(doc,
			r.getOffset(),
			r.getOffset()+r.getLength()
		);
	}

	/**
	 * Constructs a {@link DocumentRegion} on a given document. Tries its
	 * best to behave sensibly when passed 'strange' coordinates by
	 * adjusting them logically rather than throw an Exception.
	 * <p>
	 * A position before the start of the document is moved to be the start
	 * of the document.
	 * <p>
	 * A position after the end of the document is moved to the end
	 * of the document.
	 * <p>
	 * If 'end' position is before the start position it is moved be
	 * exactly at the start position (this avoids region with
	 * negative length).
	 */
	public DocumentRegion(IDocument doc, int start, int end) {
		this.doc = doc;
		this.start = limitRange(start, 0, doc.getLength());
		this.end = limitRange(end, start, doc.getLength());
	}

	/**
	 * Create {@link DocumentRegion} covering the whole document.
	 */
	public DocumentRegion(IDocument doc) {
		this(doc, 0, doc.getLength());
	}

	private int limitRange(int offset, int min, int max) {
		if (offset < min) {
			return min;
		}
		if (offset > max) {
			return max;
		}
		return offset;
	}

	@Override
	public String toString() {
		return textBetween(doc, start, end);
	}

	public DocumentRegion trim() {
		return trimEnd().trimStart();
	}

	public DocumentRegion trimStart() {
		int howMany = 0;
		int len = length();
		while (howMany < len && Character.isWhitespace(charAt(howMany))) {
			howMany++;
		}
		return subSequence(howMany, len);
	}

	public DocumentRegion trimEnd() {
		int howMany = 0; //how many chars to remove from the end
		int len = length();
		int lastChar = len-1;
		while (howMany < len && Character.isWhitespace(charAt(lastChar-howMany))) {
			howMany++;
		}
		if (howMany > 0) {
			return subSequence(0, len-howMany);
		}
		return this;
	}

	/**
	 * Gets character from the region, offset from the start of the region
	 * @return the character from the document (char)0 if the offset is outside the region.
	 */
	public char unsafeCharAt(int offset) {
		if (offset < 0 || offset >= length()) {
			throw new IndexOutOfBoundsException(""+offset);
		}
		try {
			return doc.getChar(start+offset);
		} catch (BadLocationException e) {
			throw new IndexOutOfBoundsException(""+offset);
		}
	}
	
	/**
	 * Like charAt, but doesn't IndexOutOfBoundsException. Instead it
	 * return (char)0.
	 */
	@Override
	public char charAt(int offset) {
		try {
			return unsafeCharAt(offset);
		} catch (IndexOutOfBoundsException e) {
			return 0;
		}
	}

	@Override
	public int length() {
		return end-start;
	}

	@Override
	public DocumentRegion subSequence(int start, int end) {
		int len = length();
		Assert.isLegal(start >= 0);
		Assert.isLegal(end <= len);
		if (start == 0 && end == len) {
			return this;
		}
		return new DocumentRegion(doc, this.start+start, this.start+end);
	}

	public boolean isEmpty() {
		return length() == 0;
	}

	public DocumentRegion subSequence(int start) {
		return subSequence(start, length());
	}

	public IRegion asRegion() {
		return new Region(start, end-start);
	}

	public ITypedRegion asTypedRegion(String type) {
		return new TypedRegion(start, length(), type);
	}


	public int indexOf(char ch, int fromIndex) {
		while (fromIndex < length()) {
			if (charAt(fromIndex) == ch) {
				return fromIndex;
			}
			fromIndex++;
		}
		return -1;
	}

	public DocumentRegion[] split(char c) {
		List<DocumentRegion> pieces = new ArrayList<>();
		int start = 0;
		int end;
		while ((end=indexOf(c, start)) >= 0) {
			pieces.add(subSequence(start, end));
			start = end+1;
		}
		// Do not forget the last piece!
		pieces.add(subSequence(start, length()));
		return pieces.toArray(new DocumentRegion[pieces.size()]);
	}

	public DocumentRegion[] split(Pattern delimiter) {
		List<DocumentRegion> pieces = new ArrayList<>();
		int start = 0;
		Matcher matcher = delimiter.matcher(this);
		while (matcher.find(start)) {
			int end = matcher.start();
			pieces.add(subSequence(start, end));
			start = matcher.end();
		}
		// Do not forget the last piece!
		pieces.add(subSequence(start, length()));
		return pieces.toArray(new DocumentRegion[pieces.size()]);
	}

	/**
	 * Removes a single occurrence of pat from the start of this region.
	 */
	public DocumentRegion trimStart(Pattern pat) {
		pat = Pattern.compile("^("+pat.pattern()+")");
		Matcher matcher = pat.matcher(this);
		if (matcher.find()) {
			return subSequence(matcher.end());
		}
		return this;
	}

	/**
	 * Removes a single occurrence of pat from the end of this region.
	 */
	public DocumentRegion trimEnd(Pattern pat) {
		pat = Pattern.compile("("+pat.pattern()+")$");
		Matcher matcher = pat.matcher(this);
		if (matcher.find()) {
			return subSequence(0, matcher.start());
		}
		return this;
	}

	/**
	 * Get the region after this one with a given  lenght.
	 * <p>
	 * If the document is too short to provide the requested lenght
	 * then the region is truncated to end of the document.
	 */
	public DocumentRegion textAfter(int len) {
		Assert.isLegal(len >= 0);
		return new DocumentRegion(doc, end, Integer.MAX_VALUE == len ? Integer.MAX_VALUE : end+len);
	}

	/**
	 * Get the region before this one with a given lenght.
	 * <p>
	 * If the requested region extends before the start of the document,
	 * then the region is shortened so its start coincides with document start.
	 */
	public DocumentRegion textBefore(int len) {
		Assert.isLegal(len >= 0);
		return new DocumentRegion(doc, start-len, start);
	}

	public IDocument getDocument() {
		return doc;
	}

	/**
	 * Get the start of this region in 'absolute' terms (i.e. relative to the document).
	 */
	public int getStart() {
		return start;
	}

	/**
	 * Get the end of this region in 'absolute' terms (i.e. relative to the document).
	 */
	public int getEnd() {
		return end;
	}

	/**
	 * Convert the given document offset into an offset relative to this region.
	 */
	public int toRelative(int offset) {
		return offset-start;
	}

	public int getLength() {
		return length();
	}

	public boolean endsWith(CharSequence string) {
		int myLen = length();
		int strLen = string.length();
		if (myLen >= strLen) {
			for (int i = 0; i < strLen; i++) {
				if (charAt(myLen-strLen+i) != string.charAt(i)) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * Take documentRegion at the end of
	 */
	public DocumentRegion textAtEnd(int numChars) {
		numChars = Math.min(getLength(), numChars);
		return new DocumentRegion(doc, end-numChars, end);
	}
	
	public DocumentRegion textAtStart(int numChars) {
		numChars = Math.min(getLength(), numChars);
		return new DocumentRegion(doc, start, start + numChars);
	}

	/**
	 * Fetch text between two offsets. Doesn't throw BadLocationException.
	 * If either one or both of the offsets points outside the
	 * document then they will be adjusted to point the appropriate boundary to
	 * retrieve the text just upto the end or beginning of the document instead.
	 */
	private static String textBetween(IDocument doc, int start, int end) {
		Assert.isLegal(start <= end);
		if (start >= doc.getLength()) {
			return "";
		}
		if (start < 0) {
			start = 0;
		}
		if (end > doc.getLength()) {
			end = doc.getLength();
		}
		if (end < start) {
			end = start;
		}
		try {
			return doc.get(start, end-start);
		} catch (BadLocationException e) {
			//unless the code above is wrong... this is supposed to be impossible!
			throw new IllegalStateException("Bug!", e);
		}
	}

	public DocumentRegion extendBeforeUntil(char c) {
		int start = this.start - 1;
		try {
			for (; start >= 0 && doc.getChar(start) != '<'; start--) {
				// nothing
			}
		} catch (BadLocationException e) {
			// ignore
		}
		return new DocumentRegion(doc, start, end);
	}

	public DocumentRegion textBeforeOnLine() {
		try {
			int line = doc.getLineOfOffset(start);
			int lineOffset = doc.getLineOffset(line);
			return new DocumentRegion(doc, lineOffset, start);
		} catch (BadLocationException e) {
			throw ExceptionUtil.unchecked(e);
		}
	}

	public boolean isWhiteSpace() {
		for (int i = 0; i < getLength(); i++) {
			if (!Character.isWhitespace(charAt(i))) {
				return false;
			}
		}
		return true;
	}

	public DocumentRegion textAfterOnLine() {
		DocumentRegion textAfter = this.textAfter(Integer.MAX_VALUE);
		return textAfter.takeWhile(c -> c != '\n' && c != 0);
	}

	public DocumentRegion takeWhile(Predicate<Character> condition) {
		int i = 0;
		for (; i < getLength() && condition.test(charAt(i)); i++) {
		}
		return this.textAtStart(i);
	}

//	public Range asRange() throws BadLocationException {
//		return doc.toRange(asRegion());
//	}
}