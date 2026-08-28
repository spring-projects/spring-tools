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
package org.springframework.ide.vscode.commons.util;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.commons.util.FuzzyMap.Match;

public class FuzzyMapTest {

	@Test
	public void testMatches() {
		assertMatch(true, "", "");
		assertMatch(true, "", "abc");
		assertMatch(true, "server.port", "server.port");
		assertMatch(true, "port", "server.port");
		assertMatch(true, "sport", "server.port");
		assertMatch(false, "spox", "server.port");
	}

	@Test
	public void testOrder() {
		assertMatchOrder("port",
				"port",
				"server.port",
				"server.port-mapping",
				"piano.sorting"
		);
	}

	@Test
	public void testPrefixAlfaOrder() {
		//all matches are prefix matches so should come in alpha order
		assertMatchOrder("spring",
				"spring.abracdabra",
				"spring.boot",
				"spring.candel",
				"spring.shoe",
				"spring.springer"
		);
	}

	@Test
	public void testPrefixMixedOrder() {
		assertMatchOrder("spring",
				//prefix matches first
				"spring.abracdabra",
				"spring.boot",
				"spring.candel",
				"spring.shoe",
				"spring.springer",
				//non prefix matches after prefix matches in 'similarity order'
				"zspring",
				"asprouting"
		);
	}

	public class TestMap extends FuzzyMap<String> {
		public TestMap(String... entries) {
			for (String e : entries) {
				add(e);
			}
		}
		protected String getKey(String entry) {
			return entry;
		}
	}

	@Test
	public void testCommonPrefix() {
		String[] entries = {
				"a",
				"archipel",
				"aardappel",
				"aardbei",
				"aardvark",
				"zoroaster"
		};
		String[] expectPrefix = {
				"a",
				"a",
				"aard",
				"aard",
				"aard",
				""
		};
		for (int focusOn = 0; focusOn < entries.length; focusOn++) {
			TestMap map = new TestMap();
			for (int other = 0; other < entries.length; other++) {
				if (focusOn != other) {
					map.add(entries[other]);
				}
			}
			String prefix = map.findValidPrefix(entries[focusOn]);
			String prefixEntry = map.findLongestCommonPrefixEntry(entries[focusOn]);
			assertEquals(expectPrefix[focusOn], prefix);
			assertTrue(prefixEntry.startsWith(prefixEntry));
			assertTrue(prefixEntry.length() > prefix.length());
		}
	}

	@Test
	public void testCommonPrefixWithExactMatch() {
		String[] entries = {
				"a",
				"archipel",
				"aardappel",
				"aardbei",
				"aardvark",
				"zoroaster"
		};
		TestMap map = new TestMap(entries);
		for (String find : entries) {
			String found = map.findLongestCommonPrefixEntry(find);
			assertEquals(find, found);
		}
	}

	@Test
	public void testCommonPrefixEmptyMap() {
		TestMap empty = new TestMap();
		assertEquals(null, empty.findValidPrefix("foo"));
		assertEquals(null, empty.findValidPrefix(""));
		assertEquals(null, empty.findLongestCommonPrefixEntry("aaa"));
		assertEquals(null, empty.findLongestCommonPrefixEntry(""));
	}


	private void assertMatchOrder(String pattern, String... datas) {
		TestMap map = new TestMap(datas);
		List<Match<String>> found = map.find(pattern);

		//Note that found elements are scored but not sorted.
		Collections.sort(found, new Comparator<Match<String>>() {
			public int compare(Match<String> o1, Match<String> o2) {
				return Double.valueOf(o2.score).compareTo(o1.score);
			}
		});

		//all the datas should be found and be in the order given.
		assertEquals(found.size(), datas.length);
		for (int i = 0; i < datas.length; i++) {
			assertEquals(datas[i], found.get(i).data);
		}

		// also check that scores are decreasing.
		double previousScore = found.get(0).score;
		assertTrue(previousScore != 0.0);
		for (int i = 1; i < datas.length; i++) {
			String data = datas[i];
			double score = found.get(i).score;
			assertTrue(previousScore >= score, "Wrong score order: '"+datas[i-1]+"'["+previousScore+"] '"+data+"' ["+score+"]");
			previousScore = score;
		}
	}

	private void assertMatch(boolean expect, String pattern, String data) {
		boolean actual = FuzzyMatcher.matchScore(pattern, data) != 0.0;
		assertEquals(expect, actual);
	}

}
