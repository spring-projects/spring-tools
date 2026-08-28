/*******************************************************************************
 * Copyright (c) 2014 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * A collection of data that can be searched with a simple 'fuzzy' string
 * matching algorithm. Clients must override 'getKey' method to define how
 * a search 'key' is associated with each data item.
 * <p>
 * The collection can then be searched for items who's key matches
 * simple 'fuzzy' patterns.
 */
public abstract class FuzzyMap<E> implements Iterable<E> {
	
    private static final Logger LOG = Logger.getLogger(FuzzyMap.class.getName());

	public static class Match<E> {
		public double score;
		public final E data;
		private String pattern;

		public Match(String pattern, double score, E e) {
			this.pattern = pattern;
			this.score = score;
			this.data = e;
		}
		public static <E> Match<E> getBest(Collection<Match<E>> matches) {
			double bestScore = Double.NEGATIVE_INFINITY;
			Match<E> best = null;
			for (Match<E> match : matches) {
				if (match.score > bestScore) {
					best = match;
					bestScore = match.score;
				}
			}
			return best;
		}

		@Override
		public String toString() {
			return "Match(score="+score+", data="+data+")";
		}
		public String getPattern() {
			return pattern;
		}
	}

	@Override
	public Iterator<E> iterator() {
		return entries.values().iterator();
	}

	private TreeMap<String,E> entries = new TreeMap<String, E>();

	protected abstract String getKey(E entry);

	public void add(E value) {
		//This assumes no two entries have the same id.
		String key = getKey(value);
		E existing = entries.get(key);
		if (existing == null) {
			entries.put(getKey(value), value);
		} else {
			LOG.warning(FuzzyMap.class.getName()+": Multiple entries for key "+key+" some entries discarded");
		}
	}

	/**
	 * Search for pattern. A pattern is just a sequence of characters which have to found in
	 * an entrie's key in the same order as they are in the pattern.
	 * <p>
	 * Note that returned list doesn't yet have elements sorted according to score (instead they
	 * are sorted lexicographically thanks to the fact we use a Tree representation).
	 */
	public List<Match<E>> find(String pattern) {
		if ("".equals(pattern)) {
			//Special case because
			// 1) no need to search. Matches everything
			// 2) want to use different way of sorting / scoring. See https://issuetracker.springsource.com/browse/STS-4008
			ArrayList<Match<E>> matches = new ArrayList<Match<E>>(entries.size());
			for (E v : entries.values()) {
				matches.add(new Match<E>(pattern, 1.0, v));
			}
			return matches;
		} else {
			//TODO: optimize somehow with a smarter index? (right now searches all map entries sequentially)
			ArrayList<Match<E>> matches = new ArrayList<Match<E>>();
			for (Entry<String, E> e : entries.entrySet()) {
				String key = e.getKey();
				double score = FuzzyMatcher.matchScore(pattern, key);
				if (score != 0.0) {
					matches.add(new Match<E>(pattern, score, e.getValue()));
				}
			}
			return matches;
		}
	}

	/**
	 * Searches the index for the longest string which is both
	 *  - a prefix of propertyName
	 *  - a prefix of some key in the map.
	 * Note: If the map is empty, then this returns null, since
	 * no string, not even the empty string is a prefix of a
	 * key in the map.
	 */
	public String findValidPrefix(String propertyName) {
		E best = findLongestCommonPrefixEntry(propertyName);
		return best == null ? null : StringUtil.commonPrefix(propertyName, getKey(best));
	}

	/**
	 * Find property with longest common prefix for given key.
	 */
	public E findLongestCommonPrefixEntry(String propertyName) {
		//We can implementation this O(log(n)) because the properties are kept in a TreeMap which is sorted.
		//This means that entries with common prefix will occur 'next to eachother'
		//The 'best' entry must therefore be either the entry just before or just after
		//the property we are searching for.

		Entry<String, E> ceiln = entries.ceilingEntry(propertyName);
		Entry<String, E> floor = entries.floorEntry(propertyName);
		Entry<String, E> best;
		if (floor == null || floor == ceiln) {
			best = ceiln;
		} else if (ceiln == null) {
			best = floor;
		} else {
			int floorScore = floor == null ? 0 : StringUtil.commonPrefixLength(floor.getKey(), propertyName);
			int ceilnScore = ceiln == null ? 0 : StringUtil.commonPrefixLength(ceiln.getKey(), propertyName);
			best = floorScore > ceilnScore ? floor : ceiln;
		}
		return best == null ? null : best.getValue();
	}

	/**
	 * Find an exact match if it exists.
	 */
	public E get(String id) {
		return entries.get(id);
	}

	public boolean isEmpty() {
		return entries == null || entries.isEmpty();
	}

	public int size() {
		return entries.size();
	}

	public TreeMap<String, E> getTreeMap() {
		return entries;
	}

}
