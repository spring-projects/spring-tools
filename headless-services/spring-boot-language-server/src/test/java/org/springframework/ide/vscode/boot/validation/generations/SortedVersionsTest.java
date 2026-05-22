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
			new Version(1,5,3,"RELEASE"),
			new Version(1,5,10,"RELEASE"),
			new Version(1,5,12,"RELEASE"),
			new Version(2,0,0,null),
			new Version(2,0,3,null),
			new Version(2,1,6,null),
			new Version(2,4,15,null),
			new Version(2,5,21,null),
			new Version(2,6,10,null),
			new Version(2,6,18,null),
			new Version(2,7,3,null),
			new Version(2,7,6,null),
			new Version(3,0,0,null)
		);
	
	final SortedVersions sortedVersions = new SortedVersions(releases);

	@Test
	void testGetNewerLatestPatchRelease() {
		assertEquals(Optional.of(new Version(1,5,12,"RELEASE")), sortedVersions.getNewerLatestPatchRelease(new Version(1,5,3, "RELEASE")));
		assertFalse(sortedVersions.getNewerLatestPatchRelease(new Version(1,4,3, null)).isPresent());
		assertEquals(Optional.of(new Version(2,1,6,null)), sortedVersions.getNewerLatestPatchRelease(new Version(2,1,3, null)));
		assertEquals(Optional.of(new Version(2,6,18,null)), sortedVersions.getNewerLatestPatchRelease(new Version(2,6,5, null)));
		assertFalse(sortedVersions.getNewerLatestPatchRelease(new Version(2,6,18, null)).isPresent());
		assertFalse(sortedVersions.getNewerLatestPatchRelease(new Version(3,0,0, null)).isPresent());
		assertFalse(sortedVersions.getNewerLatestPatchRelease(new Version(3,0,1, null)).isPresent());
	}

	@Test
	void testGetNewerLatestMinorRelease() {
		assertFalse(sortedVersions.getNewerLatestMinorRelease(new Version(1,5,3, "RELEASE")).isPresent());
		assertEquals(Optional.of(new Version(1,5,12,"RELEASE")), sortedVersions.getNewerLatestMinorRelease(new Version(1,4,3, "RELEASE")));
		assertEquals(Optional.of(new Version(2,7,6,null)), sortedVersions.getNewerLatestMinorRelease(new Version(2,1,3, null)));
		assertEquals(Optional.of(new Version(2,7,6,null)), sortedVersions.getNewerLatestMinorRelease(new Version(2,6,5, null)));
		assertFalse(sortedVersions.getNewerLatestMinorRelease(new Version(2,7,3, null)).isPresent());
		assertFalse(sortedVersions.getNewerLatestMinorRelease(new Version(2,7,6, null)).isPresent());
		assertFalse(sortedVersions.getNewerLatestMinorRelease(new Version(3,0,0, null)).isPresent());
		assertFalse(sortedVersions.getNewerLatestMinorRelease(new Version(3,0,1, null)).isPresent());
	}

	@Test
	void testGetNewerLatestMajorRelease() {
		assertEquals(Optional.of(new Version(3,0,0,null)), sortedVersions.getNewerLatestMajorRelease(new Version(1,5,3, "RELEASE")));
		assertEquals(Optional.of(new Version(3,0,0,null)), sortedVersions.getNewerLatestMajorRelease(new Version(1,4,3, null)));
		assertEquals(Optional.of(new Version(3,0,0,null)), sortedVersions.getNewerLatestMajorRelease(new Version(2,1,3, null)));
		assertEquals(Optional.of(new Version(3,0,0,null)), sortedVersions.getNewerLatestMajorRelease(new Version(2,6,5, null)));
		assertFalse(sortedVersions.getNewerLatestMajorRelease(new Version(3,0,0, null)).isPresent());
		assertFalse(sortedVersions.getNewerLatestMajorRelease(new Version(3,0,1, null)).isPresent());
		assertFalse(sortedVersions.getNewerLatestMajorRelease(new Version(4,1,3, null)).isPresent());
	}

}
