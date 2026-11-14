/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 * <p>
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ide.vscode.boot.java.data.DataRepositoryAotMetadataService;
import org.springframework.ide.vscode.boot.java.utils.test.MockProjectObserver;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IProjectBuild;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;
import org.springframework.ide.vscode.commons.util.BasicFileObserver;

/**
 * Tests metadata refreshes with real JSON files and {@link BasicFileObserver}.
 * The tests write JSON directly; no Java sources or compiled classes are created.
 * {@code CoffeeRepositoryImpl} is just a JSON {@code type} value used to detect updated contents.
 * Each case runs for Maven and Gradle without invoking a build. File events are sent manually.
 * Projects and classpaths are mocked; see {@link #project(Path, Path, String)}.
 * <p>
 * Default metadata layout (individual tests may use other build folders):
 * <pre>
 * workspace/build/
 * |-- spring-aot/main/resources/dev/example/   (Maven)
 * |   |-- CoffeeRepository.json
 * |   `-- TeaRepository.json                  (optional)
 * `-- generated/aotResources/dev/example/     (Gradle)
 *     |-- CoffeeRepository.json
 *     `-- TeaRepository.json                  (optional)
 * </pre>
 * In the test diagrams, {@code ...} means {@code spring-aot/main/resources/dev/example} for Maven or
 * {@code generated/aotResources/dev/example} for Gradle. File-event refreshes list affected metadata URIs.
 * Gradle project-change notifications list the project URI instead.
 */
public class DataRepositoryAotMetadataFileObserverTest {

	private static final String COFFEE_REPOSITORY = "dev.example.CoffeeRepository";
	private static final String TEA_REPOSITORY = "dev.example.TeaRepository";

	@TempDir
	Path workspace;

	private final TestFileObserver observer = new TestFileObserver();
	private final MockProjectObserver projects = new MockProjectObserver();
	private final DataRepositoryAotMetadataService service = new DataRepositoryAotMetadataService(observer, null, null, projects);
	private final List<List<URI>> refreshes = new ArrayList<>();
	private Path buildDir;

	@BeforeEach
	void setup() {
		buildDir = workspace.resolve("build");
		service.addListener(refreshes::add);
	}

