/*******************************************************************************
 * Copyright (c) 2015, 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver.completion;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.TextEdit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.util.text.IRegion;
import org.springframework.ide.vscode.commons.util.text.Region;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

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
	
	private static final Logger log = LoggerFactory.getLogger(DocumentEdits.class);

	private static final Pattern NON_WS_CHAR = Pattern.compile("\\S");

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
	//           remaining edits who's offsets are all strictly 'smaller'.
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

	public static class TextReplace {
		public final int start;
		public final int end;
		public final String newText;

		public TextReplace(int start, int end, String newText) {
			super();
			this.start = start;
			this.end = end;
			this.newText = newText;
		}

		public IRegion getRegion() {
			return new Region(start, end-start);
		}
	}

	/**
	 * When an insert occurs, a single position in the file transforms ambiguously into two different
	 * positions after the insertion, depending on whether we 'float' marker for the position after the
	 * inserted block or leave it at the beginning.
	 */
	public enum Direction {
		/**
		 * Transform positions around inserts to stick to the front of the inserted block.
		 */
		BEFORE,
		/**
		 *
		 * Transform positions around inserts to stick to the end of the inserted block.
		 */
		AFTER
	}

	private class Insertion extends Edit {
		private int offset;
		private String text;
		private Supplier<String> resolveInsert;
		private boolean resolved;
		

		public Insertion(boolean grabCursor, int offset, String insert) {
			this(grabCursor, offset, insert, null);
		}

		public Insertion(boolean grabCursor, int offset, String insert, Supplier<String> resolveInsert) {
			super(grabCursor);
			this.offset = offset;
			this.text = insert;
			this.resolveInsert = resolveInsert;
			this.resolved = resolveInsert == null;
		}
		
		@Override
		void apply(DocumentState doc) throws BadLocationException {
			doc.insert(grabCursor, offset, text);
		}

		@Override
		public String toString() {
			return "ins("+text+"@"+offset+")";
		}

		@Override
		public int getStart() {
			return offset;
		}

		@Override
		public int getEnd() {
			return offset;
		}
		
		public void resolve() {
			text = resolveInsert.get();
			resolved = true;
		}
		
		public boolean isResolved() {
			return resolved;
		}
	}

	private abstract class Edit {
		protected boolean grabCursor;
		protected Edit(boolean grabCursor) {
			this.grabCursor = grabCursor;
		}
		public abstract int getStart();
		public abstract int getEnd();
		abstract void apply(DocumentState doc) throws BadLocationException;
		@Override
		public abstract String toString();
		public boolean isResolved() {
			return true;
		}
		public void resolve() {}
	}

	private class Deletion extends Edit {

		private int start;
		private int end;

		public Deletion(boolean grabCursor, int start, int end) {
			super(grabCursor);
			this.start = start;
			this.end = end;
		}

		@Override
		void apply(DocumentState doc) throws BadLocationException {
			doc.delete(grabCursor, start, end);
		}

		@Override
		public String toString() {
			return "del("+start+"->"+end+")";
		}

		@Override
		public int getStart() {
			return start;
		}

		@Override
		public int getEnd() {
			return end;
		}

	}

	private interface OffsetTransformer {
		int transform(int offset, Direction dir);
	}

	private static final OffsetTransformer NULL_TRANSFORM = new OffsetTransformer() {
		@Override
		public int transform(int offset, Direction dir) {
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

		public void insert(boolean grabCursor, int start, final String text) throws BadLocationException {
			final int tStart = org2new.transform(start, Direction.AFTER);
			if (!text.isEmpty()) {
				if (doc != null) {
					doc.replace(tStart, 0, text);
				}
				final OffsetTransformer parent = org2new;
				org2new = new OffsetTransformer() {
					@Override
					public int transform(int org, Direction dir) {
						int tOffset = parent.transform(org, dir);
						if (tOffset < tStart) {
							return tOffset;
						} else if (tOffset > tStart) {
							return tOffset + text.length();
						} else /* tOffset == tStart*/ {
							if (dir == Direction.BEFORE) {
								return tOffset;
							} else {
								return tOffset + text.length();
							}
						}
					}
				};
			}
			if (grabCursor) {
				selection = tStart+text.length();
			} else if (selection > tStart) {
				selection += text.length();
			}
		}

		public void delete(boolean grabCursor, final int start, final int end) throws BadLocationException {
			final int tStart = org2new.transform(start, Direction.AFTER);
			if (end > start) { // skip work for 'delete nothing' op
				final int tEnd = org2new.transform(end, Direction.AFTER);
				if (tEnd > tStart) { // skip work for 'delete nothing' op
					if (doc != null) {
						doc.replace(tStart, tEnd-tStart, "");
					}

					final OffsetTransformer parent = org2new;
					org2new = new OffsetTransformer() {
						@Override
						public int transform(int org, Direction dir) {
							int tOffset = parent.transform(org, dir);
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
			if (grabCursor) {
				selection = tStart;
			} else if (selection > tStart) {
				int len = end - start;
				if (len > 0) {
					selection = Math.max(tStart, selection-len);
				}
			}
		}

		@Override
		public String toString() {
			if (doc == null) {
				return super.toString();
			}
			StringBuilder buf = new StringBuilder();
			buf.append("DocumentState(\n");
			if (selection >= 0) {
				//show cursor location for ease in debugging
				buf.append(doc.get().substring(0, selection));
				buf.append("<*>");
				buf.append(doc.get().substring(selection));
			} else {
				buf.append(doc.get()+"\n");
			}
			buf.append(")\n");
			return buf.toString();
		}
	}

	private List<Edit> edits = new ArrayList<Edit>();
	private IDocument doc;
	private boolean hasSnippets;

	/**
	 * When this is true, the cursor is moved after each edit, to be positioned right after the
	 * edit.
	 * <p>
	 * When it is false, the cursor only moves to remain in place relative to the surrounding text.
	 * (I.e. if text is inserted/deleted before the cursor its shifted by the length of the inserted/deleted text).
	 */
	private boolean grabCursor = true;

	public DocumentEdits(IDocument doc, boolean hasSnippets) {
		this.doc = doc;
		this.hasSnippets = hasSnippets;
	}
	
	public void delete(int start, int end) {
		Assert.isLegal(start <= end);
		edits.add(new Deletion(grabCursor, start, end));
	}

	public void delete(int offset, String text) {
		delete(offset, offset+text.length());
	}

	public void insertSnippet(int offset, String snippet) {
		//The way we track/handle snippet usage is not totally correct.
		//There is a bug here that if we compose multiple edits, some of which
		//use snippet placeholders and others which don't, all will be considered
		//as using snippets. This may pose problems if somehow literal text that
		//looks like a placeholder is combined with real snippet. Then all will
		//be treated as a snippet.
		hasSnippets |= true; 
		edits.add(new Insertion(grabCursor, offset, snippet.toString()));
	}

	public void insert(int offset, String insert) {
		edits.add(new Insertion(grabCursor, offset, insert));
	}
	
	public void lazyInsert(int offset, String insert, Supplier<String> resolveText) {
		edits.add(new Insertion(grabCursor, offset, insert, resolveText));
	}
	
	@Override
	public IRegion getSelection() throws Exception {
		DocumentState selectionState = new DocumentState(null);
		for (Edit edit : edits) {
			edit.apply(selectionState);
		}
		if (selectionState.selection >= 0) {
			return new Region(selectionState.selection, 0);
		}
		return null;
	}

	public TextReplace asReplacement(TextDocument doc) throws BadLocationException {
		if (!edits.isEmpty()) {
			int start = edits.stream().mapToInt(Edit::getStart).min().getAsInt();
			int end = edits.stream().mapToInt(Edit::getEnd).max().getAsInt();

			DocumentState state = new DocumentState(doc.copy());
			for (Edit edit : edits) {
				edit.apply(state);
			}
			int newStart = state.org2new.transform(start, Direction.BEFORE);
			int newEnd = state.org2new.transform(end, Direction.AFTER);
			return new TextReplace(start, end, state.doc.textBetween(newStart, newEnd));
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

	/**
	 * Adds extra indentation at the position of the first edit in this {@link DocumentEdits}
	 */
	public void indentFirstEdit(String indentString) {
		if (edits.size() > 0) {
			Edit firstEdit = edits.get(0);
			int offset = firstEdit.getStart();
			edits.add(0, new Insertion(grabCursor, offset, indentString));
		}
	}

	public void firstDelete(int start, int end) {
		edits.add(0, new Deletion(grabCursor, start, end));
	}

	/**
	 * Find first non-whitepace insertion edit and transform its contents.
	 * @param transformFun receives the insertion text of the target edit and the offset of the first non-whitespace character
	 * 						as arguments. It should return a replacement text.
	 */
	public void transformFirstNonWhitespaceEdit(BiFunction<Integer, String, String> transformFun) {
		for (Edit edit : edits) {
			if (edit instanceof Insertion) {
				Insertion insert = (Insertion) edit;
				Matcher matcher = NON_WS_CHAR.matcher(insert.text);
				if (matcher.find()) {
					insert.text = transformFun.apply(matcher.start(), insert.text);
					if (!insert.isResolved()) {
						Supplier<String> originalSupl = insert.resolveInsert;
						insert.resolveInsert = () -> {
							return transformFun.apply(matcher.start(), originalSupl.get());
						};
					}
				}
			}
		}
	}

	public Integer getFirstEditStart() {
		for (Edit edit : edits) {
			return edit.getStart();
		}
		return null;
	}

	/**
	 * Stop moving the cursor to the end of edits for successive edits. The cursor will still be
	 * update to remain 'in place' relative to the surrounding text.
	 */
	public void freezeCursor() {
		this.grabCursor = false;
	}

	public boolean hasRelativeIndents() {
		return true;
	}

	final public boolean hasSnippets() {
		return hasSnippets;
	}

	public void dropPrefix(String prefix) {
		try {
			if (edits.size() == 2 && edits.get(0) instanceof Deletion && edits.get(1) instanceof Insertion) {
				Deletion del = (Deletion) edits.get(0);
				Insertion ins = (Insertion) edits.get(1);
				String replacedText = doc.textBetween(del.start, del.end);
				if (ins.offset >= del.start && ins.offset <= del.end && replacedText.startsWith(prefix)) {
					del.start+=prefix.length();
					ins.text = ins.text.substring(prefix.length());
					if (!ins.isResolved()) {
						Supplier<String> originalSupl = ins.resolveInsert;
						ins.resolveInsert = () -> {
							return originalSupl.get().substring(prefix.length());
						};
					}
				}
			}
		} catch (BadLocationException e) {
			log.error("", e);
		}
	}
	
	public boolean isResolved() {
		for (Edit e : edits) {
			if (!e.isResolved()) {
				return false;
			}
		}
		return true;
	}
	
	public void resolve() {
		for (Edit edit : edits) {
			if (!edit.isResolved()) {
				edit.resolve();
			}
		}
	}
	
}
