/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.springsource.ide.eclipse.commons.core.PatternUtils;
import org.springsource.ide.eclipse.commons.livexp.util.Filter;

import com.google.common.collect.ImmutableSet;

/**
 * The filter for searching for tags.
 *
 * @author Alex Boyko
 * @author Kris De Volder
 */
public class  TagSearchFilter<T extends Taggable> implements Filter<T> {

	private String searchTerm;

	private String[] searchTags;

	public TagSearchFilter() {
		this(null, null);
	}

	public TagSearchFilter(String[] searchTags, String searchTerm) {
		this.searchTags = searchTags == null ? new String[0] : searchTags;
		this.searchTerm = searchTerm == null ? "" : searchTerm;
	}

	public TagSearchFilter(String s) {
		this();
		if (!s.isEmpty()) {
			String[] splitSearchStr = TagUtils.parseTags(s);
			if (splitSearchStr.length > 0) {
				if (Pattern.matches("(.+)" + TagUtils.SEPARATOR_REGEX, s)) {
					this.searchTags = splitSearchStr;
				} else {
					this.searchTags = Arrays.copyOfRange(splitSearchStr, 0, splitSearchStr.length - 1);
					this.searchTerm = splitSearchStr[splitSearchStr.length - 1];
				}
			}
		}
	}

	@Override
	public boolean accept(T element) {
		if (searchTags.length == 0 && searchTerm.isEmpty()) {
			return true;
		}

		List<Predicate<String>> patterns = new ArrayList<>(searchTags.length);
		for (String searchTag : searchTags) {
			patterns.add(toPattern(searchTag));
		}
		patterns.add(toPattern("*"+searchTerm+"*"));

		Set<String> elementTags = getTags(element);

		return patterns.stream().allMatch(
				(pat) -> elementTags.stream().anyMatch(pat)
		);
	}

	private Predicate<String> toPattern(String wildcarded) {
		Pattern pat = PatternUtils.createPattern(wildcarded, false, false);
		return (s) -> pat.matcher(s).matches();
	}

	@Override
	public String toString() {
		String initSearchText = TagUtils.toString(searchTags);
		if (!searchTerm.isEmpty()) {
			initSearchText += TagUtils.SEPARATOR + searchTerm;
		}
		return initSearchText;
	}

	protected ImmutableSet<String> getTags(T element) {
		LinkedHashSet<String> tags = element.getTags();
		if (tags == null) {
			return ImmutableSet.of();
		}
		return ImmutableSet.copyOf(tags);
	}

}
