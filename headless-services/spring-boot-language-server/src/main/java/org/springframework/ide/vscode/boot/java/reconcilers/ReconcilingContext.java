/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;

/**
 * context information for reconcilers
 */
public class ReconcilingContext {
	
	private final String docURI;

	private final IProblemCollector problemCollector;
	private final boolean isCompleteAst;
	private final boolean isIndexComplete;
	
	private final Set<String> dependencies; // set of fully qualified types
	private final Set<String> markedForAffectedFilesIndexing; // set of files
	
	private final List<SpringIndexElement> createdIndexElements;
	
	private final ReconcilingIndex reconcilingIndex;

	public ReconcilingContext(String docURI, IProblemCollector problemCollector, boolean isCompleteAst, boolean isIndexComplete,
			List<SpringIndexElement> createdIndexElements, ReconcilingIndex reconcilingIndex) {
		this.docURI = docURI;
		this.problemCollector = problemCollector;
		this.isCompleteAst = isCompleteAst;
		this.isIndexComplete = isIndexComplete;
		
		this.dependencies = new HashSet<>();
		this.markedForAffectedFilesIndexing = new HashSet<>();
		
		this.createdIndexElements = createdIndexElements;
		this.reconcilingIndex = reconcilingIndex;
	}
	
	public String getDocURI() {
		return docURI;
	}
	
	public IProblemCollector getProblemCollector() {
		return problemCollector;
	}
	
	public boolean isCompleteAst() {
		return isCompleteAst;
	}
	
	public boolean isIndexComplete() {
		return isIndexComplete;
	}

	public void addDependency(String type) {
		this.dependencies.add(type);
	}
	
	public Set<String> getDependencies() {
		return dependencies;
	}
	
	public void markForAffetcedFilesIndexing(String file) {
		this.markedForAffectedFilesIndexing.add(file);
	}

	public Set<String> getMarkedForAffectedFilesIndexing() {
		return markedForAffectedFilesIndexing;
	}
	
	public List<SpringIndexElement> getCreatedIndexElements() {
		return createdIndexElements;
	}
	
	public ReconcilingIndex getReconcilingIndex() {
		return this.reconcilingIndex;
	}

}
