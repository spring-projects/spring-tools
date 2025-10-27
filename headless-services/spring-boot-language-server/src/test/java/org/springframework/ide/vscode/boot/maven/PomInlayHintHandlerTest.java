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
package org.springframework.ide.vscode.boot.maven;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Date;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintLabelPart;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.validation.generations.SpringProjectsProvider;
import org.springframework.ide.vscode.boot.validation.generations.json.Generation;
import org.springframework.ide.vscode.boot.validation.generations.json.ResolvedSpringProject;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;

public class PomInlayHintHandlerTest {
	
	private ProjectsHarness projects = ProjectsHarness.INSTANCE;

	@Test
	void inlayProvided() throws Exception {
		MavenJavaProject jp =  projects.mavenProject("empty-boot-15-web-app");
		
		TextDocument doc = new TextDocument(jp.getProjectBuild().getBuildFile().toASCIIString(), LanguageId.XML, 0, Files.readString(Paths.get(jp.getProjectBuild().getBuildFile())));
		
		JavaProjectFinder projectFinder = mock(JavaProjectFinder.class);
		when(projectFinder.find(any())).thenReturn(Optional.of(jp));
		
		SimpleTextDocumentService documents = mock(SimpleTextDocumentService.class);
		when(documents.getLatestSnapshot(anyString())).thenReturn(doc);
		
		SimpleLanguageServer server = mock(SimpleLanguageServer.class);
		when(server.getTextDocumentService()).thenReturn(documents);
		
		Generation generation = mock(Generation.class);
		when(generation.getOssSupportEndDate()).thenReturn(new Date(System.currentTimeMillis() + Duration.ofDays(7).toMillis()).toString());
		when(generation.getName()).thenReturn("1.5.8");
		
		ResolvedSpringProject resolvedProject = mock(ResolvedSpringProject.class);
		when(resolvedProject.getGenerations()).thenReturn(List.of(generation));
		when(resolvedProject.getSlug()).thenReturn(SpringProjectUtil.SPRING_BOOT);
		
		SpringProjectsProvider projectProvider = mock(SpringProjectsProvider.class);
		when(projectProvider.getProject(SpringProjectUtil.SPRING_BOOT)).thenReturn(resolvedProject);
		
		PomInlayHintHandler inlayHandler = new PomInlayHintHandler(server, projectFinder, ProjectObserver.NULL, projectProvider);
		
		List<InlayHint> hints = inlayHandler.handle(doc, doc.toRange(0, doc.getLength()), mock(CancelChecker.class));
		
		assertEquals(1, hints.size());
		
		InlayHint hint = hints.get(0);
		assertEquals(new Position(27, 15), hint.getPosition());
		assertTrue(hint.getLabel().isRight());
		assertEquals(1, hint.getLabel().getRight().size());
		
		InlayHintLabelPart labelPart = hint.getLabel().getRight().get(0);
		assertEquals("Add Spring Boot Starters...", labelPart.getValue());
				
		Command cmd = labelPart.getCommand();
		assertNotNull(cmd);
		assertEquals("spring.initializr.addStarters", cmd.getCommand());
	}

