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
package org.springframework.ide.vscode.boot.java.beans;

import java.util.Collection;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.handlers.SymbolProvider;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.CachedSymbol;
import org.springframework.ide.vscode.boot.java.utils.FunctionUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.InjectionPoint;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import reactor.util.function.Tuple2;

/**
 * @author Martin Lippert
 * @author Kris De Volder
 */
public class BeansSymbolProvider implements SymbolProvider {
	
	private static final Logger log = LoggerFactory.getLogger(BeansSymbolProvider.class);

	@Override
	public void addSymbols(Annotation node, ITypeBinding typeBinding, Collection<ITypeBinding> metaAnnotations, SpringIndexerJavaContext context, TextDocument doc) {
		if (node == null) return;
		
		ASTNode parent = node.getParent();
		if (parent == null || !(parent instanceof MethodDeclaration)) return;
		
		MethodDeclaration method = (MethodDeclaration) parent;
		if (BeansIndexer.isMethodAbstract(method)) return;

		boolean isFunction = BeansIndexer.isFunctionBean(method);

		ITypeBinding beanType = BeansIndexer.getBeanType(method);
		String markerString = BeansIndexer.getAnnotations(method);
		
		for (Tuple2<String, DocumentRegion> nameAndRegion : BeanUtils.getBeanNamesFromBeanAnnotationWithRegions(node, doc)) {
			try {
				Location location = new Location(doc.getUri(), doc.toRange(nameAndRegion.getT2()));

				WorkspaceSymbol symbol = new WorkspaceSymbol(
								BeansIndexer.beanLabel(isFunction, nameAndRegion.getT1(), beanType.getName(), "@Bean" + markerString),
								SymbolKind.Interface,
								Either.forLeft(location)
				);

				context.getGeneratedSymbols().add(new CachedSymbol(context.getDocURI(), context.getLastModified(), symbol));

			} catch (BadLocationException e) {
				log.error("", e);
			}
		}
	}

	@Override
	public void addSymbols(TypeDeclaration typeDeclaration, SpringIndexerJavaContext context, TextDocument doc) {
		indexFunctionBeans(typeDeclaration, context, doc);
	}

	private void indexFunctionBeans(TypeDeclaration typeDeclaration, SpringIndexerJavaContext context, TextDocument doc) {
		ITypeBinding functionBean = FunctionUtils.getFunctionBean(typeDeclaration, doc);
		if (functionBean != null) {
			try {
				String beanName = BeanUtils.getBeanName(typeDeclaration);
				ITypeBinding beanType = functionBean;
				Location beanLocation = new Location(doc.getUri(), doc.toRange(ASTUtils.nodeRegion(doc, typeDeclaration.getName())));

				WorkspaceSymbol symbol = new WorkspaceSymbol(
						BeansIndexer.beanLabel(true, beanName, beanType.getName(), null),
						SymbolKind.Interface,
						Either.forLeft(beanLocation));

				context.getGeneratedSymbols().add(new CachedSymbol(context.getDocURI(), context.getLastModified(), symbol));
				
				ITypeBinding concreteBeanType = typeDeclaration.resolveBinding();
				Set<String> supertypes = ASTUtils.findSupertypes(concreteBeanType);
				
				Collection<Annotation> annotationsOnTypeDeclaration = ASTUtils.getAnnotations(typeDeclaration);
				AnnotationMetadata[] annotations = ASTUtils.getAnnotationsMetadata(annotationsOnTypeDeclaration, doc);

				InjectionPoint[] injectionPoints = ASTUtils.findInjectionPoints(typeDeclaration, doc);

				Bean beanDefinition = new Bean(beanName, concreteBeanType.getQualifiedName(), beanLocation, injectionPoints, supertypes, annotations, false, symbol.getName());
				context.getBeans().add(new CachedBean(context.getDocURI(), beanDefinition));

			} catch (BadLocationException e) {
				log.error("", e);
			}
		}
	}

}
