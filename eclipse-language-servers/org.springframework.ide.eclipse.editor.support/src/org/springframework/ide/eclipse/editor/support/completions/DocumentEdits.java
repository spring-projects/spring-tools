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
package org.springframework.ide.eclipse.editor.support.completions;

import java.util.ArrayList;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.swt.graphics.Point;
import org.eclipse.text.edits.TextEdit;

/**
 * Helper to make it easier to create composite modifications to IDocument.
 * <p>
 * It allows building up a sequence of edits which are all expressed in terms of
 * offsets in the unmodified document. (So, when computing edits based on a
 * some kind of AST its is not necessary to recompute the AST or update its
 * position information after each small modification).
 * <p>
 * Similar functionality to Eclipse's {@link TextEdit} but unlike {@link TextEdit}
 * it is not as finicky with respect to overlapping edits. We consider the
 * order in which edits are created meaningful and give a logical semantics
 * to edits that 'overlap'.
 * <p>
 * Also, each edit affects the cursor position placing it at the end of
 * that edit. This will mostly do what you would want it to, provided that
 * you save the edit where you want the cursor to end-up at for last.
 *
 * @author Kris De Volder
 */
public class DocumentEdits implements ProposalApplier {

	// Note: for small number of edits this implementation is okay.
	// for large number of edits it is potentially slow because of the
	// way it transforms edit coordinates (a growing chain of
	// OffsetTransformer is created so every extra edit added
	// will take O(n) to preform the transform on its coordinates.
	// So applying 'n' edits is O(n^2).
	//
	// A smarter way of doing this is possible. Here's a possible idea:
	//
	//   For simplicity sake assume that all edits are 'independent' (i.e.
	//   changing their executing order doesn't matter.
	//
	//   It is advantageous to sort the edits by position and execute them
	//   high to low because... we can then guarantee that the transform
	//   function that will apply to each edit does nothing on the coordinates
	//   that it cares about (since all prior edits only affect higher offets)
	//
	//   Unfortunately the simplifying assumption does not allways hold.
	//   There are two problems:
	//
	//    1) updating the selection is order dependent.
	//        => this can be solved by observing that only the last
	//           edit operation need update the selection since it cancels
	//           all prior selections.
	//        => Mark the last operation with a 'flag' 'setSelection=true'
	//           and do not update the selection in any other operations.
	//
	//    2) some edits may not be independent
	//        => When the edits are sorted in descending order based on their 'end'
	//           coordinate them 'conflicting' edits should be adjacent and we can
	//           'group them' together into 'cluster' where we can preserve their
	//           relative execution order.
	//        => While executing a 'cluster' we shall keep track of the offset
	//           transform function just like the current implementation does.
	//        => When the cluster of 'conflicting' operations has been dealt with
	//           the offset transform function no longer matters for the
	//           remaining edits who's offesets are all strictly 'smaller'.
	//           Thus the trasnform function can be discarded.
	//
	//   Assuming most edits are independent and only a few of them conflict, then
	//   this algorithm can provide equivalent functinonality to the current one
	//   but for an 'average' performance which is O(n*log(n))
	//   Of course worst-case is still O(n^2) but we wouldn't expect to hit that case
	//   assuming we mostly have lots of small edits to disjoint sections of the document.
	//
	//   So... edit operations could be sorted based on their position
	//   and executed in decreasing order of their 'start'.
	//
	//   The tricky part would be to preserve the order-dependent semantics.

	private class Insertion extends Edit {
		private int offset;
		private String text;

		public Insertion(int offset, String insert) {
			this.offset = offset;
			this.text = insert;
		}

		@Override
		void apply(DocumentState doc) throws BadLocationException {
			doc.insert(offset, text);
		}

		@Override
		public String toString() {
			return "ins("+text+"@"+offset+")";
		}
	}

	private abstract class Edit {
		abstract void apply(DocumentState doc) throws BadLocationException;
		public abstract String toString();
	}

	private class Deletion extends Edit {

		private int start;
		private int end;

		public Deletion(int start, int end) {
			this.start = start;
			this.end = end;
		}

		@Override
		void apply(DocumentState doc) throws BadLocationException {
			doc.delete(start, end);
		}

		@Override
		public String toString() {
			return "del("+start+"->"+end+")";
		}

	}

	private interface OffsetTransformer {
		int trasform(int offset);
	}

	private static final OffsetTransformer NULL_TRANSFORM = new OffsetTransformer() {
		public int trasform(int offset) {
			return offset;
		}
	};

