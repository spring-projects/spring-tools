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

/**
 * The labels asserted here come from {@link ConfigPropertyIndexElement} itself - the element the
 * AST side creates too - so they only check that the JAR side feeds it the same values.
 *
 * @author Martin Lippert
 */
public class JarConfigurationPropertiesScannerTest {

	@TempDir
	Path tempDir;

	@Test
	void aRegularClassesFieldsBecomeMembersPrefixedByThePrefixAttribute() throws Exception {
		assertEquals(List.of("app.settings.name (String)", "app.settings.port (int)"), members("com.example.Settings", """
				package com.example;
				import org.springframework.boot.context.properties.ConfigurationProperties;
				@ConfigurationProperties(prefix = "app.settings", value = "ignored.when.prefix.is.present")
				public class Settings {
					private String name;
					private int port;
				}
				"""));
	}

	@Test
	void fallsBackToTheValueAttributeWhenThereIsNoPrefixAttribute() throws Exception {
		assertEquals(List.of("app.settings.name (String)"), members("com.example.Settings", """
				package com.example;
				import org.springframework.boot.context.properties.ConfigurationProperties;
				@ConfigurationProperties("app.settings")
				public class Settings {
					private String name;
				}
				"""));
	}

	@Test
	void aRecordsComponentsBecomeMembersInsteadOfItsBackingFields() throws Exception {
		assertEquals(List.of("app.settings.name (String)", "app.settings.port (int)"), members("com.example.Settings", """
				package com.example;
				import org.springframework.boot.context.properties.ConfigurationProperties;
				@ConfigurationProperties(prefix = "app.settings")
				public record Settings(String name, int port) {}
				"""));
	}

	/**
	 * A record component's member opens the component's backing field.
	 */
	@Test
	void aRecordsComponentsAreReferencedByTheirBackingFields() throws Exception {
		File jar = JarFixtureBuilder.buildJar(tempDir, "fixture", Map.of("com.example.Settings", """
				package com.example;
				import org.springframework.boot.context.properties.ConfigurationProperties;
				@ConfigurationProperties(prefix = "app.settings")
				public record Settings(String name, int port) {}
				"""), Map.of());

		assertEquals(List.of("Lcom/example/Settings;.name)Ljava/lang/String;", "Lcom/example/Settings;.port)I"),
				JarTypes.memberKeys(JarTypes.scan(jar).get("com.example.Settings")));
	}

	@Test
	void noPrefixOrValueAttributeYieldsAnUnprefixedName() throws Exception {
		assertEquals(List.of("name (String)"), members("com.example.Settings", """
				package com.example;
				import org.springframework.boot.context.properties.ConfigurationProperties;
				@ConfigurationProperties
				public class Settings {
					private String name;
				}
				"""));
	}

	@Test
	void aClassWithoutTheAnnotationContributesNoMembers() throws Exception {
		assertEquals(List.of(), members("com.example.PlainClass", """
				package com.example;
				public class PlainClass {
					private String name;
				}
				"""));
	}

	/**
	 * Decided from the class's own annotations, as {@code AnnotationHierarchies.isAnnotatedWith}
	 * does on the AST side: {@code @ConfigurationProperties} is not inherited by a subclass.
	 */
	@Test
	void aSubclassOfAConfigurationPropertiesClassIsNotOneItself() throws Exception {
		File jar = JarFixtureBuilder.buildJar(tempDir, "fixture", Map.of(
				"com.example.Base", """
						package com.example;
						import org.springframework.boot.context.properties.ConfigurationProperties;
						@ConfigurationProperties("base")
						public class Base { private String name; }
						""",
				"com.example.Sub", "package com.example; public class Sub extends Base { private int extra; }"), Map.of());

		assertEquals(List.of(), JarTypes.memberLabels(JarTypes.scan(jar).get("com.example.Sub")));
	}

	private List<String> members(String fqn, String source) throws Exception {
		File jar = JarFixtureBuilder.buildJar(tempDir, "fixture", Map.of(fqn, source), Map.of());
		return JarTypes.memberLabels(JarTypes.scan(jar).get(fqn));
	}

}
