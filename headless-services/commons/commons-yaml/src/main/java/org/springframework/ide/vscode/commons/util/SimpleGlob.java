package org.springframework.ide.vscode.commons.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.util.Assert;


/**
 * Glob matcher to use for https://github.com/spring-projects/sts4/issues/639
 * <p>
 * The matcher only handles a simple subset of the full glob syntax. It is conservative and detects  
 * where a pattern looks 'too complex to handle'. 
 *
 */
public abstract class SimpleGlob {
	
	/**
	 * Matches patterns containing one or more '*'. 
	 */
	private static class MultiStarGlob extends SimpleGlob {

		String prefix;
		String[] middle;
		String postfix;
		
		public MultiStarGlob(String pattern) {
			super(pattern);
			Assert.isTrue(pattern.contains("*"), "Pattern must contain at least 1 '*'");
			List<String> parts = new ArrayList<>();
			int scan = 0;
			int star = pattern.indexOf('*', scan);
			while (star >= 0) {
				parts.add(pattern.substring(scan, star));
				scan = star+1;
				star = pattern.indexOf('*', scan);
			}
			postfix = pattern.substring(scan);
			middle = parts.subList(1, parts.size()).toArray(String[]::new);
			prefix = parts.get(0);
		}
		
		@Override
		public Match match(String value) {
			if (!value.startsWith(prefix)) {
				return Match.FAIL;
			}
			int scan = prefix.length();
			for (String part : middle) {
				scan = value.indexOf(part, scan);
				if (scan < 0) {
					return Match.FAIL;
				} else {
					//consume matched part so we cannot use that in matching next parts again.
					scan += part.length();
				}
			}
			if (postfix.length() > value.length()-scan) {
				// there's not enough data left to be able to find the postfix
				return Match.FAIL;
			}
			if (!value.endsWith(postfix)) {
				return Match.FAIL;
			}
			return Match.SUCCESS;
		}

	}

	static final Pattern IS_COMPLEX = Pattern.compile(".*[\\[\\]\\{\\}?!].*");
	private String pattern;
	
	public static enum Match {
		SUCCESS, 
		FAIL, 
		UNKNOWN
	}
	
	/**
	 * Private, use 'create' method instead.
	 */
	private SimpleGlob(String pattern) {
		this.pattern = pattern;
	}
	
	@Override
	public String toString() {
		return "SimpleGlob("+pattern+")";
	}

	public abstract Match match(String value);
	
	public static SimpleGlob create(String pattern) {
		if (IS_COMPLEX.matcher(pattern).matches()) {
			return fakeMatcher(pattern);
		}
		if (pattern.contains("*")) {
			return new MultiStarGlob(pattern);
		} else {
			//Not a real pattern, just a simple string to match with 'equals'
			return new SimpleGlob(pattern) {
				@Override public Match match(String value) {
					return pattern.equals(value) ? Match.SUCCESS : Match.FAIL;
				}
			};
		}
	}

	private static SimpleGlob fakeMatcher(String pattern) {
		return new SimpleGlob(pattern) {
			@Override public Match match(String value) {
				return Match.UNKNOWN;
			}
		};
	}
}
