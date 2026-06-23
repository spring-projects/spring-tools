/*******************************************************************************
 * Copyright (c) 2022, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.validation.generations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.commons.Version;

public class SortedVersionsTest {

	final List<Version> releases = Arrays.asList(
			Version.parse("1.5.3.RELEASE"),
			Version.parse("1.5.10.RELEASE"),
			Version.parse("1.5.12.RELEASE"),
			Version.parse("2.0.0"),
			Version.parse("2.0.3"),
			Version.parse("2.1.6"),
			Version.parse("2.4.15"),
			Version.parse("2.5.21"),
			Version.parse("2.6.10"),
			Version.parse("2.6.18"),
			Version.parse("2.7.3"),
			Version.parse("2.7.6"),
			Version.parse("3.0.0")
		);

	final SortedVersions sortedVersions = new SortedVersions(releases);

	@Test
	void testGetNewerLatestPatchRelease() {
		assertEquals(Optional.of(Version.parse("1.5.12.RELEASE")), sortedVersions.getNewerLatestPatchRelease(Version.parse("1.5.3.RELEASE")));
		assertFalse(sortedVersions.getNewerLatestPatchRelease(Version.parse("1.4.3")).isPresent());
		assertEquals(Optional.of(Version.parse("2.1.6")), sortedVersions.getNewerLatestPatchRelease(Version.parse("2.1.3")));
		assertEquals(Optional.of(Version.parse("2.6.18")), sortedVersions.getNewerLatestPatchRelease(Version.parse("2.6.5")));
		assertFalse(sortedVersions.getNewerLatestPatchRelease(Version.parse("2.6.18")).isPresent());
		assertFalse(sortedVersions.getNewerLatestPatchRelease(Version.parse("3.0.0")).isPresent());
		assertFalse(sortedVersions.getNewerLatestPatchRelease(Version.parse("3.0.1")).isPresent());
	}

	@Test
	void testGetNewerLatestMinorRelease() {
		assertFalse(sortedVersions.getNewerLatestMinorRelease(Version.parse("1.5.3.RELEASE")).isPresent());
		assertEquals(Optional.of(Version.parse("1.5.12.RELEASE")), sortedVersions.getNewerLatestMinorRelease(Version.parse("1.4.3.RELEASE")));
		assertEquals(Optional.of(Version.parse("2.7.6")), sortedVersions.getNewerLatestMinorRelease(Version.parse("2.1.3")));
		assertEquals(Optional.of(Version.parse("2.7.6")), sortedVersions.getNewerLatestMinorRelease(Version.parse("2.6.5")));
		assertFalse(sortedVersions.getNewerLatestMinorRelease(Version.parse("2.7.3")).isPresent());
		assertFalse(sortedVersions.getNewerLatestMinorRelease(Version.parse("2.7.6")).isPresent());
		assertFalse(sortedVersions.getNewerLatestMinorRelease(Version.parse("3.0.0")).isPresent());
		assertFalse(sortedVersions.getNewerLatestMinorRelease(Version.parse("3.0.1")).isPresent());
	}

	@Test
	void testGetNewerLatestMajorRelease() {
		assertEquals(Optional.of(Version.parse("3.0.0")), sortedVersions.getNewerLatestMajorRelease(Version.parse("1.5.3.RELEASE")));
		assertEquals(Optional.of(Version.parse("3.0.0")), sortedVersions.getNewerLatestMajorRelease(Version.parse("1.4.3")));
		assertEquals(Optional.of(Version.parse("3.0.0")), sortedVersions.getNewerLatestMajorRelease(Version.parse("2.1.3")));
		assertEquals(Optional.of(Version.parse("3.0.0")), sortedVersions.getNewerLatestMajorRelease(Version.parse("2.6.5")));
		assertFalse(sortedVersions.getNewerLatestMajorRelease(Version.parse("3.0.0")).isPresent());
		assertFalse(sortedVersions.getNewerLatestMajorRelease(Version.parse("3.0.1")).isPresent());
		assertFalse(sortedVersions.getNewerLatestMajorRelease(Version.parse("4.1.3")).isPresent());
	}

}
