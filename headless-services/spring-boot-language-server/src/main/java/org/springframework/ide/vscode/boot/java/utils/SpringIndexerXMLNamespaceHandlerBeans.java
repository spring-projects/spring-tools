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

import java.util.List;

import org.eclipse.lemminx.dom.DOMAttr;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.ide.vscode.boot.java.beans.BeanUtils;
import org.springframework.ide.vscode.boot.java.beans.CachedIndexElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.lang.NonNull;

/**
 * @author Martin Lippert
 */
public class SpringIndexerXMLNamespaceHandlerBeans implements SpringIndexerXMLNamespaceHandler {

	@Override
	public void processNode(DOMNode node, IJavaProject project, String docURI, long lastModified, TextDocument document,
			List<CachedSymbol> generatedSymbols, List<CachedIndexElement> generatedBeans) throws Exception {
		String localName = node.getLocalName();
		if (localName != null && "bean".equals(localName)) {
			createBeanSymbol(node, project, docURI, lastModified, document, generatedSymbols, generatedBeans);
		}
	}

	private void createBeanSymbol(DOMNode node, IJavaProject project, String docURI, long lastModified, TextDocument document,
			List<CachedSymbol> generatedSymbols, List<CachedIndexElement> generatedBeans) throws Exception {
		String beanID = null;
		int symbolStart = 0;
		int symbolEnd = 0;

		String beanClass = null;
		String fqBeanClass = null;

		List<DOMAttr> attributes = node.getAttributeNodes();
		for (DOMAttr attribute : attributes) {

			String name = attribute.getName();
			if (name != null && name.equals("id")) {
				beanID = attribute.getValue();

				symbolStart = attribute.getStart();
				symbolEnd = attribute.getEnd();
			}
			else if (name != null && name.equals("class")) {
				fqBeanClass = attribute.getValue();
				beanClass = fqBeanClass.substring(fqBeanClass.lastIndexOf(".") + 1);

				if (symbolStart == 0 && symbolEnd == 0) {
					symbolStart = attribute.getStart();
					symbolEnd = attribute.getEnd();
				}
			}
		}

		if (beanClass != null) {
			int lineStart = document.getLineOfOffset(symbolStart);
			int lineEnd = document.getLineOfOffset(symbolEnd);

			int startInLine = symbolStart - document.getLineOffset(lineStart);
			int endInLine = symbolEnd - document.getLineOffset(lineEnd);

			Range range = new Range();
			range.setStart(new Position(lineStart, startInLine));
			range.setEnd(new Position(lineEnd, endInLine));

			Location location = new Location();
			location.setUri(docURI);
			location.setRange(range);

			if (beanID == null) {
				beanID = deriveBeanIDFromClass(beanClass);
			}

			WorkspaceSymbol symbol = new WorkspaceSymbol("@+ '" + beanID + "' " + beanClass, SymbolKind.Interface, Either.forLeft(new Location(docURI, range)));
			CachedSymbol cachedSymbol = new CachedSymbol(docURI, lastModified, symbol);
			generatedSymbols.add(cachedSymbol);
			
			// TODO: bean index
			generatedBeans.add(new CachedIndexElement(docURI, new Bean(beanID, fqBeanClass, location, null, null, null, false, symbol.getName())));
		}
	}

	private String deriveBeanIDFromClass(@NonNull String beanClass) {
		return BeanUtils.getBeanNameFromType(beanClass);
	}

}
