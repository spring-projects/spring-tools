/*******************************************************************************
 * Copyright (c) 2017, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
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
import org.springframework.ide.vscode.boot.java.handlers.SymbolProvider;
import org.springframework.ide.vscode.boot.java.utils.CachedSymbol;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Martin Lippert
 */
public class RequestMappingSymbolProvider implements SymbolProvider {
	
	private static final Logger log = LoggerFactory.getLogger(RequestMappingSymbolProvider.class);

	@Override
	public void addSymbols(Annotation node, ITypeBinding annotationType, Collection<ITypeBinding> metaAnnotations, SpringIndexerJavaContext context, TextDocument doc) {

		if (node.getParent() instanceof MethodDeclaration) {
			try {
				Location location = new Location(doc.getUri(), doc.toRange(node.getStartPosition(), node.getLength()));
				String[] path = RequestMappingIndexer.getPath(node, context);
				String[] parentPath = RequestMappingIndexer.getParentPath(node, context);
				String[] methods = RequestMappingIndexer.getMethod(node, context);
				String[] contentTypes = RequestMappingIndexer.getContentTypes(node, context);
				String[] acceptTypes = RequestMappingIndexer.getAcceptTypes(node, context);
				String version = RequestMappingIndexer.getVersion(node, context);

				Stream<String> stream = parentPath == null ? Stream.of("") : Arrays.stream(parentPath);
				stream.filter(Objects::nonNull)
						.flatMap(parent -> (path == null ? Stream.<String>empty() : Arrays.stream(path))
								.filter(Objects::nonNull).map(p -> {
									return WebEndpointIndexer.combinePath(parent, p);
								}))
						.forEach(p -> {
							// symbol
							WorkspaceSymbol symbol = RouteUtils.createRouteSymbol(location, p, methods, contentTypes, acceptTypes, version);
							context.getGeneratedSymbols().add(new CachedSymbol(context.getDocURI(), context.getLastModified(), symbol));
						});

			} catch (BadLocationException e) {
				log.error("problem occured while scanning for request mapping symbols from " + doc.getUri(), e);
			}
		}
	}

}
