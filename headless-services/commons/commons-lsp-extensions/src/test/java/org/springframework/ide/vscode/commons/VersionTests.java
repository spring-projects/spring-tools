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
}