	/**
	 * DocumentState provides methods to modify a document, its methods accept
	 * offsets expressed relative to the original document contents and keeps track
	 * of a OffsetTransformer that maps them to offsets in the current document.
	 */
	private static class DocumentState {
		private IDocument doc; //may be null, in which case no actual modifications are performed
		private OffsetTransformer org2new = NULL_TRANSFORM;
		private int selection = -1; //-1 Means no edits where applied that change selection so
									// the current selection is unknown

		public DocumentState(IDocument doc) {
			this.doc = doc;
		}

		public void insert(int start, final String text) throws BadLocationException {
			final int tStart = org2new.trasform(start);
			if (!text.isEmpty()) {
				if (doc != null) {
					doc.replace(tStart, 0, text);
				}
				final OffsetTransformer parent = org2new;
				org2new = new OffsetTransformer() {
					public int trasform(int org) {
						int tOffset = parent.trasform(org);
						if (tOffset < tStart) {
							return tOffset;
						} else {
							return tOffset + text.length();
						}
					}
				};
			}
			selection = tStart+text.length();
		}

		public void delete(final int start, final int end) throws BadLocationException {
			final int tStart = org2new.trasform(start);
			if (end > start) { // skip work for 'delete nothing' op
				final int tEnd = org2new.trasform(end);
				if (tEnd > tStart) { // skip work for 'delete nothing' op
					if (doc != null) {
						doc.replace(tStart, tEnd-tStart, "");
					}

					final OffsetTransformer parent = org2new;
					org2new = new OffsetTransformer() {
						public int trasform(int org) {
							int tOffset = parent.trasform(org);
							if (tOffset <= tStart) {
								return tOffset;
							} else if (tOffset >= tEnd) {
								return tOffset - tEnd + tStart;
							} else {
								return start;
							}
						}
					};
				}
			}
			selection = tStart;
		}

		@Override
		public String toString() {
			if (doc == null) {
				return super.toString();
			}
			StringBuilder buf = new StringBuilder();
			buf.append("DocumentState(\n");
			buf.append(doc.get()+"\n");
			buf.append(")\n");
			return buf.toString();
		}
	}

	private ArrayList<Edit> edits = new ArrayList<Edit>();
	private IDocument doc;

	public DocumentEdits(IDocument doc) {
		this.doc = doc;
	}

	public void delete(int start, int end) {
		Assert.isLegal(start <= end);
		edits.add(new Deletion(start, end));
	}

	public void delete(int offset, String text) {
		delete(offset, offset+text.length());
	}

	public void insert(int offset, String insert) {
		edits.add(new Insertion(offset, insert));
	}

	@Override
	public Point getSelection(IDocument doc) throws Exception {
		DocumentState selectionState = new DocumentState(null);
		for (Edit edit : edits) {
			edit.apply(selectionState);
		}
		if (selectionState.selection >= 0) {
			return new Point(selectionState.selection, 0);
		}
		return null;
	}

	@Override
	public void apply(IDocument _doc) throws Exception {
		DocumentState doc = new DocumentState(_doc);
		for (Edit edit : edits) {
			edit.apply(doc);
		}
	}

	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("DocumentModifier(\n");
		for (Edit edit : edits) {
			buf.append("   "+edit);
		}
		buf.append(")\n");
		return buf.toString();
	}

	public void moveCursorTo(int newCursor) {
		insert(newCursor, "");
	}

	public void deleteLineBackwardAtOffset(int offset) throws Exception {
		int line = doc.getLineOfOffset(offset);
		deleteLineBackward(line);
	}

	/**
	 * Deletes the line of text with given line number, including either the following or
	 * preceding newline. If there is a choice between the preceding or following newline,
	 * the preceding newline is deleted. This will leave the cursor at the end of
	 * the preceding line.
	 * <p>
	 * Note: a similar operation 'deleteLineForward' could be implemented prefering to
	 * delete the following newline. This would be equivalent except that it will leave the
	 * cursor at the start of the following line.
	 */
	public void deleteLineBackward(int lineNumber) throws BadLocationException {
		IRegion line = doc.getLineInformation(lineNumber);
		int startOfDeletion;
		int endOfDeletion;
		if (lineNumber > 0) {
			IRegion previousLine = doc.getLineInformation(lineNumber-1);
			startOfDeletion = endOf(previousLine);
			endOfDeletion = endOf(line);
		} else if (lineNumber < doc.getNumberOfLines()-1) {
			IRegion nextLine = doc.getLineInformation(lineNumber+1);
			startOfDeletion = line.getOffset();
			endOfDeletion = nextLine.getOffset();
		} else {
			startOfDeletion = line.getOffset();
			endOfDeletion = endOf(line);
		}
		delete(startOfDeletion, endOfDeletion);
	}

	private int endOf(IRegion line) {
		return line.getOffset()+line.getLength();
	}

	public void replace(int start, int end, String newText) {
		delete(start, end);
		insert(start, newText);
	}

}
