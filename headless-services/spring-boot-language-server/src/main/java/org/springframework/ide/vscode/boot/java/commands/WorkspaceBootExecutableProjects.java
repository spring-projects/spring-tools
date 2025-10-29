/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Gav;
import org.springframework.ide.vscode.commons.protocol.java.ProjectGavParams;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.BeansParams;

import com.google.gson.JsonElement;

public class WorkspaceBootExecutableProjects {
	
	public record ExecutableProject(String name, String uri, String gav, String mainClass, Collection<String> classpath) {}
	
	public record BootProjectInfo(String name, String uri, String mainClass, String buildTool, String springBootVersion, String javaVersion) {}
	
	final static String CMD = "sts/spring-boot/executableBootProjects";
	
	final static String BOOT_PROJECT_INFO_CMD = "sts/spring-boot/bootProjectInfo";
	
	private final static Logger log = LoggerFactory.getLogger(WorkspaceBootExecutableProjects.class);
	
	final private JavaProjectFinder projectFinder;
	final private SpringSymbolIndex symbolIndex;
	final private SimpleLanguageServer server;

	public WorkspaceBootExecutableProjects(SimpleLanguageServer server, JavaProjectFinder projectFinder, SpringSymbolIndex symbolIndex) {
		this.server = server;
		this.projectFinder = projectFinder;
		this.symbolIndex = symbolIndex;
		server.onCommand(CMD, params -> findExecutableProjects());
		
		server.onCommand(BOOT_PROJECT_INFO_CMD, (params) -> {
            return getBootProjectInfo(params);
        });
	}
	
	private CompletableFuture<Optional<ExecutableProject>> mapToExecProject(IJavaProject project) {
		BeansParams params = new BeansParams();
		params.setProjectName(project.getElementName());
		return symbolIndex.beans(params).thenApply(beans -> {
			List<Bean> bootAppBeans = beans.stream()
					.filter(b -> hasAnnotation(b, Annotations.BOOT_APP))
					.limit(2)
					.collect(Collectors.toList());
			if (bootAppBeans.size() == 1) {
				try {
					Bean appBean = bootAppBeans.get(0);
					Collection<String> classpath = project.getClasspath().getClasspathEntries().stream()
							.filter(cpe -> !cpe.isTest() && !cpe.isSystem())
							.map(cpe -> Classpath.isSource(cpe) ? cpe.getOutputFolder() : cpe.getPath())
							.collect(Collectors.toSet());
					return Optional.of(new ExecutableProject(project.getElementName(), project.getLocationUri().toASCIIString(), null, appBean.getType(), classpath));
				} catch (Exception e) {
					log.error("", e);
				}
			}
			return Optional.empty();
		});
	}
	
	private boolean hasAnnotation(Bean bean, String annotationType) {
		AnnotationMetadata[] annotations = bean.getAnnotations();
		for (int i = 0; i < annotations.length; i++) {
			if (annotationType.equals(annotations[i].getAnnotationType())) {
				return true;
			}
		}
		
		return false;
	}
	
	private CompletableFuture<List<ExecutableProject>> findExecutableProjects() {
		List<CompletableFuture<Optional<ExecutableProject>>> futures = projectFinder.all().stream()
				.filter(p -> SpringProjectUtil.isBootProject(p))
				.map(this::mapToExecProject)
				.collect(Collectors.toList());
		List<ExecutableProject> executableProjects = Collections.synchronizedList(new ArrayList<>());
		futures.forEach(f -> f.thenAccept(opt -> opt.ifPresent(executableProjects::add)));
		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).thenCompose(v -> {
			 final long start = System.currentTimeMillis();
			 return server.getClient().projectGAV(new ProjectGavParams(executableProjects.stream().map(p -> p.uri()).toList())).thenApply(gavs -> {
				 List<ExecutableProject> filteredExecProjects = new ArrayList<>(executableProjects.size());
				 for (int i = 0; i < executableProjects.size(); i++) {
					 ExecutableProject ep = executableProjects.get(i);
					 if (gavs.get(i) != null) {
						 Gav gav = gavs.get(i);
						 filteredExecProjects.add(new ExecutableProject(ep.name(), ep.uri(), "%s:%s:%s".formatted(gav.groupId(), gav.artifactId(), gav.version()), ep.mainClass(), ep.classpath()));
					 } else {
						 filteredExecProjects.add(ep);
					 }
				 }
				 log.info("GAV for %d projects took: %d".formatted(executableProjects.size(), System.currentTimeMillis() - start));
				 return filteredExecProjects;
			 });
		});
	}
	
	private CompletableFuture<Optional<BootProjectInfo>> mapToBootProjectInfo(IJavaProject project) {
		BeansParams params = new BeansParams();
		params.setProjectName(project.getElementName());
		return symbolIndex.beans(params).thenApply(beans -> {
			List<Bean> bootAppBeans = beans.stream()
					  .filter(b -> hasAnnotation(b, Annotations.BOOT_APP))
					  .limit(2)
					  .collect(Collectors.toList());
			if (bootAppBeans.size() > 0) {
				try {
					String appBean = bootAppBeans.get(0) != null ? bootAppBeans.get(0).getType() : null;
					String springBootVersion = SpringProjectUtil.getSpringBootVersion(project).toString();
					String buildTool = project.getProjectBuild().getType();
					String javaVersion = project.getClasspath().getJre() == null ? null : project.getClasspath().getJre().version();
					return Optional
							.of(new BootProjectInfo(project.getElementName(), project.getLocationUri().toASCIIString(),
									appBean, buildTool, springBootVersion, javaVersion));
				} catch (Exception e) {
					log.error("", e);
				}
			}
			return Optional.empty();
		});
	}

	private CompletableFuture<BootProjectInfo> getBootProjectInfo(ExecuteCommandParams params) {
		String projectUri = ((JsonElement) params.getArguments().get(0)).getAsString();
		IJavaProject project = projectFinder.find(new TextDocumentIdentifier(projectUri)).orElse(null);

		return mapToBootProjectInfo(project).thenApply(opt -> opt.orElse(null));
	}
	
}