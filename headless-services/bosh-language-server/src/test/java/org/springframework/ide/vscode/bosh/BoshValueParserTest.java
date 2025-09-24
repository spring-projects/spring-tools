/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.bosh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.commons.util.ValueParseException;
import org.springframework.ide.vscode.commons.util.ValueParser;

public class BoshValueParserTest {

	private static final String MARKER = "<*>";

    @Test
    void integerOrRangeOkay() throws Exception {
        BoshValueParsers.INTEGER_OR_RANGE.parse("123");
        BoshValueParsers.INTEGER_OR_RANGE.parse("123-456");
    }

    @Test
    void integerOrRangeGarbage() throws Exception {
        assertProblem(BoshValueParsers.INTEGER_OR_RANGE, "<*>garbage<*>", "Should be either a Integer, or a range (of the form '<integer>-<integer>')");
        assertProblem(BoshValueParsers.INTEGER_OR_RANGE, "<*>123--456<*>", "Should be either a Integer, or a range (of the form '<integer>-<integer>')");
        assertProblem(BoshValueParsers.INTEGER_OR_RANGE, "<*>garbage<*>-123", "Should be a Integer");
        assertProblem(BoshValueParsers.INTEGER_OR_RANGE, "123-<*>garbage<*>", "Should be a Integer");
        assertProblem(BoshValueParsers.INTEGER_OR_RANGE, "<*>123-122<*>", "123 should be smaller than 122");
    }

    @Test
    void urlOkay() throws Exception {
        ValueParser urlParser = BoshValueParsers.url("http", "https", "file");
        urlParser.parse("https://foobar.com/munhings.tar.gz");
        urlParser.parse("https://foobar.com/munhings.tar.gz");
        urlParser.parse("hTTp://foobar.com/munhings.tar.gz");
        urlParser.parse("HTTPS://foobar.com/munhings.tar.gz");
        urlParser.parse("file://local/file");
        urlParser.parse("file:///local/file");
        urlParser.parse("FILE:///local/file");
    }

    @Test
    void urlGarbage() throws Exception {
        ValueParser urlParser = BoshValueParsers.url("http", "https", "file");
        assertProblem(urlParser, "<*>woot<*>://foobar.com", "Url scheme must be one of [http, https, file]");
        assertProblem(urlParser, "<*>wOOt<*>://foobar.com", "Url scheme must be one of [http, https, file]");
    }

	private void assertProblem(ValueParser parser, String input, String expectedMessage) throws Exception {
		String unmarkedInput = input.replace(MARKER, "");
		int firstMarker = input.indexOf(MARKER);
		assertTrue(firstMarker>=0);
		int secondMarker = input.indexOf(MARKER, firstMarker+1)-MARKER.length();
		assertTrue(secondMarker>=firstMarker);
		try {
			parser.parse(unmarkedInput);
		} catch (ValueParseException e) {
			int startIndex = startIndex(unmarkedInput, e);
			int endIndex = endIndex(unmarkedInput, e);
			String markedInput = 
					unmarkedInput.substring(0, startIndex) + 
					MARKER +
					unmarkedInput.substring(startIndex, endIndex) +
					MARKER +
					unmarkedInput.substring(endIndex);
			assertEquals(input, markedInput);
			assertEquals(expectedMessage, e.getMessage());
		}
	}

	private int endIndex(String unmarkedInput, ValueParseException e) {
		int i = e.getEndIndex();
		if (i>=0) {
			return i;
		} else {
			return unmarkedInput.length();
		}
	}

	private int startIndex(String unarkedInput, ValueParseException e) {
		int i = e.getStartIndex();
		if (i>=0) {
			return i;
		} else {
			return 0;
		}
	}

}
