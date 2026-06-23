/*******************************************************************************
 * Copyright (c) 2023, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.commons.Version.ReleaseType;

public class VersionTests {

	// ---- parsing ----

	@Test
	void parseThreePartRelease() {
		Version v = Version.parse("2.7.5");
		assertEquals(2, v.getMajor());
		assertEquals(7, v.getMinor());
		assertEquals(5, v.getPatch());
		assertNull(v.getQualifier());
		assertEquals(ReleaseType.RELEASE, v.getReleaseType());
		assertTrue(v.isRelease());
		assertFalse(v.isPreRelease());
		assertEquals("2.7.5", v.toString());
	}

	@Test
	void parseSnapshot() {
		Version v = Version.parse("3.0.0-SNAPSHOT");
		assertEquals(3, v.getMajor());
		assertEquals(0, v.getMinor());
		assertEquals(0, v.getPatch());
		assertEquals("SNAPSHOT", v.getQualifier());
		assertEquals(ReleaseType.SNAPSHOT, v.getReleaseType());
		assertTrue(v.isSnapshot());
		assertTrue(v.isPreRelease());
		assertFalse(v.isRelease());
	}

	@Test
	void parseRcWithHyphen() {
		Version v = Version.parse("2.6.14-RC2");
		assertEquals(2, v.getMajor());
		assertEquals(6, v.getMinor());
		assertEquals(14, v.getPatch());
		assertEquals("RC2", v.getQualifier());
		assertEquals(ReleaseType.RC, v.getReleaseType());
		assertTrue(v.isPreRelease());
	}

	@Test
	void parseMilestoneWithHyphen() {
		Version v = Version.parse("3.3.0-M1");
		assertEquals(3, v.getMajor());
		assertEquals(3, v.getMinor());
		assertEquals(0, v.getPatch());
		assertEquals("M1", v.getQualifier());
		assertEquals(ReleaseType.MILESTONE, v.getReleaseType());
		assertTrue(v.isPreRelease());
	}

	@Test
	void parseOldStyleReleaseQualifier() {
		Version v = Version.parse("2.7.5.RELEASE");
		assertEquals(2, v.getMajor());
		assertEquals(7, v.getMinor());
		assertEquals(5, v.getPatch());
		assertEquals("RELEASE", v.getQualifier());
		assertEquals(ReleaseType.RELEASE, v.getReleaseType());
		assertTrue(v.isRelease());
	}

	@Test
	void parseOldStyleMilestoneQualifier() {
		Version v = Version.parse("1.0.0.M1");
		assertEquals(1, v.getMajor());
		assertEquals(0, v.getMinor());
		assertEquals(0, v.getPatch());
		assertEquals("M1", v.getQualifier());
		assertEquals(ReleaseType.MILESTONE, v.getReleaseType());
		assertTrue(v.isPreRelease());
	}

	@Test
	void parseOldStyleRcQualifier() {
		Version v = Version.parse("2.7.5.RC1");
		assertEquals(ReleaseType.RC, v.getReleaseType());
	}

	@Test
	void parseFourPartNumericVersion() {
		Version v = Version.parse("3.3.0.1");
		assertNotNull(v);
		assertEquals(3, v.getMajor());
		assertEquals(3, v.getMinor());
		assertEquals(0, v.getPatch());
		assertEquals(1, v.getBuild());
		assertNull(v.getQualifier());
		assertEquals(ReleaseType.RELEASE, v.getReleaseType());
		assertEquals("3.3.0.1", v.toString());
	}

	@Test
	void parseFourPartWithQualifier() {
		Version v = Version.parse("3.3.0.1.RELEASE");
		assertNotNull(v);
		assertEquals(3, v.getMajor());
		assertEquals(3, v.getMinor());
		assertEquals(0, v.getPatch());
		assertEquals(1, v.getBuild());
		assertEquals("RELEASE", v.getQualifier());
		assertEquals(ReleaseType.RELEASE, v.getReleaseType());
	}

	@Test
	void parseTwoPart() {
		Version v = Version.parse("2.7");
		assertEquals(2, v.getMajor());
		assertEquals(7, v.getMinor());
		assertEquals(0, v.getPatch());
		assertNull(v.getQualifier());
	}

	@Test
	void parseOnePart() {
		Version v = Version.parse("2");
		assertEquals(2, v.getMajor());
		assertEquals(0, v.getMinor());
		assertEquals(0, v.getPatch());
		assertNull(v.getQualifier());
	}

	@Test
	void parseGaQualifier() {
		Version v = Version.parse("3.3.0.GA");
		assertEquals(ReleaseType.RELEASE, v.getReleaseType());
		assertTrue(v.isRelease());
	}

	@Test
	void parseAlpha() {
		Version v = Version.parse("1.0.0-ALPHA1");
		assertEquals(ReleaseType.ALPHA, v.getReleaseType());
		assertTrue(v.isPreRelease());
	}

	@Test
	void parseBeta() {
		Version v = Version.parse("1.0.0-BETA2");
		assertEquals(ReleaseType.BETA, v.getReleaseType());
		assertTrue(v.isPreRelease());
	}

	// ---- sorting / compareTo ----

	@Test
	void releaseGreaterThanSnapshot() {
		Version release = Version.parse("3.3.0");
		Version snapshot = Version.parse("3.3.0-SNAPSHOT");
		assertTrue(release.compareTo(snapshot) > 0, "3.3.0 should be > 3.3.0-SNAPSHOT");
		assertTrue(snapshot.compareTo(release) < 0, "3.3.0-SNAPSHOT should be < 3.3.0");
	}

	@Test
	void releaseGreaterThanMilestone() {
		assertTrue(Version.parse("3.3.0").compareTo(Version.parse("3.3.0-M1")) > 0);
	}

	@Test
	void releaseGreaterThanRc() {
		assertTrue(Version.parse("3.3.0").compareTo(Version.parse("3.3.0-RC1")) > 0);
	}

	@Test
	void rcGreaterThanMilestone() {
		assertTrue(Version.parse("3.3.0-RC1").compareTo(Version.parse("3.3.0-M1")) > 0);
	}

	@Test
	void milestoneGreaterThanBeta() {
		assertTrue(Version.parse("3.3.0-M1").compareTo(Version.parse("3.3.0-BETA1")) > 0);
	}

	@Test
	void betaGreaterThanAlpha() {
		assertTrue(Version.parse("3.3.0-BETA1").compareTo(Version.parse("3.3.0-ALPHA1")) > 0);
	}

	@Test
	void laterMilestoneGreaterThanEarlier() {
		assertTrue(Version.parse("3.3.0-M2").compareTo(Version.parse("3.3.0-M1")) > 0);
	}

	@Test
	void laterRcGreaterThanEarlier() {
		assertTrue(Version.parse("3.3.0-RC2").compareTo(Version.parse("3.3.0-RC1")) > 0);
	}

	@Test
	void fourPartVersionSorting() {
		Version v100 = Version.parse("3.3.0");
		Version v101 = Version.parse("3.3.0.1");
		assertTrue(v101.compareTo(v100) > 0, "3.3.0.1 should be > 3.3.0");
	}

	@Test
	void oldStyleReleaseEquivalentToPlain() {
		// LatestRelease normalises .RELEASE → treated as GA, should compare equal to plain
		Version plain = Version.parse("3.3.0");
		Version withRelease = Version.parse("3.3.0.RELEASE");
		assertEquals(0, plain.compareTo(withRelease));
	}

	@Test
	void newerPatchGreaterThanOlder() {
		assertTrue(Version.parse("3.3.1").compareTo(Version.parse("3.3.0")) > 0);
	}

	@Test
	void newerMinorGreaterThanOlder() {
		assertTrue(Version.parse("3.4.0").compareTo(Version.parse("3.3.0")) > 0);
	}

	@Test
	void newerMajorGreaterThanOlder() {
		assertTrue(Version.parse("4.0.0").compareTo(Version.parse("3.3.0")) > 0);
	}

	// ---- isRelease / isPreRelease ----

	@Test
	void releaseTypeClassification() {
		assertTrue(Version.parse("3.3.0").isRelease());
		assertFalse(Version.parse("3.3.0").isPreRelease());

		assertTrue(Version.parse("3.3.0-SNAPSHOT").isSnapshot());
		assertFalse(Version.parse("3.3.0-SNAPSHOT").isRelease());

		assertTrue(Version.parse("3.3.0-M1").isPreRelease());
		assertFalse(Version.parse("3.3.0-M1").isRelease());

		assertTrue(Version.parse("3.3.0-RC1").isPreRelease());
		assertFalse(Version.parse("3.3.0-RC1").isRelease());
	}

	// ---- display / toString ----

	@Test
	void toStringPreservesOriginalString() {
		assertEquals("3.3.0-M1", Version.parse("3.3.0-M1").toString());
		assertEquals("3.3.0-SNAPSHOT", Version.parse("3.3.0-SNAPSHOT").toString());
		assertEquals("2.7.5.RELEASE", Version.parse("2.7.5.RELEASE").toString());
		assertEquals("3.3.0.1", Version.parse("3.3.0.1").toString());
	}

	@Test
	void parseRoundTrip() {
		assertEquals("3.3.0",   Version.parse("3.3.0").toString());
		assertEquals("3.3.0.1", Version.parse("3.3.0.1").toString());
		
	}

	@Test
	void toMajorMinorVersionStr() {
		assertEquals("3.3", Version.parse("3.3.0-M1").toMajorMinorVersionStr());
	}

	@Test
	void toMajorMinorPatchVersionStr() {
		assertEquals("3.3.0", Version.parse("3.3.0-M1").toMajorMinorPatchVersionStr());
	}

	// ---- comprehensive sort-based comparison tests ----

	@Test
	void sortAllThreePartVersionVariants() {
		// Covers every qualifier type and numeric progression (M1<M2, RC1<RC2, SP1<SP2).
		// Aliases (A/ALPHA, B/BETA, CR/RC) compare equal and are tested in aliasesCompareEqual.
		assertSortedOrder(
			"3.3.0-ALPHA1",    // alpha
			"3.3.0-BETA1",     // beta
			"3.3.0-M1",        // milestone
			"3.3.0-M2",        // milestone, higher qualifier number
			"3.3.0-RC1",       // rc
			"3.3.0-RC2",       // rc, higher qualifier number
			"3.3.0-SNAPSHOT",  // snapshot
			"3.3.0",           // release (GA)
			"3.3.0-SP1",       // service pack
			"3.3.0-SP2"        // service pack, higher qualifier number
		);
	}

	@Test
	void sortAllFourPartVersionVariants() {
		// Same qualifier coverage as the 3-part test but with a 4th numeric component.
		assertSortedOrder(
			"3.3.0.1-ALPHA1",
			"3.3.0.1-BETA1",
			"3.3.0.1-M1",
			"3.3.0.1-M2",
			"3.3.0.1-RC1",
			"3.3.0.1-RC2",
			"3.3.0.1-SNAPSHOT",
			"3.3.0.1",
			"3.3.0.1-SP1",
			"3.3.0.1-SP2"
		);
	}

	@Test
	void sortThreeAndFourPartVersionsMixed() {
		// All 3-part and 4-part variants interleaved in one list.
		// The critical boundary is 3.3.0-SP2 < 3.3.0.1-ALPHA1: the 4th numeric
		// component (build=1 vs 0) dominates even the highest 3-part qualifier.
		assertSortedOrder(
			"3.3.0-ALPHA1",
			"3.3.0-BETA1",
			"3.3.0-M1",
			"3.3.0-M2",
			"3.3.0-RC1",
			"3.3.0-RC2",
			"3.3.0-SNAPSHOT",
			"3.3.0",
			"3.3.0-SP1",
			"3.3.0-SP2",
			"3.3.0.1-ALPHA1",
			"3.3.0.1-BETA1",
			"3.3.0.1-M1",
			"3.3.0.1-M2",
			"3.3.0.1-RC1",
			"3.3.0.1-RC2",
			"3.3.0.1-SNAPSHOT",
			"3.3.0.1",
			"3.3.0.1-SP1",
			"3.3.0.1-SP2"
		);
	}

	@Test
	void numericComponentsDominateQualifier() {
		// A strictly higher numeric component (build, patch, minor, major) always
		// wins over qualifier, so alpha of the next tier > SP of the prior tier.
		assertSortedOrder(
			"3.3.0-SP2",    // highest 3-part qualifier
			"3.3.0.1-A1",   // 4th numeric component beats any 3-part qualifier
			"3.3.0.1-SP2",  // highest 4-part qualifier
			"3.3.1-A1",     // higher patch beats any build/qualifier
			"3.3.1-SP2",
			"3.4.0-A1",     // higher minor beats any patch/qualifier
			"3.4.0-SP2",
			"4.0.0-A1",     // higher major beats everything
			"4.0.0-SP2"
		);
	}

	@Test
	void timestampedSnapshotTreatedAsSnapshot() {
		// Maven timestamped snapshots (e.g. from a remote repo) must be < the GA release.
		Version tsSnap = Version.parse("4.0.2-SNAPSHOT-20250101.123456-1");
		Version snap   = Version.parse("4.0.2-SNAPSHOT");
		Version fin    = Version.parse("4.0.2.Final");
		assertEquals(ReleaseType.SNAPSHOT, tsSnap.getReleaseType());
		assertTrue(tsSnap.compareTo(fin) < 0,  "timestamped snapshot < Final");
		assertEquals(0, tsSnap.compareTo(snap), "timestamped snapshot == plain snapshot");
	}

	@Test
	void gaAliasesCompareEqualToPlainRelease() {
		Version plain = Version.parse("3.3.0");
		assertEquals(0, plain.compareTo(Version.parse("3.3.0.RELEASE")));
		assertEquals(0, plain.compareTo(Version.parse("3.3.0.GA")));
		assertEquals(0, plain.compareTo(Version.parse("3.3.0.FINAL")));
		assertEquals(0, Version.parse("3.3.0.RELEASE").compareTo(Version.parse("3.3.0.GA")));
		assertEquals(0, Version.parse("3.3.0.GA").compareTo(Version.parse("3.3.0.FINAL")));
	}

	@Test
	void dotSeparatorQualifiersRespectPriority() {
		// Old-style dot-separated qualifiers observe the same priority order.
		assertTrue(Version.parse("3.3.0.M1").compareTo(Version.parse("3.3.0.RC1")) < 0);
		assertTrue(Version.parse("3.3.0.RC1").compareTo(Version.parse("3.3.0.SNAPSHOT")) < 0);
		// Separator style is irrelevant — only the qualifier number matters,
		// so "3.3.0-M2" (number=2) > "3.3.0.M1" (number=1).
		assertTrue(Version.parse("3.3.0-M2").compareTo(Version.parse("3.3.0.M1")) > 0);
	}

	@Test
	void aliasesCompareEqual() {
		// Short and long qualifier aliases have the same ReleaseType and number, so they compare equal.
		assertEquals(0, Version.parse("3.3.0-A1").compareTo(Version.parse("3.3.0-ALPHA1")));
		assertEquals(0, Version.parse("3.3.0-B1").compareTo(Version.parse("3.3.0-BETA1")));
		assertEquals(0, Version.parse("3.3.0-CR1").compareTo(Version.parse("3.3.0-RC1")));
	}

	@Test
	void caseInsensitiveVersionStringsCompareEqual() {
		// Qualifier parsing is case-insensitive, so mixed-case versions compare equal.
		assertEquals(0, Version.parse("3.3.0-SNAPSHOT").compareTo(Version.parse("3.3.0-snapshot")));
		assertEquals(0, Version.parse("3.3.0-RC1").compareTo(Version.parse("3.3.0-rc1")));
		assertEquals(0, Version.parse("3.3.0-M1").compareTo(Version.parse("3.3.0-m1")));
	}

	private void assertSortedOrder(String... expectedOrder) {
		List<Version> versions = Arrays.stream(expectedOrder)
				.map(Version::parse)
				.collect(Collectors.toList());
		Collections.reverse(versions);
		versions.sort(null);
		List<String> actual = versions.stream().map(Version::toString).collect(Collectors.toList());
		assertEquals(List.of(expectedOrder), actual);
	}
}
