/*******************************************************************************
 * Copyright (c) 2019, 2023 Pivotal, Inc.
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

import org.eclipse.lemminx.dom.DOMNode;
import org.springframework.ide.vscode.boot.java.beans.CachedIndexElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Martin Lippert
 */
public interface SpringIndexerXMLNamespaceHandler {

	void processNode(DOMNode node, IJavaProject project, String docURI, long lastModifiued, TextDocument document,
			List<CachedSymbol> generatedSymbols, List<CachedIndexElement> generatedBeans) throws Exception;

}
