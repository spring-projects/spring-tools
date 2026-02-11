/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.events;

import java.util.Collection;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.handlers.SymbolProvider;
import org.springframework.ide.vscode.boot.java.utils.CachedSymbol;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.util.BadLocationException;

/**
 * @author Martin Lippert
 */
public class EventListenerSymbolProvider implements SymbolProvider {
	
	private static final Logger log = LoggerFactory.getLogger(EventListenerSymbolProvider.class);

	@Override
	public void addSymbols(Annotation node, ITypeBinding typeBinding, Collection<ITypeBinding> metaAnnotations, SpringIndexerJavaContext context) {
		if (node == null) return;
		
		ASTNode parent = node.getParent();
		if (parent == null || !(parent instanceof MethodDeclaration)) return;
		
		MethodDeclaration method = (MethodDeclaration) parent;
		
		// symbol
		try {
			String symbolLabel = EventListenerIndexer.createEventListenerSymbolLabel(node, method);
			WorkspaceSymbol symbol = new WorkspaceSymbol(symbolLabel, SymbolKind.Interface,
					Either.forLeft(new Location(context.getDoc().getUri(), context.getDoc().toRange(node.getStartPosition(), node.getLength()))));

			context.getGeneratedSymbols().add(new CachedSymbol(context.getDocURI(), context.getLastModified(), symbol));
		}
		catch (BadLocationException e) {
			log.error("", e);
		}
	}
	
}
