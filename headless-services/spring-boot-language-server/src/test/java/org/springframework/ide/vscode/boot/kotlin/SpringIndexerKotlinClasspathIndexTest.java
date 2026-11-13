/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.kotlin;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.springframework.ide.vscode.boot.java.beans.ComponentIndexer;
import org.springframework.ide.vscode.boot.java.data.DataRepositoryIndexer;
import org.springframework.ide.vscode.boot.java.handlers.SpringComponentIndexer;
import org.springframework.ide.vscode.boot.java.requestmapping.RequestMappingIndexElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypesIndexer;
import org.springframework.ide.vscode.boot.java.utils.SymbolHandler;
import org.springframework.ide.vscode.commons.java.ClasspathIndex;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IProjectBuild;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.util.FileObserver;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;

/**
 * The Kotlin indexer reads the types of the classpath from a Jandex index of the classpath, also
 * for projects whose own index is something else (in VSCode, projects are backed by JDT LS, and
 * their index asks the client for every single type).
 *
 * @author Martin Lippert
 */
@AllKotlinParsers
public class SpringIndexerKotlinClasspathIndexTest {

	@Parameter
	String parser;

	/** a project whose own index must not be used */
	private static IJavaProject withoutUsableIndex(IJavaProject project) {
		return new IJavaProject() {
			@Override public IClasspath getClasspath() { return project.getClasspath(); }
			@Override public IProjectBuild getProjectBuild() { return project.getProjectBuild(); }
			// a JDT LS project registers a listener with the project cache when its index is created,
			// which deadlocks while the cache notifies its listeners: it must not even be asked for
			@Override public ClasspathIndex getIndex() { throw new AssertionError("the index of the project is asked for"); }
			@Override public URI getLocationUri() { return project.getLocationUri(); }
			@Override public boolean exists() { return project.exists(); }
			@Override public Map<String, String> getJavaCoreOptions() { return project.getJavaCoreOptions(); }
		};
	}

	private static final FileObserver NO_FILE_OBSERVER = new FileObserver() {
		@Override public String onFilesCreated(List<String> globPattern, Consumer<String[]> handler) { return "none"; }
		@Override public String onFilesChanged(List<String> globPattern, Consumer<String[]> handler) { return "none"; }
		@Override public String onFilesDeleted(List<String> globPattern, Consumer<String[]> handler) { return "none"; }
		@Override public boolean unsubscribe(String subscriptionId) { return true; }
	};

	private final List<SpringIndexElement> published = new ArrayList<>();

	private SpringIndexerKotlin indexer() {
		SymbolHandler handler = new SymbolHandler() {
			@Override
			public void addSymbols(IJavaProject p, String docURI, List<SpringIndexElement> elements, List<Diagnostic> diagnostics) {
				published.addAll(elements);
			}
			@Override
			public void addSymbols(IJavaProject p, Map<String, List<SpringIndexElement>> elementsByDoc, Map<String, List<Diagnostic>> diagnosticsByDoc) {
				elementsByDoc.values().forEach(published::addAll);
			}
			@Override
			public void removeSymbols(IJavaProject p, String docURI) {
			}
		};

		SpringComponentIndexer[] indexers = { new StereotypesIndexer(), new ComponentIndexer(new DataRepositoryIndexer(null)) };
		return new SpringIndexerKotlin(handler, indexers, () -> true, () -> false, () -> parser, () -> NO_FILE_OBSERVER);
	}

	@Test
	void theTypesOfTheClasspathComeFromAJandexIndexOfTheClasspath() throws Exception {
		IJavaProject project = withoutUsableIndex(ProjectsHarness.INSTANCE.mavenProjectAlreadyBuilt("test-request-mapping-symbols-kotlin"));
		indexer().initializeProject(project, true);

		// the annotations of the classpath are resolved: the controllers are beans, with their mappings
		Bean controller = published.stream().filter(Bean.class::isInstance).map(Bean.class::cast)
				.filter(bean -> bean.getName().equals("simpleMappingClass")).findFirst().orElseThrow();
		assertThat(controller.getChildren()).anyMatch(child -> child instanceof RequestMappingIndexElement mapping && mapping.getPath().equals("/greeting"));

		StereotypeClassElement type = published.stream().filter(StereotypeClassElement.class::isInstance).map(StereotypeClassElement.class::cast)
				.filter(element -> element.getType().equals("org.test.SimpleMappingClass")).findFirst().orElseThrow();
		assertThat(type.getAnnotationTypes()).contains("org.springframework.stereotype.Controller", "org.springframework.stereotype.Component");
	}

	/**
	 * The outline is asked for on a request thread that holds the lock of the symbol index, which
	 * the indexing of a project needs, too. When VSCode starts with a Kotlin file open, that happens
	 * before the project is indexed. Nothing must be set up for the project then.
	 */
	@Test
	void theOutlineOfAProjectThatIsNotIndexedYetIsEmptyAndSetsUpNothing() throws Exception {
		IJavaProject project = withoutUsableIndex(ProjectsHarness.INSTANCE.mavenProjectAlreadyBuilt("test-request-mapping-symbols-kotlin"));
		Path file = Paths.get(project.getLocationUri()).resolve("src/main/kotlin/org/test/SimpleMappingClass.kt");
		String docUri = file.toUri().toString();
		String content = Files.readString(file);

		SpringIndexerKotlin indexer = indexer();
		assertThat(indexer.computeDocumentSymbols(project, docUri, content)).isEmpty();

		indexer.initializeProject(project, true);
		assertThat(indexer.computeDocumentSymbols(project, docUri, content))
				.anyMatch(symbol -> symbol.getChildren() != null && symbol.getChildren().stream().anyMatch(child -> child.getName().equals("@/greeting")));
	}

}
