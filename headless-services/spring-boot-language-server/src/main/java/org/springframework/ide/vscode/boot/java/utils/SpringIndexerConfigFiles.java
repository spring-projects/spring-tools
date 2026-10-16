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
package org.springframework.ide.vscode.boot.java.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DocumentSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.reconcilers.CachedDiagnostic;
import org.springframework.ide.vscode.boot.properties.BootPropertiesReconcileEngine;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IReconcileEngine;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.util.UriUtil;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * Indexer that reconciles the Spring Boot configuration files of a project
 * ({@code application*.properties}, {@code application*.yml}/{@code .yaml}, the {@code bootstrap*}
 * variants and {@code *.factories} files) found in its own source and resource folders, and keeps
 * the resulting diagnostics per project so that they can be queried without a client having opened
 * the files - which is what {@code getProjectDiagnostics} needs in the MCP-only standalone mode.
 *
 * <p>Diagnostics are kept in memory only: the files are few and cheap to reconcile, and their
 * diagnostics depend on the project's classpath metadata, so persisting them across sessions
 * would only add cache-invalidation complexity. Nothing is published to an LSP client here;
 * open documents keep getting their diagnostics from the regular document reconciler.
 *
 * @author Martin Lippert
 */
public class SpringIndexerConfigFiles implements SpringIndexer {

	private static final Logger log = LoggerFactory.getLogger(SpringIndexerConfigFiles.class);

	// mirrors the file name patterns the VS Code extension maps to the spring-boot-properties(-yaml) and spring-factories languages
	private static final Pattern CONFIG_FILE_NAME = Pattern.compile("(application|bootstrap)[^/\\\\]*\\.(properties|yml|yaml)|[^/\\\\]+\\.factories");

	private static final String FACTORIES = ".factories";

	private static final String[] FILE_WATCH_PATTERNS = {
			"**/application*.properties", "**/application*.yml", "**/application*.yaml",
			"**/bootstrap*.properties", "**/bootstrap*.yml", "**/bootstrap*.yaml",
			"**/*.factories"
	};

	private final IReconcileEngine propertiesReconcileEngine;
	private final IReconcileEngine factoriesReconcileEngine;
	private final BiFunction<TextDocument, BiConsumer<String, Diagnostic>, IProblemCollector> problemCollectorCreator;

	// project name -> doc URI -> diagnostics of that file
	private final Map<String, Map<String, List<CachedDiagnostic>>> diagnosticsByProject = new ConcurrentHashMap<>();

	private volatile boolean scanTestSources = false;

	public SpringIndexerConfigFiles(IReconcileEngine propertiesReconcileEngine, IReconcileEngine factoriesReconcileEngine,
			BiFunction<TextDocument, BiConsumer<String, Diagnostic>, IProblemCollector> problemCollectorCreator) {
		this.propertiesReconcileEngine = propertiesReconcileEngine;
		this.factoriesReconcileEngine = factoriesReconcileEngine;
		this.problemCollectorCreator = problemCollectorCreator;
	}

	public void setScanTestSources(boolean scanTestSources) {
		this.scanTestSources = scanTestSources;
	}

	@Override
	public String[] getFileWatchPatterns() {
		return FILE_WATCH_PATTERNS;
	}

	@Override
	public boolean isInterestedIn(String resource) {
		if (resource == null || !(BootPropertiesReconcileEngine.isConfigFile(resource) || resource.endsWith(FACTORIES))) {
			return false;
		}
		int lastSeparator = Math.max(resource.lastIndexOf('/'), resource.lastIndexOf('\\'));
		String fileName = lastSeparator < 0 ? resource : resource.substring(lastSeparator + 1);
		return CONFIG_FILE_NAME.matcher(fileName).matches();
	}

	@Override
	public List<DocumentSymbol> computeDocumentSymbols(IJavaProject project, String docURI, String content) throws Exception {
		return Collections.emptyList();
	}

	/**
	 * All diagnostics currently known for the project's configuration files. Files deleted since
	 * they were reconciled are skipped: deletions reach the indexers only for documents with index
	 * elements, which config files never have.
	 */
	public List<CachedDiagnostic> getDiagnostics(IJavaProject project) {
		Map<String, List<CachedDiagnostic>> perDoc = diagnosticsByProject.get(project.getElementName());
		if (perDoc == null) {
			return Collections.emptyList();
		}
		List<CachedDiagnostic> result = new ArrayList<>();
		for (Map.Entry<String, List<CachedDiagnostic>> entry : perDoc.entrySet()) {
			if (Files.isRegularFile(UriUtil.toFile(entry.getKey()).toPath())) {
				result.addAll(entry.getValue());
			} else {
				perDoc.remove(entry.getKey());
			}
		}
		return result;
	}

