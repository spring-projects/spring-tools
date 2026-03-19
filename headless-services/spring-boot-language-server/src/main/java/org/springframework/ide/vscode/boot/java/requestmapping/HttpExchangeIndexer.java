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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.lsp4j.Location;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.beans.CachedIndexElement;
import org.springframework.ide.vscode.boot.java.handlers.SpringComponentIndexer;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.stereotype.Component;

/**
 * @author Martin Lippert
 */
@Component
public class HttpExchangeIndexer implements SpringComponentIndexer {
	
	private static final Set<String> ATTRIBUTE_NAME_VALUE_PATH = Set.of("value", "url");

	private static final Set<String> ATTRIBUTE_NAME_METHOD = Set.of("method");
	private static final Set<String> ATTRIBUTE_NAME_ACCEPT = Set.of("accept");
	private static final Set<String> ATTRIBUTE_NAME_CONTENT_TYPE = Set.of("contentType");
	private static final Set<String> ATTRIBUTE_NAME_VERSION = Set.of("version");
	
	private static final Map<String, String[]> METHOD_MAPPING = Map.of(
			Annotations.GET_EXCHANGE, new String[] { "GET" },
			Annotations.POST_EXCHANGE, new String[] { "POST" },
			Annotations.DELETE_EXCHANGE, new String[] { "DELETE" },
			Annotations.PUT_EXCHANGE, new String[] { "PUT" },
			Annotations.PATCH_EXCHANGE, new String[] { "PATCH" }
			);


	@Override
	public void index(MethodDeclaration methodDeclaration, SpringIndexerJavaContext context) throws Exception {
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

	private void indexHttpExchange(Annotation annotation, SpringIndexerJavaContext context) throws Exception {
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
	}
		
	public static String[] getPath(Annotation node, SpringIndexerJavaContext context) {
		return WebEndpointIndexer.getPath(node, context, ATTRIBUTE_NAME_VALUE_PATH);
	}

	public static String[] getParentPath(Annotation node, SpringIndexerJavaContext context) {
		return WebEndpointIndexer.getParentPath(node, context, ATTRIBUTE_NAME_VALUE_PATH, Annotations.HTTP_EXCHANGE);
	}
	
	public static String[] getAcceptTypes(Annotation node, SpringIndexerJavaContext context) {
		return WebEndpointIndexer.getAttributeValues(node, context, ATTRIBUTE_NAME_ACCEPT, Annotations.HTTP_EXCHANGE);
	}
	
	public static String[] getContentTypes(Annotation node, SpringIndexerJavaContext context) {
		return WebEndpointIndexer.getAttributeValues(node, context, ATTRIBUTE_NAME_CONTENT_TYPE, Annotations.HTTP_EXCHANGE);
	}

	public static String[] getMethod(Annotation node, SpringIndexerJavaContext context) {
		return WebEndpointIndexer.getMethod(node, context, ATTRIBUTE_NAME_METHOD, METHOD_MAPPING, Annotations.HTTP_EXCHANGE);
	}

	public static String getVersion(Annotation node, SpringIndexerJavaContext context) {
		String[] versions = WebEndpointIndexer.getAttributeValues(node, context, ATTRIBUTE_NAME_VERSION, Annotations.SPRING_REQUEST_MAPPING);
		return versions != null && versions.length == 1 ? versions[0] : null;
	}

}
