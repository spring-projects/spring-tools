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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.springframework.ide.vscode.boot.java.beans.CachedIndexElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
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
	private final IProblemCollector problemCollector;
	private final List<String> nextPassFiles;
	private final boolean fullAst;
	private final boolean isIndexComplete;
	private final SpringIndexerJavaScanResult scanResult;
	
	private final Set<QualifiedTypeName> dependencies = new HashSet<>();
	private final Set<QualifiedTypeName> scannedTypes = new HashSet<>();

	/**
	 * The declarations that got an index element of their own while scanning this file, and the
	 * types whose content hash therefore has to leave them out - see
	 * {@link #markAsOwnIndexElement(ASTNode)}. Filled by the indexers, consumed once they have all
	 * run (see {@code SpringIndexerJavaAstScanner.scanAST}).
	 */
	private final List<ASTNode> nodesWithOwnIndexElement = new ArrayList<>();
	private final Map<AbstractTypeDeclaration, StereotypeClassElement> typesToHash = new LinkedHashMap<>();

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
		this.project = project;
		this.cu = cu;
		this.docURI = docURI;
		this.file = file;
		this.lastModified = lastModified;
		this.doc = doc;
		this.content = content;
		this.problemCollector = problemCollector;
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

	public List<CachedIndexElement> getGeneratedIndexElements() {
		return getResult().getGeneratedIndexElements();
	}
	
	public List<String> getNextPassFiles() {
		return nextPassFiles;
	}

	public Set<QualifiedTypeName> getDependencies() {
		return dependencies;
	}
	
	public void addDependency(ITypeBinding dependsOn) {
		if (dependsOn != null && dependsOn.isFromSource()) {
			String type = dependsOn.getQualifiedName();
		
			if (type != null) {
				QualifiedTypeName q = QualifiedTypeName.of(type);
				if (!scannedTypes.contains(q)) {
					dependencies.add(q);
				}
			}
		}
	}
	
	public void addDependency(String qualifiedTypeName) {
		if (qualifiedTypeName != null) {
			dependencies.add(QualifiedTypeName.of(qualifiedTypeName));
		}
	}

	public void addDependency(QualifiedTypeName qualifiedTypeName) {
		if (qualifiedTypeName != null) {
			dependencies.add(qualifiedTypeName);
		}
	}

	/**
	 * Records that the given declaration - a method, a field - produced an index element of its
	 * own, and so gets its own node in the logical structure tree.
	 *
	 * <p>Changes to it are reported on that node, so its source is left out of the content hash of
	 * the type around it: otherwise editing any member would light up its type as well. What is
	 * left in the type's hash is everything with no node of its own (a plain private method, a
	 * field, the type's own annotations), which the type is the only place to report.
	 */
	public void markAsOwnIndexElement(ASTNode node) {
		if (node != null) {
			nodesWithOwnIndexElement.add(node);
		}
	}

	public List<ASTNode> getNodesWithOwnIndexElement() {
		return nodesWithOwnIndexElement;
	}

	/**
	 * Registers a type whose content hash can only be computed once every indexer has run for this
	 * file, because it depends on which of its members got an element of their own.
	 */
	public void hashTypeAfterScanning(AbstractTypeDeclaration typeDeclaration, StereotypeClassElement element) {
		typesToHash.put(typeDeclaration, element);
	}

	public Map<AbstractTypeDeclaration, StereotypeClassElement> getTypesToHash() {
		return typesToHash;
	}

	public Set<QualifiedTypeName> getScannedTypes() {
		return scannedTypes;
	}
	
	public void addScannedType(ITypeBinding scannedType) {
		if (scannedType != null) {
			String type = scannedType.getQualifiedName();
			if (type != null) {
				QualifiedTypeName q = QualifiedTypeName.of(type);
				scannedTypes.add(q);
				dependencies.remove(q);
			}
		}
	}

	public IProblemCollector getProblemCollector() {
		return this.problemCollector;
	}
	
	public boolean isFullAst() {
		return fullAst;
	}
	
	public boolean isIndexComplete() {
		return isIndexComplete;
	}
	
	public void resetDocumentRelatedElements(String docURI) {
		// whatever this pass found out about members and types goes with the elements it produced:
		// the pass is being thrown away, and a fresh one will work it out again
		nodesWithOwnIndexElement.clear();
		typesToHash.clear();

		Iterator<CachedIndexElement> beansIterator = getGeneratedIndexElements().iterator();
		while (beansIterator.hasNext()) {
			if (beansIterator.next().getDocURI().equals(docURI)) {
				beansIterator.remove();
			}
		}
	}

}
