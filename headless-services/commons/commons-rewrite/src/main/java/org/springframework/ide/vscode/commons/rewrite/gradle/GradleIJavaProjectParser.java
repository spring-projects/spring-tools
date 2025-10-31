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

import static org.openrewrite.Tree.randomId;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser.Input;
import org.openrewrite.SourceFile;
import org.openrewrite.gradle.GradleParser;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.gradle.toolingapi.OpenRewriteModel;
import org.openrewrite.gradle.toolingapi.OpenRewriteModelBuilder;
import org.openrewrite.groovy.GroovyParser;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaParser.Builder;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.marker.BuildTool;
import org.openrewrite.marker.Marker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.rewrite.java.AbstractJavaProjectParser;

public class GradleIJavaProjectParser extends AbstractJavaProjectParser {

	private static final Logger log = LoggerFactory.getLogger(GradleIJavaProjectParser.class);

	public GradleIJavaProjectParser(IJavaProject jp, Builder<?, ?> javaParserBuilder,
			Function<Path, Input> parserInputProvider) {
		super(jp, javaParserBuilder, parserInputProvider);
	}

	@Override
	protected List<Marker> getJavaProvenance(SourceFile buildFileAst, Path projectDirectory) {
		Optional<GradleProject> gradleProject =  buildFileAst.getMarkers().findFirst(GradleProject.class);
		
        String javaRuntimeVersion = System.getProperty("java.runtime.version");
        String javaVendor = System.getProperty("java.vm.vendor");
        String sourceCompatibility = javaRuntimeVersion;
        String targetCompatibility = javaRuntimeVersion;

        return Arrays.asList(
                new BuildTool(randomId(), BuildTool.Type.Gradle, null),
                new JavaVersion(randomId(), javaRuntimeVersion, javaVendor, sourceCompatibility, targetCompatibility),
                new JavaProject(randomId(), gradleProject.map(g -> g.getName()).orElse(null), null/*new JavaProject.Publication(
                        mavenModel.getGroupId(),
                        mavenModel.getArtifactId(),
                        mavenModel.getVersion()*/
                )
        );
	}

	@Override
	public List<G.CompilationUnit> parseBuildFiles(Path projectDir, ExecutionContext ctx) {
		try {
			OpenRewriteModel openRewriteGradleModel = OpenRewriteModelBuilder.forProjectDirectory(projectDir.toFile(), Paths.get(jp.getProjectBuild().getBuildFile()).toFile());
			GradleProject gradleProject =openRewriteGradleModel.getGradleProject();
			GradleParser gradleParser = GradleParser.builder().groovyParser(GroovyParser.builder()).build();
			
			Path buildFilePath = Paths.get(jp.getProjectBuild().getBuildFile());
			
			List<G.CompilationUnit> gradleFiles = gradleParser.parseInputs(() -> 
				getInputs(Stream.of(buildFilePath)).iterator(), null, ctx).map(G.CompilationUnit.class::cast).collect(Collectors.toList());
			return ListUtils.map(
				gradleFiles, (i, gb) -> gb.withMarkers(gb.getMarkers().addIfAbsent(gradleProject))
			);
		} catch (IOException e) {
			log.error("", e);
			return List.of();
		}
	}

}
