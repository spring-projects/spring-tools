/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.yaml.structure;

import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.DocumentUtil;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.util.text.IRegion;
import org.springframework.ide.vscode.commons.yaml.structure.YamlStructureParser.SRootNode;

/**
 * Wraps around a IDocument which is presumed to contain YML content and provides
 * some utility methods for working with the contents of the document.
 *
 * @author Kris De Volder
 */
public class YamlDocument {

	private IDocument doc;
	private YamlStructureProvider structureProvider;
	private SRootNode structure;

	public YamlDocument(IDocument _doc, YamlStructureProvider structureProvider) {
		this.doc = _doc;
		this.structureProvider = structureProvider;
	}

	public IDocument getDocument() {
		return doc;
	}

	public SRootNode getStructure() throws Exception {
		if (this.structure == null) {
			this.structure = structureProvider.getStructure(this);
		}
		return structure;
	}

	public int getLineOfOffset(int offset) throws BadLocationException {
		return doc.getLineOfOffset(offset);
	}

	public IRegion getLineInformation(int line) throws BadLocationException {
		return doc.getLineInformation(line);
	}

	public int getLineOffset(int line) throws BadLocationException {
		return doc.getLineOffset(line);
	}


	/**
	 * Returns the number of leading spaces in front of a line. If the line is effectively empty (only contains
	 * comments and/or spaces then this returns -1 (meaning undefined, as indentation level only really means
	 * something for lines which have 'real' content.
	 */
	public int getLineIndentation(int line) {
		IRegion r;
		try {
			r = getLineInformation(line);
		} catch (BadLocationException e) {
			//not a line in the document so it has no indentation
			return -1;
		}
		int len = r.getLength();
		int startOfLine = r.getOffset();
		int leadingSpaces = 0;
		while (leadingSpaces < len) {
			char c = getChar(startOfLine+leadingSpaces);
			if (c == ' ') {
				leadingSpaces++;
			} else if (c == '#') {
				return -1;
			} else if (c != ' ') {
				return leadingSpaces;
			}
			leadingSpaces++;
		}
		//Whole line scanned and nothing but spaces found
		return -1;
	}

	public char getChar(int offset) {
		try {
			return doc.getChar(offset);
		} catch (BadLocationException e) {
			return 0;
		}
	}

	/**
	 * Determine whether given offset is inside a comment.
	 */
	public boolean isCommented(int offset) throws Exception {
		//Yaml only has end of line comments marked with a '#'.
		//So comments never span multiple lines of text and we only have scan back
		//from offset upto the start of the current line.
		IRegion lineInfo = doc.getLineInformationOfOffset(offset);
		if (lineInfo != null) {
			int startOfLine = lineInfo.getOffset();
			while (offset >= startOfLine) {
				char c = getChar(offset);
				if (c == '#') {
					return true;
				}
				offset--;
			}
		}
		return false;
	}

	/**
	 * Fetch text between two offsets. Doesn't throw BadLocationException.
	 * If either one or both of the offsets points outside the
	 * document then they will be adjusted to point the appropriate boundary to
	 * retrieve the text just upto the end or beginning of the document instead.
	 */
	public String textBetween(int start, int end) {
		return DocumentUtil.textBetween(doc, start, end);
	}

	public int getColumn(int offset) throws Exception {
		IRegion r = doc.getLineInformationOfOffset(offset);
		return offset - r.getOffset();
	}

	/**
	 * Fetct text between a given offset and the start of the line that
	 * offset belongs to.
	 */
	public String getLineTextBefore(int offset) throws Exception {
		IRegion l = doc.getLineInformationOfOffset(offset);
		return textBetween(l.getOffset(), offset);
	}

	/**
	 * Fetch the text of the line at a given offset (i.e. all text extending from
	 * offset to the beginning and end of line)
	 */
	public String getLineTextAtOffset(int offset) throws Exception {
		IRegion l = doc.getLineInformationOfOffset(offset);
		return textBetween(l.getOffset(), l.getOffset()+l.getLength());
	}

	public int getStartOfLineAtOffset(int offset) throws Exception {
		return doc.getLineInformationOfOffset(offset).getOffset();
	}

	@Override
	public String toString() {
		return "YamlDocument(>>>>\n"+getDocument().get()+"\n<<<<)";
	}

}
