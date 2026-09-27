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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ide.vscode.boot.java.commands.StructureMember;
import org.springframework.ide.vscode.boot.java.stereotypes.JarFixtureBuilder;
import org.springframework.ide.vscode.boot.java.stereotypes.JarStereotypeScanner;

/**
 * @author Martin Lippert
 */
public class JarConfigurationPropertiesScannerTest {

	@TempDir
	Path tempDir;

	/**
	 * Matches {@code ConfigPropertyIndexElement.getDocumentSymbol()}'s exact label shape:
	 * {@code name + " (" + shortTypeName + ")"} - and the {@code prefix} attribute, when present,
	 * over {@code value} (via the shared {@code ConfigurationPropertiesIndexer.resolvePrefix}).
	 */
	@Test
	void aRegularClassesFieldsBecomeMembersPrefixedByThePrefixAttribute() throws Exception {
		ClassInfo classInfo = scanSingleClass("com.example.Settings", """
				package com.example;
				import org.springframework.boot.context.properties.ConfigurationProperties;
				@ConfigurationProperties(prefix = "app.settings", value = "ignored.when.prefix.is.present")
				public class Settings {
					private String name;
					private int port;
				}
				""");

		List<StructureMember> members = JarConfigurationPropertiesScanner.membersOf(classInfo,
				Set.of("org.springframework.boot.context.properties.ConfigurationProperties"));

		assertEquals(List.of(
				new StructureMember("app.settings.name (String)", null, null),
				new StructureMember("app.settings.port (int)", null, null)), members);
	}

	@Test
	void fallsBackToTheValueAttributeWhenThereIsNoPrefixAttribute() throws Exception {
		ClassInfo classInfo = scanSingleClass("com.example.Settings", """
				package com.example;
				import org.springframework.boot.context.properties.ConfigurationProperties;
				@ConfigurationProperties("app.settings")
				public class Settings {
					private String name;
				}
				""");

		List<StructureMember> members = JarConfigurationPropertiesScanner.membersOf(classInfo,
				Set.of("org.springframework.boot.context.properties.ConfigurationProperties"));

		assertEquals(List.of(new StructureMember("app.settings.name (String)", null, null)), members);
	}

	@Test
	void aRecordsComponentsBecomeMembersInsteadOfItsBackingFields() throws Exception {
		ClassInfo classInfo = scanSingleClass("com.example.Settings", """
				package com.example;
				import org.springframework.boot.context.properties.ConfigurationProperties;
				@ConfigurationProperties(prefix = "app.settings")
				public record Settings(String name, int port) {}
				""");

		List<StructureMember> members = JarConfigurationPropertiesScanner.membersOf(classInfo,
				Set.of("org.springframework.boot.context.properties.ConfigurationProperties"));

		assertEquals(List.of(
				new StructureMember("app.settings.name (String)", null, null),
				new StructureMember("app.settings.port (int)", null, null)), members);
	}

	@Test
	void aClassWithoutTheAnnotationInItsAlreadyComputedAnnotationTypesContributesNoMembers() throws Exception {
		ClassInfo classInfo = scanSingleClass("com.example.PlainClass", """
				package com.example;
				public class PlainClass {
					private String name;
				}
				""");

		assertEquals(List.of(), JarConfigurationPropertiesScanner.membersOf(classInfo, Set.of()));
	}

	@Test
	void noPrefixOrValueAttributeYieldsAnUnprefixedName() throws Exception {
		ClassInfo classInfo = scanSingleClass("com.example.Settings", """
				package com.example;
				import org.springframework.boot.context.properties.ConfigurationProperties;
				@ConfigurationProperties
				public class Settings {
					private String name;
				}
				""");

		List<StructureMember> members = JarConfigurationPropertiesScanner.membersOf(classInfo,
				Set.of("org.springframework.boot.context.properties.ConfigurationProperties"));

		assertTrue(members.get(0).label().startsWith("name ("), "no prefix means the bare field name");
	}

	private ClassInfo scanSingleClass(String fqn, String source) throws Exception {
		File jar = JarFixtureBuilder.buildJar(tempDir, "fixture", Map.of(fqn, source), Map.of());

		Indexer indexer = new Indexer();
		JarStereotypeScanner.indexInto(indexer, jar);
		Index index = indexer.complete();

		return index.getClassByName(DotName.createSimple(fqn));
	}

}
