/*******************************************************************************
 * Copyright (c) 2018, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils;

import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.handlers.SymbolProvider;
import org.springframework.ide.vscode.commons.util.BadLocationException;

/**
 * @author Martin Lippert
 */
public class RestrictedDefaultSymbolProvider implements SymbolProvider {

	private static final Logger log = LoggerFactory.getLogger(RestrictedDefaultSymbolProvider.class);

	@Override
	public void addSymbols(Annotation node, ITypeBinding typeBinding, Collection<ITypeBinding> metaAnnotations, SpringIndexerJavaContext context) {

		// provide default symbol only in case this annotation is not combined with @Bean annotation
		if (!isCombinedWithAnnotation(node, Annotations.BEAN)) {
			try {
				WorkspaceSymbol symbol = DefaultSymbolProvider.provideDefaultSymbol(node, context.getDoc());
				context.getGeneratedSymbols().add(new CachedSymbol(context.getDocURI(), context.getLastModified(), symbol));
				
//				SimpleSymbolElement symbolIndexElement = new SimpleSymbolElement(symbol);
//				SpringIndexElement parentIndexElement = context.getNearestIndexElementForNode(node.getParent());
//				if (parentIndexElement != null) {
//					parentIndexElement.addChild(symbolIndexElement);
//				}
//				else {
//					context.getBeans().add(new CachedBean(context.getDocURI(), symbolIndexElement));
//				}
//				context.setIndexElementForASTNode(node.getParent(), symbolIndexElement);
				
			} catch (BadLocationException e) {
				log.warn(e.getMessage());
			}
		}
	}

	private boolean isCombinedWithAnnotation(Annotation node, String annotation) {
		ASTNode parent = node.getParent();

		if (parent instanceof MethodDeclaration) {
			MethodDeclaration method = (MethodDeclaration) parent;

			List<?> modifiers = method.modifiers();
			for (Object modifier : modifiers) {
				if (modifier instanceof Annotation) {
					Annotation anno = (Annotation) modifier;
					IAnnotationBinding annotationBinding = anno.resolveAnnotationBinding();
					String type = annotationBinding.getAnnotationType().getBinaryName();

					if (type != null && type.equals(annotation)) {
						return true;
					}
				}
			}
		}

		return false;
	}

}
