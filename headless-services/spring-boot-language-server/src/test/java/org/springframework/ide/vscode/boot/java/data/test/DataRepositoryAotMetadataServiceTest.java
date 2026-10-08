/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;

import org.springframework.ide.vscode.boot.java.data.DataRepositoryAotMetadata;
import org.springframework.ide.vscode.boot.java.data.DataRepositoryAotMetadataService;
import org.springframework.ide.vscode.boot.java.data.DataRepositoryModule;
import org.springframework.ide.vscode.boot.java.data.IDataRepositoryAotMethodMetadata;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IProjectBuild;
import org.springframework.ide.vscode.commons.java.parser.JLRMethodParser.JLRMethod;
import org.springframework.ide.vscode.commons.java.parser.JLRMethodParser;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;
import org.springframework.ide.vscode.commons.util.FileObserver;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class DataRepositoryAotMetadataServiceTest {
	
	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private CompilationUnitCache cuCache;
	@Autowired private DataRepositoryAotMetadataService service;
	
	private IJavaProject testProject;

	@BeforeEach
	public void setup() throws Exception {
		testProject = ProjectsHarness.INSTANCE.mavenProject("aot-data-repositories-jpa");
		
		harness.useProject(testProject);
		harness.intialize(null);

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(testProject.getLocationUri().toASCIIString())).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}
	
	@Test
	void testBasicRepositoryAotMetadataLookup() throws Exception {
		DataRepositoryAotMetadata metadata = service.getRepositoryMetadata(testProject, "example.springdata.aot.UserRepository").orElse(null);
		assertNotNull(metadata);
		assertEquals("example.springdata.aot.UserRepository", metadata.name());
		assertEquals(DataRepositoryModule.JPA, metadata.module());
	}

	@Test
	void testRepositoryMethodsIntMetadata() throws Exception {

		DataRepositoryAotMetadata metadata = service.getRepositoryMetadata(testProject, "example.springdata.aot.UserRepository").orElse(null);
		IDataRepositoryAotMethodMetadata[] methods = metadata.methods();
		
		assertEquals(32, methods.length);
		
		IDataRepositoryAotMethodMetadata methodMetadata = Arrays.stream(methods).filter(method -> method.getName().equals("countUsersByLastnameLike")).findFirst().get();
		assertEquals("countUsersByLastnameLike", methodMetadata.getName());
		assertEquals("public abstract java.lang.Long example.springdata.aot.UserRepository.countUsersByLastnameLike(java.lang.String)", methodMetadata.getSignature());
		
		JLRMethod parsedMethodSignature = JLRMethodParser.parse(methodMetadata.getSignature());
		assertEquals("example.springdata.aot.UserRepository", parsedMethodSignature.getFQClassName());
		assertEquals("java.lang.Long", parsedMethodSignature.getReturnType());
		assertEquals("countUsersByLastnameLike", parsedMethodSignature.getMethodName());
		
		String[] parameters = parsedMethodSignature.getParameters();
		assertEquals("java.lang.String", parameters[0]);
		assertEquals(1, parameters.length);
	}
	
	@Test
	void testRepositoryMethodsMatching() throws Exception {
		DataRepositoryAotMetadata metadata = service.getRepositoryMetadata(testProject, "example.springdata.aot.UserRepository").orElse(null);
		
		URI docUri = testProject.getLocationUri().resolve("src/main/java/example/springdata/aot/UserRepository.java");
		cuCache.withCompilationUnit(testProject, docUri, cu -> {
			cu.accept(new ASTVisitor() {
				public boolean visit(MethodDeclaration node) {
					IMethodBinding binding = node.resolveBinding();
					
					IDataRepositoryAotMethodMetadata method = metadata.findMethod(binding).orElse(null);
					assertNotNull(method);
					
					if (method.getName().equals("findUserByLastnameStartingWith") && binding.getParameterTypes().length == 1) {
						assertEquals("public abstract java.util.List<example.springdata.aot.User> example.springdata.aot.UserRepository.findUserByLastnameStartingWith(java.lang.String)", method.getSignature());
					}
					else if (method.getName().equals("findUserByLastnameStartingWith") && binding.getParameterTypes().length == 2) {
						assertEquals("public abstract org.springframework.data.domain.Page<example.springdata.aot.User> example.springdata.aot.UserRepository.findUserByLastnameStartingWith(java.lang.String,org.springframework.data.domain.Pageable)", method.getSignature());
					}
					
					return true;
				}
			});
			
			return null;
		});
		
	}
	
	@Test
	void testCachingFunctionality() throws Exception {
		// First call should load and cache the metadata
		DataRepositoryAotMetadata metadata1 = service.getRepositoryMetadata(testProject, "example.springdata.aot.UserRepository").orElse(null);
		assertNotNull(metadata1);
		
		// Second call should return the same cached instance
		DataRepositoryAotMetadata metadata2 = service.getRepositoryMetadata(testProject, "example.springdata.aot.UserRepository").orElse(null);
		assertSame(metadata1, metadata2, "Should return the same cached instance");
	}
	
	@Test
	void testNegativeCaching() throws Exception {
		// First call to non-existent repository should return null and cache the negative result
		DataRepositoryAotMetadata metadata1 = service.getRepositoryMetadata(testProject, "nonexistent.Repository").orElse(null);
		assertNull(metadata1);
		
		// Second call should also return null (from cache, not file system check)
		DataRepositoryAotMetadata metadata2 = service.getRepositoryMetadata(testProject, "nonexistent.Repository").orElse(null);
		assertNull(metadata2);
	}

	/**
	 * Whether AOT generation is available (and so the code lenses over Spring Data repository methods are offered)
	 * depends on the tasks of the Gradle build: it needs the {@code processAot} task, which the IDE reports as part of
	 * the project build. The tasks change when the project is synchronized without the classpath changing, the service
	 * tells its listeners (the lenses) when the build of a project changed.
	 *
	 * @author Alex Boyko
	 */
	@Nested
	class Availability {

		private DataRepositoryAotMetadataService service;
		private int notifications;
		private ProjectObserver.Listener listener;

		@BeforeEach
		void setup() {
			ProjectObserver observer = mock(ProjectObserver.class);
			service = new DataRepositoryAotMetadataService(null, null, null, observer);
			notifications = 0;
			service.addListener(files -> notifications++);

			ArgumentCaptor<ProjectObserver.Listener> captor = ArgumentCaptor.forClass(ProjectObserver.Listener.class);
			verify(observer).addListener(captor.capture());
			listener = captor.getValue();
		}

		private static IJavaProject gradleProject(Set<String> tasks) {
			IJavaProject project = mock(IJavaProject.class);
			when(project.getLocationUri()).thenReturn(URI.create("file:///project/"));
			when(project.getProjectBuild()).thenReturn(IProjectBuild.create(ProjectBuild.GRADLE_PROJECT_TYPE, URI.create("file:///project/build.gradle"), tasks));
			return project;
		}

		@Test
		void processAotAvailability() {
			assertTrue(service.isAotGenerationAvailable(gradleProject(Set.of("build", "processAot", "aotClasses"))));
			assertFalse(service.isAotGenerationAvailable(gradleProject(Set.of("build", "classes", "bootJar"))));
			assertFalse(service.isAotGenerationAvailable(gradleProject(Set.of())));
			// unknown tasks get the benefit of the doubt
			assertTrue(service.isAotGenerationAvailable(gradleProject(null)));
		}

		@Test
		void otherProjectsAreNotAffected() {
			IJavaProject maven = mock(IJavaProject.class);
			when(maven.getProjectBuild()).thenReturn(IProjectBuild.create(ProjectBuild.MAVEN_PROJECT_TYPE, URI.create("file:///project/pom.xml")));
			IJavaProject untyped = mock(IJavaProject.class);
			when(untyped.getProjectBuild()).thenReturn(IProjectBuild.create(null, null));

			assertTrue(service.isAotGenerationAvailable(maven));
			assertTrue(service.isAotGenerationAvailable(untyped));
			assertTrue(service.isAotGenerationAvailable(mock(IJavaProject.class)));
		}

		@Test
		void listenersAreNotifiedWhenTheBuildOrTheClasspathOfAGradleProjectChanged() {
			listener.buildChanged(gradleProject(Set.of("build", "processAot")));
			assertEquals(1, notifications);

			// the AOT resources folder becomes a source folder
			listener.changed(gradleProject(Set.of("build", "processAot")), false);
			assertEquals(2, notifications);
		}

		@Test
		void listenersAreNotNotifiedForOtherProjectsOrWhenProjectsAreCreatedOrDeleted() {
			IJavaProject maven = mock(IJavaProject.class);
			when(maven.getProjectBuild()).thenReturn(IProjectBuild.create(ProjectBuild.MAVEN_PROJECT_TYPE, URI.create("file:///project/pom.xml")));

			listener.changed(maven, false);
			listener.buildChanged(maven);
			listener.created(gradleProject(Set.of("build")));
			listener.deleted(gradleProject(Set.of("build")));

			assertEquals(0, notifications);
		}

	}

	/**
	 * The repository AOT metadata of a Gradle project is looked up in the {@code aotResources} source folder of the
	 * project. It is read once and cached, so the cache has to notice when the build regenerates or deletes the files.
	 *
	 * @author Alex Boyko
	 */
	@Nested
	class GradleMetadata {

		private static final String REPOSITORY = "dev.example.CoffeeRepository";
		private static final String METADATA = """
				{"name": "dev.example.CoffeeRepository", "module": "jdbc", "type": "dev.example.CoffeeRepositoryImpl__AotRepository", "methods": []}
				""";

		@TempDir
		Path projectDir;

		private FileObserver observer;
		private DataRepositoryAotMetadataService service;
		private IClasspath classpath;
		private IJavaProject project;
		private final List<String> refreshed = new ArrayList<>();

		private Consumer<String[]> changeHandler;
		private Consumer<String[]> deleteHandler;
		private List<String> changeGlobs;
		private List<String> deleteGlobs;

		@SuppressWarnings("unchecked")
		@BeforeEach
		void setup() throws Exception {
			observer = mock(FileObserver.class);
			service = new DataRepositoryAotMetadataService(observer, null, null);
			service.addListener(files -> files.forEach(f -> refreshed.add(f.toString())));

			ArgumentCaptor<List<String>> changeGlobCaptor = ArgumentCaptor.forClass(List.class);
			ArgumentCaptor<Consumer<String[]>> changeCaptor = ArgumentCaptor.forClass(Consumer.class);
			verify(observer).onAnyChange(changeGlobCaptor.capture(), changeCaptor.capture());
			changeGlobs = changeGlobCaptor.getValue();
			changeHandler = changeCaptor.getValue();

			ArgumentCaptor<List<String>> deleteGlobCaptor = ArgumentCaptor.forClass(List.class);
			ArgumentCaptor<Consumer<String[]>> deleteCaptor = ArgumentCaptor.forClass(Consumer.class);
			verify(observer).onFilesDeleted(deleteGlobCaptor.capture(), deleteCaptor.capture());
			deleteGlobs = deleteGlobCaptor.getValue();
			deleteHandler = deleteCaptor.getValue();

			classpath = mock(IClasspath.class);
			when(classpath.getClasspathEntries()).thenReturn(List.of(aotResourcesFolder(projectDir.resolve("build/generated/aotResources"))));
			project = mock(IJavaProject.class);
			when(project.getClasspath()).thenReturn(classpath);
			when(project.getProjectBuild()).thenReturn(IProjectBuild.create(ProjectBuild.GRADLE_PROJECT_TYPE, projectDir.resolve("build.gradle").toUri()));
		}

		private static CPE aotResourcesFolder(Path folder) {
			return CPE.source(folder.toFile(), folder.resolveSibling("aotClasses").toFile());
		}

		private Path metadataFile() {
			return projectDir.resolve("build/generated/aotResources/dev/example/CoffeeRepository.json");
		}

		private void generateMetadata() throws Exception {
			Files.createDirectories(metadataFile().getParent());
			Files.writeString(metadataFile(), METADATA);
		}

		@Test
		void metadataFilesAndFoldersAreWatched() {
			assertTrue(changeGlobs.contains("**/aotResources/**/*.json"), changeGlobs.toString());
			assertTrue(changeGlobs.contains("**/spring-aot/main/resources/**/*.json"), "Maven is still watched");
			assertTrue(deleteGlobs.contains("**/aotResources"), deleteGlobs.toString());
			assertTrue(deleteGlobs.contains("**/generated"), deleteGlobs.toString());
			assertTrue(deleteGlobs.contains("**/spring-aot"), "Maven is still watched");
		}

		@Test
		void metadataIsReadFromTheSourceFolderWhereverTheBuildDirectoryIs() throws Exception {
			Path file = projectDir.resolve("out/gradle/generated/aotResources/dev/example/CoffeeRepository.json");
			Files.createDirectories(file.getParent());
			Files.writeString(file, METADATA);
			when(classpath.getClasspathEntries()).thenReturn(List.of(aotResourcesFolder(projectDir.resolve("out/gradle/generated/aotResources"))));

			DataRepositoryAotMetadata metadata = service.getRepositoryMetadata(project, REPOSITORY).orElse(null);

			assertEquals(REPOSITORY, metadata.name());
			assertEquals(DataRepositoryModule.JDBC, metadata.module());
		}

		@Test
		void noMetadataIfTheAotResourcesAreNotASourceFolder() throws Exception {
			generateMetadata();
			when(classpath.getClasspathEntries()).thenReturn(List.of());

			assertFalse(service.getRepositoryMetadata(project, REPOSITORY).isPresent());
		}

		@Test
		void regeneratedMetadataIsPickedUp() throws Exception {
			generateMetadata();
			DataRepositoryAotMetadata before = service.getRepositoryMetadata(project, REPOSITORY).orElse(null);
			assertEquals(before, service.getRepositoryMetadata(project, REPOSITORY).orElse(null));

			changeHandler.accept(new String[] { metadataFile().toUri().toString() });

			assertEquals(List.of(metadataFile().toUri().toString()), refreshed);
			assertNotSame(before, service.getRepositoryMetadata(project, REPOSITORY).orElse(null));
		}

		@Test
		void deletingTheAotResourcesFolderForgetsTheMetadata() throws Exception {
			generateMetadata();
			assertTrue(service.getRepositoryMetadata(project, REPOSITORY).isPresent());

			Files.delete(metadataFile());
			deleteHandler.accept(new String[] { metadataFile().getParent().getParent().getParent().toUri().toString() });

			assertFalse(service.getRepositoryMetadata(project, REPOSITORY).isPresent());
			assertEquals(1, refreshed.size());
		}

	}

}
