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
package org.springframework.ide.vscode.boot.validation.generations;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.maven.MavenParser;
import org.openrewrite.maven.MavenSettings;
import org.openrewrite.maven.internal.MavenPomDownloader;
import org.openrewrite.maven.tree.GroupArtifact;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.util.FileObserver;

public class MavenMetadataProvider {

	private static final Logger log = LoggerFactory.getLogger(MavenMetadataProvider.class);

	private final Map<String, MavenMetadata> cache = new ConcurrentHashMap<>();

	public MavenMetadataProvider(FileObserver fileObserver) {
		if (fileObserver != null) {
			fileObserver.onAnyChange(List.of("**/pom.xml"), files -> {
				for (String file : files) {
					Path pom = Paths.get(URI.create(file));
					cache.keySet().removeIf(key -> key.startsWith(pom.toString()));
				}
			});
		}
	}

	private MavenMetadata compute(Path pomPath, String groupId, String artifactId) {
		try {
			if (!Files.exists(pomPath)) {
				return null;
			}

			ExecutionContext ctx = new InMemoryExecutionContext();
			MavenExecutionContextView mvnCtx = MavenExecutionContextView.view(ctx);
			MavenSettings settings = mvnCtx.getSettings();
			if (settings == null) {
				mvnCtx.setMavenSettings(MavenSettings.readMavenSettingsFromDisk(ctx));
			}

			MavenParser parser = MavenParser.builder().skipDependencyResolution(true).build();

			List<SourceFile> parsed = parser.parse(Collections.singletonList(pomPath), pomPath.getParent(), ctx)
					.collect(Collectors.toList());

			if (parsed.isEmpty()) {
				return null;
			}

			MavenResolutionResult mrr = parsed.get(0).getMarkers().findFirst(MavenResolutionResult.class).orElse(null);
			if (mrr == null) {
				return null;
			}

			MavenExecutionContextView mctx = MavenExecutionContextView.view(ctx);
			Optional<MavenSettings> maybeSettings = Optional.ofNullable(mctx.effectiveSettings(mrr));
			List<String> activeProfiles = maybeSettings.map(MavenSettings::getActiveProfiles)
					.map(MavenSettings.ActiveProfiles::getActiveProfiles).orElse(null);
			org.openrewrite.maven.tree.MavenMetadata rawMetadata = new MavenPomDownloader(mrr.getProjectPoms(), ctx, maybeSettings.orElse(null),
					activeProfiles)
					.downloadMetadata(new GroupArtifact(groupId, artifactId), null, mrr.getPom().getRepositories());

			if (rawMetadata != null) {
				return new MavenMetadata(rawMetadata);
			}
			return null;

		} catch (Exception e) {
			log.warn("Failed to fetch Maven metadata for {}:{} in pom {}", groupId, artifactId, pomPath, e);
			return null;
		}
	}

	public MavenMetadata getMetadata(IJavaProject javaProject, String groupId, String artifactId) {
		URI buildFileUri = javaProject.getProjectBuild() == null ? null : javaProject.getProjectBuild().getBuildFile();
		if (buildFileUri == null || !buildFileUri.getPath().endsWith("pom.xml")) {
			return null;
		}
		Path pom = Paths.get(buildFileUri);
		return cache.computeIfAbsent(getKey(pom, groupId, artifactId), k -> compute(pom, groupId, artifactId));
	}
	
	private String getKey(Path pom, String groupId, String artifactId) {
		return pom.toString() + "|" + groupId + ":" + artifactId;
	}

}
