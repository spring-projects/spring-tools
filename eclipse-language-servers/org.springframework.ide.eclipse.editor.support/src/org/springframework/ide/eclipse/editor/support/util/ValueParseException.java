/*******************************************************************************
 * Copyright (c) 2017 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.util;

/**
 * Exception if there is a failure when parsing a value. It does not wrap
 * other exceptions such that when thrown, the parse exception is the "deepest"
 * error.
 *
 */
public class ValueParseException extends Exception {

	private int startIndex = -1;
	private int endIndex = -1;
	private String highightString;

	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	public ValueParseException(String message) {
		super(message);
	}

	public ValueParseException(String message, int startIndex, int endIndex, String highlightString) {
		this(message, startIndex, endIndex);
		this.highightString = highlightString;
	}

	public ValueParseException(String message, int startIndex, int endIndex) {
		this(message);
		this.startIndex = startIndex;
		this.endIndex = endIndex;
	}

	public int getStartIndex() {
		return startIndex;
	}

	public int getEndIndex() {
		return endIndex;
	}

	public void adjustOffset(int by) {
		if (startIndex >= 0) {
			this.startIndex += by;
		}
		if (endIndex >= 0) {
			this.endIndex += by;
		}
	}

	public DocumentRegion getHighlightRegion(DocumentRegion containingRegion) {
		int start = startIndex >= 0 ? startIndex : 0;
		int end = endIndex >= 0 ? endIndex : containingRegion.length();
		if (highightString != null) {
			//Make a 'best effort' adjusting start and end to highlight the correct string,
			// even if positions are screwy because of handling escape sequences before parsing.
			String actualHighlight = containingRegion.subSequence(start, end).toString();
			if (!actualHighlight.equals(highightString)) {
				String containingString = containingRegion.toString();
				//Search 'close' to start position first
				int found = containingString.indexOf(highightString, start);
				if (found >= 0) {
					return containingRegion.subSequence(found, found+highightString.length());
				}
				//Second... search whole string
				found = containingString.indexOf(highightString);
				if (found >= 0) {
					return containingRegion.subSequence(found, found+highightString.length());
				}
				//Give up, couldn't find the highlight string... highlight everything
				return containingRegion;
			}
		}
		return containingRegion.subSequence(start, end);
	}

}
