/*******************************************************************************
 * Copyright (c) 2016, 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.commons.util.text;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.BadLocationException;

public class DocumentUtil {

	//TODO: this stuff belongs in IDocument and its implementation, not here. This class should be removed.

	/**
	 * Fetch text between two offsets. Doesn't throw BadLocationException.
	 * If either one or both of the offsets points outside the
	 * document then they will be adjusted to point the appropriate boundary to
	 * retrieve the text just upto the end or beginning of the document instead.
	 */
	public static String textBetween(IDocument doc, int start, int end) {
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
	
	/**
	 * Compares two LSP4J positions
	 * @param p1
	 * @param p2
	 * @return integer number which is 0 if equals, <0 if p1 comes before p2 and >0 otherwise
	 */
	public static int compare(Position p1, Position p2) {
		int res = p1.getLine() - p2.getLine();
		if (res == 0) {
			res = p1.getCharacter() - p2.getCharacter();
		}
		return res;
 	}
	
	public static boolean containsRange(Range outer, Range inner) {
		return compare(outer.getStart(), inner.getStart()) <= 0 
				&& compare(outer.getEnd(), inner.getEnd()) >= 0;
	}

}
