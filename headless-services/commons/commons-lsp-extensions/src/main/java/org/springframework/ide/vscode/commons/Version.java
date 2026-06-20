/*******************************************************************************
 * Copyright (c) 2022, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a parsed artifact version with support for multi-part numeric versions
 * (major.minor.patch.build) and pre-release qualifiers (M, RC, SNAPSHOT, etc.).
 * <p>
 * Sorting follows the same qualifier precedence as OpenRewrite's {@code LatestRelease}:
 * alpha &lt; beta &lt; milestone &lt; rc &lt; snapshot &lt; release &lt; service-pack.
 * <p>
 * The comparison and parsing logic in this class is derived from
 * {@code org.openrewrite.semver.VersionComparator} and {@code org.openrewrite.semver.LatestRelease}
 * (Apache License 2.0, Copyright 2021 the OpenRewrite authors).
 *
 * @author Alex Boyko
 */
public final class Version implements Comparable<Version> {

	// Derived from org.openrewrite.semver.VersionComparator (Apache 2.0)
	// Groups: 1=major, 2=minor, 3=patch, 4=build (4th numeric part), 5=fifth part, 6=qualifier suffix
	private static final Pattern RELEASE_PATTERN = Pattern.compile(
			"(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:\\.(\\d+))?(?:\\.(\\d+))?([-.+].*)?");
	private static final String[] RELEASE_SUFFIXES = { ".final", ".ga", ".release" };
	private static final int QUALIFIER_GROUP = 6;

	private static final Pattern QUALIFIER_TYPE_PATTERN =
			Pattern.compile("^(snapshot|alpha|a|beta|b|milestone|m|rc|cr|sp)(\\d*)$",
					Pattern.CASE_INSENSITIVE);

	/**
	 * The release category of a version, ordered from least to most stable.
	 */
	public enum ReleaseType {
		ALPHA(1),
		BETA(2),
		MILESTONE(3),
		RC(4),
		SNAPSHOT(5),
		RELEASE(6),
		SERVICE_PACK(7);

		private final int priority;

		ReleaseType(int priority) {
			this.priority = priority;
		}

		/** Numeric ordering value — higher means more stable/newer. */
		public int getPriority() {
			return priority;
		}

		/** True for everything before a GA release (alpha, beta, milestone, rc, snapshot). */
		public boolean isPreRelease() {
			return priority < RELEASE.priority;
		}

		public boolean isSnapshot() {
			return this == SNAPSHOT;
		}
	}

	private final int major;
	private final int minor;
	private final int patch;
	/** Fourth numeric component (e.g. the {@code 1} in {@code 3.3.0.1}); 0 if absent. */
	private final int build;
	/** Qualifier text without its leading separator (e.g. {@code "M1"}, {@code "SNAPSHOT"}), or {@code null}. */
	private final String qualifier;
	private final ReleaseType releaseType;
	/** Original parsed string — used for display and comparison. */
	private final String versionString;