	@Test
	void inlayNotProvidedOutOfOssSupport() throws Exception {
		MavenJavaProject jp =  projects.mavenProject("empty-boot-15-web-app");
		
		TextDocument doc = new TextDocument(jp.getProjectBuild().getBuildFile().toASCIIString(), LanguageId.XML, 0, Files.readString(Paths.get(jp.getProjectBuild().getBuildFile())));
		
		JavaProjectFinder projectFinder = mock(JavaProjectFinder.class);
		when(projectFinder.find(any())).thenReturn(Optional.of(jp));
		
		SimpleTextDocumentService documents = mock(SimpleTextDocumentService.class);
		when(documents.getLatestSnapshot(anyString())).thenReturn(doc);
		
		SimpleLanguageServer server = mock(SimpleLanguageServer.class);
		when(server.getTextDocumentService()).thenReturn(documents);
		
		Generation generation = mock(Generation.class);
		when(generation.getOssSupportEndDate()).thenReturn(new Date(System.currentTimeMillis() - Duration.ofDays(7).toMillis()).toString());
		when(generation.getName()).thenReturn("1.5.8");
		
		ResolvedSpringProject resolvedProject = mock(ResolvedSpringProject.class);
		when(resolvedProject.getGenerations()).thenReturn(List.of(generation));
		when(resolvedProject.getSlug()).thenReturn(SpringProjectUtil.SPRING_BOOT);
		
		SpringProjectsProvider projectProvider = mock(SpringProjectsProvider.class);
		when(projectProvider.getProject(SpringProjectUtil.SPRING_BOOT)).thenReturn(resolvedProject);
		
		PomInlayHintHandler inlayHandler = new PomInlayHintHandler(server, projectFinder, ProjectObserver.NULL, projectProvider);
		
		List<InlayHint> hints = inlayHandler.handle(doc, doc.toRange(0, doc.getLength()), mock(CancelChecker.class));
		assertEquals(0, hints.size());
	}
	
	@Test
	void upgradePatchVersionInlay() throws Exception {
		MavenJavaProject jp =  projects.mavenProject("empty-boot-15-web-app");
		
		TextDocument doc = new TextDocument(jp.getProjectBuild().getBuildFile().toASCIIString(), LanguageId.XML, 0, Files.readString(Paths.get(jp.getProjectBuild().getBuildFile())));
		
		JavaProjectFinder projectFinder = mock(JavaProjectFinder.class);
		when(projectFinder.find(any())).thenReturn(Optional.of(jp));
		
		SimpleTextDocumentService documents = mock(SimpleTextDocumentService.class);
		when(documents.getLatestSnapshot(anyString())).thenReturn(doc);
		
		SimpleLanguageServer server = mock(SimpleLanguageServer.class);
		when(server.getTextDocumentService()).thenReturn(documents);
		
		ResolvedSpringProject resolvedProject = mock(ResolvedSpringProject.class);
		when(resolvedProject.getGenerations()).thenReturn(null);
		when(resolvedProject.getSlug()).thenReturn(SpringProjectUtil.SPRING_BOOT);
		when(resolvedProject.getReleases()).thenReturn(List.of(
				Version.parse("1.5.6"),
				Version.parse("1.5.7"),
				Version.parse("1.5.8"),
				Version.parse("1.5.9"),
				Version.parse("1.5.10"),
				Version.parse("2.0.0")
		));
		
		SpringProjectsProvider projectProvider = mock(SpringProjectsProvider.class);
		when(projectProvider.getProject(SpringProjectUtil.SPRING_BOOT)).thenReturn(resolvedProject);
		
		PomInlayHintHandler inlayHandler = new PomInlayHintHandler(server, projectFinder, ProjectObserver.NULL, projectProvider);
		
		List<InlayHint> hints = inlayHandler.handle(doc, doc.toRange(0, doc.getLength()), mock(CancelChecker.class));
		assertEquals(1, hints.size());
		
		InlayHint hint = hints.get(0);
		assertEquals(new Position(17, 34), hint.getPosition());
		assertTrue(hint.getLabel().isRight());
		assertEquals(1, hint.getLabel().getRight().size());
		
		InlayHintLabelPart labelPart = hint.getLabel().getRight().get(0);
		assertEquals("Upgrade to the Latest Patch", labelPart.getValue());
				
		Command cmd = labelPart.getCommand();
		
		assertNotNull(cmd);
		assertEquals("sts/upgrade/spring-boot", cmd.getCommand());
		assertEquals(jp.getLocationUri().toASCIIString(), cmd.getArguments().get(0));
		assertEquals("1.5.10", cmd.getArguments().get(1));
	}
	
