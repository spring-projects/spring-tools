/*******************************************************************************
 * Copyright (c) 2019, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils;

import java.util.List;

import org.eclipse.lsp4j.DocumentSymbol;
import org.springframework.ide.vscode.commons.java.IJavaProject;

/**
 * @author Martin Lippert
 */
public interface SpringIndexer {

	String[] getFileWatchPatterns();
	boolean isInterestedIn(String resource); // note that this might be a document URI or a standard file path on the system
	
	List<DocumentSymbol> computeDocumentSymbols(IJavaProject project, String docURI, String string) throws Exception;

	void initializeProject(IJavaProject project, boolean clean) throws Exception;
	void removeProject(IJavaProject project) throws Exception;

	void updateFile(IJavaProject project, DocumentDescriptor updatedDoc, String content) throws Exception;
	void updateFiles(IJavaProject project, DocumentDescriptor[] updatedDocs) throws Exception;
	void removeFiles(IJavaProject project, String[] docURIs) throws Exception;

}
