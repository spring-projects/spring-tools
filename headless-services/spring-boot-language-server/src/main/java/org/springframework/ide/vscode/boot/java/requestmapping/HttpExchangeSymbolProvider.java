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
package org.springframework.ide.vscode.boot.java.requestmapping;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.beans.CachedBean;
import org.springframework.ide.vscode.boot.java.handlers.SymbolProvider;
import org.springframework.ide.vscode.boot.java.utils.CachedSymbol;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

public class HttpExchangeSymbolProvider implements SymbolProvider {
	
	private static final Logger log = LoggerFactory.getLogger(HttpExchangeSymbolProvider.class);

	@Override
	public void addSymbols(Annotation node, ITypeBinding typeBinding, Collection<ITypeBinding> metaAnnotations, SpringIndexerJavaContext context, TextDocument doc) {
		if (node.getParent() instanceof MethodDeclaration) {
			try {
				Location location = new Location(doc.getUri(), doc.toRange(node.getStartPosition(), node.getLength()));
				
				String[] path = HttpExchangeIndexer.getPath(node, context);
				String[] parentPath = HttpExchangeIndexer.getParentPath(node, context);
				String[] methods = HttpExchangeIndexer.getMethod(node, context);
				String[] contentTypes = HttpExchangeIndexer.getContentTypes(node, context);
				String[] acceptTypes = HttpExchangeIndexer.getAcceptTypes(node, context);

				Stream<String> stream = parentPath == null ? Stream.of("") : Arrays.stream(parentPath);
				stream.filter(Objects::nonNull)
						.flatMap(parent -> (path == null ? Stream.<String>empty() : Arrays.stream(path))
								.filter(Objects::nonNull).map(p -> {
									return WebEndpointIndexer.combinePath(parent, p);
								}))
						.forEach(p -> {
							// symbol
							WorkspaceSymbol symbol = RouteUtils.createRouteSymbol(location, p, methods, contentTypes, acceptTypes);
							context.getGeneratedSymbols().add(new CachedSymbol(context.getDocURI(), context.getLastModified(), symbol));
							
							// index element
							HttpExchangeIndexElement requestMappingIndexElement = new HttpExchangeIndexElement(p, methods, contentTypes, acceptTypes, location.getRange(), symbol.getName());
							context.getBeans().add(new CachedBean(doc.getUri(), requestMappingIndexElement));
						});

			} catch (BadLocationException e) {
				log.error("problem occured while scanning for request mapping symbols from " + doc.getUri(), e);
			}
		}
	}
	

}
