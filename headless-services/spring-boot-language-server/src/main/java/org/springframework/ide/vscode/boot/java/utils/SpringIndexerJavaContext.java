/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.springframework.ide.vscode.boot.java.beans.CachedIndexElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Martin Lippert
 */
public class SpringIndexerJavaContext {

	private final IJavaProject project;
	private final CompilationUnit cu;
	private final String docURI;
	private final String file;
	private final long lastModified;
	private final TextDocument doc;
	private final String content;
	private final IProblemCollector getProblemCollector;
	private final List<String> nextPassFiles;
	private final boolean fullAst;
	private final boolean isIndexComplete;
	private final SpringIndexerJavaScanResult scanResult;
	
	private final Set<String> dependencies = new HashSet<>();
	private final Set<String> scannedTypes = new HashSet<>();

	public SpringIndexerJavaContext(
			IJavaProject project, 
			CompilationUnit cu, 
			String docURI, 
			String file, 
			long lastModified,
			TextDocument doc, 
			String content, 
			IProblemCollector problemCollector,
			List<String> nextPassFiles,
			boolean fullAst,
			boolean isIndexComplete,
			SpringIndexerJavaScanResult scanResult
	) {
		super();
		this.project = project;
		this.cu = cu;
		this.docURI = docURI;
		this.file = file;
		this.lastModified = lastModified;
		this.doc = doc;
		this.content = content;
		this.getProblemCollector = problemCollector;
		this.nextPassFiles = nextPassFiles;
		this.fullAst = fullAst;
		this.isIndexComplete = isIndexComplete;
		this.scanResult = scanResult;
	}

	public IJavaProject getProject() {
		return project;
	}

	public CompilationUnit getCu() {
		return cu;
	}

	public String getDocURI() {
		return docURI;
	}

	public String getFile() {
		return file;
	}

	public long getLastModified() {
		return lastModified;
	}

	public TextDocument getDoc() {
		return doc;
	}

	public String getContent() {
		return content;
	}
	
	public SpringIndexerJavaScanResult getResult() {
		return scanResult;
	}

	public List<CachedSymbol> getGeneratedSymbols() {
		return getResult().getGeneratedSymbols();
	}
	
	public List<CachedIndexElement> getGeneratedIndexElements() {
		return getResult().getGeneratedIndexElements();
	}
	
	public List<String> getNextPassFiles() {
		return nextPassFiles;
	}

	public Set<String> getDependencies() {
		return dependencies;
	}
	
	public void addDependency(ITypeBinding dependsOn) {
		if (dependsOn != null && dependsOn.isFromSource()) {
			String type = dependsOn.getQualifiedName();
		
			if (type != null && !scannedTypes.contains(type)) {
				dependencies.add(type);
			}
		}
	}
	
	public void addDependency(String qualifiedTypeName) {
		if (qualifiedTypeName != null) {
			dependencies.add(qualifiedTypeName);
		}
	}

	public Set<String> getScannedTypes() {
		return scannedTypes;
	}
	
	public void addScannedType(ITypeBinding scannedType) {
		if (scannedType != null) {
			String type = scannedType.getQualifiedName();
			scannedTypes.add(type);
			dependencies.remove(type);
		}
	}

	public IProblemCollector getProblemCollector() {
		return this.getProblemCollector;
	}
	
	public boolean isFullAst() {
		return fullAst;
	}
	
	public boolean isIndexComplete() {
		return isIndexComplete;
	}
	
	public void resetDocumentRelatedElements(String docURI) {
		Iterator<CachedIndexElement> beansIterator = getGeneratedIndexElements().iterator();
		while (beansIterator.hasNext()) {
			if (beansIterator.next().getDocURI().equals(docURI)) {
				beansIterator.remove();
			}
		}
		
		Iterator<CachedSymbol> symbolsIterator = getGeneratedSymbols().iterator();
		while (symbolsIterator.hasNext()) {
			if (symbolsIterator.next().getDocURI().equals(docURI)) {
				symbolsIterator.remove();
			}
		}
		
	}

}
