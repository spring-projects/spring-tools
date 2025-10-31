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
package org.springframework.ide.vscode.commons.rewrite.java;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Parser.Input;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.marker.Marker;
import org.openrewrite.properties.PropertiesParser;
import org.openrewrite.text.PlainTextParser;
import org.openrewrite.xml.XmlParser;
import org.openrewrite.yaml.YamlParser;

public abstract class ProjectParser {
	
	public static final String MAIN = "main";
	public static final String TEST = "test";

	protected static record SourceSet(String name, Collection<Path> javaSources, Collection<Path> resources, Collection<Path> classpath) {};
	
	public List<SourceFile> parse(Path projectDir, ExecutionContext ctx) {
		List<? extends SourceFile> buildFiles = parseBuildFiles(projectDir, ctx);
		List<SourceFile> sources = new ArrayList<>();
		
		for (SourceFile buildFile : buildFiles) {
			sources.addAll(parseModule(projectDir, buildFile, ctx));
		}
		
		return sources;
	}

	protected Collection<? extends SourceFile> parseModule(Path projectDir, SourceFile buildFile, ExecutionContext ctx) {
		List<SourceFile> sources = new ArrayList<>();
		List<Marker> projectProvenance = getJavaProvenance(buildFile, projectDir);		
		sources.add(addProjectProvenance(buildFile, projectProvenance));
		
		for (SourceSet ss : getSourceSets(projectDir, buildFile)) {
			JavaParser javaParser = getJavaParserBuilder().build();

			Collection<Path> mainClasspath = ss.classpath();
			javaParser.setClasspath(mainClasspath);

			List<SourceFile> javaSources = ListUtils.map(javaParser.parseInputs(
					() -> getInputs(ss.javaSources.stream()).iterator(), null, ctx).collect(Collectors.toList()), addProvenance(projectProvenance));
			JavaSourceSet javaSourceSet = ORAstUtils.addJavaSourceSet(javaSources, ss.name(),
					mainClasspath);
			sources.addAll(javaSources);

			List<Input> resources = getInputs(ss.resources().stream()).collect(Collectors.toList());
			parseResources(resources, null, sources, projectProvenance, javaSourceSet, ctx);
		}
		
		return sources;
	}
	
    private void parseResources(List<Parser.Input> resources, Path relativeTo, List<SourceFile> sourceFiles, List<Marker> projectProvenance, JavaSourceSet sourceSet, ExecutionContext ctx) {
        List<Marker> provenance = new ArrayList<>(projectProvenance);
        provenance.add(sourceSet);

        sourceFiles.addAll(ListUtils.map(new XmlParser().parseInputs(
                resources.stream()
                        .filter(p -> p.getPath().getFileName().toString().endsWith(".xml") ||
                                p.getPath().getFileName().toString().endsWith(".wsdl") ||
                                p.getPath().getFileName().toString().endsWith(".xhtml") ||
                                p.getPath().getFileName().toString().endsWith(".xsd") ||
                                p.getPath().getFileName().toString().endsWith(".xsl") ||
                                p.getPath().getFileName().toString().endsWith(".xslt"))
                        .collect(Collectors.toList()),
                relativeTo,
                ctx
        ).collect(Collectors.toList()), addProvenance(provenance)));

        sourceFiles.addAll(ListUtils.map(new YamlParser().parseInputs(
                resources.stream()
                        .filter(p -> p.getPath().getFileName().toString().endsWith(".yml") || p.getPath().getFileName().toString().endsWith(".yaml"))
                        .collect(Collectors.toList()),
                relativeTo,
                ctx
        ).collect(Collectors.toList()), addProvenance(provenance)));

        sourceFiles.addAll(ListUtils.map(new PropertiesParser().parseInputs(
                resources.stream()
                        .filter(p -> p.getPath().getFileName().toString().endsWith(".properties"))
                        .collect(Collectors.toList()),
                relativeTo,
                ctx
        ).collect(Collectors.toList()), addProvenance(provenance)));
        
        sourceFiles.addAll(ListUtils.map(new PlainTextParser().parseInputs(
                resources.stream()
                        .filter(p -> p.getPath().getFileName().toString().endsWith(".factories"))
                        .collect(Collectors.toList()),
                relativeTo,
                ctx
        ).collect(Collectors.toList()), addProvenance(provenance)));
    }


	private <S extends SourceFile> S addProjectProvenance(S s, List<Marker> projectProvenance) {
		for (Marker marker : projectProvenance) {
			s = s.withMarkers(s.getMarkers().addIfAbsent(marker));
		}
		return s;
	}

	private <S extends SourceFile> BiFunction<Integer, S, S> addProvenance(List<Marker> projectProvenance) {
		return (i, s) -> {
			if (projectProvenance != null) {
				s = addProjectProvenance(s, projectProvenance);
			}
			return s;
		};
	}
	
	protected JavaParser.Builder<?, ?> getJavaParserBuilder() {
		return JavaParser.fromJavaVersion();
	}
	
	protected final Stream<Parser.Input> getInputs(Stream<Path> files) {
		return files.map(p -> 
				new Parser.Input(p, () -> {
					try {
						return Files.newInputStream(p);
					} catch (IOException e) {
						return new ByteArrayInputStream(new byte[0]);
					}
				})
		);
	}
	
	protected Parser.Input createParserInput(Path p) {
		return new Parser.Input(p, () -> {
			try {
				return Files.newInputStream(p);
			} catch (IOException e) {
				return new ByteArrayInputStream(new byte[0]);
			}
		});
	}

	
    abstract protected List<Marker> getJavaProvenance(SourceFile buildFileAst, Path projectDirectory);

	abstract public List<? extends SourceFile> parseBuildFiles(Path projectDir, ExecutionContext ctx);
	
	abstract protected Collection<SourceSet> getSourceSets(Path projectDir, SourceFile buildFile);
	
	
}
