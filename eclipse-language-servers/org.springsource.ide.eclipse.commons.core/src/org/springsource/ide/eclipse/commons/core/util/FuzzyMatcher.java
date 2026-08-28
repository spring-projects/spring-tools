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
package org.springsource.ide.eclipse.commons.core.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jface.text.IRegion;

/**
 * @author Kris De Volder
 */
public class FuzzyMatcher {

	private static class Region implements IRegion {
		public Region(int start, int end) {
			super();
			this.start = start;
			this.end = end;
		}
		int start;
		int end;
		@Override
		public int getLength() {
			return end-start;
		}
		@Override
		public int getOffset() {
			return start;
		}
		@Override
		public String toString() {
			return "Region["+start+".."+end+"]";
		}
	}

	public static Collection<IRegion> highlights(CharSequence query, String data) {
		List<IRegion> highlights = new ArrayList<>();
		int qpos = 0;
		int dpos = 0;
		int qlen = query.length();
		int dlen = data.length();
		while (qpos < qlen && dpos < dlen) {
			int nextMatch = data.indexOf(query.charAt(qpos), dpos);
			if (nextMatch < 0) {
				return highlights;
			}
			addMatch(highlights, nextMatch);
			qpos++;
			dpos = nextMatch+1;
		}
		return highlights;
	}

	private static void addMatch(List<IRegion> highlights, int nextMatch) {
		if (highlights.isEmpty()) {
			highlights.add(new Region(nextMatch, nextMatch+1));
		}
		Region lastRegion = (Region) highlights.get(highlights.size()-1);
		if (lastRegion.getOffset()+lastRegion.getLength() == nextMatch) {
			lastRegion.end++;
		} else {
			highlights.add(new Region(nextMatch, nextMatch+1));
		}
	}

	/**
	 * Match given pattern with a given data. The data is considered a 'match' for the
	 * pattern if all characters in the pattern can be found in the data, in the
	 * same order but with possible 'gaps' in between.
	 * <p>
	 * The function returns 0. when the pattern doesn't match the data and a non-zero
	 * 'score' when it does. The higher the score, the better the match is considered to
	 * be.
	 */
	public static double matchScore(CharSequence pattern, String data) {
		int ppos = 0; //pos of next char in pattern to look for
		int dpos = 0; //pos of next char in data not yet matched
		int gaps = 0; //number of 'gaps' in the match. A gap is any non-empty run of consecutive characters in the data that are not used by the match
		int skips = 0; //number of skipped characters. This is the sum of the length of all the gaps.
		int plen = pattern.length();
		int dlen = data.length();
		if (plen > dlen) {
			return 0.0;
		}
		while (ppos < plen) {
			if (dpos >= dlen) {
				//still chars left in pattern but no more data
				return 0.0;
			}
			char c = pattern.charAt(ppos++);
			int foundCharAt = data.indexOf(c, dpos);
			if (foundCharAt >= 0) {
				if (foundCharAt > dpos) {
					gaps++;
					skips+=foundCharAt-dpos;
				}
				dpos = foundCharAt+1;
			} else {
				return 0.0;
			}
		}
		//end of pattern reached. All matched.
		if (dpos < dlen) {
			//data left over
			//gaps++; don't count end skipped chars as a real 'gap'. Otherwise we
			//tend to favor matches at the end of the string over matches in the middle.
			skips+=dlen-dpos; //but do count the extra chars at end => more extra = worse score
		}
		return score(gaps, skips, pattern);
	}

	private static double score(int gaps, int skips, CharSequence pattern) {
		if (gaps == 0) {
			//gaps == 0 means a prefix match, ignore 'skips' at end of String and just sort
			// alphabetic (see STS-4049)
			return 0.5+pattern.length(); //all scored equally, assumes using a 'stable' sorter.
		} else {
			double badness = 1+gaps + skips/1000.0; // higher is worse
			return 1.0/badness + pattern.length(); //higher is better
		}
	}

}
