/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.jdt.core.JavaCore;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaParserUtils;

/**
 * Unit tests for {@link SpringIndexerJavaParserUtils}.
 */
class SpringIndexerJavaParserUtilsTest {

	@Test
	void createChunks_emptyInput_returnsEmptyList() {
		assertTrue(SpringIndexerJavaParserUtils.createChunks(new String[0], 100).isEmpty());
	}

	@Test
	void createChunks_fewerElementsThanChunkSize_singleChunk() {
		String[] in = { "a", "b", "c" };
		List<String[]> chunks = SpringIndexerJavaParserUtils.createChunks(in, 10);
		assertEquals(1, chunks.size());
		assertArrayEquals(in, chunks.get(0));
	}

	@Test
	void createChunks_exactMultiple_splitsEvenly() {
		String[] in = { "a", "b", "c", "d" };
		List<String[]> chunks = SpringIndexerJavaParserUtils.createChunks(in, 2);
		assertEquals(2, chunks.size());
		assertArrayEquals(new String[] { "a", "b" }, chunks.get(0));
		assertArrayEquals(new String[] { "c", "d" }, chunks.get(1));
	}

	@Test
	void createChunks_remainderInLastChunk() {
		String[] in = { "a", "b", "c" };
		List<String[]> chunks = SpringIndexerJavaParserUtils.createChunks(in, 2);
		assertEquals(2, chunks.size());
		assertArrayEquals(new String[] { "a", "b" }, chunks.get(0));
		assertArrayEquals(new String[] { "c" }, chunks.get(1));
	}

	@Test
	void createChunks_chunkSizeOne_oneElementPerChunk() {
		String[] in = { "x", "y" };
		List<String[]> chunks = SpringIndexerJavaParserUtils.createChunks(in, 1);
		assertEquals(2, chunks.size());
		assertArrayEquals(new String[] { "x" }, chunks.get(0));
		assertArrayEquals(new String[] { "y" }, chunks.get(1));
	}

	@Test
	void getComplianceJavaVersion_null_defaultsToJavaCore25() {
		assertEquals(JavaCore.VERSION_25, SpringIndexerJavaParserUtils.getComplianceJavaVersion(null));
	}

	@Test
	void getComplianceJavaVersion_blankDefaultsToJavaCore25() {
		assertEquals(JavaCore.VERSION_25, SpringIndexerJavaParserUtils.getComplianceJavaVersion("   "));
	}

	@Test
	void getComplianceJavaVersion_legacyJdkStyle_returnsMajorMinor() {
		assertEquals("1.8", SpringIndexerJavaParserUtils.getComplianceJavaVersion("1.8.0_292"));
	}

	@Test
	void getComplianceJavaVersion_modernJdkStripsBuildMetadata() {
		assertEquals("17", SpringIndexerJavaParserUtils.getComplianceJavaVersion("17.0.12+7"));
	}

	@Test
	void getComplianceJavaVersion_modernJdkWithoutPlus() {
		assertEquals("21", SpringIndexerJavaParserUtils.getComplianceJavaVersion("21.0.1"));
	}
}
