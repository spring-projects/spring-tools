/*******************************************************************************
 * Copyright (c) 2014-2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.util;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.springframework.ide.eclipse.editor.support.EditorSupportActivator;

public class DocumentUtil {

	/**
	 * Returns location of the resource underlying the document. May return null
	 * if the location can not be determined.
	 */
	public static IPath getLocation(IDocument doc) {
		ITextFileBufferManager bufferMgr = FileBuffers.getTextFileBufferManager();
		if (bufferMgr != null) {
			ITextFileBuffer buf = bufferMgr.getTextFileBuffer(doc);
			if (buf != null) {
				return buf.getLocation();
			}
		}
		return null;
	}

	public static IProject getProject(IDocument doc) {
		IPath location = getLocation(doc);
		if (location != null) {
			if (location.segmentCount() >= 1) {
				String projectName = location.segment(0);
				return ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
			}
		}
		return null;
	}

	public static IJavaProject getJavaProject(IDocument doc) {
		try {
			if (doc != null) {
				IProject p = getProject(doc);
				if (p != null && p.isAccessible() && p.hasNature(JavaCore.NATURE_ID)) {
					return JavaCore.create(p);
				}
			}
		} catch (Exception e) {
			EditorSupportActivator.log(e);
		}
		return null;
	}

	public static int firstNonWhitespaceCharOfRegion(IDocument doc, IRegion region) {
		try {
			int pos = skipWhiteSpace(doc, region.getOffset());
			if (pos < region.getOffset()+region.getLength()) {
				return pos;
			}
		} catch (Exception e) {
			EditorSupportActivator.log(e);
		}
		return -1;
	}

	/**
	 * Compute location of the last character in the region that is not a whitespace character.
	 */
	public static int lastNonWhitespaceCharOfRegion(IDocument doc, IRegion errorRegion) {
		try {
			int pos = errorRegion.getOffset()+errorRegion.getLength()-1;
			while (pos >= errorRegion.getOffset()&&Character.isWhitespace(doc.getChar(pos))) {
				pos--;
			}
			if (pos >= errorRegion.getOffset()) {
				return pos;
			}
		} catch (Exception e) {
			EditorSupportActivator.log(e);
		}
		return -1;
	}

	public static int skipWhiteSpace(IDocument doc, int pos) {
		try {
			int end = doc.getLength();
			while (pos < end&&Character.isWhitespace(doc.getChar(pos))) {
				pos++;
			}
			if (pos < end) {
				return pos;
			}
		} catch (Exception e) {
			EditorSupportActivator.log(e);
		}
		return -1;
	}

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

//	public static IRegion trim(IDocument doc, IRegion region) {
//		if (region.getLength() == 0) {
//			//Special case avoid doing any work for empty region trimming.
//			return region;
//		}
//		int start = firstNonWhitespaceCharOfRegion(doc, region);
//		if (start >= 0) {
//			int end = lastNonWhitespaceCharOfRegion(doc, region);
//			if (end >= start) {
//				return new Region(start, end-start+1); //+1 because 'end' character should be included.
//			}
//		}
//		//No non-whitespace chars found. It is somewhat ambiguous how to trim
//		// the region down to 'nothing'. We can essentially pick any offset within the
//		// region as the start of the empty region. We decided to just pick the start
//		// of the region (so trim from the end).
//		return new Region(region.getOffset(), 0);
//	}

}
