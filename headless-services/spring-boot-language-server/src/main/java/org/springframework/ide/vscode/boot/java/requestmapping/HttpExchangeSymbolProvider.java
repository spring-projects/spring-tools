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
package org.springframework.ide.vscode.boot.java.requestmapping;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.lsp4j.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.beans.CachedIndexElement;
import org.springframework.ide.vscode.boot.java.handlers.SpringComponentIndexer;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.stereotype.Component;

@Component
public class HttpExchangeSymbolProvider implements SpringComponentIndexer {
	
	private static final Logger log = LoggerFactory.getLogger(HttpExchangeSymbolProvider.class);
	
	@Override
	public void index(MethodDeclaration methodDeclaration, SpringIndexerJavaContext context) {
		IMethodBinding binding = methodDeclaration.resolveBinding();
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(methodDeclaration);
		
		if (annotationHierarchies.isAnnotatedWith(binding, Annotations.HTTP_EXCHANGE)) {
			Collection<Annotation> annotations = ASTUtils.getAnnotations(methodDeclaration);
			for (Annotation annotation : annotations) {
				ITypeBinding typeBinding = annotation.resolveTypeBinding();
				if (typeBinding != null && Annotations.EXCHANGE_ANNOTATIONS.contains(typeBinding.getQualifiedName())) {
					indexHttpExchange(annotation, context);
				}
			}
		}
	}
	
	private void indexHttpExchange(Annotation annotation, SpringIndexerJavaContext context) {
		try {
			Location location = new Location(context.getDoc().getUri(), context.getDoc().toRange(annotation.getStartPosition(), annotation.getLength()));
			
			String[] path = HttpExchangeIndexer.getPath(annotation, context);
			String[] parentPath = HttpExchangeIndexer.getParentPath(annotation, context);
			String[] methods = HttpExchangeIndexer.getMethod(annotation, context);
			String[] contentTypes = HttpExchangeIndexer.getContentTypes(annotation, context);
			String[] acceptTypes = HttpExchangeIndexer.getAcceptTypes(annotation, context);
			String version = HttpExchangeIndexer.getVersion(annotation, context);
			
			Stream<String> stream = parentPath == null ? Stream.of("") : Arrays.stream(parentPath);
			stream.filter(Objects::nonNull)
					.flatMap(parent -> (path == null ? Stream.<String>empty() : Arrays.stream(path))
							.filter(Objects::nonNull).map(p -> {
								return WebEndpointIndexer.combinePath(parent, p);
							}))
					.forEach(p -> {
						String label = RouteUtils.createRouteLabel(location, p, methods, contentTypes, acceptTypes, version);
						HttpExchangeIndexElement requestMappingIndexElement =
								new HttpExchangeIndexElement(p, methods, contentTypes, acceptTypes, version, location.getRange(), label);

						context.getGeneratedIndexElements().add(new CachedIndexElement(context.getDoc().getUri(), requestMappingIndexElement));
					});

		} catch (BadLocationException e) {
			log.error("problem occured while scanning for request mapping symbols from " + context.getDoc().getUri(), e);
		}
	}
	

//	@Override
//	public void addSymbols(Annotation node, ITypeBinding typeBinding, Collection<ITypeBinding> metaAnnotations, SpringIndexerJavaContext context) {
//		if (node.getParent() instanceof MethodDeclaration) {
//			try {
//				Location location = new Location(context.getDoc().getUri(), context.getDoc().toRange(node.getStartPosition(), node.getLength()));
//				
//				String[] path = HttpExchangeIndexer.getPath(node, context);
//				String[] parentPath = HttpExchangeIndexer.getParentPath(node, context);
//				String[] methods = HttpExchangeIndexer.getMethod(node, context);
//				String[] contentTypes = HttpExchangeIndexer.getContentTypes(node, context);
//				String[] acceptTypes = HttpExchangeIndexer.getAcceptTypes(node, context);
//				String version = HttpExchangeIndexer.getVersion(node, context);
//				
//				Stream<String> stream = parentPath == null ? Stream.of("") : Arrays.stream(parentPath);
//				stream.filter(Objects::nonNull)
//						.flatMap(parent -> (path == null ? Stream.<String>empty() : Arrays.stream(path))
//								.filter(Objects::nonNull).map(p -> {
//									return WebEndpointIndexer.combinePath(parent, p);
//								}))
//						.forEach(p -> {
//							String label = RouteUtils.createRouteLabel(location, p, methods, contentTypes, acceptTypes, version);
//							HttpExchangeIndexElement requestMappingIndexElement =
//									new HttpExchangeIndexElement(p, methods, contentTypes, acceptTypes, version, location.getRange(), label);
//
//							context.getGeneratedIndexElements().add(new CachedIndexElement(context.getDoc().getUri(), requestMappingIndexElement));
//						});
//
//			} catch (BadLocationException e) {
//				log.error("problem occured while scanning for request mapping symbols from " + context.getDoc().getUri(), e);
//			}
//		}
//	}
	

}
