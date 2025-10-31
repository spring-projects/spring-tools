/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.openrewrite.java.spring;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.openrewrite.maven.MavenDownloadingExceptions;
import org.openrewrite.maven.MavenParser;
import org.openrewrite.maven.cache.LocalMavenArtifactCache;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.maven.tree.ResolvedDependency;
import org.openrewrite.maven.tree.Scope;
import org.openrewrite.maven.utilities.MavenArtifactDownloader;
import org.openrewrite.xml.tree.Xml;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

public class Jars {
	
    private static List<Path> getDependencyJarsForClasspath(String pom) throws MavenDownloadingExceptions {
        Xml.Document doc = (Xml.Document) MavenParser.builder().build().parse(pom).collect(Collectors.toList()).get(0);
        MavenResolutionResult resolutionResult = doc.getMarkers().findFirst(MavenResolutionResult.class).orElseThrow(() -> new IllegalStateException());
        // As of 8.52.x the resolution result has all dependency poms resolved
//        resolutionResult = resolutionResult.resolveDependencies(new MavenPomDownloader(Collections.emptyMap(), new InMemoryExecutionContext(), null, null), new InMemoryExecutionContext());
        List<ResolvedDependency> deps = resolutionResult.getDependencies().get(Scope.Compile);
        MavenArtifactDownloader downloader = new MavenArtifactDownloader(new LocalMavenArtifactCache(Paths.get(System.getProperty("user.home"), ".m2", "repository")), null, (t) -> {});
        return deps.stream().filter(d -> "jar".equals(d.getType())).map(downloader::downloadArtifact).filter(Objects::nonNull).collect(Collectors.toList());
    }

	
    public static final Supplier<List<Path>> BOOT_2_7 = Suppliers.memoize(() -> {
        try {
            return getDependencyJarsForClasspath(
                    //language=xml
                    """
                    <project>
                        <groupId>com.example</groupId>
                        <artifactId>demo</artifactId>
                        <version>0.0.1-SNAPSHOT</version>
                        <dependencyManagement>
                            <dependencies>
                                <dependency>
                                    <groupId>org.springframework.boot</groupId>
                                    <artifactId>spring-boot-dependencies</artifactId>
                                    <version>2.7.18</version>
                                    <type>pom</type>
                                    <scope>import</scope>
                                </dependency>
                            </dependencies>
                        </dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-starter</artifactId>
                            </dependency>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-starter-web</artifactId>
                            </dependency>
                            <dependency>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-starter-security</artifactId>
                            </dependency>
                            <dependency>
                                <groupId>org.springframework.security</groupId>
                                <artifactId>spring-security-ldap</artifactId>
                            </dependency>
                            <dependency>
							    <groupId>org.springframework.boot</groupId>
							    <artifactId>spring-boot-starter-test</artifactId>
                            </dependency>
							<dependency>
							    <groupId>org.springframework.batch</groupId>
							    <artifactId>spring-batch-test</artifactId>
							</dependency>
                        </dependencies>
                    </project>
                    """
            );
        } catch (MavenDownloadingExceptions e) {
            throw new IllegalStateException(e);
        }
    });

}
