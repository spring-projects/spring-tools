/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class RenderablesTest {

	private static Renderable snippet(String content) {
		return Renderables.inlineSnippet(content);
	}

	private static String snippetMD(String content) {
		return snippet(content).toMarkdown();
	}

	@Test
	void inlineSnippetRendersAsHtmlCodeElementWithEntityEscaping() {
		assertThat(snippet("a<b>&c").toHtml()).isEqualTo("<code>a&lt;b&gt;&amp;c</code>");
	}

	@Test
	void inlineSnippetOfNullRendersAsHtmlLiteralNullTooForConsistencyWithMarkdown() {
		assertThat(snippet(null).toHtml()).isEqualTo("<code>null</code>");
	}

	@Test
	void escapeParenthesisForMardownLink() {
		Renderable r = Renderables.link("my-link-with-parenthesis", "https://foo.com/index(1).html");
		StringBuilder sb = new StringBuilder();
		r.renderAsMarkdown(sb);
		assertThat(sb.toString()).isEqualTo("[my-link-with-parenthesis](https://foo.com/index%281%29.html)");
	}

	@Test
	void escapeBracketsAndBackslashForMarkdownLinkText() {
		Renderable r = Renderables.link("weird]name\\with[brackets", "https://foo.com");
		StringBuilder sb = new StringBuilder();
		r.renderAsMarkdown(sb);
		assertThat(sb.toString()).isEqualTo("[weird\\]name\\\\with\\[brackets](https://foo.com)");
	}

	@Test
	void escapeLoneBackslashNotAdjacentToABracketInLinkText() {
		// A single '\' between two plain characters: CommonMark only treats "\" followed by
		// ASCII punctuation as an escape, so a lone backslash must still be doubled or a
		// compliant parser would otherwise just print it literally anyway — doubling it here
		// keeps the escaping logic uniform regardless of what follows the backslash.
		Renderable r = Renderables.link("a\\b", "https://foo.com");
		StringBuilder sb = new StringBuilder();
		r.renderAsMarkdown(sb);
		assertThat(sb.toString()).isEqualTo("[a\\\\b](https://foo.com)");
	}

	@Test
	void escapeIsAppliedUniformlyEvenWhenInputAlreadyContainsABackslashBracketPair() {
		// Guards the escaping ORDER: backslash must be escaped before '[' / ']', otherwise an
		// attacker-supplied "\]" could ride through as what looks like an already-escaped
		// bracket and end up under-escaped. Build inputs/outputs via explicit char appends
		// instead of string literals so the backslash counts can't be miscounted by eye.
		String input = new StringBuilder().append('\\').append(']').toString(); // real chars: \ ]
		Renderable r = Renderables.link(input, "https://foo.com");
		StringBuilder sb = new StringBuilder();
		r.renderAsMarkdown(sb);
		String expectedLabel = new StringBuilder().append('\\').append('\\').append('\\').append(']').toString(); // real chars: \ \ \ ]
		assertThat(sb.toString()).isEqualTo("[" + expectedLabel + "](https://foo.com)");
	}

	@Test
	void escapeAppliesToBalancedBracketsToo() {
		// CommonMark link text may legally contain balanced brackets unescaped, but escaping
		// them anyway is harmless (an escaped bracket renders as the literal character) and
		// avoids having to reason about balance for attacker-controlled content.
		Renderable r = Renderables.link("[nested]", "https://foo.com");
		StringBuilder sb = new StringBuilder();
		r.renderAsMarkdown(sb);
		assertThat(sb.toString()).isEqualTo("[\\[nested\\]](https://foo.com)");
	}

	@Test
	void inlineSnippetWithoutBacktick() {
		assertThat(snippetMD("plain-text")).isEqualTo("`plain-text`");
	}

	@Test
	void inlineSnippetWithEmbeddedSingleBacktick() {
		assertThat(snippetMD("has`backtick")).isEqualTo("``has`backtick``");
	}

	@Test
	void inlineSnippetWithLongerEmbeddedBacktickRun() {
		assertThat(snippetMD("has``double")).isEqualTo("```has``double```");
	}

	@Test
	void inlineSnippetFenceScalesToLongestEmbeddedBacktickRun() {
		// A run of 3 backticks in the content needs a 4-backtick fence, not just "one more
		// than the shortest run" — this pins the fence length to maxRun + 1 in general, not
		// just for the 1- and 2-backtick cases already covered above.
		assertThat(snippetMD("a```b")).isEqualTo("````a```b````");
	}

	@Test
	void inlineSnippetFenceSizedByLongestOfSeveralDistinctRuns() {
		// Runs of length 1, 2, then 3 appear in the content; the fence must be sized off the
		// longest (3), not the first or the last one encountered.
		assertThat(snippetMD("a`b``c```d")).isEqualTo("````a`b``c```d````");
	}

	@Test
	void inlineSnippetOfSingleBacktickCharacter() {
		// The canonical CommonMark example: content that is exactly one backtick needs a
		// 2-backtick fence plus padding, since the content both starts and ends with '`'.
		assertThat(snippetMD("`")).isEqualTo("`` ` ``");
	}

	@Test
	void inlineSnippetOfContentThatIsEntirelyBackticks() {
		assertThat(snippetMD("``")).isEqualTo("``` `` ```");
	}

	@Test
	void inlineSnippetPadsWhenContentStartsOrEndsWithBacktick() {
		assertThat(snippetMD("`leading")).isEqualTo("`` `leading ``");
		assertThat(snippetMD("trailing`")).isEqualTo("`` trailing` ``");
	}

	@Test
	void inlineSnippetPadsBothStartAndEndBacktickBoundary() {
		assertThat(snippetMD("`both`")).isEqualTo("`` `both` ``");
	}

	@Test
	void inlineSnippetOfEmptyString() {
		// An empty code span can't be represented in markdown; omit it entirely
		// rather than rendering literal spaces (as a naive "` `" wrapping would).
		assertThat(snippetMD("")).isEqualTo("");
	}

	@Test
	void inlineSnippetOfNull() {
		assertThat(snippetMD(null)).isEqualTo("`null`");
	}

	@Test
	void inlineSnippetPadsWhenContentStartsAndEndsWithSpaceButIsNotAllSpaces() {
		// Without the extra pad space, CommonMark's own space-stripping rule would
		// turn "` a `" into "a", losing the leading/trailing space from the original content.
		assertThat(snippetMD(" a ")).isEqualTo("`  a  `");
	}

	@Test
	void inlineSnippetPadIsIndependentOfHowManyBoundarySpacesAreAlreadyPresent() {
		// The parser only ever strips ONE space from each side, no matter how many are there,
		// so exactly one pad space is enough to protect a 2-space boundary too.
		assertThat(snippetMD("  a  ")).isEqualTo("`" + "   " + "a" + "   " + "`");
	}

	@Test
	void inlineSnippetDoesNotPadWhenOnlyOneSideHasABoundarySpace() {
		// CommonMark's strip rule requires the content to BOTH start and end with a space;
		// a space on only one side is left completely alone by the parser, so padding it
		// would incorrectly add a space that was never there in the original content.
		assertThat(snippetMD(" abc")).isEqualTo("` abc`");
		assertThat(snippetMD("abc ")).isEqualTo("`abc `");
	}

	@Test
	void inlineSnippetDoesNotPadContentThatIsEntirelySpaces() {
		// CommonMark's space-stripping rule only applies when the content isn't all spaces,
		// so no extra padding is needed to preserve an all-space payload.
		assertThat(snippetMD("   ")).isEqualTo("`   `");
	}

	@Test
	void inlineSnippetOfSingleSpaceIsTreatedAsAllSpaces() {
		assertThat(snippetMD(" ")).isEqualTo("` `");
	}

}