	/**
	 * <pre>
	 * workspace/
	 * `-- project[demo]/                  (or project[)
	 *     `-- out/custom-build/           (deleted last)
	 *         `-- .../CoffeeRepository.json (missing -> created -> updated -> deleted)
	 * </pre>
	 * The second CSV value becomes {@code folderName} in {@code workspace.resolve(folderName)}.
	 * Cache the missing file, then send create/change events for it and a deletion event for the build directory.
	 * Each event must clear the old cached result and send one refresh, despite the brackets in the path.
	 */
	@ParameterizedTest
	@CsvSource({ "maven,project[demo]", "maven,project[", "gradle,project[demo]", "gradle,project[" })
	void testMetadataRefreshesUnderPathsContainingBrackets(String buildType, String folderName) throws Exception {
		Path dir = workspace.resolve(folderName);
		Path buildDir = dir.resolve("out/custom-build");
		IJavaProject project = project(dir, buildDir, buildType);
		assertFalse(service.getRepositoryMetadata(project, COFFEE_REPOSITORY).isPresent(),
				"CoffeeRepository metadata should be absent before its file is created");

		Path file = writeMetadata(buildDir, buildType, COFFEE_REPOSITORY, "CoffeeRepositoryImpl");
		assertFalse(service.getRepositoryMetadata(project, COFFEE_REPOSITORY).isPresent(),
				"CoffeeRepository's missing-file result should stay cached until the create event");
		observer.notifyFileCreated(file.toUri().toString());
		var before = service.getRepositoryMetadata(project, COFFEE_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing CoffeeRepository metadata after the create event under the bracketed project folder"));
		assertEquals("CoffeeRepositoryImpl", before.type(),
				"Create event should load the type from CoffeeRepository.json");
		assertSingleRefresh(file.toUri());
		refreshes.clear();

		writeMetadata(buildDir, buildType, COFFEE_REPOSITORY, "UpdatedCoffeeRepositoryImpl");
		assertSame(before, service.getRepositoryMetadata(project, COFFEE_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing cached CoffeeRepository metadata before the change event")),
				"Updating CoffeeRepository.json should keep its cached object until the change event");
		observer.notifyFileChanged(file.toUri().toString());
		var after = service.getRepositoryMetadata(project, COFFEE_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing CoffeeRepository metadata after the change event under the bracketed project folder"));
		assertNotSame(before, after,
				"Change event should replace the cached CoffeeRepository object");
		assertEquals("UpdatedCoffeeRepositoryImpl", after.type(),
				"Change event should load the updated type from CoffeeRepository.json");
		assertSingleRefresh(file.toUri());
		refreshes.clear();

		// A clean may report just the deleted build directory, without events for its contents.
		FileUtils.deleteDirectory(buildDir.toFile());
		observer.notifyFileDeleted(buildDir.toUri().toString());
		assertFalse(service.getRepositoryMetadata(project, COFFEE_REPOSITORY).isPresent(),
				"Build-directory deletion event should remove CoffeeRepository metadata under " + folderName);
		assertSingleRefresh(file.toUri());
	}

	/**
	 * <pre>
	 * workspace/
	 * |-- project/build/.../
	 * |   |-- CoffeeRepository.json       (cached; update this file)
	 * |   `-- TeaRepository.json          (cached; leave unchanged)
	 * `-- other-project/build/.../
	 *     `-- CoffeeRepository.json       (cached; type=OtherCoffeeRepositoryImpl; leave unchanged)
	 * </pre>
	 * Change the first Coffee file's type from {@code CoffeeRepositoryImpl} to {@code UpdatedCoffeeRepositoryImpl}.
	 * After its change event, the next lookup must reload that metadata. Only this file is refreshed;
	 * the other two cached objects stay the same.
	 */
	@ParameterizedTest
	@ValueSource(strings = { ProjectBuild.MAVEN_PROJECT_TYPE, ProjectBuild.GRADLE_PROJECT_TYPE })
	void testChangingOneRepositoryPreservesOtherCachedMetadata(String buildType) throws Exception {
		Path dir = workspace.resolve("project");
		Path buildDir = dir.resolve("build");
		IJavaProject project = project(dir, buildDir, buildType);
		Path otherDir = workspace.resolve("other-project");
		Path otherBuildDir = otherDir.resolve("build");
		IJavaProject otherProject = project(otherDir, otherBuildDir, buildType);

		Path file = writeMetadata(buildDir, buildType, COFFEE_REPOSITORY, "CoffeeRepositoryImpl");
		writeMetadata(buildDir, buildType, TEA_REPOSITORY, "TeaRepositoryImpl");
		writeMetadata(otherBuildDir, buildType, COFFEE_REPOSITORY, "OtherCoffeeRepositoryImpl");
		var before = service.getRepositoryMetadata(project, COFFEE_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing CoffeeRepository metadata in project before updating its file"));
		var otherRepository = service.getRepositoryMetadata(project, TEA_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing TeaRepository metadata in project before updating CoffeeRepository"));
		var otherProjectMetadata = service.getRepositoryMetadata(otherProject, COFFEE_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing CoffeeRepository metadata in other-project before changing CoffeeRepository in project"));

		writeMetadata(buildDir, buildType, COFFEE_REPOSITORY, "UpdatedCoffeeRepositoryImpl");
		observer.notifyFileChanged(file.toUri().toString());

		var after = service.getRepositoryMetadata(project, COFFEE_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing CoffeeRepository metadata in project after its change event"));
		assertNotSame(before, after,
				"Changing CoffeeRepository.json in project should replace its cached metadata object");
		assertEquals("UpdatedCoffeeRepositoryImpl", after.type(),
				"Change event should load the updated CoffeeRepository type in project");
		assertSame(otherRepository, service.getRepositoryMetadata(project, TEA_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing cached TeaRepository metadata after changing CoffeeRepository")),
				"Changing CoffeeRepository.json must keep TeaRepository's cached object in project");
		assertSame(otherProjectMetadata, service.getRepositoryMetadata(otherProject, COFFEE_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing cached CoffeeRepository metadata in other-project after changing CoffeeRepository in project")),
				"Changing CoffeeRepository.json in project must keep the cached CoffeeRepository object in other-project");
		assertSingleRefresh(file.toUri());
	}

	/**
	 * <pre>
	 * workspace/
	 * |-- build/                         (project's build folder; delete)
	 * |   `-- .../
	 * |       |-- CoffeeRepository.json  (cached; type=CoffeeRepositoryImpl)
	 * |       `-- TeaRepository.json     (cached; type=TeaRepositoryImpl)
	 * `-- build-other/                   (other-project's build folder; keep)
	 *     `-- .../CoffeeRepository.json  (cached; type=OtherCoffeeRepositoryImpl)
	 * </pre>
	 * Report only the deletion of {@code build}. Its Coffee and Tea entries must leave the cache, with one refresh
	 * listing both file URIs. The Coffee object under {@code build-other} must stay cached despite the shared name prefix.
	 */
	@ParameterizedTest
	@ValueSource(strings = { ProjectBuild.MAVEN_PROJECT_TYPE, ProjectBuild.GRADLE_PROJECT_TYPE })
	void testDeletingBuildDirectoryPreservesNeighboringDirectory(String buildType) throws Exception {
		IJavaProject project = project(workspace.resolve("project"), buildDir, buildType);
		Path neighboringBuildDir = workspace.resolve("build-other");
		IJavaProject neighboringProject = project(workspace.resolve("other-project"), neighboringBuildDir, buildType);

		Path file = writeMetadata(buildDir, buildType, COFFEE_REPOSITORY, "CoffeeRepositoryImpl");
		Path otherFile = writeMetadata(buildDir, buildType, TEA_REPOSITORY, "TeaRepositoryImpl");
		writeMetadata(neighboringBuildDir, buildType, COFFEE_REPOSITORY, "OtherCoffeeRepositoryImpl");
		service.getRepositoryMetadata(project, COFFEE_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing CoffeeRepository metadata under build before deleting that directory"));
		service.getRepositoryMetadata(project, TEA_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing TeaRepository metadata under build before deleting that directory"));
		var neighboringMetadata = service.getRepositoryMetadata(neighboringProject, COFFEE_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing CoffeeRepository metadata under build-other before deleting build"));

		FileUtils.deleteDirectory(buildDir.toFile());
		observer.notifyFileDeleted(buildDir.toUri().toString());

		assertFalse(service.getRepositoryMetadata(project, COFFEE_REPOSITORY).isPresent(),
				"Deleting build must clear CoffeeRepository metadata beneath it");
		assertFalse(service.getRepositoryMetadata(project, TEA_REPOSITORY).isPresent(),
				"Deleting build must clear TeaRepository metadata beneath it");
		assertSame(neighboringMetadata, service.getRepositoryMetadata(neighboringProject, COFFEE_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing cached CoffeeRepository metadata under build-other after deleting build")),
				"Deleting build must keep the cached CoffeeRepository object under build-other");
		assertSingleRefresh(file.toUri(), otherFile.toUri());
	}

	/**
	 * <pre>
	 * workspace/build/.../
	 * `-- CoffeeRepository.json          (cached -> deleted -> recreated)
	 * </pre>
	 * The delete event clears the original metadata; the next lookup caches "file missing".
	 * The create event must clear that result and load {@code RegeneratedCoffeeRepositoryImpl} from the new JSON.
	 * Expect one refresh for deletion and one for recreation.
	 */
	@ParameterizedTest
	@ValueSource(strings = { ProjectBuild.MAVEN_PROJECT_TYPE, ProjectBuild.GRADLE_PROJECT_TYPE })
	void testDeletedMetadataIsReloadedAfterRecreation(String buildType) throws Exception {
		IJavaProject project = project(workspace, buildDir, buildType);
		Path file = writeMetadata(buildDir, buildType, COFFEE_REPOSITORY, "CoffeeRepositoryImpl");
		var before = service.getRepositoryMetadata(project, COFFEE_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing CoffeeRepository metadata before deleting its file"));

		Files.delete(file);
		assertSame(before, service.getRepositoryMetadata(project, COFFEE_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing cached CoffeeRepository metadata before the deletion event")),
				"Deleting CoffeeRepository.json should keep its cached object until the deletion event");
		observer.notifyFileDeleted(file.toUri().toString());
		assertFalse(service.getRepositoryMetadata(project, COFFEE_REPOSITORY).isPresent(),
				"Deletion event should make CoffeeRepository metadata absent");
		assertSingleRefresh(file.toUri());
		refreshes.clear();

		writeMetadata(buildDir, buildType, COFFEE_REPOSITORY, "RegeneratedCoffeeRepositoryImpl");
		assertFalse(service.getRepositoryMetadata(project, COFFEE_REPOSITORY).isPresent(),
				"Recreating CoffeeRepository.json should keep the missing-file result cached until the create event");
		observer.notifyFileCreated(file.toUri().toString());
		assertEquals("RegeneratedCoffeeRepositoryImpl", service.getRepositoryMetadata(project, COFFEE_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing CoffeeRepository metadata after recreating its file and sending the create event")).type(),
				"Create event should load the regenerated type from CoffeeRepository.json");
		assertSingleRefresh(file.toUri());
	}

	/**
	 * <pre>
	 * workspace/build/.../
	 * |-- CoffeeRepository.json          (missing -> created -> changed)
	 * `-- TeaRepository.json             (missing -> created -> changed)
	 * </pre>
	 * Cache both missing files, create them, then change them after loading their metadata.
	 * For both creation and change, send a batch containing Coffee, Tea and Coffee again, then repeat the batch
	 * before another lookup. Each stage must reload both files and send one refresh listing each URI once.
	 */
	@ParameterizedTest
	@ValueSource(strings = { ProjectBuild.MAVEN_PROJECT_TYPE, ProjectBuild.GRADLE_PROJECT_TYPE })
	void testBatchedCreatesAndChangesRefreshEachRepositoryOnce(String buildType) throws Exception {
		IJavaProject project = project(workspace, buildDir, buildType);
		assertFalse(service.getRepositoryMetadata(project, COFFEE_REPOSITORY).isPresent(),
				"CoffeeRepository metadata should be absent before the batch creates its file");
		assertFalse(service.getRepositoryMetadata(project, TEA_REPOSITORY).isPresent(),
				"TeaRepository metadata should be absent before the batch creates its file");
		Path file = writeMetadata(buildDir, buildType, COFFEE_REPOSITORY, "CoffeeRepositoryImpl");
		Path otherFile = writeMetadata(buildDir, buildType, TEA_REPOSITORY, "TeaRepositoryImpl");
		String[] events = { file.toUri().toString(), otherFile.toUri().toString(), file.toUri().toString() };

		observer.notifyFilesCreated(events);
		// Repeated events without an intervening lookup have no cached entries left to invalidate.
		observer.notifyFilesCreated(events);
		assertSingleRefresh(file.toUri(), otherFile.toUri());
		assertEquals("CoffeeRepositoryImpl", service.getRepositoryMetadata(project, COFFEE_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing CoffeeRepository metadata after the repeated create batches")).type(),
				"CoffeeRepository lookup should read the created JSON after repeated create batches");
		assertEquals("TeaRepositoryImpl", service.getRepositoryMetadata(project, TEA_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing TeaRepository metadata after the repeated create batches")).type(),
				"TeaRepository lookup should read the created JSON after repeated create batches");
		refreshes.clear();

		writeMetadata(buildDir, buildType, COFFEE_REPOSITORY, "UpdatedCoffeeRepositoryImpl");
		writeMetadata(buildDir, buildType, TEA_REPOSITORY, "UpdatedTeaRepositoryImpl");
		observer.notifyFilesChanged(events);
		observer.notifyFilesChanged(events);
		assertSingleRefresh(file.toUri(), otherFile.toUri());
		assertEquals("UpdatedCoffeeRepositoryImpl", service.getRepositoryMetadata(project, COFFEE_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing CoffeeRepository metadata after the repeated change batches")).type(),
				"CoffeeRepository lookup should read the updated JSON after repeated change batches");
		assertEquals("UpdatedTeaRepositoryImpl", service.getRepositoryMetadata(project, TEA_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing TeaRepository metadata after the repeated change batches")).type(),
				"TeaRepository lookup should read the updated JSON after repeated change batches");
	}

	/**
	 * <pre>
	 * workspace/build/                   (delete the directory and its contents)
	 * `-- .../
	 *     |-- CoffeeRepository.json      (cached)
	 *     `-- TeaRepository.json         (cached)
	 * </pre>
	 * Send deletion events for the directory and both files, with duplicates, in directory-first and file-first order.
	 * Repeat the batch before another lookup. Both entries must leave the cache, with one refresh listing each URI once.
	 */
	@ParameterizedTest
	@CsvSource({ "maven,true", "maven,false", "gradle,true", "gradle,false" })
	void testOverlappingDeletionEventsRefreshEachRepositoryOnce(String buildType, boolean parentFirst) throws Exception {
		IJavaProject project = project(workspace, buildDir, buildType);
		Path file = writeMetadata(buildDir, buildType, COFFEE_REPOSITORY, "CoffeeRepositoryImpl");
		Path otherFile = writeMetadata(buildDir, buildType, TEA_REPOSITORY, "TeaRepositoryImpl");
		service.getRepositoryMetadata(project, COFFEE_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing CoffeeRepository metadata before deleting the build directory"));
		service.getRepositoryMetadata(project, TEA_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing TeaRepository metadata before deleting the build directory"));

		FileUtils.deleteDirectory(buildDir.toFile());
		String parentUri = buildDir.toUri().toString();
		String fileUri = file.toUri().toString();
		String otherFileUri = otherFile.toUri().toString();
		String[] events = parentFirst ? new String[] { parentUri, fileUri, otherFileUri, parentUri }
				: new String[] { fileUri, parentUri, otherFileUri, fileUri };
		observer.notifyFilesDeleted(events);
		observer.notifyFilesDeleted(events);

		assertSingleRefresh(file.toUri(), otherFile.toUri());
		assertFalse(service.getRepositoryMetadata(project, COFFEE_REPOSITORY).isPresent(),
				"Overlapping deletion events must leave CoffeeRepository metadata absent");
		assertFalse(service.getRepositoryMetadata(project, TEA_REPOSITORY).isPresent(),
				"Overlapping deletion events must leave TeaRepository metadata absent");
	}

	/**
	 * <pre>
	 * workspace/build/.../
	 * `-- CoffeeRepository.json          ("not-json" or "{" -> valid metadata)
	 * </pre>
	 * Cache the failed read, replace the contents, then send a change event. The next lookup must read
	 * {@code RecoveredCoffeeRepositoryImpl} from the JSON, and the listener must receive one refresh.
	 */
	@ParameterizedTest
	@CsvSource({ "maven,not-json", "maven,{", "gradle,not-json", "gradle,{" })
	void testMalformedMetadataRecoversAfterChange(String buildType, String invalidJson) throws Exception {
		IJavaProject project = project(workspace, buildDir, buildType);
		Path file = metadataFile(buildDir, buildType, COFFEE_REPOSITORY);
		Files.createDirectories(file.getParent());
		Files.writeString(file, invalidJson);
		assertFalse(service.getRepositoryMetadata(project, COFFEE_REPOSITORY).isPresent(),
				"Invalid CoffeeRepository JSON should yield no metadata: " + invalidJson);

		writeMetadata(buildDir, buildType, COFFEE_REPOSITORY, "RecoveredCoffeeRepositoryImpl");
		assertFalse(service.getRepositoryMetadata(project, COFFEE_REPOSITORY).isPresent(),
				"CoffeeRepository's parse failure should stay cached until the change event");
		observer.notifyFileChanged(file.toUri().toString());

		assertEquals("RecoveredCoffeeRepositoryImpl", service.getRepositoryMetadata(project, COFFEE_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing CoffeeRepository metadata after replacing malformed JSON and sending the change event")).type(),
				"Change event should load the repaired CoffeeRepository JSON");
		assertSingleRefresh(file.toUri());
	}

	/**
	 * <pre>
	 * workspace/
	 * |-- build/.../
	 * |   |-- CoffeeRepository.json      (cached; leave unchanged)
	 * |   `-- TeaRepository.json         (not cached; send events for this file)
	 * `-- notes.txt                      (unrelated; send events for this file)
	 * </pre>
	 * Send create/change/delete events for Tea and notes.txt. Tea's events reach the service, but Tea is not cached.
	 * The observer filters out create/change events for notes.txt; only its deletion reaches the service.
	 * Coffee's cached object must stay the same, and no refresh should be sent.
	 */
	@ParameterizedTest
	@ValueSource(strings = { ProjectBuild.MAVEN_PROJECT_TYPE, ProjectBuild.GRADLE_PROJECT_TYPE })
	void testUnrelatedFileEventsDoNotRefreshCachedMetadata(String buildType) throws Exception {
		IJavaProject project = project(workspace, buildDir, buildType);
		writeMetadata(buildDir, buildType, COFFEE_REPOSITORY, "CoffeeRepositoryImpl");
		var before = service.getRepositoryMetadata(project, COFFEE_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing CoffeeRepository metadata before sending unrelated file events"));
		Path uncachedFile = writeMetadata(buildDir, buildType, TEA_REPOSITORY, "TeaRepositoryImpl");
		Path unrelatedFile = Files.writeString(workspace.resolve("notes.txt"), "unrelated");
		String[] events = { uncachedFile.toUri().toString(), unrelatedFile.toUri().toString() };

		observer.notifyFilesCreated(events);
		observer.notifyFilesChanged(events);
		Files.delete(uncachedFile);
		Files.delete(unrelatedFile);
		observer.notifyFilesDeleted(events);

		assertSame(before, service.getRepositoryMetadata(project, COFFEE_REPOSITORY)
				.orElseThrow(() -> new AssertionError("Missing cached CoffeeRepository metadata after events for TeaRepository.json and notes.txt")),
				"Events for TeaRepository.json and notes.txt must keep CoffeeRepository's cached object");
		assertTrue(refreshes.isEmpty(),
				"Events for uncached TeaRepository.json and notes.txt must not send refreshes: " + refreshes);
	}

	/**
	 * <pre>
	 * workspace/                         (empty; no project or metadata files are created)
	 *
	 * Separate project mock per row (paths are not created):
	 * Project location -> Build folder
	 * project0         -> project0/build
	 * project0         -> project0/other-build
	 * project1         -> project1/build
	 * project1         -> project1/other-build
	 * project2         -> project2/build
	 * project2         -> project2/other-build
	 * </pre>
	 * For each mock, look up the missing Coffee metadata and report a project change followed by deletion.
	 * Subscriptions are created in the service constructor. Their count must stay unchanged, guarding against
	 * adding watches for each project or build folder.
	 */
	@ParameterizedTest
	@ValueSource(strings = { ProjectBuild.MAVEN_PROJECT_TYPE, ProjectBuild.GRADLE_PROJECT_TYPE })
	void testProjectChangesDoNotAccumulateSubscriptions(String buildType) throws Exception {
		int subscriptions = observer.subscriptionCount();
		for (int i = 0; i < 3; i++) {
			Path dir = workspace.resolve("project" + i);
			for (String output : List.of("build", "other-build")) {
				IJavaProject project = project(dir, dir.resolve(output), buildType);
				service.getRepositoryMetadata(project, COFFEE_REPOSITORY);
				projects.doWithListeners(listener -> listener.changed(project, false));
				assertEquals(subscriptions, observer.subscriptionCount(),
						"Project change must preserve the file subscription count for " + dir.resolve(output));
				projects.doWithListeners(listener -> listener.deleted(project));
				assertEquals(subscriptions, observer.subscriptionCount(),
						"Project deletion must preserve the file subscription count for " + dir.resolve(output));
			}
		}
	}

	private static Path writeMetadata(Path buildDir, String buildType, String repository, String implementation) throws Exception {
		Path file = metadataFile(buildDir, buildType, repository);
		Files.createDirectories(file.getParent());
		Files.writeString(file, """
				{"name": "%s", "module": "jdbc", "type": "%s", "methods": []}
				""".formatted(repository, implementation));
		return file;
	}

	private static Path metadataFile(Path buildDir, String buildType, String repository) {
		return metadataFolder(buildDir, buildType).resolve(repository.replace('.', '/') + ".json");
	}

	/** Checks one notification containing each expected file URI exactly once, in any order. */
	private void assertSingleRefresh(URI... files) {
		assertEquals(1, refreshes.size(),
				"Expected one listener notification for " + Set.of(files));
		assertEquals(files.length, refreshes.getFirst().size(),
				"Refresh must contain one entry per affected metadata file");
		assertEquals(Set.of(files), Set.copyOf(refreshes.getFirst()),
				"Refresh must list exactly the affected metadata file URIs");
	}

	/**
	 * Maven uses the parent of the classes output folder; Gradle uses the {@code aotResources} source folder.
	 */
	private static IJavaProject project(Path dir, Path buildDir, String buildType) throws Exception {
		boolean maven = ProjectBuild.MAVEN_PROJECT_TYPE.equals(buildType);
		CPE source = CPE.source((maven ? dir.resolve("src/main/java") : metadataFolder(buildDir, buildType)).toFile(),
				buildDir.resolve("classes").toFile());
		source.setOwn(true);
		IClasspath classpath = mock(IClasspath.class);
		when(classpath.getClasspathEntries()).thenReturn(List.of(source));
		IJavaProject project = mock(IJavaProject.class);
		when(project.getClasspath()).thenReturn(classpath);
		when(project.getLocationUri()).thenReturn(dir.toUri());
		when(project.getProjectBuild()).thenReturn(IProjectBuild.create(buildType, dir.resolve(maven ? "pom.xml" : "build.gradle").toUri()));
		return project;
	}

	/** Uses the standard AOT resources layout beneath the supplied build directory. */
	private static Path metadataFolder(Path buildDir, String buildType) {
		return buildDir.resolve(ProjectBuild.MAVEN_PROJECT_TYPE.equals(buildType) ? "spring-aot/main/resources" : "generated/aotResources");
	}

	private static class TestFileObserver extends BasicFileObserver {
		int subscriptionCount() {
			return createRegistry.size() + changeRegistry.size() + deleteRegistry.size();
		}
	}

}
