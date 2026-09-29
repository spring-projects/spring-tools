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
package org.springframework.ide.vscode.boot.java.beans;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ide.vscode.boot.java.stereotypes.JarFixtureBuilder;
import org.springframework.ide.vscode.boot.java.stereotypes.JarTypes;
import org.springframework.stereotype.Component;

/**
 * Which classes of a JAR get a bean - {@code ComponentIndexer.index}'s decisions.
 *
 * @author Martin Lippert
 */
public class JarBeanIndexerTest {

	@TempDir
	Path tempDir;

	@Test
	void aJavaxNamedClassIsAComponent() throws Exception {
		File jar = JarFixtureBuilder.buildJar(tempDir, "fixture", Map.of(
				"javax.inject.Named", "package javax.inject; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface Named { String value() default \"\"; }",
				"com.example.Named1", "package com.example; @javax.inject.Named public class Named1 {}"), Map.of());

		assertEquals(1, JarBeanIndexer.beansOf(JarTypes.scan(jar).get("com.example.Named1")).size());
	}

	/**
	 * The source side never indexes an enum declaration as a bean, whatever its annotations.
	 */
	@Test
	void anEnumIsNoBean() throws Exception {
		File jar = JarFixtureBuilder.buildJar(tempDir, "fixture", Map.of(
				"com.example.Kind", "package com.example; @org.springframework.stereotype.Component public enum Kind { A }"), Map.of());

		assertEquals(List.of(), JarBeanIndexer.beansOf(JarTypes.scan(jar, List.of(jarOf(Component.class))).get("com.example.Kind")));
	}

	private static File jarOf(Class<?> type) throws Exception {
		return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI());
	}

}
