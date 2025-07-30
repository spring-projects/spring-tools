/*******************************************************************************
 * Copyright (c) 2023 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.rewrite.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.text.PlainText;
import org.openrewrite.yaml.tree.Yaml;
import org.springframework.ide.vscode.commons.gradle.GradleCore;
import org.springframework.ide.vscode.commons.gradle.GradleJavaProject;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.rewrite.java.ProjectParser;

public class GradleIJavaProjectParserTest {
	
	@Disabled
	@Test
	void testSingleGroovy() throws Exception {
		URL resource = getClass().getResource("/test-projects/example-gradle-groovy");
		Path testProjectPath = Paths.get(resource.toURI());
		
		IJavaProject jp = GradleJavaProject.create(null, GradleCore.getDefault(), testProjectPath.resolve("build.gradle").toFile(), null);
		
		GradleIJavaProjectParser parser = new GradleIJavaProjectParser(jp, JavaParser.fromJavaVersion(), null);
		
		List<SourceFile> sources = parser.parse(Paths.get(jp.getLocationUri()), new InMemoryExecutionContext(t -> {
			throw new RuntimeException(t);
		}));
		
		assertEquals(9, sources.size());
		
		// java sources
		assertEquals(2, sources.stream().filter(J.CompilationUnit.class::isInstance).collect(Collectors.toList()).size());
		
		// java sources from main
		assertEquals(1, sources.stream().filter(JavaSourceFile.class::isInstance).map(JavaSourceFile.class::cast).filter(j -> {
			String sourceSetName = j.getMarkers().findFirst(JavaSourceSet.class).map(js -> js.getName()).orElse(null);
			return ProjectParser.MAIN.equals(sourceSetName);
		}).collect(Collectors.toList()).size());

		// java sources from test
		assertEquals(1, sources.stream().filter(JavaSourceFile.class::isInstance).map(JavaSourceFile.class::cast).filter(j -> {
			String sourceSetName = j.getMarkers().findFirst(JavaSourceSet.class).map(js -> js.getName()).orElse(null);
			return ProjectParser.TEST.equals(sourceSetName);
		}).collect(Collectors.toList()).size());
		
		// properties files
		assertEquals(1, sources.stream().filter(Properties.File.class::isInstance).collect(Collectors.toList()).size());
		
		// application properties
		assertEquals(1, sources.stream().filter(Properties.File.class::isInstance).filter(f -> f.getSourcePath().getFileName().toString().equals("application.properties")).collect(Collectors.toList()).size());

		// yaml files
		assertEquals(2, sources.stream().filter(Yaml.Documents.class::isInstance).collect(Collectors.toList()).size());
		
		// application yaml
		assertEquals(2, sources.stream().filter(Yaml.Documents.class::isInstance).filter(f -> f.getSourcePath().getFileName().toString().equals("application.yml")).collect(Collectors.toList()).size());
		
		// plain text files
		assertEquals(3, sources.stream().filter(PlainText.class::isInstance).collect(Collectors.toList()).size());

		// .factories files
		assertEquals(3, sources.stream().filter(PlainText.class::isInstance).filter(f -> f.getSourcePath().getFileName().toString().endsWith(".factories")).collect(Collectors.toList()).size());


	}

}
