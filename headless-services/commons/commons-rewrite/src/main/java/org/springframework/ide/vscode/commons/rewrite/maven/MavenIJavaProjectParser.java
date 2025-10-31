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

import static org.openrewrite.Tree.randomId;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser.Input;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaParser.Builder;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.marker.JavaVersion;
import org.openrewrite.marker.BuildTool;
import org.openrewrite.marker.Marker;
import org.openrewrite.maven.MavenParser;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedPom;
import org.openrewrite.xml.tree.Xml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.rewrite.java.AbstractJavaProjectParser;

public class MavenIJavaProjectParser extends AbstractJavaProjectParser {
	
	private static final Logger logger = LoggerFactory.getLogger(MavenIJavaProjectParser.class);

	private static final Pattern mavenWrapperVersionPattern = Pattern.compile(".*apache-maven/(.*?)/.*");

	private MavenParser.Builder mavenParserBuilder;

    public MavenIJavaProjectParser(IJavaProject jp, Builder<?, ?> javaParserBuilder,
			Function<Path, Input> parserInputProvider, MavenParser.Builder mavenParserBuilder) {
		super(jp, javaParserBuilder, parserInputProvider);
		this.mavenParserBuilder = mavenParserBuilder;
	}

	protected List<Marker> getJavaProvenance(SourceFile maven, Path projectDirectory) {
        ResolvedPom mavenModel = getModel((Xml.Document) maven);
        String javaRuntimeVersion = System.getProperty("java.runtime.version");
        String javaVendor = System.getProperty("java.vm.vendor");
        String sourceCompatibility = javaRuntimeVersion;
        String targetCompatibility = javaRuntimeVersion;
        String propertiesSourceCompatibility = mavenModel.getValue(mavenModel.getValue("maven.compiler.source"));
        if (propertiesSourceCompatibility != null) {
            sourceCompatibility = propertiesSourceCompatibility;
        }
        String propertiesTargetCompatibility = mavenModel.getValue(mavenModel.getValue("maven.compiler.target"));
        if (propertiesTargetCompatibility != null) {
            targetCompatibility = propertiesTargetCompatibility;
        }

        Path wrapperPropertiesPath = projectDirectory.resolve(".mvn/wrapper/maven-wrapper.properties");
        String mavenVersion = "3.6";
        if (Files.exists(wrapperPropertiesPath)) {
            try {
                Properties wrapperProperties = new Properties();
                wrapperProperties.load(new FileReader(wrapperPropertiesPath.toFile()));
                String distributionUrl = (String) wrapperProperties.get("distributionUrl");
                if (distributionUrl != null) {
                    Matcher wrapperVersionMatcher = mavenWrapperVersionPattern.matcher(distributionUrl);
                    if (wrapperVersionMatcher.matches()) {
                        mavenVersion = wrapperVersionMatcher.group(1);
                    }
                }
            } catch (IOException e) {
                logger.error("", e);
            }
        }

        return Arrays.asList(
                new BuildTool(randomId(), BuildTool.Type.Maven, mavenVersion),
                new JavaVersion(randomId(), javaRuntimeVersion, javaVendor, sourceCompatibility, targetCompatibility),
                new JavaProject(randomId(), mavenModel.getRequested().getName(), new JavaProject.Publication(
                        mavenModel.getGroupId(),
                        mavenModel.getArtifactId(),
                        mavenModel.getVersion()
                ))
        );
	}

	@Override
	public List<? extends SourceFile> parseBuildFiles(Path projectDir, ExecutionContext ctx) {
		MavenParser mavenParser = mavenParserBuilder.build();
		return mavenParser.parseInputs(() -> getInputs(Stream.of(Paths.get(jp.getProjectBuild().getBuildFile()))).iterator(), null, ctx).collect(Collectors.toList());
	}
	
    private static ResolvedPom getModel(Xml.Document maven) {
    	MavenResolutionResult pom = getResolvedPom(maven);
    	return pom == null ? null : pom.getPom();
    }
    
    private static MavenResolutionResult getResolvedPom(Xml.Document maven) {
    	return maven.getMarkers().findFirst(MavenResolutionResult.class).orElse(null);
    }


}
