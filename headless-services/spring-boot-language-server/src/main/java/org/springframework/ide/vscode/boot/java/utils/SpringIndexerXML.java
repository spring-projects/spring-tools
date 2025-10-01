/*******************************************************************************
 * Copyright (c) 2019, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.dom.DOMParser;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.index.SpringIndexToSymbolsConverter;
import org.springframework.ide.vscode.boot.index.cache.IndexCache;
import org.springframework.ide.vscode.boot.index.cache.IndexCacheKey;
import org.springframework.ide.vscode.boot.java.beans.CachedIndexElement;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.util.UriUtil;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Martin Lippert
 */
public class SpringIndexerXML implements SpringIndexer {

	private static final Logger log = LoggerFactory.getLogger(SpringIndexerJava.class);
	
	// whenever the implementation of the indexer changes in a way that the stored data in the cache is no longer valid,
	// we need to change the generation - this will result in a re-indexing due to no up-to-date cache data being found
	private static final String GENERATION = "GEN-12";

	private static final String SYMBOL_KEY = "symbols";
	private static final String INDEX_KEY = "index";

	private final SymbolHandler symbolHandler;
	private final Map<String, SpringIndexerXMLNamespaceHandler> namespaceHandler;
	private final IndexCache cache;
	private final JavaProjectFinder projectFinder;
	
	private String[] scanFolders = new String[0];

	public SpringIndexerXML(SymbolHandler handler, Map<String, SpringIndexerXMLNamespaceHandler> namespaceHandler,
			IndexCache cache, JavaProjectFinder projectFinder) {
		this.symbolHandler = handler;
		this.namespaceHandler = namespaceHandler;
		this.cache = cache;
		this.projectFinder = projectFinder;
	}

	public boolean updateScanFolders(String[] scanFoldes) {
		if (!Arrays.equals(this.scanFolders, scanFoldes)) {
			clearIndex();
			this.scanFolders = scanFoldes;
			populateIndex();
			return true;
		}
		return false;
	}

	@Override
	public String[] getFileWatchPatterns() {
		String[] patterns = new String[scanFolders.length * 2];
		for (int i = 0; i < scanFolders.length; i+=2) {
			StringBuilder sb = new StringBuilder();
			sb.append("**/");
			sb.append(scanFolders[i]);
			sb.append('/');
			StringBuilder pattern1 = new StringBuilder(sb);
			pattern1.append("*.xml");
			patterns[i] = pattern1.toString();
			
			StringBuilder pattern2 = new StringBuilder(sb);
			pattern2.append("**/");
			pattern2.append("*.xml");
			patterns[i + 1] = pattern2.toString();
		}
		return patterns;
	}

	@Override
	public boolean isInterestedIn(String resource) {
		return resource.endsWith(".xml");
	}

	@Override
	public void initializeProject(IJavaProject project, boolean clean) throws Exception {
		long startTime = System.currentTimeMillis();
		String[] files = this.getFiles(project);

		log.info("scan xml files for symbols for project: " + project.getElementName() + " - no. of files: " + files.length);

		IndexCacheKey symbolsCacheKey = getCacheKey(project, SYMBOL_KEY);
		IndexCacheKey indexCacheKey = getCacheKey(project, INDEX_KEY);

		CachedSymbol[] symbols = this.cache.retrieveSymbols(symbolsCacheKey, files, CachedSymbol.class);
		CachedIndexElement[] indexElements = this.cache.retrieveSymbols(indexCacheKey, files, CachedIndexElement.class);

		if (symbols == null || indexElements == null || clean) {
			List<CachedSymbol> generatedSymbols = new ArrayList<CachedSymbol>();
			List<CachedIndexElement> generatedIndexElements = new ArrayList<CachedIndexElement>();

			for (String file : files) {
				scanFile(project, file, generatedSymbols, generatedIndexElements);
			}

			this.cache.store(symbolsCacheKey, files, generatedSymbols, null, CachedSymbol.class);
			this.cache.store(indexCacheKey, files, generatedIndexElements, null, CachedIndexElement.class);

			symbols = (CachedSymbol[]) generatedSymbols.toArray(new CachedSymbol[generatedSymbols.size()]);
			indexElements = (CachedIndexElement[]) generatedIndexElements.toArray(new CachedIndexElement[generatedIndexElements.size()]);
		}
		else {
			log.info("scan xml files used cached data: " + project.getElementName() + " - no. of cached symbols retrieved: " + symbols.length);
		}

		if (symbols != null && indexElements != null) {
			WorkspaceSymbol[] enhancedSymbols = Arrays.stream(symbols).map(cachedSymbol -> cachedSymbol.getEnhancedSymbol()).toArray(WorkspaceSymbol[]::new);
			Map<String, List<SpringIndexElement>> allIndexElements = Arrays.stream(indexElements).filter(cachedIndexElement -> cachedIndexElement.getIndexElement() != null).collect(Collectors.groupingBy(CachedIndexElement::getDocURI, Collectors.mapping(CachedIndexElement::getIndexElement, Collectors.toList())));
			symbolHandler.addSymbols(project, enhancedSymbols, allIndexElements, null);
		}

		long endTime = System.currentTimeMillis();

		log.info("scan xml files for symbols for project: " + project.getElementName() + " took ms: " + (endTime - startTime) + " Symbols Found: " + symbols.length);
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

		List<CachedSymbol> generatedSymbols = new ArrayList<CachedSymbol>();
		List<CachedIndexElement> generatedIndexElements = new ArrayList<CachedIndexElement>();

		String docURI = updatedDoc.getDocURI();

		scanFile(project, content, docURI, updatedDoc.getLastModified(), generatedSymbols, generatedIndexElements);

		IndexCacheKey symbolsCacheKey = getCacheKey(project, SYMBOL_KEY);
		IndexCacheKey indexCacheKey = getCacheKey(project, INDEX_KEY);

		String file = new File(new URI(docURI)).getAbsolutePath();
		this.cache.update(symbolsCacheKey, file, updatedDoc.getLastModified(), generatedSymbols, null, CachedSymbol.class);
		this.cache.update(indexCacheKey, file, updatedDoc.getLastModified(), generatedIndexElements, null, CachedIndexElement.class);

		WorkspaceSymbol[] symbols = generatedSymbols.stream().map(cachedSymbol -> cachedSymbol.getEnhancedSymbol()).toArray(WorkspaceSymbol[]::new);
		List<SpringIndexElement> indexElements = generatedIndexElements.stream().filter(cachedBean -> cachedBean.getIndexElement() != null).map(cachedBean -> cachedBean.getIndexElement()).toList();
		symbolHandler.addSymbols(project, docURI, symbols, indexElements, null);
	}

