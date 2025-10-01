/*******************************************************************************
 * Copyright (c) 2023, 2025 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolLocation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.index.cache.IndexCache;
import org.springframework.ide.vscode.boot.index.cache.IndexCacheKey;
import org.springframework.ide.vscode.boot.java.beans.BeanUtils;
import org.springframework.ide.vscode.boot.java.beans.BeansIndexer;
import org.springframework.ide.vscode.boot.java.beans.CachedIndexElement;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.DefaultValues;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.Region;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.java.properties.antlr.parser.AntlrParser;
import org.springframework.ide.vscode.java.properties.parser.PropertiesAst;
import org.springframework.ide.vscode.java.properties.parser.PropertiesAst.KeyValuePair;

import com.google.common.collect.ImmutableList;

public class SpringFactoriesIndexer implements SpringIndexer {
	
	private static final Logger log = LoggerFactory.getLogger(SpringFactoriesIndexer.class);
	
	// whenever the implementation of the indexer changes in a way that the stored data in the cache is no longer valid,
	// we need to change the generation - this will result in a re-indexing due to no up-to-date cache data being found
	private static final String GENERATION = "GEN-14";
	
	private static final String SYMBOL_KEY = "symbols";
	private static final String INDEX_KEY = "index";
	
	private static final String FILE_PATTERN = "**/META-INF/spring/*.factories";
	
	private static final PathMatcher FILE_GLOB_PATTERN = FileSystems.getDefault().getPathMatcher("glob:" + FILE_PATTERN);
	
	private static final Set<String> KEYS = Set.of(
			"org.springframework.aot.hint.RuntimeHintsRegistrar",
			"org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor",
			"org.springframework.beans.factory.aot.BeanRegistrationAotProcessor"
	); 
	
	private final SymbolHandler symbolHandler;
	private final IndexCache cache;
	
	public SpringFactoriesIndexer(SymbolHandler symbolHandler, IndexCache cache) {
		super();
		this.symbolHandler = symbolHandler;
		this.cache = cache;
	}

	@Override
	public String[] getFileWatchPatterns() {
		return new String[] {
				FILE_PATTERN
		};
	}

	@Override
	public boolean isInterestedIn(String resource) {
		if (resource.endsWith(".factories")) {
			Path path = Paths.get(URI.create(resource));
			return FILE_GLOB_PATTERN.matches(path);
		}
		return false;
	}

	@Override
	public List<WorkspaceSymbol> computeSymbols(IJavaProject project, String docURI, String content)
			throws Exception {
		return computeSymbols(docURI, content).symbols;
	}
	
	@Override
	public List<DocumentSymbol> computeDocumentSymbols(IJavaProject project, String docURI, String content) throws Exception {
		return computeSymbols(docURI, content).symbols.stream()
				.map(workspaceSymbol -> convertToDocumentSymbol(workspaceSymbol))
				.toList();
	}
	
	private DocumentSymbol convertToDocumentSymbol(WorkspaceSymbol workspaceSymbol) {
		Either<Location, WorkspaceSymbolLocation> location = workspaceSymbol.getLocation();
		
		Range range = null;
		if (location.isLeft()) {
			Location l = location.getLeft();
			range = l.getRange();
		}
		else if (location.isRight()) {
			range = new Range();
		}
		
		return new DocumentSymbol(workspaceSymbol.getName(), workspaceSymbol.getKind(), range, range);
	}

	private ComputeResult computeSymbols(String docURI, String content) {
		ImmutableList.Builder<WorkspaceSymbol> symbols = ImmutableList.builder();
		ImmutableList.Builder<Bean> beans = ImmutableList.builder();

		PropertiesAst ast = new AntlrParser().parse(content).ast;
		if (ast != null) {
			for (KeyValuePair pair : ast.getPropertyValuePairs()) {
				String key = pair.getKey().decode();
				
				if (KEYS.contains(key)) {
					String value = pair.getValue().decode();
					
					TextDocument doc = new TextDocument(docURI, LanguageId.SPRING_FACTORIES, 0, content);
					
					for(String fqName : value.split("\\s*,\\s*")) {
						try {
							String simpleName = getSimpleName(fqName);
							String beanId = BeanUtils.getBeanNameFromType(simpleName);
							Range range = doc.toRange(new Region(pair.getOffset(), pair.getLength()));
							Location location = new Location(docURI, range);
							
							WorkspaceSymbol symbol = new WorkspaceSymbol(
									BeansIndexer.beanLabel(beanId, fqName, Paths.get(URI.create(docURI)).getFileName().toString()),
									SymbolKind.Interface,
									Either.forLeft(location));

							symbols.add(symbol);
							
							Bean bean = new Bean(beanId, fqName, location, DefaultValues.EMPTY_INJECTION_POINTS, Collections.emptySet(), DefaultValues.EMPTY_ANNOTATIONS, false, symbol.getName());
							beans.add(bean);

						} catch (Exception e) {
							log.error("", e);
						}
					}
				}
			}
		}
		return new ComputeResult(symbols.build(), beans.build());
	}
	
	private static String getSimpleName(String fqName) {
		int idx = fqName.lastIndexOf('.');
		if (idx >= 0 && idx < fqName.length() - 1) {
			return fqName.substring(idx + 1);
		}
		return fqName;
	}
	
	@Override
	public void initializeProject(IJavaProject project, boolean clean) throws Exception {
		long startTime = System.currentTimeMillis();
		List<Path> files = getFiles(project);
		String[] filesStr = files.stream().map(f -> f.toAbsolutePath().toString()).toArray(String[]::new);

		log.info("scan factories files for symbols for project: " + project.getElementName() + " - no. of files: " + files.size());

		IndexCacheKey symbolsCacheKey = getCacheKey(project, SYMBOL_KEY);
		IndexCacheKey indexCacheKey = getCacheKey(project, INDEX_KEY);

		CachedSymbol[] symbols = this.cache.retrieveSymbols(symbolsCacheKey, filesStr, CachedSymbol.class);
		CachedIndexElement[] indexElements = this.cache.retrieveSymbols(indexCacheKey, filesStr, CachedIndexElement.class);

		if (symbols == null || indexElements == null || clean) {
			List<CachedSymbol> generatedSymbols = new ArrayList<CachedSymbol>();
			List<CachedIndexElement> generatedIndexElements = new ArrayList<CachedIndexElement>();

			for (Path file : files) {
				ScanResult scanResult = scanFile(file);
				
				if (scanResult != null) {
					generatedSymbols.addAll(scanResult.symbols);
					generatedIndexElements.addAll(scanResult.beans);
				}
			}

			this.cache.store(symbolsCacheKey, filesStr, generatedSymbols, null, CachedSymbol.class);
			this.cache.store(indexCacheKey, filesStr, generatedIndexElements, null, CachedIndexElement.class);

			symbols = (CachedSymbol[]) generatedSymbols.toArray(new CachedSymbol[generatedSymbols.size()]);
			indexElements = (CachedIndexElement[]) generatedIndexElements.toArray(new CachedIndexElement[generatedIndexElements.size()]);
		}
		else {
			log.info("scan factories files used cached data: " + project.getElementName() + " - no. of cached symbols retrieved: " + symbols.length);
		}

		if (symbols != null && indexElements != null) {
			WorkspaceSymbol[] enhancedSymbols = Arrays.stream(symbols).map(cachedSymbol -> cachedSymbol.getEnhancedSymbol()).toArray(WorkspaceSymbol[]::new);
			Map<String, List<SpringIndexElement>> beansByDoc = Arrays.stream(indexElements).filter(cachedBean -> cachedBean.getIndexElement() != null).collect(Collectors.groupingBy(CachedIndexElement::getDocURI, Collectors.mapping(CachedIndexElement::getIndexElement, Collectors.toList())));
			symbolHandler.addSymbols(project, enhancedSymbols, beansByDoc, null);
		}

		long endTime = System.currentTimeMillis();

		log.info("scan factories files for symbols for project: " + project.getElementName() + " took ms: " + (endTime - startTime));

	}
	
	@Override
	public void removeProject(IJavaProject project) throws Exception {
		IndexCacheKey symbolsCacheKey = getCacheKey(project, SYMBOL_KEY);
		IndexCacheKey indexCacheKey = getCacheKey(project, INDEX_KEY);

		this.cache.remove(symbolsCacheKey);
		this.cache.remove(indexCacheKey);
	}

	@Override
	public void updateFile(IJavaProject project, DocumentDescriptor updatedDoc, String content) throws Exception {
		this.symbolHandler.removeSymbols(project, updatedDoc.getDocURI());

		List<Path> outputFolders = IClasspathUtil.getOutputFolders(project.getClasspath()).map(f -> f.toPath()).collect(Collectors.toList());
		String docURI = updatedDoc.getDocURI();
		Path path = Paths.get(URI.create(docURI));
		
		if (!outputFolders.stream().anyMatch(out -> path.startsWith(out))) {
			
			ScanResult scanResult = scanFile(path);
			
			List<CachedSymbol> generatedSymbols = Collections.emptyList();
			List<CachedIndexElement> generatedIndexElements = Collections.emptyList(); 

			if (scanResult != null) {
				generatedSymbols = scanResult.symbols;
				generatedIndexElements = scanResult.beans; 
			}

			IndexCacheKey symbolsCacheKey = getCacheKey(project, SYMBOL_KEY);
			IndexCacheKey indexCacheKey = getCacheKey(project, INDEX_KEY);

			String file = new File(new URI(docURI)).getAbsolutePath();
			this.cache.update(symbolsCacheKey, file, updatedDoc.getLastModified(), generatedSymbols, null, CachedSymbol.class);
			this.cache.update(indexCacheKey, file, updatedDoc.getLastModified(), generatedIndexElements, null, CachedIndexElement.class);

			WorkspaceSymbol[] symbols = generatedSymbols.stream().map(cachedSymbol -> cachedSymbol.getEnhancedSymbol()).toArray(WorkspaceSymbol[]::new);
			List<SpringIndexElement> beans = generatedIndexElements.stream().filter(cachedBean -> cachedBean.getIndexElement() != null).map(cachedBean -> cachedBean.getIndexElement()).toList();
			symbolHandler.addSymbols(project, docURI, symbols, beans, null);
		}
	}

	@Override
	public void updateFiles(IJavaProject project, DocumentDescriptor[] updatedDocs) throws Exception {
		IndexCacheKey symbolsCacheKey = getCacheKey(project, SYMBOL_KEY);
		IndexCacheKey indexCacheKey = getCacheKey(project, INDEX_KEY);

		List<Path> outputFolders = IClasspathUtil.getOutputFolders(project.getClasspath()).map(f -> f.toPath()).collect(Collectors.toList());

		for (DocumentDescriptor d : updatedDocs) {
			Path path = Paths.get(URI.create(d.getDocURI()));

			if (!outputFolders.stream().anyMatch(out -> path.startsWith(out))) {

				if (Files.isRegularFile(path)) {
					
					updateFile(project, d, Files.readString(path));

				} else {
					String file = new File(new URI(d.getDocURI())).getAbsolutePath();

					cache.removeFile(symbolsCacheKey, file, CachedSymbol.class);
					cache.removeFile(indexCacheKey, file, CachedIndexElement.class);
				}
			}
		}
	}

	@Override
	public void removeFiles(IJavaProject project, String[] docURIs) throws Exception {
		String[] files = Arrays.stream(docURIs).map(docURI -> {
			try {
				return new File(new URI(docURI)).getAbsolutePath();
			} catch (URISyntaxException e) {
				throw new RuntimeException(e);
			}
		}).toArray(String[]::new);
		
		IndexCacheKey symbolsCacheKey = getCacheKey(project, SYMBOL_KEY);
		IndexCacheKey indexCacheKey = getCacheKey(project, SYMBOL_KEY);

		cache.removeFiles(symbolsCacheKey, files, CachedSymbol.class);
		cache.removeFiles(indexCacheKey, files, CachedIndexElement.class);
	}
	
	private ScanResult scanFile(Path file) {
		try {

			String content = Files.readString(file);
			
			ImmutableList.Builder<CachedSymbol> symbols = ImmutableList.builder();
			ImmutableList.Builder<CachedIndexElement> beans = ImmutableList.builder();
			
			long lastModified = Files.getLastModifiedTime(file).toMillis();
			String docUri = file.toUri().toASCIIString();

			ComputeResult result = computeSymbols(docUri, content);
			
			for (WorkspaceSymbol symbol : result.symbols) {
				symbols.add(new CachedSymbol(docUri, lastModified, symbol));
			}

			for (Bean bean : result.beans) {
				beans.add(new CachedIndexElement(docUri, bean));
			}

			return new ScanResult(symbols.build(), beans.build());

		} catch (IOException e) {
			log.error("", e);
			return null;
		}
	}

	private List<Path> getFiles(IJavaProject project) {
		try {
			return project.getClasspath().getClasspathEntries().stream()
				.filter(Classpath::isProjectSource)
				.map(cpe -> new File(cpe.getPath()).toPath())
				.map(p -> p.resolve("META-INF").resolve("spring"))
				.filter(Files::isDirectory)
				.flatMap(d -> {
					try {
						return Files.list(d);
					} catch (IOException e) {
						// ignore
						return Stream.empty();
					}
				})
				.filter(p -> p.toString().endsWith(".factories"))
				.collect(Collectors.toList());
		} catch (Exception e) {
			log.error("", e);
			return Collections.emptyList();
		}
			
	}

	private IndexCacheKey getCacheKey(IJavaProject project, String elementType) {
		String filesIndentifier = getFiles(project).stream()
				.filter(f -> Files.isRegularFile(f))
				.map(f -> {
					try {
						return f.toAbsolutePath().toString() + "#" + Files.getLastModifiedTime(f).toMillis();
					} catch (IOException e) {
						log.error("", e);
						return f.toAbsolutePath().toString() + "#0";
					}
				})
				.collect(Collectors.joining(","));
		return new IndexCacheKey(project.getElementName(), "factories", elementType, DigestUtils.md5Hex(GENERATION + "-" + filesIndentifier).toUpperCase());
	}
	
	private static record ScanResult (List<CachedSymbol> symbols, List<CachedIndexElement> beans) {}
	private static record ComputeResult (List<WorkspaceSymbol> symbols, List<Bean> beans) {}
	
}
