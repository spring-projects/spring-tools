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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.eclipse.lsp4j.DocumentSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.index.SpringIndexToSymbolsConverter;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.beans.CachedIndexElement;
import org.springframework.ide.vscode.boot.java.handlers.SpringComponentIndexer;
import org.springframework.ide.vscode.boot.java.lombok.LombokSettings;
import org.springframework.ide.vscode.boot.java.reconcilers.ReconcilingIndex;
import org.springframework.ide.vscode.boot.java.utils.DocumentDescriptor;
import org.springframework.ide.vscode.boot.java.utils.DocumentUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexer;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaAstScanner;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaDependencyTracker;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaScanResult;
import org.springframework.ide.vscode.boot.java.utils.SymbolHandler;
import org.springframework.ide.vscode.boot.kotlin.ast.KotlinDomBuilder;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinDeclarationIndex;
import org.springframework.ide.vscode.boot.kotlin.bindings.KotlinTypeResolver;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinPsiReader;
import org.springframework.ide.vscode.boot.kotlin.syntax.KotlinSyntax;
import org.springframework.ide.vscode.commons.jandex.JandexClasspath;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.JavaProject;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.util.FileObserver;
import org.springframework.ide.vscode.commons.util.UriUtil;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * Indexes Kotlin source files the way {@code SpringIndexerJava} indexes Java source files: the
 * Kotlin file is parsed into an AST that the indexers of the Java side understand, and the very
 * same {@link SpringComponentIndexer}s run on it. This indexer is a separate implementation that
 * does not share any state with the Java one, so it can be switched on and off on its own (see
 * {@code BootJavaConfig#isKotlinSupportEnabled()}).
 * <p>
 * The nodes of the Kotlin AST resolve to JDT bindings that are adapters over the Kotlin type
 * resolution (see {@link KotlinDomBuilder}), so the indexers of the Java side run on it unchanged.
 * <p>
 * It is a prototype: there is no persistent cache, and nothing is reconciled. A file that changes
 * is indexed again on its own, not the files that depend on it.
 *
 * @author Martin Lippert
 */
public class SpringIndexerKotlin implements SpringIndexer {

	private static final Logger log = LoggerFactory.getLogger(SpringIndexerKotlin.class);

	private static final IProblemCollector NO_OP_PROBLEM_COLLECTOR = new IProblemCollector() {
		@Override public void beginCollecting() {}
		@Override public void endCollecting() {}
		@Override public void accept(ReconcileProblem problem) {}
	};

	private final SymbolHandler symbolHandler;
	private final BooleanSupplier enabled;
	private final BooleanSupplier scanTestSources;

	private final KotlinPsiReader reader = new KotlinPsiReader();
	private final KotlinDomBuilder domBuilder = new KotlinDomBuilder();
	private final SpringIndexerJavaAstScanner astScanner;

	private final Supplier<FileObserver> fileObserver;

	private final Map<String, ProjectState> projects = new ConcurrentHashMap<>();

	/** what is known about the Kotlin sources of a project */
	private static class ProjectState {
		final KotlinDeclarationIndex declarations = new KotlinDeclarationIndex();
		final KotlinTypeResolver resolver;
		final Set<String> publishedDocs = ConcurrentHashMap.newKeySet();

		/** the index of the classpath this state created for itself, null if it uses the one of the project */
		private final JandexClasspath ownIndex;

		/**
		 * The types of the classpath come from a Jandex index of the classpath, as the Java indexer
		 * reads them from the jars of the classpath as well. A project that is backed by JDT LS has
		 * an index that asks the client for every type (one request per type, with what JDT LS
		 * reports about it), so a Jandex index of its classpath is created instead.
		 * <p>
		 * The index of such a project is not even asked for: creating it registers a listener with
		 * the project cache, which must not happen while the cache notifies its listeners.
		 */
		ProjectState(IJavaProject project, FileObserver fileObserver) {
			this.ownIndex = project instanceof JavaProject ? null : new JandexClasspath(project.getClasspath(), fileObserver, null);
			this.resolver = new KotlinTypeResolver(ownIndex != null ? ownIndex : project.getIndex(), declarations);
		}

		void dispose() {
			if (ownIndex != null) {
				ownIndex.dispose();
			}
		}
	}

	/**
	 * @param componentIndexers the indexers that run on the Kotlin files, the ones of the Java side
	 * @param enabled whether Kotlin files are indexed at all
	 * @param scanTestSources whether the Kotlin files in the test source folders are indexed
	 * @param fileObserver watches the output folders for the Jandex index of the classpath
	 */
	public SpringIndexerKotlin(SymbolHandler symbolHandler, SpringComponentIndexer[] componentIndexers, BooleanSupplier enabled,
			BooleanSupplier scanTestSources, Supplier<FileObserver> fileObserver) {
		this.symbolHandler = symbolHandler;
		this.fileObserver = fileObserver;
		this.enabled = enabled;
		this.scanTestSources = scanTestSources;
		this.astScanner = new SpringIndexerJavaAstScanner(componentIndexers, new SpringIndexerJavaDependencyTracker(), (context, index) -> {});
	}

	@Override
	public String[] getFileWatchPatterns() {
		return new String[] { "**/*.kt" };
	}

	@Override
	public boolean isInterestedIn(String resource) {
		return resource.endsWith(".kt");
	}

	@Override
	public void initializeProject(IJavaProject project, boolean clean) throws Exception {
		String projectName = project.getElementName();

		if (!enabled.getAsBoolean()) {
			ProjectState previous = projects.remove(projectName);
			if (previous != null) {
				previous.publishedDocs.forEach(docURI -> symbolHandler.removeSymbols(project, docURI));
				previous.dispose();
			}
			return;
		}

		long start = System.currentTimeMillis();

		ProjectState state = new ProjectState(project, fileObserver.get());
		ProjectState previous = projects.put(projectName, state);
		if (previous != null) {
			previous.publishedDocs.forEach(docURI -> symbolHandler.removeSymbols(project, docURI));
			previous.dispose();
		}

		List<DocumentDescriptor> docs = kotlinFiles(project).stream()
				.map(path -> DocumentDescriptor.createFromFile(path.toString()))
				.toList();

		log.info("scan kotlin files for symbols for project: {} - no. of files: {}", projectName, docs.size());

		scan(project, state, docs, null);

		log.info("scan kotlin files for symbols for project: {} took ms: {}", projectName, System.currentTimeMillis() - start);
	}

	@Override
	public void removeProject(IJavaProject project) throws Exception {
		ProjectState previous = projects.remove(project.getElementName());
		if (previous != null) {
			previous.dispose();
		}
	}

	@Override
	public void updateFile(IJavaProject project, DocumentDescriptor updatedDoc, String content) throws Exception {
		if (updatedDoc != null) {
			updateFiles(project, new DocumentDescriptor[] { updatedDoc }, content);
		}
	}

	@Override
	public void updateFiles(IJavaProject project, DocumentDescriptor[] updatedDocs) throws Exception {
		updateFiles(project, updatedDocs, null);
	}

	private void updateFiles(IJavaProject project, DocumentDescriptor[] updatedDocs, String content) throws Exception {
		if (!enabled.getAsBoolean() || updatedDocs == null) {
			return;
		}

		ProjectState state = projects.get(project.getElementName());
		if (state == null) {
			initializeProject(project, false);
			return;
		}

		List<DocumentDescriptor> docs = Arrays.stream(updatedDocs)
				.filter(doc -> isInterestedIn(doc.getDocURI()))
				.filter(doc -> isInSourceFolders(project, doc.getFile()))
				.toList();

		for (DocumentDescriptor doc : docs) {
			symbolHandler.removeSymbols(project, doc.getDocURI());
		}

		scan(project, state, docs, content);
	}

	@Override
	public void removeFiles(IJavaProject project, String[] docURIs) throws Exception {
		ProjectState state = projects.get(project.getElementName());
		if (state != null) {
			for (String docURI : docURIs) {
				state.declarations.remove(docURI);
				state.publishedDocs.remove(docURI);
			}
			state.resolver.clearCaches();
		}
	}

	@Override
	public List<DocumentSymbol> computeDocumentSymbols(IJavaProject project, String docURI, String content) throws Exception {
		if (!enabled.getAsBoolean() || content == null || !isInterestedIn(docURI)) {
			return Collections.emptyList();
		}

		// The symbols are computed while the symbol index is locked, on a request thread. So the state
		// of a project that is not indexed yet is not created here: that sets up the index of the
		// classpath, and it must not hold up (or block) the indexing of the project, which needs the
		// same lock. The outline is empty until the project is indexed.
		ProjectState state = projects.get(project.getElementName());
		if (state == null) {
			return Collections.emptyList();
		}

		String file = UriUtil.toFileString(docURI);
		KotlinSyntax.File syntax = reader.read(fileName(file), content);

		// the file takes part with what the editor has, in the declarations of the project
		state.declarations.put(docURI, syntax);
		state.resolver.clearCaches();

		SpringIndexerJavaScanResult result = new SpringIndexerJavaScanResult(project, new String[] { file });
		scanFile(project, new AnnotationHierarchies(), state, docURI, file, 0, content, syntax, result);

		return SpringIndexToSymbolsConverter.createDocumentSymbols(result.getGeneratedIndexElements().stream()
				.map(CachedIndexElement::getIndexElement)
				.filter(element -> element != null)
				.toList());
	}

	/**
	 * Indexes the documents in two passes: all of them are parsed first and registered in the
	 * declarations of the project, since the classes in the files refer to each other. Then they
	 * are indexed.
	 *
	 * @param content the content of the document if there is exactly one document, and its content
	 *        is not the one on disk, null otherwise
	 */
	private void scan(IJavaProject project, ProjectState state, List<DocumentDescriptor> docs, String content) {
		Map<DocumentDescriptor, String> contents = new java.util.LinkedHashMap<>();
		Map<DocumentDescriptor, KotlinSyntax.File> syntaxes = new java.util.LinkedHashMap<>();

		for (DocumentDescriptor doc : docs) {
			try {
				String text = content != null && docs.size() == 1 ? content : Files.readString(Paths.get(doc.getFile()), StandardCharsets.UTF_8);
				KotlinSyntax.File syntax = reader.read(fileName(doc.getFile()), text);

				contents.put(doc, text);
				syntaxes.put(doc, syntax);
				state.declarations.put(doc.getDocURI(), syntax);
			}
			catch (IOException | RuntimeException e) {
				log.error("error parsing kotlin file {}", doc.getFile(), e);
			}
		}

		state.resolver.clearCaches();

		SpringIndexerJavaScanResult result = new SpringIndexerJavaScanResult(project, docs.stream().map(DocumentDescriptor::getFile).toArray(String[]::new));
		AnnotationHierarchies hierarchies = new AnnotationHierarchies();

		for (DocumentDescriptor doc : syntaxes.keySet()) {
			try {
				scanFile(project, hierarchies, state, doc.getDocURI(), doc.getFile(), doc.getLastModified(), contents.get(doc), syntaxes.get(doc), result);
				state.publishedDocs.add(doc.getDocURI());
			}
			catch (RuntimeException e) {
				log.error("error indexing kotlin file {}", doc.getFile(), e);
			}
		}

		result.publishResults(symbolHandler);
	}

	private void scanFile(IJavaProject project, AnnotationHierarchies hierarchies, ProjectState state, String docURI, String file,
			long lastModified, String content, KotlinSyntax.File syntax, SpringIndexerJavaScanResult result) {

		KotlinDomBuilder.Result built = domBuilder.build(syntax, state.resolver);
		AnnotationHierarchies.set(built.compilationUnit(), hierarchies);

		TextDocument doc = DocumentUtils.createTempTextDocument(docURI, content);
		SpringIndexerJavaContext context = new SpringIndexerJavaContext(project, built.compilationUnit(), docURI, file, lastModified, doc,
				content, NO_OP_PROBLEM_COLLECTOR, new ArrayList<>(), true, false, result, LombokSettings.DISABLED);

		astScanner.scanAST(context, false, new ReconcilingIndex(), false);
	}

	/**
	 * The Kotlin files of the project. Kotlin sources live next to the Java sources, in a
	 * {@code kotlin} folder that sits beside the {@code java} folder, or in the Java source folder itself.
	 * (The classpath of the project does not know about Kotlin source folders yet.)
	 */
	private List<Path> kotlinFiles(IJavaProject project) {
		List<Path> files = new ArrayList<>();
		for (Path root : kotlinSourceFolders(project)) {
			if (Files.isDirectory(root)) {
				try (Stream<Path> walk = Files.walk(root)) {
					walk.filter(path -> path.getFileName().toString().endsWith(".kt")).filter(Files::isRegularFile).forEach(files::add);
				}
				catch (IOException e) {
					log.error("error looking for kotlin files in {}", root, e);
				}
			}
		}
		return files.stream().map(Path::toAbsolutePath).distinct().sorted().toList();
	}

	private Set<Path> kotlinSourceFolders(IJavaProject project) {
		Set<Path> roots = new java.util.LinkedHashSet<>();

		Stream<File> javaFolders = IClasspathUtil.getProjectJavaSourceFoldersWithoutTests(project.getClasspath());
		if (scanTestSources.getAsBoolean()) {
			javaFolders = Stream.concat(javaFolders, IClasspathUtil.getProjectTestJavaSources(project.getClasspath()));
		}

		javaFolders.map(File::toPath).forEach(folder -> {
			roots.add(folder);
			if ("java".equals(folder.getFileName().toString()) && folder.getParent() != null) {
				roots.add(folder.getParent().resolve("kotlin"));
			}
		});

		// the usual places, also for projects whose build does not name them as source folders
		if (project.getLocationUri() != null) {
			try {
				Path base = Paths.get(project.getLocationUri());
				roots.add(base.resolve("src").resolve("main").resolve("kotlin"));
				if (scanTestSources.getAsBoolean()) {
					roots.add(base.resolve("src").resolve("test").resolve("kotlin"));
				}
			}
			catch (RuntimeException e) {
				log.debug("cannot determine the location of project {}", project.getElementName(), e);
			}
		}

		return roots;
	}

	private boolean isInSourceFolders(IJavaProject project, String file) {
		Path path = Paths.get(file).toAbsolutePath();
		return kotlinSourceFolders(project).stream().anyMatch(root -> path.startsWith(root.toAbsolutePath()));
	}

	private static String fileName(String file) {
		return new File(file).getName();
	}

}