	@Override
	public void updateFiles(IJavaProject project, DocumentDescriptor[] updatedDocs) throws Exception {

		for (DocumentDescriptor updatedDoc : updatedDocs) {
			String docURI = updatedDoc.getDocURI();
			
			this.symbolHandler.removeSymbols(project, docURI);

			Path path = new File(new URI(docURI)).toPath();
			String content = new String(Files.readAllBytes(path));

			List<CachedSymbol> generatedSymbols = new ArrayList<CachedSymbol>();
			List<CachedIndexElement> generatedIndexElements = new ArrayList<CachedIndexElement>();
			scanFile(project, content, docURI, updatedDoc.getLastModified(), generatedSymbols, generatedIndexElements);
	
			IndexCacheKey symbolCacheKey = getCacheKey(project, SYMBOL_KEY);
			IndexCacheKey indexCacheKey = getCacheKey(project, INDEX_KEY);

			String file = new File(new URI(docURI)).getAbsolutePath();
			this.cache.update(symbolCacheKey, file, updatedDoc.getLastModified(), generatedSymbols, null, CachedSymbol.class);
			this.cache.update(indexCacheKey, file, updatedDoc.getLastModified(), generatedIndexElements, null, CachedIndexElement.class);
			
			WorkspaceSymbol[] symbols = generatedSymbols.stream().map(cachedSymbol -> cachedSymbol.getEnhancedSymbol()).toArray(WorkspaceSymbol[]::new);
			List<SpringIndexElement> indexElements = generatedIndexElements.stream().filter(cachedBean -> cachedBean.getIndexElement() != null).map(cachedBean -> cachedBean.getIndexElement()).toList();
			symbolHandler.addSymbols(project, docURI, symbols, indexElements, null);
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
		IndexCacheKey indexCacheKey = getCacheKey(project, INDEX_KEY);
		
		this.cache.removeFiles(symbolsCacheKey, files, CachedSymbol.class);
		this.cache.removeFiles(indexCacheKey, files, CachedIndexElement.class);
	}

	private void scanFile(IJavaProject project, String fileName, List<CachedSymbol> generatedSymbols, List<CachedIndexElement> generatedIndexElements) {
		log.debug("starting to parse XML file for Spring symbol indexing: {}", fileName);

		try {
			File file = new File(fileName);
			long lastModified = file.lastModified();

			String docURI = UriUtil.toUri(file).toASCIIString();
			String fileContent = FileUtils.readFileToString(file, Charset.defaultCharset());

	        scanFile(project, fileContent, docURI, lastModified, generatedSymbols, generatedIndexElements);
		}
		catch (Exception e) {
			log.error("error parsing XML file: ", e);
		}
	}

	private void scanFile(IJavaProject project, String fileContent, String docURI, long lastModified, List<CachedSymbol> generatedSymbols,
			List<CachedIndexElement> generatedIndexElements) throws Exception {
		DOMParser parser = DOMParser.getInstance();
		DOMDocument document = parser.parse(fileContent, "", null);

		AtomicReference<TextDocument> docRef = new AtomicReference<>();
		scanNode(document, project, docURI, lastModified, docRef, fileContent, generatedSymbols, generatedIndexElements);
	}

	private void scanNode(DOMNode node, IJavaProject project, String docURI, long lastModified, AtomicReference<TextDocument> docRef, String content,
			List<CachedSymbol> generatedSymbols, List<CachedIndexElement> generatedIndexElements) throws Exception {

		String namespaceURI = node.getNamespaceURI();

		if (namespaceURI != null && this.namespaceHandler.containsKey(namespaceURI)) {
			SpringIndexerXMLNamespaceHandler namespaceHandler = this.namespaceHandler.get(namespaceURI);

			TextDocument document = DocumentUtils.getTempTextDocument(docURI, docRef, content);
			namespaceHandler.processNode(node, project, docURI, lastModified, document, generatedSymbols, generatedIndexElements);
		}


//		if ("http://www.springframework.org/schema/beans".equals(namespaceURI)) {
//			List<DOMAttr> attributeNodes = node.getAttributeNodes();
//			if (attributeNodes != null) {
//				for (DOMAttr attribute : attributeNodes) {
//				}
//			}
//		}

		List<DOMNode> children = node.getChildren();
		for (DOMNode child : children) {
			scanNode(child, project, docURI, lastModified, docRef, content, generatedSymbols, generatedIndexElements);
		}


	}

	private String[] getFiles(IJavaProject project) throws Exception {
		long start = System.currentTimeMillis();
		Path projectPath = new File(project.getLocationUri()).toPath();
		String[] xmlFiles = Arrays.stream(scanFolders)
			.map(folder -> projectPath.resolve(folder))
			.filter(Files::isDirectory)
			.flatMap(folder -> {
				try {
					return Files.walk(folder);
				} catch (IOException e) {
					log.error("", e);
					return Stream.empty();
				}
			})
			.filter(Files::isRegularFile)
			.filter(file -> file.getFileName().toString().endsWith(".xml"))
			.map(file -> file.toString())
			.toArray(String[]::new);
		
		log.info("Found {} XML files to scan in {}ms", xmlFiles.length, System.currentTimeMillis() - start);
		return xmlFiles;
	}

	private IndexCacheKey getCacheKey(IJavaProject project, String elementType) {
		IClasspath classpath = project.getClasspath();
		Stream<File> classpathEntries = IClasspathUtil.getAllBinaryRoots(classpath).stream();

		String classpathIdentifier = classpathEntries
				.filter(file -> file.exists())
				.map(file -> file.getAbsolutePath() + "#" + file.lastModified())
				.collect(Collectors.joining(","));

		return new IndexCacheKey(project.getElementName(), "xml", elementType, DigestUtils.md5Hex(GENERATION + "-" + classpathIdentifier).toUpperCase());
	}
	
	private void clearIndex() {
		for (IJavaProject project : projectFinder.all()) {
			try {
				String[] files = getFiles(project);
				
				if (files.length > 0) {
					String[] docURIs = new String[files.length];
					for (int i = 0; i < files.length; i++) {

						String docURI = UriUtil.toUri(new File(files[i])).toASCIIString();
						symbolHandler.removeSymbols(project, docURI);
						docURIs[i] = docURI;
					}
					
					removeFiles(project, docURIs);
				}
			} catch (Exception e) {
				log.error("{}", e);
			}
		}
	}
	
	private void populateIndex() {
		for (IJavaProject project : projectFinder.all()) {
			try {
				initializeProject(project, true);
			} catch (Exception e) {
				log.error("{}", e);
			}
		}
	}

	@Override
	public List<WorkspaceSymbol> computeSymbols(IJavaProject project, String docURI, String content) throws Exception {
		if (content != null) {
	        List<CachedSymbol> generatedSymbols = new ArrayList<>();
	        List<CachedIndexElement> generatedIndexElements = new ArrayList<>();

	        scanFile(project, content, docURI, 0, generatedSymbols, generatedIndexElements);
			return generatedSymbols.stream().map(s -> s.getEnhancedSymbol()).collect(Collectors.toList());			
		}

		return Collections.emptyList();
	}

	@Override
	public List<DocumentSymbol> computeDocumentSymbols(IJavaProject project, String docURI, String content) throws Exception {
		if (content != null) {
	        List<CachedSymbol> generatedSymbols = new ArrayList<>();
	        List<CachedIndexElement> generatedIndexElements = new ArrayList<>();

	        scanFile(project, content, docURI, 0, generatedSymbols, generatedIndexElements);
	        
	        return SpringIndexToSymbolsConverter.createDocumentSymbols(generatedIndexElements.stream().map(cachedIndexElement -> cachedIndexElement.getIndexElement()).toList());
		}

		return Collections.emptyList();
	}
	


}
