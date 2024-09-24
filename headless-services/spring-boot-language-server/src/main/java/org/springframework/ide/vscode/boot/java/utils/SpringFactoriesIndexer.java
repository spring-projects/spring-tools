/*******************************************************************************
 * Copyright (c) 2023, 2024 VMware, Inc.
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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.index.cache.IndexCache;
import org.springframework.ide.vscode.boot.index.cache.IndexCacheKey;
import org.springframework.ide.vscode.boot.java.beans.BeanUtils;
import org.springframework.ide.vscode.boot.java.beans.BeansSymbolAddOnInformation;
import org.springframework.ide.vscode.boot.java.beans.BeansSymbolProvider;
import org.springframework.ide.vscode.boot.java.beans.SpringFactoryInformation;
import org.springframework.ide.vscode.boot.java.handlers.EnhancedSymbolInformation;
import org.springframework.ide.vscode.boot.java.handlers.SymbolAddOnInformation;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
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
	private static final String GENERATION = "GEN-5";
	
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
	public boolean isInterestedIn(String docURI) {
		if (docURI.endsWith(".factories")) {
			Path path = Paths.get(URI.create(docURI));
			return FILE_GLOB_PATTERN.matches(path);
		}
		return false;
	}

	@Override
	public List<EnhancedSymbolInformation> computeSymbols(IJavaProject project, String docURI, String content)
			throws Exception {
		return computeSymbols(docURI, content);
	}
	
	private List<EnhancedSymbolInformation> computeSymbols(String docURI, String content) {
		ImmutableList.Builder<EnhancedSymbolInformation> symbols = ImmutableList.builder();
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
							symbols.add(new EnhancedSymbolInformation(new WorkspaceSymbol(
									BeansSymbolProvider.beanLabel(false, beanId, fqName, Paths.get(URI.create(docURI)).getFileName().toString()),
									SymbolKind.Interface,
									Either.forLeft(new Location(docURI, range))), new SymbolAddOnInformation[] {
											new BeansSymbolAddOnInformation(beanId, fqName),
											new SpringFactoryInformation(key)
									}));
						} catch (Exception e) {
							log.error("", e);
						}
					}
				}
			}
		}
		return symbols.build();
	}
	
	private static String getSimpleName(String fqName) {
		int idx = fqName.lastIndexOf('.');
		if (idx >= 0 && idx < fqName.length() - 1) {
			return fqName.substring(idx + 1);
		}
		return fqName;
	}
	
	private IndexCacheKey getCacheKey(IJavaProject project) {
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
		return new IndexCacheKey(project.getElementName(), "factories", "", DigestUtils.md5Hex(GENERATION + "-" + filesIndentifier).toUpperCase());
	}


	@Override
	public void initializeProject(IJavaProject project, boolean clean) throws Exception {
		long startTime = System.currentTimeMillis();
		List<Path> files = getFiles(project);
		String[] filesStr = files.stream().map(f -> f.toAbsolutePath().toString()).toArray(String[]::new);

		log.info("scan factories files for symbols for project: " + project.getElementName() + " - no. of files: " + files.size());

		IndexCacheKey cacheKey = getCacheKey(project);

		CachedSymbol[] symbols = this.cache.retrieveSymbols(cacheKey, filesStr, CachedSymbol.class);
		if (symbols == null || clean) {
			List<CachedSymbol> generatedSymbols = new ArrayList<CachedSymbol>();

			for (Path file : files) {
				generatedSymbols.addAll(scanFile(file));
			}

			this.cache.store(cacheKey, filesStr, generatedSymbols, null, CachedSymbol.class);

			symbols = (CachedSymbol[]) generatedSymbols.toArray(new CachedSymbol[generatedSymbols.size()]);
		}
		else {
			log.info("scan factories files used cached data: " + project.getElementName() + " - no. of cached symbols retrieved: " + symbols.length);
		}

		if (symbols != null) {
			EnhancedSymbolInformation[] enhancedSymbols = Arrays.stream(symbols).map(cachedSymbol -> cachedSymbol.getEnhancedSymbol()).toArray(EnhancedSymbolInformation[]::new);
			symbolHandler.addSymbols(project, enhancedSymbols, null, null);
		}

		long endTime = System.currentTimeMillis();

		log.info("scan factories files for symbols for project: " + project.getElementName() + " took ms: " + (endTime - startTime));

	}
	
	private List<CachedSymbol> scanFile(Path file) {
		try {
			String content = Files.readString(file);
			ImmutableList.Builder<CachedSymbol> builder = ImmutableList.builder();
			long lastModified = Files.getLastModifiedTime(file).toMillis();
			String docUri = file.toUri().toASCIIString();
			for (EnhancedSymbolInformation s : computeSymbols(docUri, content)) {
				builder.add(new CachedSymbol(docUri, lastModified, s));
			}
			return builder.build();
		} catch (IOException e) {
			log.error("", e);
			return Collections.emptyList();
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

	@Override
	public void removeProject(IJavaProject project) throws Exception {
		IndexCacheKey cacheKey = getCacheKey(project);
		this.cache.remove(cacheKey);
	}

	@Override
	public void updateFile(IJavaProject project, DocumentDescriptor updatedDoc, String content) throws Exception {
		this.symbolHandler.removeSymbols(project, updatedDoc.getDocURI());

		List<Path> outputFolders = IClasspathUtil.getOutputFolders(project.getClasspath()).map(f -> f.toPath()).collect(Collectors.toList());
		String docURI = updatedDoc.getDocURI();
		Path path = Paths.get(URI.create(docURI));
		if (!outputFolders.stream().anyMatch(out -> path.startsWith(out))) {
			List<CachedSymbol> generatedSymbols = scanFile(path);

			IndexCacheKey cacheKey = getCacheKey(project);
			String file = new File(new URI(docURI)).getAbsolutePath();
			this.cache.update(cacheKey, file, updatedDoc.getLastModified(), generatedSymbols, null, CachedSymbol.class);

			EnhancedSymbolInformation[] symbols = generatedSymbols.stream().map(cachedSymbol -> cachedSymbol.getEnhancedSymbol()).toArray(EnhancedSymbolInformation[]::new);
			symbolHandler.addSymbols(project, docURI, symbols, null, null);
		}
	}

	@Override
	public void updateFiles(IJavaProject project, DocumentDescriptor[] updatedDocs) throws Exception {
		IndexCacheKey key = getCacheKey(project);
		List<Path> outputFolders = IClasspathUtil.getOutputFolders(project.getClasspath()).map(f -> f.toPath()).collect(Collectors.toList());
		for (DocumentDescriptor d : updatedDocs) {
			Path path = Paths.get(URI.create(d.getDocURI()));
			if (!outputFolders.stream().anyMatch(out -> path.startsWith(out))) {
				if (Files.isRegularFile(path)) {
					updateFile(project, d, Files.readString(path));
				} else {
					String file = new File(new URI(d.getDocURI())).getAbsolutePath();
					cache.removeFile(key, file, CachedSymbol.class);
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
		
		IndexCacheKey key = getCacheKey(project);
		cache.removeFiles(key, files, CachedSymbol.class);
	}
	
}
