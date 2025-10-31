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
package org.springframework.ide.vscode.commons.rewrite.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.maven.MavenParser;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.text.PlainText;
import org.openrewrite.yaml.tree.Yaml;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.javadoc.JavaDocProviders;
import org.springframework.ide.vscode.commons.maven.MavenBuilder;
import org.springframework.ide.vscode.commons.maven.MavenCore;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.rewrite.java.ProjectParser;

public class MavenIJavaProjectParserTest {
	
	private static IJavaProject createProject(String name) throws Exception {
		Path projectPath = Paths.get(MavenIJavaProjectParserTest.class.getResource("/test-projects/" + name).toURI());

		MavenBuilder.newBuilder(projectPath).clean().pack().skipTests().execute();
		return MavenJavaProject.create(null, MavenCore.getDefault(),
				projectPath.resolve(MavenCore.POM_XML).toFile(), (uri, cpe) -> JavaDocProviders.createFor(cpe));
	}

	
	@Test
	void parseResources() throws Exception {
        IJavaProject jp = createProject("test-maven-project-parser");
		Path testProjectPath = Paths.get(jp.getLocationUri());

        MavenParser.Builder mavenParserBuilder = MavenParser.builder()
                /*.mavenConfig(testProjectPath.resolve(".mvn/maven.config"))*/;
        
        MavenIJavaProjectParser parser = new MavenIJavaProjectParser(jp, JavaParser.fromJavaVersion(), null, mavenParserBuilder);
		
		List<SourceFile> sources = parser.parse(testProjectPath, new InMemoryExecutionContext(t -> {
			if (t instanceof Error) {
				throw new RuntimeException(t);
			};
		}));
		
		assertEquals(13, sources.size());
		
		// java sources
		assertEquals(6, sources.stream().filter(JavaSourceFile.class::isInstance).collect(Collectors.toList()).size());
		
		// java sources from main
		assertEquals(5, sources.stream().filter(JavaSourceFile.class::isInstance).map(JavaSourceFile.class::cast).filter(j -> {
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
