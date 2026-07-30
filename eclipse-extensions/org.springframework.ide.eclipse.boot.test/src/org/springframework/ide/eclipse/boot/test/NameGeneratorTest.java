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
package org.springframework.ide.eclipse.boot.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.springsource.ide.eclipse.commons.core.util.NameGenerator;

public class NameGeneratorTest {

	@Test public void blankNameFallsBackToPlainDemoFirst() throws Exception {
		NameGenerator generator = new NameGenerator("");
		assertEquals("demo", generator.generateNext());
		assertEquals("demo-1", generator.generateNext());
		assertEquals("demo-2", generator.generateNext());
	}

	@Test public void nullNameFallsBackToPlainDemoFirst() throws Exception {
		NameGenerator generator = new NameGenerator(null);
		assertEquals("demo", generator.generateNext());
		assertEquals("demo-1", generator.generateNext());
	}

	@Test public void whitespaceOnlyNameFallsBackToPlainDemoFirst() throws Exception {
		NameGenerator generator = new NameGenerator("   ");
		assertEquals("demo", generator.generateNext());
		assertEquals("demo-1", generator.generateNext());
	}

	@Test public void nonBlankNameIsAlwaysSuffixedWithNumber() throws Exception {
		NameGenerator generator = new NameGenerator("my-app");
		assertEquals("my-app-1", generator.generateNext());
		assertEquals("my-app-2", generator.generateNext());
	}

	@Test public void nameEndingInNumberContinuesTheSequence() throws Exception {
		NameGenerator generator = new NameGenerator("demo-5");
		assertEquals("demo-6", generator.generateNext());
		assertEquals("demo-7", generator.generateNext());
	}

}
