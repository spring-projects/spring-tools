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
package org.springframework.ide.vscode.boot.java.lombok;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.Location;
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
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.InjectionPoint;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * The injection points of components whose constructors Lombok generates.
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class LombokInjectionPointsTest {

	private static final String PROJECT_NAME = "test-lombok-indexing";

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
		directory = new File(ProjectsHarness.class.getResource("/test-projects/" + PROJECT_NAME + "/").toURI());
	}

	private void index(String lombokSettings) throws Exception {
		if (lombokSettings != null) {
			harness.changeConfiguration("{\"boot-java\": {\"lombok\": " + lombokSettings + "}}");
		}
		projectFinder.find(new TextDocumentIdentifier(directory.toURI().toString())).get();
		indexer.waitOperation().get(30, TimeUnit.SECONDS);
	}

	@Test
	void disabledByDefault() throws Exception {
		index(null);

		assertEquals(List.of(), names(injectionPointsOf("requiredArgsPublisher")));
		assertEquals(List.of(), names(injectionPointsOf("dataPublisher")));
	}

	@Test
	void requiredArgsConstructor() throws Exception {
		index("{\"on\": true}");

		InjectionPoint[] injectionPoints = injectionPointsOf("requiredArgsPublisher");
		assertEquals(List.of("publisher", "dependency"), names(injectionPoints));
		assertEquals("org.springframework.context.ApplicationEventPublisher", injectionPoints[0].getType());
		assertEquals("com.example.lombok.SomeDependency", injectionPoints[1].getType());

		// located at the field the parameter is generated for
		String docUri = directory.toPath().resolve("src/main/java/com/example/lombok/RequiredArgsPublisher.java").toUri().toString();
		assertEquals(new Location(docUri, new Range(new Position(14, 41), new Position(14, 50))), injectionPoints[0].getLocation());

		// @Qualifier is not copied onto the parameter unless configured to
		assertEquals(0, injectionPoints[0].getAnnotations().length);
		assertEquals(0, injectionPoints[1].getAnnotations().length);
	}

	@Test
	void copySpringAnnotations() throws Exception {
		index("{\"on\": true, \"copy-spring-annotations\": true}");

		InjectionPoint[] injectionPoints = injectionPointsOf("requiredArgsPublisher");
		AnnotationMetadata[] annotations = injectionPoints[1].getAnnotations();
		assertEquals(1, annotations.length);
		assertEquals("org.springframework.beans.factory.annotation.Qualifier", annotations[0].getAnnotationType());
		assertEquals("special", annotations[0].getAttributes().get("value")[0].getName());

		assertEquals(0, injectionPoints[0].getAnnotations().length);
	}

	@Test
	void fieldDefaultsFinal() throws Exception {
		index("{\"on\": true, \"field-defaults-final\": true}");

		// the field that is neither final nor initialized becomes final, and so a parameter
		assertEquals(List.of("publisher", "dependency", "notInjected"), names(injectionPointsOf("requiredArgsPublisher")));
	}

	@Test
	void otherGeneratedConstructors() throws Exception {
		index("{\"on\": true}");

		assertEquals(List.of("publisher"), names(injectionPointsOf("allArgsPublisher")));
		assertEquals(List.of("publisher"), names(injectionPointsOf("dataPublisher")));
		assertEquals(List.of("publisher"), names(injectionPointsOf("fieldDefaultsPublisher")));
		assertEquals(List.of("publisher"), names(injectionPointsOf("valuePublisher")));
		assertEquals(List.of("publisher"), names(injectionPointsOf("autowiredConstructorPublisher")));

		InjectionPoint[] nonNull = injectionPointsOf("nonNullComponent");
		assertEquals(List.of("dependency"), names(nonNull));
		assertEquals("lombok.NonNull", nonNull[0].getAnnotations()[0].getAnnotationType());
	}

	@Test
	void noGeneratedConstructorInjects() throws Exception {
		index("{\"on\": true}");

		// two constructors, none autowired
		assertEquals(List.of(), names(injectionPointsOf("twoConstructorsComponent")));

		// the explicit constructor keeps @Data from generating one
		assertEquals(List.of("dependency"), names(injectionPointsOf("dataWithExplicitConstructor")));
	}

	private InjectionPoint[] injectionPointsOf(String beanName) {
		Bean[] beans = springIndex.getBeansWithName(PROJECT_NAME, beanName);
		assertEquals(1, beans.length, beanName);
		return beans[0].getInjectionPoints();
	}

	private static List<String> names(InjectionPoint[] injectionPoints) {
		return Arrays.stream(injectionPoints).map(InjectionPoint::getName).toList();
	}

}
