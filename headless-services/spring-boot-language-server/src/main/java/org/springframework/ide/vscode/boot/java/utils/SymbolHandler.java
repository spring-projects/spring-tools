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
import java.util.Map;

import org.eclipse.lsp4j.Diagnostic;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;

/**
 * @author Martin Lippert
 */
public interface SymbolHandler {

	void addSymbols(IJavaProject project, String docURI, List<SpringIndexElement> beanDefinitions, List<Diagnostic> diagnostics);
	void addSymbols(IJavaProject project, Map<String, List<SpringIndexElement>> beanDefinitionsByDoc, Map<String, List<Diagnostic>> diagnosticsByDoc);

	void removeSymbols(IJavaProject project, String docURI);

}
