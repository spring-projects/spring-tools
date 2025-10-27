/*******************************************************************************
 * Copyright (c) 2017, 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.spel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class SpelDefinitionProviderTest {

	@Autowired
	private BootLanguageServerHarness harness;
	@Autowired
	private JavaProjectFinder projectFinder;
	@Autowired
	private SpringMetamodelIndex springIndex;
	@Autowired
	private SpringSymbolIndex indexer;

	private File directory;
	private IJavaProject project;

	private Bean visitService;
	private Bean spelExpressionsClass;
	private String expectedDefinitionUriVisitService;
	private String expectedDefinitionUriSpelClass;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-indexing/").toURI());

		String projectDir = directory.toURI().toString();
		project = projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		expectedDefinitionUriVisitService = directory.toPath().resolve("src/main/java/org/test/VisitService.java").toUri().toString();
		expectedDefinitionUriSpelClass = directory.toPath().resolve("src/main/java/org/test/SpelExpressionsClass.java").toUri().toString();
		visitService = new Bean("visitService", "org.test.VisitService",
				new Location(expectedDefinitionUriVisitService, new Range(new Position(4, 0), new Position(4, 8))),
				null, null, null, false, "symbolLabel");
		spelExpressionsClass = new Bean("spelExpressionsClass", "org.test.SpelExpressionsClass",
				new Location(expectedDefinitionUriSpelClass, new Range(new Position(7, 0), new Position(7, 11))), null,
				null, null, false, "symbolLabel");

		springIndex.updateBeans(project.getElementName(), new Bean[] { visitService, spelExpressionsClass });

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);

	}

	@Test
	public void testBeanDefinitionLinkInSpel() throws Exception {
		String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

				import org.springframework.beans.factory.annotation.Value;
				import org.springframework.stereotype.Controller;
				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.ResponseBody;

				@Controller
				public class SpelExpressionsClass {

					@Value("${app.version}")
					private String appVersion;

					@Value(value = "#{@visitService.isValidVersion('${app.version}') ? 'Valid Version' :'Invalid Version'}")
					private String versionValidity;
				}""", tempJavaDocUri);

		Bean[] beans = springIndex.getBeansWithName(project.getElementName(), "visitService");
		assertEquals(1, beans.length);

		LocationLink expectedLocation = new LocationLink(expectedDefinitionUriVisitService,
				beans[0].getLocation().getRange(), beans[0].getLocation().getRange(), null);

		editor.assertDefinitionLinkTargets("visitService", List.of(expectedLocation));
	}

	@Test
	public void testMultipleBeanDefinitionLinksInSpel() throws Exception {

		String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

		Editor editor = harness.newEditor(LanguageId.JAVA,
				"""
						package org.test;

						import org.springframework.beans.factory.annotation.Value;
						import org.springframework.stereotype.Controller;
						import org.springframework.web.bind.annotation.GetMapping;
						import org.springframework.web.bind.annotation.ResponseBody;

						@Controller
						public class SpelExpressionsClass {

							@Value("${app.version}")
							private String appVersion;

							@Value("#{@visitService.isValidVersion('${app.version}') ? @spelExpressionsClass.toUpperCase('valid') :@spelExpressionsClass.text2('invalid version')}")
							private String fetchVersion;
						}""",
				tempJavaDocUri);

		Bean[] visitServiceBean = springIndex.getBeansWithName(project.getElementName(), "visitService");
		assertEquals(1, visitServiceBean.length);

		Bean[] spelExpBean = springIndex.getBeansWithName(project.getElementName(), "spelExpressionsClass");
		assertEquals(1, spelExpBean.length);

		LocationLink expectedLocation1 = new LocationLink(expectedDefinitionUriVisitService,
				visitServiceBean[0].getLocation().getRange(), visitServiceBean[0].getLocation().getRange(), null);

		LocationLink expectedLocation2 = new LocationLink(expectedDefinitionUriSpelClass,
				spelExpBean[0].getLocation().getRange(), spelExpBean[0].getLocation().getRange(), null);

		editor.assertDefinitionLinkTargets("visitService", List.of(expectedLocation1));
		editor.assertDefinitionLinkTargets("spelExpressionsClass", List.of(expectedLocation2));

	}
	
	@Test
	public void testMultipleBeanDefinitionLinksWithTypeRefInSpel() throws Exception {

		String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

		Editor editor = harness.newEditor(LanguageId.JAVA,
				"""
						package org.test;

						import org.springframework.beans.factory.annotation.Value;
						import org.springframework.stereotype.Controller;
						import org.springframework.web.bind.annotation.GetMapping;
						import org.springframework.web.bind.annotation.ResponseBody;

						@Controller
						public class SpelExpressionsClass {

							@Value("#{T(org.test.SpelExpressionClass).toUpperCase('hello') + ' ' + @spelExpressionsClass.concat('world', '!')}")
							private String greeting;
						}""",
				tempJavaDocUri);

		Bean[] spelExpBean = springIndex.getBeansWithName(project.getElementName(), "spelExpressionsClass");
		assertEquals(1, spelExpBean.length);

		LocationLink expectedLocation2 = new LocationLink(expectedDefinitionUriSpelClass,
				spelExpBean[0].getLocation().getRange(), spelExpBean[0].getLocation().getRange(), null);

		editor.assertDefinitionLinkTargets("spelExpressionsClass", List.of(expectedLocation2));

	}
	
	@Test
	public void testMethodDefinitionLinkInSpel() throws Exception {
		String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

				import org.springframework.beans.factory.annotation.Value;
				import org.springframework.stereotype.Controller;
				import org.springframework.web.bind.annotation.GetMapping;
				import org.springframework.web.bind.annotation.ResponseBody;

				@Controller
				public class SpelExpressionsClass {

					@Value("${app.version}")
					private String appVersion;

					@Value(value = "#{@visitService.isValidVersion('${app.version}') ? 'Valid Version' :'Invalid Version'}")
					private String versionValidity;
					
					@Value("#{T(org.test.SpelExpressionClass).toUpperCase('hello') + ' ' + @spelExpressionsClass.concat('world', '!')}")
					private String greeting;
					
					public static boolean isValidVersion(String version) {
						if (version.matches("\\d+\\.\\d+\\.\\d+")) {
							String[] parts = version.split("\\.");
							int major = Integer.parseInt(parts[0]);
							int minor = Integer.parseInt(parts[1]);
							int patch = Integer.parseInt(parts[2]);
							return (major > 3) || (major == 3 && (minor > 0 || (minor == 0 && patch >= 0)));
						}
						return false;
					}
				
					public static String toUpperCase(String input) {
						return input.toUpperCase();
					}
				
					public static String concat(String str1, String str2) {
						return str1 + str2;
					}
				}""", tempJavaDocUri);

		LocationLink expectedLocation1 = new LocationLink(expectedDefinitionUriVisitService,
				new Range(new Position(7, 23), new Position(7, 37)), new Range(new Position(7, 23), new Position(7, 37)), null);
		LocationLink expectedLocation2 = new LocationLink(expectedDefinitionUriSpelClass,
				new Range(new Position(37, 22), new Position(37, 28)), new Range(new Position(37, 22), new Position(37, 28)), null);

		editor.assertDefinitionLinkTargets("isValidVersion", List.of(expectedLocation1));
		editor.assertDefinitionLinkTargets("concat", List.of(expectedLocation2));
	}

}