	private Version(int major, int minor, int patch, int build, String qualifier, String originalString) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
		this.build = build;
		this.qualifier = qualifier;
		this.releaseType = toReleaseType(qualifier);
		this.versionString = originalString;
	}

	public int getMajor() {
		return major;
	}

	public int getMinor() {
		return minor;
	}

	public int getPatch() {
		return patch;
	}

	/** Fourth numeric version component; 0 when not present (e.g. {@code 3.3.0} → 0). */
	public int getBuild() {
		return build;
	}

	/**
	 * Raw qualifier text without its leading separator, or {@code null} for GA releases.
	 * Examples: {@code "SNAPSHOT"}, {@code "M1"}, {@code "RC2"}.
	 */
	public String getQualifier() {
		return qualifier;
	}

	public ReleaseType getReleaseType() {
		return releaseType;
	}

	/** True for GA releases and service packs. */
	public boolean isRelease() {
		return releaseType == ReleaseType.RELEASE || releaseType == ReleaseType.SERVICE_PACK;
	}

	/** True for any version that is not yet a GA release (alpha, beta, M, RC, SNAPSHOT). */
	public boolean isPreRelease() {
		return releaseType.isPreRelease();
	}

	public boolean isSnapshot() {
		return releaseType == ReleaseType.SNAPSHOT;
	}

	public String toMajorMinorVersionStr() {
		return major + "." + minor;
	}

	public String toMajorMinorPatchVersionStr() {
		return major + "." + minor + "." + patch;
	}

	/** Returns the original parsed version string (e.g. {@code "3.3.0-M1"}, {@code "3.3.0.RELEASE"}). */
	@Override
	public String toString() {
		return versionString;
	}

	/**
	 * Compares using qualifier-aware semantic ordering:
	 * alpha &lt; beta &lt; milestone &lt; rc &lt; snapshot &lt; release &lt; service-pack.
	 * Four-part numeric versions are handled correctly.
	 * <p>
	 * Algorithm derived from {@code org.openrewrite.semver.LatestRelease} (Apache 2.0).
	 */
	@Override
	public int compareTo(Version o) {
		return compareVersionStrings(this.versionString, o.versionString);
	}

	@Override
	public int hashCode() {
		return Objects.hash(major, minor, patch, build, qualifier);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Version)) {
			return false;
		}
		Version other = (Version) obj;
		return major == other.major && minor == other.minor && patch == other.patch
				&& build == other.build && Objects.equals(qualifier, other.qualifier);
	}

	/**
	 * Parses a version string into a {@link Version}.
	 * <p>
	 * Accepts formats like {@code 3.3.0}, {@code 3.3.0-M1}, {@code 3.3.0-SNAPSHOT},
	 * {@code 3.3.0.RELEASE}, {@code 3.3.0.1} (four-part), {@code 2.7}, {@code 2}.
	 *
	 * @return the parsed version, or {@code null} if the string cannot be parsed
	 */
	public static Version parse(String versionStr) {
		if (versionStr == null || versionStr.isEmpty()) {
			return null;
		}
		Matcher m = RELEASE_PATTERN.matcher(versionStr);
		if (m.matches()) {
			int major = parseGroup(m, 1);
			int minor = parseGroup(m, 2);
			int patch = parseGroup(m, 3);
			int build = parseGroup(m, 4);
			String qualifier = stripSeparator(m.group(QUALIFIER_GROUP));
			return new Version(major, minor, patch, build, qualifier, versionStr);
		}
		// Fallback: simple dot-delimited numeric segments
		String[] parts = versionStr.split("\\.");
		try {
			int major = Integer.parseInt(parts[0]);
			int minor = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
			int patch = parts.length >= 3 ? Integer.parseInt(parts[2]) : 0;
			return new Version(major, minor, patch, 0, null, versionStr);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	// ---- comparison logic (derived from org.openrewrite.semver.LatestRelease, Apache 2.0) ----

	private static int compareVersionStrings(String v1, String v2) {
		if (v1.equalsIgnoreCase(v2)) {
			return 0;
		}

		String nv1 = normalizeVersion(v1);
		String nv2 = normalizeVersion(v2);

		int vp1 = countVersionParts(nv1);
		int vp2 = countVersionParts(nv2);

		// Pad the shorter version with ".0" segments so both have the same number of parts
		if (vp1 > vp2) {
			StringBuilder sb = new StringBuilder(nv2);
			for (int i = vp2; i < vp1; i++) sb.append(".0");
			nv2 = sb.toString();
		} else if (vp2 > vp1) {
			StringBuilder sb = new StringBuilder(nv1);
			for (int i = vp1; i < vp2; i++) sb.append(".0");
			nv1 = sb.toString();
		}

		Matcher m1 = RELEASE_PATTERN.matcher(nv1);
		Matcher m2 = RELEASE_PATTERN.matcher(nv2);
		m1.find();
		m2.find();

		int maxParts = Math.max(vp1, vp2);
		for (int i = 1; i <= maxParts; i++) {
			String p1 = m1.group(i);
			String p2 = m2.group(i);
			if (p1 == null) {
				return p2 == null ? nv1.compareTo(nv2) : -1;
			} else if (p2 == null) {
				return 1;
			}
			long diff = Long.parseLong(p1) - Long.parseLong(p2);
			if (diff != 0) {
				return diff > 0 ? 1 : -1;
			}
		}

		// All numeric parts equal — compare by qualifier priority
		int prio1 = qualifierPriority(m1.group(QUALIFIER_GROUP));
		int prio2 = qualifierPriority(m2.group(QUALIFIER_GROUP));
		if (prio1 != prio2) {
			return Integer.compare(prio1, prio2);
		}
		return nv1.compareTo(nv2);
	}

	/** Strips trailing {@code .final} / {@code .ga} / {@code .release} and pads to at least 3 parts. */
	private static String normalizeVersion(String version) {
		int lastDotIdx = version.lastIndexOf('.');
		for (String suffix : RELEASE_SUFFIXES) {
			if (version.regionMatches(true, lastDotIdx, suffix, 0, suffix.length())) {
				version = version.substring(0, lastDotIdx);
				break;
			}
		}
		int parts = countVersionParts(version);
		if (parts <= 2) {
			String[] split = version.split("(?=[-+])");
			for (; parts <= 2; parts++) {
				split[0] += ".0";
			}
			version = split.length > 1 ? split[0] + split[1] : split[0];
		}
		return version;
	}

	private static int countVersionParts(String version) {
		int count = 0;
		int len = version.length();
		int lastSepIdx = -1;
		for (int i = 0; i < len; i++) {
			char c = version.charAt(i);
			if (c == '.' || c == '-' || c == '$') {
				if (lastSepIdx == i - 1) return count;
				lastSepIdx = i;
			} else if (lastSepIdx == i - 1) {
				if (!Character.isDigit(c)) break;
				count++;
			}
		}
		return count;
	}

	private static int qualifierPriority(String suffix) {
		switch (extractQualifier(suffix)) {
			case "alpha": case "a":                        return 1;
			case "beta":  case "b":                        return 2;
			case "milestone": case "m":                    return 3;
			case "rc":    case "cr":                       return 4;
			case "snapshot":                               return 5;
			case "": case "ga": case "final": case "release": return 6;
			case "sp":                                     return 7;
			default:                                       return 8;
		}
	}

	private static String extractQualifier(String suffix) {
		if (suffix == null) return "";
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i < suffix.length(); i++) {
			char c = suffix.charAt(i);
			if (Character.isLetter(c)) sb.append(Character.toLowerCase(c));
			else break;
		}
		return sb.toString();
	}

	// ---- parsing helpers ----

	private static int parseGroup(Matcher m, int group) {
		String s = m.group(group);
		return s != null ? Integer.parseInt(s) : 0;
	}

	private static String stripSeparator(String qualifierWithSep) {
		if (qualifierWithSep == null || qualifierWithSep.isEmpty()) {
			return null;
		}
		char sep = qualifierWithSep.charAt(0);
		if (sep == '-' || sep == '.' || sep == '+') {
			String text = qualifierWithSep.substring(1);
			return text.isEmpty() ? null : text;
		}
		return qualifierWithSep;
	}

	private static ReleaseType toReleaseType(String qualifier) {
		if (qualifier == null || qualifier.isEmpty()) {
			return ReleaseType.RELEASE;
		}
		String lower = qualifier.toLowerCase();
		if (lower.equals("release") || lower.equals("ga") || lower.equals("final")) {
			return ReleaseType.RELEASE;
		}
		if (lower.startsWith("sp")) {
			return ReleaseType.SERVICE_PACK;
		}
		Matcher m = QUALIFIER_TYPE_PATTERN.matcher(lower);
		if (m.matches()) {
			switch (m.group(1).toLowerCase()) {
				case "snapshot":               return ReleaseType.SNAPSHOT;
				case "rc": case "cr":          return ReleaseType.RC;
				case "milestone": case "m":    return ReleaseType.MILESTONE;
				case "beta": case "b":         return ReleaseType.BETA;
				case "alpha": case "a":        return ReleaseType.ALPHA;
				default:                       break;
			}
		}
		return ReleaseType.RELEASE;
	}
}