	@Override
	public void initializeProject(IJavaProject project, boolean clean) throws Exception {
		long startTime = System.currentTimeMillis();

		Map<String, List<CachedDiagnostic>> perDoc = new ConcurrentHashMap<>();
		List<Path> files = getFiles(project);
		for (Path file : files) {
			String docURI = UriUtil.toUri(file.toFile()).toASCIIString();
			perDoc.put(docURI, reconcile(docURI, null));
		}
		diagnosticsByProject.put(project.getElementName(), perDoc);

		log.info("reconcile config files for project: {} - no. of files: {} - took ms: {}", project.getElementName(), files.size(),
				System.currentTimeMillis() - startTime);
	}

	@Override
	public void removeProject(IJavaProject project) throws Exception {
		diagnosticsByProject.remove(project.getElementName());
	}

	@Override
	public void updateFile(IJavaProject project, DocumentDescriptor updatedDoc, String content) throws Exception {
		String docURI = updatedDoc.getDocURI();
		if (!isInterestedIn(docURI) || !isInScannedFolder(project, UriUtil.toFile(docURI).toPath())) {
			return;
		}
		Map<String, List<CachedDiagnostic>> perDoc = diagnosticsByProject.computeIfAbsent(project.getElementName(),
				name -> new ConcurrentHashMap<>());
		if (content == null && !Files.isRegularFile(UriUtil.toFile(docURI).toPath())) {
			perDoc.remove(docURI);
		} else {
			perDoc.put(docURI, reconcile(docURI, content));
		}
	}

	@Override
	public void updateFiles(IJavaProject project, DocumentDescriptor[] updatedDocs) throws Exception {
		for (DocumentDescriptor updatedDoc : updatedDocs) {
			updateFile(project, updatedDoc, null);
		}
	}

	@Override
	public void removeFiles(IJavaProject project, String[] docURIs) throws Exception {
		Map<String, List<CachedDiagnostic>> perDoc = diagnosticsByProject.get(project.getElementName());
		if (perDoc != null) {
			for (String docURI : docURIs) {
				perDoc.remove(docURI);
			}
		}
	}

	private List<CachedDiagnostic> reconcile(String docURI, String content) {
		try {
			if (content == null) {
				content = Files.readString(UriUtil.toFile(docURI).toPath(), StandardCharsets.UTF_8);
			}
			TextDocument doc = new TextDocument(docURI, languageOf(docURI), 0, content);

			List<CachedDiagnostic> diagnostics = new ArrayList<>();
			IProblemCollector problemCollector = problemCollectorCreator.apply(doc,
					(uri, diagnostic) -> diagnostics.add(new CachedDiagnostic(uri, diagnostic)));
			(docURI.endsWith(FACTORIES) ? factoriesReconcileEngine : propertiesReconcileEngine).reconcile(doc, problemCollector);
			return diagnostics;
		} catch (Exception e) {
			log.error("error reconciling config file " + docURI, e);
			return Collections.emptyList();
		}
	}

	private static LanguageId languageOf(String docURI) {
		if (docURI.endsWith(FACTORIES)) {
			return LanguageId.SPRING_FACTORIES;
		}
		return docURI.endsWith(BootPropertiesReconcileEngine.PROPERTIES) ? LanguageId.BOOT_PROPERTIES : LanguageId.BOOT_PROPERTIES_YAML;
	}

	private List<Path> getFiles(IJavaProject project) {
		List<Path> files = new ArrayList<>();
		for (Path folder : getFoldersToScan(project)) {
			try (Stream<Path> walk = Files.walk(folder)) {
				walk.filter(Files::isRegularFile)
					.filter(path -> isInterestedIn(path.getFileName().toString()))
					.forEach(files::add);
			} catch (IOException e) {
				log.error("error scanning folder for config files: " + folder, e);
			}
		}
		return files;
	}

	private boolean isInScannedFolder(IJavaProject project, Path path) {
		Path normalized = path.toAbsolutePath().normalize();
		return getFoldersToScan(project).stream().anyMatch(normalized::startsWith);
	}

	/** Own source and resource folders of the project (test folders only when enabled), deduplicated and existing. */
	private Set<Path> getFoldersToScan(IJavaProject project) {
		Set<Path> folders = new LinkedHashSet<>();
		IClasspath classpath = project.getClasspath();
		if (classpath != null) {
			try {
				for (CPE cpe : classpath.getClasspathEntries()) {
					if (Classpath.isProjectSource(cpe) && (scanTestSources || !cpe.isTest())) {
						Path folder = new File(cpe.getPath()).toPath().toAbsolutePath().normalize();
						if (Files.isDirectory(folder)) {
							folders.add(folder);
						}
					}
				}
			} catch (Exception e) {
				log.error("error resolving source folders of project " + project.getElementName(), e);
			}
		}
		return folders;
	}

}
