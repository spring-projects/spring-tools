/*******************************************************************************
 * Copyright (c) 2016, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.languageserver.testharness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness.HIGHLIGHTS_TIMEOUT;
import static org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness.getDocString;
import static org.springframework.ide.vscode.languageserver.testharness.TestAsserts.assertContains;
import static org.springframework.ide.vscode.languageserver.testharness.TestAsserts.assertDoesNotContain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.swing.text.BadLocationException;

import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemTag;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.InsertReplaceEdit;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.ide.vscode.commons.languageserver.semantic.tokens.SemanticTokenData;
import org.springframework.ide.vscode.commons.protocol.HighlightParams;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.Unicodes;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.languageserver.testharness.SemanticTokensAssert.ExpectedSemanticToken;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;

public class Editor {

	public static final Predicate<CompletionItem> RELAXED_COMPLETION
			= c -> c.getLabel().startsWith("- ")
				|| c.getLabel().startsWith(Unicodes.LEFT_ARROW + " ")
				|| c.getLabel().startsWith(Unicodes.RIGHT_ARROW + " ")
				;
	public static final Predicate<CompletionItem> SNIPPET_COMPLETION = c -> c.getLabel().endsWith("Snippet");
	public static final Predicate<CompletionItem> PLAIN_COMPLETION = RELAXED_COMPLETION.negate();
	public static final Predicate<CompletionItem> DEDENTED_COMPLETION = c -> c.getLabel().startsWith(Unicodes.LEFT_ARROW+" ");
	public static final Predicate<CompletionItem> INDENTED_COMPLETION = c -> c.getLabel().startsWith(Unicodes.RIGHT_ARROW+" ");

	static class EditorState {
		String documentContents;
		int selectionStart;
		int selectionEnd;

		public EditorState(String text) {
			selectionStart = text.indexOf(CURSOR);
			if (selectionStart >= 0) {
				text = text.substring(0,selectionStart) + text.substring(selectionStart + CURSOR.length());
				selectionEnd = text.indexOf(CURSOR, selectionStart);
				if (selectionEnd >= 0) {
					text = text.substring(0, selectionEnd) + text.substring(selectionEnd + CURSOR.length());
				} else {
					selectionEnd = selectionStart;
				}
			} else {
				//No CURSOR markers found
				selectionStart = text.length();
				selectionEnd = text.length();
			}
			this.documentContents = text;
		}
	}

	private static final String CURSOR = "<*>"; // used by our test harness
	private static final String VS_CODE_CURSOR_MARKER = "{{}}"; //vscode uses this in edits to mark cursor position
	
	public static String markupEitherToString(Either<String, MarkupContent> content) {
		return content.isLeft() ? content.getLeft() : content.getRight().getValue();
	}

	private static final Comparator<Diagnostic> PROBLEM_COMPARATOR = new Comparator<Diagnostic>() {
		@Override
		public int compare(Diagnostic o1, Diagnostic o2) {
			int diff = compare(o1.getRange().getStart(), o2.getRange().getStart());
			if (diff != 0) return diff;
			diff = compare(o1.getRange().getEnd(), o2.getRange().getEnd());
			if (diff != 0) return diff;
			return markupEitherToString(o1.getMessage()).compareTo(markupEitherToString(o2.getMessage()));
		}

		private int compare(Position p1, Position p2) {
			int d = p1.getLine() - p2.getLine();
			if (d != 0) return d;
			return p1.getCharacter() - p2.getCharacter();
		}
	};
	public static final Comparator<Range> RANGE_COMPARATOR = new Comparator<Range>() {
		@Override
		public int compare(Range o1, Range o2) {
			int diff = compare(o1.getStart(), o2.getStart());
			if (diff != 0) return diff;
			return compare(o1.getEnd(), o2.getEnd());
		}
		private int compare(Position p1, Position p2) {
			int d = p1.getLine() - p2.getLine();
			if (d != 0) return d;
			return p1.getCharacter() - p2.getCharacter();
		}
	};

	private LanguageServerHarness harness;
	private TextDocumentInfo doc;

	private int selectionEnd;

	private int selectionStart;
	private Set<String> ignoredTypes;
	private LanguageId languageId;
	private Future<HighlightParams> highlightsFuture;

	public Editor(LanguageServerHarness harness, String contents, LanguageId languageId) throws Exception {
		this(harness, contents, languageId, null);
	}

	public Editor(LanguageServerHarness harness, String contents, LanguageId languageId, String extension) throws Exception {
		this.harness = harness;
		this.languageId = LanguageId.of(languageId.getId()); // So we can catch bugs that use == for langauge id comparison.
		EditorState state = new EditorState(contents);
		
		String tempUri = harness.createTempUri(extension);
		this.doc = harness.openDocument(harness.createDocFromContentWithResource(state.documentContents, tempUri, this.languageId));

		this.selectionStart = state.selectionStart;
		this.selectionEnd = state.selectionEnd;
		this.ignoredTypes = new HashSet<>();
		this.highlightsFuture = harness.getHighlightsFuture(doc);
	}
	
	public Editor(LanguageServerHarness harness, TextDocumentInfo doc, String contents, LanguageId languageId) throws Exception {
		this.harness = harness;
		this.languageId = LanguageId.of(languageId.getId()); // So we can catch bugs that use == for langauge id comparison.
		EditorState state = new EditorState(contents);

		String tempUri = doc.getUri();
		this.doc = harness.openDocument(harness.createDocFromContentWithResource(state.documentContents, tempUri, languageId));

		this.selectionStart = state.selectionStart;
		this.selectionEnd = state.selectionEnd;
		this.ignoredTypes = new HashSet<>();
		this.highlightsFuture = harness.getHighlightsFuture(doc);
	}

	/**
	 * Check that a 'expectedProblems' are found by the reconciler. Expected problems are
	 * specified by string of the form "${badSnippet}|${messageSnippet}" or
	 * "${badSnippet}^${followSnippet}|${messageSnippet}"
	 * <p>
	 * The badSnippet is the text expected to be covered by the marker's region and the message snippet must
	 * be found in the error marker's message.
	 * <p>
	 * In addition, if followSnippet is specified, the text that comes right after the error marker must match it.
	 * <p>
	 * The expected problems are matched one-to-one in the order given (so markers in the
	 * editor must appear in the expected order for the assert to pass).
	 *
	 * @param editor
	 * @param expectedProblems
	 * @throws BadLocationException
	 */
	public List<Diagnostic> assertProblems(String... expectedProblems) throws Exception {
		Editor editor = this;
		List<Diagnostic> actualProblems = new ArrayList<>(editor.reconcile().stream().filter(d -> {
			return !ignoredTypes.contains(d.getCode().getLeft());
		}).collect(Collectors.toList()));
		Collections.sort(actualProblems, PROBLEM_COMPARATOR);
		String bad = null;
		if (actualProblems.size() != expectedProblems.length) {
			bad = "Wrong number of problems (expecting "+expectedProblems.length+" but found "+actualProblems.size()+")";
		} else {
			for (int i = 0; i < expectedProblems.length; i++) {
				if (!matchProblem(actualProblems.get(i), expectedProblems[i])) {
					bad = "First mismatch at index "+i+": " + expectedProblems[i] + "\n";
					break;
				}
			}
		}
		if (bad!=null) {
			fail(bad + problemSumary(editor, actualProblems));
		}
		return ImmutableList.copyOf(actualProblems);
	}

	private String problemSumary(Editor editor, List<Diagnostic> actualProblems) throws Exception {
		StringBuilder buf = new StringBuilder();
		for (Diagnostic p : actualProblems) {
			buf.append("\n----------------------\n");

			String snippet = editor.getText(p.getRange());
			buf.append("(" + p.getRange().getStart().getLine() + ", " + p.getRange().getStart().getCharacter() + ")[" + snippet + "]:\n");
			buf.append("   " + p.getMessage());
		}
		return buf.toString();
	}


	public List<Range> assertHighlights(String... expectedHighlights) throws Exception {
		HighlightParams highlights = this.highlightsFuture.get(HIGHLIGHTS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		List<Range> ranges = highlights != null ? highlights.getCodeLenses().stream().map(CodeLens::getRange).collect(Collectors.toList()) : ImmutableList.of();
		Collections.sort(ranges, RANGE_COMPARATOR);
		List<String> actualHighlights = ranges.stream()
			.map(this::getText)
			.collect(Collectors.toList());
		assertEquals(ImmutableList.copyOf(expectedHighlights), actualHighlights);
		return ranges;
	}
	
	public void assertDocumentHighlights(String afterString, DocumentHighlight... expected) throws Exception {
		int pos = getRawText().indexOf(afterString);
		if (pos >= 0) {
			pos += afterString.length();
		}
		List<? extends DocumentHighlight> actual = harness.getDocumentHighlights(doc.getId(), doc.toPosition(pos));
		
		if (actual == null) {
			assertTrue(expected == null || expected.length == 0);
		}
		else {
			assertEquals(ImmutableList.copyOf(expected), actual);
		}
	}

	/**
	 * Get the editor text, with cursor markers inserted (for easy textual comparison
	 * after applying a proposal)
	 */
	public String getText() {
		String text = doc.getText();
		text = text.substring(0, selectionEnd) + CURSOR + text.substring(selectionEnd);
		if (selectionStart < selectionEnd) {
			text = text.substring(0,selectionStart) + CURSOR + text.substring(selectionStart);
		}
		return text;
	}

	public void setText(String content) throws Exception {
		EditorState state = new EditorState(content);
		doc = harness.changeDocument(doc.getUri(), state.documentContents);
		this.selectionStart = state.selectionStart;
		this.selectionEnd = state.selectionEnd;
	}

	/**
	 * @return The 'raw' text in the editor, i.e. without the cursor markers.
	 */
	public String getRawText() throws Exception {
		return doc.getText();
	}

	private void replaceText(int start, int end, String newText) {
		doc = harness.changeDocument(doc.getUri(), start, end, newText);
	}

	public void setRawText(String newContent) throws Exception {
		doc = harness.changeDocument(doc.getUri(), newContent);
	}

	public String getText(Range range) {
		return doc.getText(range);
	}

	private boolean matchProblem(Diagnostic problem, String expect) {
		String[] parts = expect.split("\\|");
		assertEquals(2, parts.length);
		String badSnippet = parts[0];
		String snippetBefore;
		String snippetAfter;
		String[] badParts = StringUtil.split(badSnippet, '^');
		assertTrue(badParts.length <= 3);
		if (badParts.length == 1) {
			snippetBefore = "";
			snippetAfter = "";
			badSnippet = badParts[0];
		} else if (badParts.length == 2) {
			snippetBefore = "";
			badSnippet = badParts[0];
			snippetAfter = badParts[1];
		} else { // badParts.length == 3
			snippetBefore = badParts[0];
			badSnippet = badParts[1];
			snippetAfter = badParts[2];
		}
		String messageSnippet = parts[1];
		boolean spaceSensitive = badSnippet.trim().length() < badSnippet.length();
		boolean emptyRange = problem.getRange().getStart().equals(problem.getRange().getEnd());
		String actualBadSnippet = emptyRange
				? getCharAt(problem.getRange().getStart())
				: getText(problem.getRange());
		if (!spaceSensitive) {
			actualBadSnippet = actualBadSnippet.trim();
		}

		int start = doc.toOffset(problem.getRange().getStart());
		int end = doc.toOffset(problem.getRange().getEnd());

		return actualBadSnippet.equals(badSnippet)
				&& snippetBefore.equals(doc.textBetween(start - snippetBefore.length(), start))
				&& snippetAfter.equals(doc.textBetween(end, end+snippetAfter.length()))
				&& markupEitherToString(problem.getMessage()).contains(messageSnippet);
	}

	private String getText(Position start, int length) {
		int offset = doc.toOffset(start);
		String text = doc.getText();
		return text.substring(offset, offset+length);
	}

	private String getCharAt(Position start) {
		String text = doc.getText();
		int offset = doc.toOffset(start);
		return offset < text.length()
			? text.substring(offset, offset + 1)
			: "";
	}

	public List<Diagnostic> reconcile() throws Exception {
		PublishDiagnosticsParams diagnostics = harness.getDiagnostics(doc);
		if (diagnostics != null) {
			return diagnostics.getDiagnostics();
		}
		return Collections.emptyList();
	}

	public List<CompletionItem> assertCompletions(String... expectTextAfter) throws Exception {
		return assertCompletions((item) -> true, expectTextAfter);
	}

	public List<CompletionItem> assertCompletions(Predicate<CompletionItem> filter, String... expectTextAfter) throws Exception {
		StringBuilder expect = new StringBuilder();
		StringBuilder actual = new StringBuilder();
		for (String after : expectTextAfter) {
			expect.append(after);
			expect.append("\n-------------------\n");
		}

		List<CompletionItem> completions = getCompletions();
		for (CompletionItem completion : completions) {
			if (filter.test(completion)) {
				Editor editor = this.clone();
				editor.apply(completion);
				actual.append(editor.getText());
				actual.append("\n-------------------\n");
			}
		}
		assertEquals(expect.toString(), actual.toString());
		return completions;
	}

	public List<CompletionItem> assertCompletionLabels(String... expectedLabels) throws Exception {
		return assertCompletionLabels(c -> true, expectedLabels);
	}

	public List<CompletionItem> assertCompletionLabels(Predicate<CompletionItem> isInteresting, String... expectedLabels) throws Exception {
		StringBuilder expect = new StringBuilder();
		StringBuilder actual = new StringBuilder();
		for (String label : expectedLabels) {
			expect.append(label);
			expect.append("\n");
		}

		List<CompletionItem> completions;
		for (CompletionItem completion : completions = getCompletions()) {
			if (isInteresting.test(completion)) {
				actual.append(completion.getLabel());
				actual.append("\n");
			}
		}
		assertEquals(expect.toString(), actual.toString());
		return completions;
	}

	public void assertContainsCompletions(String... expectTextAfter) throws Exception {
		StringBuilder actual = new StringBuilder();

		for (CompletionItem completion : getCompletions()) {
			Editor editor = this.clone();
			editor.apply(completion);
			actual.append(editor.getText());
			actual.append("\n-------------------\n");
		}
		String actualText = actual.toString();

		for (String after : expectTextAfter) {
			assertContains(after, actualText);
		}
	}

	public void assertNoCompletionsWithLabel(Predicate<String> labelPredicate) throws Exception {
		List<String> found = getCompletions().stream()
			.map(c -> c.getLabel())
			.filter(labelPredicate)
			.collect(Collectors.toList());
		if (!found.isEmpty()) {
			fail("Found but not expected: " + found);
		}
	}

	public void assertDoesNotContainCompletions(String... notToBeFound) throws Exception {
		StringBuilder actual = new StringBuilder();

		for (CompletionItem completion : getCompletions()) {
			Editor editor = this.clone();
			editor.apply(completion);
			actual.append(editor.getText());
			actual.append("\n-------------------\n");
		}
		String actualText = actual.toString();

		for (String after : notToBeFound) {
			assertDoesNotContain(after, actualText);
		}
	}

	public void assertContextualCompletions(Predicate<CompletionItem> isInteresting, String textBefore, String... textAfter) throws Exception {
		LanguageId language = this.getLanguageId();
		Editor editor = harness.newEditor(language, this.getText());
		editor.reconcile(); //this ensures the conText is parsed and its AST is cached (will be used for
		                    //dynamic CA when the conText + textBefore is not parsable.

		textBefore = replaceSelection(textBefore);
		textAfter = Arrays.stream(textAfter)
				.map((String t) -> replaceSelection(t))
				.toArray(String[]::new);

		editor.setText(textBefore);
		editor.assertCompletions(isInteresting, textAfter);
	}

	public void assertContextualCompletions(String textBefore, String... textAfter) throws Exception {
		assertContextualCompletions((x) -> true, textBefore, textAfter);
	}

	private String replaceSelection(String replacement) {
		try {
			String text = getRawText();
			return text.substring(0, selectionStart) + replacement + text.substring(selectionEnd);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void apply(CompletionItem completion) throws Exception {
		completion = harness.resolveCompletionItem(completion);
		Either<TextEdit,InsertReplaceEdit> edit = completion.getTextEdit();
		
		TextDocument original = new TextDocument(doc.getUri(), doc.getLanguageId());
		original.setText(doc.getText());
		
		int insertPosition = 0;
		int insertLength = 0;
		
		if (edit != null) {
			if (edit.isLeft()) {
				String replaceWith = edit.getLeft().getNewText();
				int cursorReplaceOffset = 0;

				// Replace the cursor string
				cursorReplaceOffset = replaceWith.indexOf(VS_CODE_CURSOR_MARKER);
				if (cursorReplaceOffset >= 0) {
					replaceWith = replaceWith.substring(0, cursorReplaceOffset)
							+ replaceWith.substring(cursorReplaceOffset + VS_CODE_CURSOR_MARKER.length());
				} else {
					cursorReplaceOffset = replaceWith.length();
				}

				Range rng = edit.getLeft().getRange();
				
				int start = original.toOffset(rng.getStart());
				int end = original.toOffset(rng.getEnd());
				replaceText(start, end, replaceWith);
				
				insertPosition = start;
				insertLength = replaceWith.length() - (end - start);
				
				selectionStart = selectionEnd = start+cursorReplaceOffset;
			} else {
				throw new UnsupportedOperationException("InsertReplaceEdit edits not supported");
			}
		} else {
			String insertText = getInsertText(completion);
			String newText = original.get().substring(0, selectionStart) + insertText + original.get().substring(selectionStart);
			
			insertPosition = selectionStart;
			insertLength = insertText.length();

			selectionStart += insertText.length();
			selectionEnd += insertText.length();
			setRawText(newText);
		}
		
		// Experimental additional edit support. Only makes good sense when it is above the text being edited
		if (completion.getAdditionalTextEdits() != null) {
			for (TextEdit te : completion.getAdditionalTextEdits()) {
				String replaceWith = te.getNewText();
				Range rng = te.getRange();
				int start = original.toOffset(rng.getStart());
				int end = original.toOffset(rng.getEnd());
				
				// adjust start/end, if the completion insert text was positioned before the additional text edits
				if (start > insertPosition) {
					start += insertLength;
					end += insertLength;
				}
				else {
					selectionStart = selectionEnd = selectionStart - (end - start) + replaceWith.length();
				}
				
				replaceText(start, end, replaceWith);
			}
		}
		
		Position beforeCmdCursorPosition = getCursor();
		String beforeCmd = doc.getText();
		
		// Apply command
		if (completion.getCommand() != null) {
			harness.executeCommand(completion.getCommand());
			if (beforeCmd.length() != getRawText().length()) {
				TextDocument beforeDoc = new TextDocument(doc.getUri(), doc.getLanguageId());
				beforeDoc.setText(beforeCmd);
				int beforeOffset = beforeDoc.toOffset(beforeCmdCursorPosition);
				TextDocument changedDoc = new TextDocument(doc.getUri(), doc.getLanguageId());
				changedDoc.setText(doc.getText());
				int newOffset = beforeOffset + (getRawText().length() - beforeCmd.length());
				setCursor(changedDoc.toPosition(newOffset));
			}
		}
		
	}

	private String getInsertText(CompletionItem completion) {
		String s = completion.getInsertText();
		if (s == null) {
			//If no insertText is provided the label is used
			s = completion.getLabel();
		}
		return s;
	}

	@Override
	public Editor clone() {
		try {
			return new Editor(harness, getText(), getLanguageId());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public List<CompletionItem> getCompletions() throws Exception {
		CompletionList cl = harness.getCompletions(this.doc, this.getCursor());
		ArrayList<CompletionItem> items = new ArrayList<>(cl.getItems());
		return items;
	}
	
	public List<? extends Location> getReferences() throws Exception {
		List<? extends Location> references = harness.getReferences(this.doc, this.getCursor());
		return references;
	}

	public CompletionItem getFirstCompletion() throws Exception {
		return getCompletions().get(0);
	}

	private Position getCursor() {
		return doc.toPosition(selectionStart);
	}

	public void assertIsHoverRegion(String string) throws Exception {
		int hoverPosition = getHoverPosition(string, 1);
		Hover hover = harness.getHover(doc, doc.toPosition(hoverPosition));
		assertEquals(string, getText(hover.getRange()));
	}

	public void assertHoverContains(String hoverOver, int occurrence, String snippet) throws Exception {
		int hoverPosition = getHoverPosition(hoverOver, occurrence);
		Hover hover = harness.getHover(doc, doc.toPosition(hoverPosition));
		assertContains(snippet, hoverString(hover));
	}
	
	public void assertCodeLens(String over, int occurrence, String codeLensText) throws Exception {
		int offset = getHoverPosition(over, occurrence);
		List<? extends CodeLens> matchingCodeLenses = harness.getCodeLenses(doc).stream()
				.filter(cl -> doc.toOffset(cl.getRange().getStart()) <= offset
						&& doc.toOffset(cl.getRange().getEnd()) >= offset)
				.collect(Collectors.toList());
		if (matchingCodeLenses.isEmpty()) {
			if (codeLensText == null) {
				// Success! No code lenses expected at this spot
				return;
			} 
			fail("No CodeLenses over the string `%s`".formatted(over));
		}
		if (!matchingCodeLenses.stream().anyMatch(cl -> cl.getCommand().getTitle().contains(codeLensText))) {
			fail("None of the found CodeLenses: [ %s ] contained '%s'".formatted(matchingCodeLenses.stream()
					.map(cl -> "'%s'".formatted(cl.getCommand().getTitle())).collect(Collectors.joining(", ")), codeLensText));
		}
	}
	
	public List<CodeLens> getCodeLenses(String over, int occurrence) throws Exception {
		int offset = getHoverPosition(over, occurrence);
		return harness.getCodeLenses(doc).stream()
				.filter(cl -> doc.toOffset(cl.getRange().getStart()) <= offset
						&& doc.toOffset(cl.getRange().getEnd()) >= offset)
				.collect(Collectors.toList());
	}

	public void assertLiveCodeLensContains(String codeLensOver, int occurrence, String snippet) throws Exception {
		int cmPosition = getHoverPosition(codeLensOver, occurrence);
		for (CodeLens cm : getLiveDataCodeLenses()) {
			if (cmPosition >= doc.toOffset(cm.getRange().getStart()) && cmPosition <= doc.toOffset(cm.getRange().getEnd())) {
				if (cm.getData() instanceof String) {
					if (((String) cm.getData()).contains(snippet)) {
						return;
					}
				} else {
					fail("Live Data CodeLens data field is not a string");
				}
			}
		}
		fail("Cannot find '" + snippet + "' in Live Data Code Lenses");
	}
	
	public void assertLiveCodeLensContains(String codeLensOver, String snippet) throws Exception {
		assertLiveCodeLensContains(codeLensOver, 1, snippet);
	}
	
	public String hoverString(Hover hover) {
		StringBuilder buf = new StringBuilder();
		boolean first = true;
		Either<List<Either<String, MarkedString>>, MarkupContent> contents = hover.getContents();
		if (contents.isLeft()) {
			for (Either<String, MarkedString> block : contents.getLeft()) {
				if (!first) {
					buf.append("\n\n");
				}
				if (block.isLeft()) {
					String s = block.getLeft();
					buf.append(s);
				} else if (block.isRight()) {
					MarkedString ms = block.getRight();
					buf.append("```" + ms.getLanguage() + "\n");
					buf.append(ms.getValue());
					buf.append("\n```");
				}
				first = false;
			}
		} else {
			buf.append(contents.getRight().getValue());
		}
		return buf.toString();
	}

	private int getHoverPosition(String hoverOver, int occurrence) throws Exception {
		assertTrue(occurrence > 0);
		return occurrences(getRawText(), hoverOver)
				.elementAt(occurrence-1)
				.map(offset -> offset + hoverOver.length() / 2)
				.block();
	}

	private Flux<Integer> occurrences(String text, String substring) {
		return Flux.fromIterable(() -> new Iterator<Integer>() {
			int searchFrom = 0;
			@Override
			public boolean hasNext() {
				return searchFrom>=0 && searchFrom < text.length() && text.indexOf(substring, searchFrom) >= 0;
			}

			@Override
			public Integer next() {
				int found = text.indexOf(substring, searchFrom);
				assertTrue(found >= 0);
				searchFrom = found+1;
				return found;
			}
		});
	}

	public void assertHoverContains(String hoverOver, String snippet) throws Exception {
		int hoverPosition = getHoverPosition(hoverOver,1);
		Hover hover = harness.getHover(doc, doc.toPosition(hoverPosition));
		assertContains(snippet, hoverString(hover));
	}

	public void assertTrimmedHover(String hoverOver, String expectedHover) throws Exception {
		assertTrimmedHover(hoverOver, 1, expectedHover);
	}

	public void assertTrimmedHover(String hoverOver, int occurence, String expectedHover) throws Exception {
		int hoverPosition = getHoverPosition(hoverOver,occurence);
		Hover hover = harness.getHover(doc, doc.toPosition(hoverPosition));
		assertEquals(expectedHover.trim(), hoverString(hover).trim());
	}
	
	public List<? extends CodeLens> getCodeLenses() throws Exception {
		return harness.getCodeLenses(doc);
	}
	
	public List<? extends CodeLens> getLiveDataCodeLenses() throws Exception {
		HighlightParams highlights = this.highlightsFuture.get(HIGHLIGHTS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		return highlights != null ? highlights.getCodeLenses() : ImmutableList.of();
	}


	public void assertNoHover(String hoverOver, int occurence) throws Exception {
		int hoverPosition = getHoverPosition(hoverOver,occurence);
		Hover hover = harness.getHover(doc, doc.toPosition(hoverPosition));
		List<Either<String, MarkedString>> contents = hover.getContents().getLeft();
		assertTrue(contents.isEmpty(), contents.toString());
	}

	public void assertNoHover(String hoverOver) throws Exception {
		assertNoHover(hoverOver, 1);
	}

	/**
	 * Verifies an expected textSnippet is contained in the hover text that is
	 * computed when hovering mouse at position at the end of first occurrence of
	 * a given string in the editor.
	 */
	public void assertHoverText(String afterString, String expectSnippet) throws Exception {
		int pos = getRawText().indexOf(afterString);
		if (pos >= 0) {
			pos += afterString.length();
		}
		Hover hover = harness.getHover(doc, doc.toPosition(pos));
		assertContains(expectSnippet, hoverString(hover));
	}

	/**
	 * Verifies an expected text is the hover text that is computed when
	 * hovering mouse at position at the end of first occurrence of a given
	 * string in the editor.
	 */
	public void assertHoverExactText(String afterString, String expectedHover) throws Exception {
		int pos = getRawText().indexOf(afterString);
		if (pos >= 0) {
			pos += afterString.length();
		}
		Hover hover = harness.getHover(doc, doc.toPosition(pos));
		assertEquals(expectedHover, hoverString(hover));
	}

	public Hover getHover(String afterString) throws Exception {
		int pos = getRawText().indexOf(afterString);
		if (pos >= 0) {
			pos += afterString.length();
		}
		return harness.getHover(doc, doc.toPosition(pos));
	}

	public CompletionItem assertCompletionDetails(String expectLabel, String expectDetail, String expectDocSnippet) throws Exception {
		CompletionItem it = harness.resolveCompletionItem(assertCompletionWithLabel(expectLabel));
		if (expectDetail != null) {
			assertEquals(expectDetail, it.getDetail());
		}
		if (expectDocSnippet != null) {
			assertContains(expectDocSnippet, getDocString(it));
		}
		return it;
	}

	public CompletionItem assertCompletionDetailsWithDeprecation(String expectLabel, String expectDetail, String expectDocSnippet, boolean deprecated) throws Exception {
		CompletionItem it = harness.resolveCompletionItem(assertCompletionWithLabel(expectLabel));
		if (expectDetail != null) {
			assertEquals(expectDetail, it.getDetail());
		}
		if (expectDocSnippet != null) {
			assertContains(expectDocSnippet, getDocString(it));
		}
		@SuppressWarnings("deprecation")
		boolean actualDeprecated = Boolean.TRUE.equals(it.getDeprecated()) || (it.getTags() != null && it.getTags().stream().anyMatch(t -> t.equals(CompletionItemTag.Deprecated))); 
		assertEquals(deprecated, actualDeprecated);
		return it;
	}

	protected CompletionItem assertCompletionWithLabel(Predicate<String> expectLabel) throws Exception {
		List<CompletionItem> completions = getCompletions();
		Optional<CompletionItem> completion = completions.stream()
				.filter((item) -> expectLabel.test(item.getLabel()))
				.findFirst();
		if (completion.isPresent()) {
			return completion.get();
		}
		fail("Not found in " + completions.stream().map(c -> c.getLabel()).collect(Collectors.toList()));
		return null; //unreachable but compiler doesn't know.
	}

	protected CompletionItem assertCompletionWithLabel(String expectLabel) throws Exception {
		List<CompletionItem> completions = getCompletions();
		Optional<CompletionItem> completion = completions.stream()
				.filter((item) -> item.getLabel().equals(expectLabel))
				.findFirst();
		if (completion.isPresent()) {
			return completion.get();
		}
		fail("Not found '" + expectLabel + "' in " + completions.stream().map(c -> c.getLabel()).collect(Collectors.toList()));
		return null; //unreachable but compiler doesn't know.
	}

	public void assertCompletionWithLabel(String expectLabel, String expectedResult) throws Exception {
		CompletionItem completion = assertCompletionWithLabel(expectLabel);
		String saveText = getText();
		apply(completion);
		assertEquals(expectedResult, getText());
		setText(saveText);
	}

	public CompletionItem assertCompletionWithLabel(Predicate<String> expectLabel, String expectedResult) throws Exception {
		CompletionItem completion = assertCompletionWithLabel(expectLabel);
		String saveText = getText();
		apply(completion);
		assertEquals(expectedResult, getText());
		setText(saveText);
		return completion;
	}

	public void setSelection(int start, int end) {
		assertTrue(start >= 0);
		assertTrue(end >= start);
		assertTrue(end <= doc.getText().length());
		this.selectionStart = start;
		this.selectionEnd = end;
	}

	@Override
	public String toString() {
		return "Editor(\n" + getText() + "\n)";
	}

	public void assertDefinitionLinkTargets(String hoverOver, Collection<LocationLink> expectedLocations) throws Exception {
		int pos = getRawText().indexOf(hoverOver);
		if (pos >= 0) {
			pos += hoverOver.length() / 2;
		}
		assertTrue(pos>=0, "Not found in editor: '"+hoverOver+"'");
		
		assertDefinitionLinkTargets(doc.toPosition(pos), expectedLocations);
	}
	
	public void assertDefinitionLinkTargets(Position pos, Collection<LocationLink> expectedLocations) throws Exception {
		DefinitionParams params = new DefinitionParams(new TextDocumentIdentifier(getUri()), pos);
		List<? extends LocationLink> definitions = harness.getDefinitions(params);
		assertEquals(ImmutableSet.copyOf(expectedLocations), ImmutableSet.copyOf(definitions));
	}
	
	public void assertImplementationLinkTargets(String hoverOver, Collection<LocationLink> expectedLocations) throws Exception {
		int pos = getRawText().indexOf(hoverOver);
		if (pos >= 0) {
			pos += hoverOver.length() / 2;
		}
		assertTrue(pos>=0, "Not found in editor: '"+hoverOver+"'");
		
		assertImplementationLinkTargets(doc.toPosition(pos), expectedLocations);
	}

	public void assertImplementationLinkTargets(Position pos, Collection<LocationLink> expectedLocations) throws Exception {
		ImplementationParams params = new ImplementationParams(new TextDocumentIdentifier(getUri()), pos);
		List<? extends LocationLink> definitions = harness.getImplemetations(params);
		assertEquals(ImmutableSet.copyOf(expectedLocations), ImmutableSet.copyOf(definitions));
	}
	
	public void assertNoDefinitionLinkTargets(String hoverOver) throws Exception {
		int pos = getRawText().indexOf(hoverOver);
		if (pos >= 0) {
			pos += hoverOver.length() / 2;
		}
		assertTrue(pos >= 0, "Not found in editor: '" + hoverOver + "'");

		DefinitionParams params = new DefinitionParams(new TextDocumentIdentifier(getUri()), doc.toPosition(pos));
		List<? extends LocationLink> definitions = harness.getDefinitions(params);

		assertTrue(definitions == null || definitions.isEmpty());
	}

	@Deprecated
	public void assertLinkTargets(String hoverOver, String... expecteds) {
		throw new UnsupportedOperationException("Not implemented yet!");
//		Editor editor = this;
//		int pos = getRawText().indexOf(hoverOver);
//		if (pos>=0) {
//			pos += hoverOver.length();
//		}
//		return harness.getHover(doc, doc.toPosition(pos));
//		assertTrue("Not found in editor: '"+hoverOver+"'", pos>=0);
//
//		List<IJavaElement> targets = getLinkTargets(editor, pos);
//		assertEquals(expecteds.length, targets.size());
//		for (int i = 0; i < expecteds.length; i++) {
//			assertEquals(expecteds[i], JavaElementLabels.getElementLabel(targets.get(i), JavaElementLabels.DEFAULT_QUALIFIED | JavaElementLabels.M_PARAMETER_TYPES));
//		}
	}

	/**
	 * Get a problem that covers the given text in the editor. Throws exception
	 * if no matching problem is found.
	 */
	public Diagnostic assertProblem(String coveredText) throws Exception {
		Editor editor = this;
		List<Diagnostic> problems = editor.reconcile();
		for (Diagnostic p : problems) {
			String c = editor.getText(p.getRange());
			if (c.equals(coveredText)) {
				return p;
			}
		}
		fail("No problem found covering the text '" + coveredText + "' in: \n"
				+ problemSumary(editor, problems)
		);
		return null; //unreachable but compiler doesn't know
	}

	public CodeAction assertFirstQuickfix(Diagnostic problem, String expectLabel) throws Exception {
		CodeAction ca = assertCodeAction(problem);
		assertEquals(expectLabel, ca.getLabel());
		return ca;
	}
	
	public List<CodeAction> assertQuickfixes(Diagnostic problem, String... expectedLabels) throws Exception {
		List<CodeAction> actions = getCodeActions(problem);
		StringBuilder expecteds = new StringBuilder();
		for (String l : expectedLabels) {
			expecteds.append(l + "\n");
		}
		StringBuilder actuals = new StringBuilder();
		for (CodeAction a : actions) {
			actuals.append(a.getLabel() + "\n");
		}
		assertEquals(expecteds.toString(), actuals.toString());
		return actions;
	}

	public void assertText(String expected) {
		assertEquals(expected, getText());
	}

	public void ignoreProblem(Object type) {
		ignoredTypes.add(type.toString());
	}

	public void assertGotoDefinition(Position pos, Range expectedTarget, Range highlightRange) throws Exception {
		TextDocumentIdentifier textDocumentId = doc.getId();
		DefinitionParams params = new DefinitionParams(textDocumentId, pos);
		List<? extends LocationLink> defs = harness.getDefinitions(params);
		assertEquals(1, defs.size());
		assertEquals(new LocationLink(textDocumentId.getUri(), expectedTarget, expectedTarget, highlightRange), defs.get(0));
	}

	/**
	 * Determines the position of a snippet of text in the document.
	 *
	 * @param contextSnippet A larger snippet containing the actual snippet to look for.
	 *                   This larger snippet is used to narrow the section of the document
	 *                   where we look for the actual snippet. This is useful when the snippet
	 *                   occurs multiple times in the document.
	 * @param focusSnippet The snippet to look for
	 */
	public Position positionOf(String longSnippet, String focusSnippet) throws Exception {
		Range r = rangeOf(longSnippet, focusSnippet);
		return r == null ? null : r.getStart();
	}

	public Position positionOf(String snippet) throws Exception {
		return positionOf(snippet, snippet);
	}

	public Range rangeOf(String focusSnippet) throws Exception {
		return rangeOf(focusSnippet, focusSnippet);
	}

	/**
	 * Determines the range of a snippet of text in the document.
	 *
	 * @param contextSnippet A larger snippet containing the actual snippet to look for.
	 *                   This larger snippet is used to narrow the section of the document
	 *                   where we look for the actual snippet. This is useful when the snippet
	 *                   occurs multiple times in the document.
	 * @param focusSnippet The snippet to look for
	 */
	public Range rangeOf(String longSnippet, String focusSnippet) throws Exception {
		int relativeOffset = longSnippet.indexOf(focusSnippet);
		int contextStart = getRawText().indexOf(longSnippet);
		assertTrue(contextStart >= 0, "'" + longSnippet + "' not found in editor");
		int start = contextStart + relativeOffset;
		return new Range(doc.toPosition(start), doc.toPosition(start+focusSnippet.length()));
	}

	public LanguageId getLanguageId() {
		return languageId;
	}

	public List<CodeAction> getCodeActions(Diagnostic problem) throws Exception {
		return harness.getCodeActions(doc, problem);
	}
	
	public List<CodeAction> getCodeActions(String overStr, int occurrence) throws Exception {
		assertTrue(occurrence > 0);
		int offset = occurrences(getRawText(), overStr)
				.elementAt(occurrence - 1)
				.map(o -> o + overStr.length() / 2)
				.block();
		Position position = doc.toPosition(offset);
		return harness.getCodeActions(doc, new Range(position, position));
	}

	public CodeAction assertCodeAction(Diagnostic problem) throws Exception {
		List<CodeAction> actions = getCodeActions(problem);
		assertEquals(1, actions.size(), "Number of codeActions");
		return actions.get(0);
	}

	public void assertNoCodeAction(Diagnostic problem) throws Exception {
		List<CodeAction> actions = getCodeActions(problem);
		if (actions != null && !actions.isEmpty()) {
			StringBuilder found = new StringBuilder();
			for (CodeAction codeAction : actions) {
				found.append("\n" + codeAction.getLabel());
			}
			fail("Expected no code actions but found:"+found);
		}
	}


	public String getUri() {
		return doc.getUri();
	}

	public void assertRawText(String expectedText) throws Exception {
		assertEquals(expectedText, getRawText());
	}

	public void assertDocumentSymbols(String... symbols) throws Exception {
		Arrays.sort(symbols);

		StringBuilder expected = new StringBuilder();
		for (String string : symbols) {
			expected.append(string + "\n");
		}

		List<? extends DocumentSymbol> actualSymbols = getDocumentSymbols();

		List<String> actuals = new ArrayList<>();
		for (DocumentSymbol actualSymbol : actualSymbols) {
			String coveredText = getText(actualSymbol.getRange());
			assertEquals(actualSymbol.getName(), coveredText);
			actuals.add(coveredText);
		}

		Collections.sort(actuals);
		StringBuilder actual = new StringBuilder();
		for (String string : actuals) {
			actual.append(string + "\n");
		}

		assertEquals(expected.toString(), actual.toString());
	}
	
	public void assertSemanticTokensFull(ExpectedSemanticToken... expectedTokens) throws Exception {
		List<SemanticTokenData> actualTokens = harness.getSemanticTokensFull(doc);
		SemanticTokensAssert.assertTokens(getRawText(), actualTokens, expectedTokens);
	}

	public void assertHierarchicalDocumentSymbols(String expectedSymbolDump) throws Exception {
		StringBuilder symbolDump = new StringBuilder();
		List<? extends DocumentSymbol> rootSymbols = getDocumentSymbols();
		dumpSymbols(rootSymbols, 0, symbolDump);
		assertEquals(expectedSymbolDump, symbolDump.toString());
	}

	private void dumpSymbols(List<? extends DocumentSymbol> syms, int indent, StringBuilder symbolDump) {
		for (DocumentSymbol s : syms) {
			dumpSymbol(s, indent, symbolDump);
		}
	}

	private void dumpSymbol(DocumentSymbol s, int indent, StringBuilder symbolDump) {
		for (int i = 0; i < indent; i++) {
			symbolDump.append("  ");
		}
		symbolDump.append(s.getName());
		symbolDump.append("::");
		symbolDump.append(s.getDetail());
		symbolDump.append("\n");
		assertEquals(s.getName(), getText(s.getSelectionRange()));
		
		assertNestedRange("Invalid selection range for " + s.getName(), s.getRange(), s.getSelectionRange());
		List<DocumentSymbol> children = s.getChildren();
		if (children != null) {
			dumpSymbols(children, indent+1, symbolDump);
		}
	}

	private void assertNestedRange(String msg, Range outerRange, Range innerRange) {
		boolean startOk = compare(outerRange.getStart(), innerRange.getStart()) <= 0;
		boolean endOk = compare(outerRange.getEnd(), innerRange.getEnd()) >= 0;
		if (startOk && endOk) {
			//it's fine!
		} else {
			fail(msg + "\n" +
				"outer: '" + this.getText(outerRange)+"'\n" +
				"does not contain\n" +
				"inner: '" + this.getText(innerRange) + "'"
			);
		}
	}

	private static int compare(Position p1, Position p2) {
		if (p1.getLine() == p2.getLine()) {
			return p1.getCharacter() - p2.getCharacter();
		} else {
			return p1.getLine() - p2.getLine();
		}
	}

	private List<? extends DocumentSymbol> getDocumentSymbols() throws Exception {
		return harness.getDocumentSymbols(this.doc);
	}

	public void setCursor(Position position) {
		this.selectionStart = this.selectionEnd = doc.toOffset(position);
	}


}
