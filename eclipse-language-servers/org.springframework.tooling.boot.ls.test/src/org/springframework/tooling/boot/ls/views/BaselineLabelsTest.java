/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.boot.ls.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * @author Martin Lippert
 */
public class BaselineLabelsTest {

	@Test
	public void shortensACommitSha() {
		assertEquals("0123456", BaselineLabels.shortSha("0123456789abcdef"));
		assertEquals("abc", BaselineLabels.shortSha("abc"));
	}

	@Test
	public void describesASnapshotOfACommitByItsShaAndMessage() {
		assertEquals("0123456 - fix the thing", BaselineLabels.describe("0123456789abcdef", "fix the thing", "2026-01-02T03:04:05Z"));
		assertEquals("0123456 - (no commit message)", BaselineLabels.describe("0123456789abcdef", null, "2026-01-02T03:04:05Z"));
		assertEquals("0123456 - (no commit message)", BaselineLabels.describe("0123456789abcdef", "", null));
	}

	@Test
	public void describesAManualSnapshotByWhenItWasCaptured() {
		String description = BaselineLabels.describe(null, null, "2026-01-02T03:04:05Z");

		assertTrue(description, description.startsWith("manual snapshot from "));
		assertEquals("manual snapshot from " + BaselineLabels.formatCapturedAt("2026-01-02T03:04:05Z"), description);
		assertEquals("manual snapshot from an unknown time", BaselineLabels.describe("", null, null));
	}

	@Test
	public void showsATimeThatCannotBeReadAsItIs() {
		assertEquals("yesterday", BaselineLabels.formatCapturedAt("yesterday"));
	}

	@Test
	public void readsTimesWithAndWithoutAnOffset() {
		assertEquals(BaselineLabels.formatCapturedAt("2026-01-02T03:04:05Z"), BaselineLabels.formatCapturedAt("2026-01-02T03:04:05+00:00"));
	}

	@Test
	public void theClassOfAMemberIsFoundInItsBindingKey() {
		assertEquals("Lcom/example/Foo;", JavaElementOpener.classKeyOf("Lcom/example/Foo;.bar()V"));
		assertEquals("Lcom/example/Foo;", JavaElementOpener.classKeyOf("Lcom/example/Foo;"));
		assertEquals("", JavaElementOpener.classKeyOf("not a binding key"));
	}

}