	@Test
	void noInlayHintOnEmptyVersionTag() throws Exception {
		MavenJavaProject jp =  projects.mavenProject("empty-boot-15-web-app");
		
		String docUri = jp.getProjectBuild().getBuildFile().toASCIIString();

		String pomFileContent = Files.readString(Paths.get(jp.getProjectBuild().getBuildFile()));
		pomFileContent = docUri.replace("1.5.8.RELEASE", "");

		TextDocument doc = new TextDocument(docUri, LanguageId.XML, 0, pomFileContent);
		
		JavaProjectFinder projectFinder = mock(JavaProjectFinder.class);
		when(projectFinder.find(any())).thenReturn(Optional.of(jp));
		
		SimpleTextDocumentService documents = mock(SimpleTextDocumentService.class);
		when(documents.getLatestSnapshot(anyString())).thenReturn(doc);
		
		SimpleLanguageServer server = mock(SimpleLanguageServer.class);
		when(server.getTextDocumentService()).thenReturn(documents);
		
		ResolvedSpringProject resolvedProject = mock(ResolvedSpringProject.class);
		when(resolvedProject.getGenerations()).thenReturn(null);
		when(resolvedProject.getSlug()).thenReturn(SpringProjectUtil.SPRING_BOOT);
		when(resolvedProject.getReleases()).thenReturn(List.of(
				Version.parse("1.5.6"),
				Version.parse("1.5.7"),
				Version.parse("1.5.8"),
				Version.parse("1.5.9"),
				Version.parse("1.5.10"),
				Version.parse("2.0.0")
		));
		
		SpringProjectsProvider projectProvider = mock(SpringProjectsProvider.class);
		when(projectProvider.getProject(SpringProjectUtil.SPRING_BOOT)).thenReturn(resolvedProject);
		
		PomInlayHintHandler inlayHandler = new PomInlayHintHandler(server, projectFinder, ProjectObserver.NULL, projectProvider);
		
		List<InlayHint> hints = inlayHandler.handle(doc, doc.toRange(0, doc.getLength()), mock(CancelChecker.class));
		assertEquals(0, hints.size());
	}

	@Test
	void noInlayHintOnEmptyVersionWithSpacesTag() throws Exception {
		MavenJavaProject jp =  projects.mavenProject("empty-boot-15-web-app");
		
		String docUri = jp.getProjectBuild().getBuildFile().toASCIIString();

		String pomFileContent = Files.readString(Paths.get(jp.getProjectBuild().getBuildFile()));
		pomFileContent = pomFileContent.replace("1.5.8.RELEASE", "  ");

		TextDocument doc = new TextDocument(docUri, LanguageId.XML, 0, pomFileContent);
		
		JavaProjectFinder projectFinder = mock(JavaProjectFinder.class);
		when(projectFinder.find(any())).thenReturn(Optional.of(jp));
		
		SimpleTextDocumentService documents = mock(SimpleTextDocumentService.class);
		when(documents.getLatestSnapshot(anyString())).thenReturn(doc);
		
		SimpleLanguageServer server = mock(SimpleLanguageServer.class);
		when(server.getTextDocumentService()).thenReturn(documents);
		
		ResolvedSpringProject resolvedProject = mock(ResolvedSpringProject.class);
		when(resolvedProject.getGenerations()).thenReturn(null);
		when(resolvedProject.getSlug()).thenReturn(SpringProjectUtil.SPRING_BOOT);
		when(resolvedProject.getReleases()).thenReturn(List.of(
				Version.parse("1.5.6"),
				Version.parse("1.5.7"),
				Version.parse("1.5.8"),
				Version.parse("1.5.9"),
				Version.parse("1.5.10"),
				Version.parse("2.0.0")
		));
		
		SpringProjectsProvider projectProvider = mock(SpringProjectsProvider.class);
		when(projectProvider.getProject(SpringProjectUtil.SPRING_BOOT)).thenReturn(resolvedProject);
		
		PomInlayHintHandler inlayHandler = new PomInlayHintHandler(server, projectFinder, ProjectObserver.NULL, projectProvider);
		
		List<InlayHint> hints = inlayHandler.handle(doc, doc.toRange(0, doc.getLength()), mock(CancelChecker.class));
		assertEquals(0, hints.size());
	}

