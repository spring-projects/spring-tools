/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.app.RestTemplateFactory.HostExclusions;

public class RestTemplateFactoryTest {
	
	@Test
	void testHostExclusionSet() {
		HostExclusions exclusions = new RestTemplateFactory.HostExclusions(List.of("my-host", "foo-*", "*.bar"));
		assertEquals(exclusions.hosts(), Set.of("my-host"));
		assertEquals(exclusions.regexs().stream().map(r -> r.pattern()).collect(Collectors.toList()), List.of("foo-.*", ".*\\.bar"));
		
		assertTrue(exclusions.contains("my-host"));
		assertFalse(exclusions.contains("my_host"));
		
		assertTrue(exclusions.contains("foo-bar"));
		assertTrue(exclusions.contains("foo-bar-baz"));
		assertFalse(exclusions.contains("bar-foo"));
		
		assertTrue(exclusions.contains("foo.bar"));
		assertTrue(exclusions.contains(".bar"));
		assertFalse(exclusions.contains("foo.bar.baz"));
		assertFalse(exclusions.contains("bar.foo"));

		exclusions = new RestTemplateFactory.HostExclusions(List.of("*"));
		assertTrue(exclusions.contains("my_host"));
		assertTrue(exclusions.contains("foo.bar.baz"));
		assertTrue(exclusions.contains("bar.foo"));

	}

}