	@Test
	void noInlayHintOnVersionTagWithNonParseableValue() throws Exception {
		MavenJavaProject jp =  projects.mavenProject("empty-boot-15-web-app");
		
		String docUri = jp.getProjectBuild().getBuildFile().toASCIIString();

		String pomFileContent = Files.readString(Paths.get(jp.getProjectBuild().getBuildFile()));
		pomFileContent = pomFileContent.replace("1.5.8.RELEASE", "foo");

		TextDocument doc = new TextDocument(docUri, LanguageId.XML, 0, pomFileContent);
		
		JavaProjectFinder projectFinder = mock(JavaProjectFinder.class);
		when(projectFinder.find(any())).thenReturn(Optional.of(jp));
		
		SimpleTextDocumentService documents = mock(SimpleTextDocumentService.class);
		when(documents.getLatestSnapshot(anyString())).thenReturn(doc);
		
		SimpleLanguageServer server = mock(SimpleLanguageServer.class);
		when(server.getTextDocumentService()).thenReturn(documents);
		
		ResolvedSpringProject resolvedProject = mock(ResolvedSpringProject.class);
		when(resolvedProject.getGenerations()).thenReturn(null);
		when(resolvedProject.getSlug()).thenReturn(SpringProjectUtil.SPRING_BOOT);
		when(resolvedProject.getReleases()).thenReturn(List.of(
				Version.parse("1.5.6"),
				Version.parse("1.5.7"),
				Version.parse("1.5.8"),
				Version.parse("1.5.9"),
				Version.parse("1.5.10"),
				Version.parse("2.0.0")
		));
		
		SpringProjectsProvider projectProvider = mock(SpringProjectsProvider.class);
		when(projectProvider.getProject(SpringProjectUtil.SPRING_BOOT)).thenReturn(resolvedProject);
		
		PomInlayHintHandler inlayHandler = new PomInlayHintHandler(server, projectFinder, ProjectObserver.NULL, projectProvider);
		
		List<InlayHint> hints = inlayHandler.handle(doc, doc.toRange(0, doc.getLength()), mock(CancelChecker.class));
		assertEquals(0, hints.size());
	}

	@Test
	void upgradePatchVersionInlay_AlreadyOnLatestPatch() throws Exception {
		MavenJavaProject jp =  projects.mavenProject("empty-boot-15-web-app");
		
		TextDocument doc = new TextDocument(jp.getProjectBuild().getBuildFile().toASCIIString(), LanguageId.XML, 0, Files.readString(Paths.get(jp.getProjectBuild().getBuildFile())));
		
		JavaProjectFinder projectFinder = mock(JavaProjectFinder.class);
		when(projectFinder.find(any())).thenReturn(Optional.of(jp));
		
		SimpleTextDocumentService documents = mock(SimpleTextDocumentService.class);
		when(documents.getLatestSnapshot(anyString())).thenReturn(doc);
		
		SimpleLanguageServer server = mock(SimpleLanguageServer.class);
		when(server.getTextDocumentService()).thenReturn(documents);
		
		ResolvedSpringProject resolvedProject = mock(ResolvedSpringProject.class);
		when(resolvedProject.getGenerations()).thenReturn(null);
		when(resolvedProject.getSlug()).thenReturn(SpringProjectUtil.SPRING_BOOT);
		when(resolvedProject.getReleases()).thenReturn(List.of(
				Version.parse("1.5.6"),
				Version.parse("1.5.7"),
				Version.parse("1.5.8"),
				Version.parse("2.0.0")
		));
		
		SpringProjectsProvider projectProvider = mock(SpringProjectsProvider.class);
		when(projectProvider.getProject(SpringProjectUtil.SPRING_BOOT)).thenReturn(resolvedProject);
		
		PomInlayHintHandler inlayHandler = new PomInlayHintHandler(server, projectFinder, ProjectObserver.NULL, projectProvider);
		
		List<InlayHint> hints = inlayHandler.handle(doc, doc.toRange(0, doc.getLength()), mock(CancelChecker.class));
		assertEquals(0, hints.size());		
	}

}
