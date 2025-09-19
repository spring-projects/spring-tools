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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Martin Lippert
 */
public class RequestMappingIndexer {
	
	private static final Logger log = LoggerFactory.getLogger(RequestMappingIndexer.class);
	
	private static final Set<String> ATTRIBUTE_NAME_VALUE_PATH = Set.of("value", "path");

	private static final Set<String> ATTRIBUTE_NAME_METHOD = Set.of("method");
	private static final Set<String> ATTRIBUTE_NAME_CONSUMES = Set.of("consumes");
	private static final Set<String> ATTRIBUTE_NAME_PRODUCES = Set.of("produces");
	private static final Set<String> ATTRIBUTE_NAME_VERSION = Set.of("version");
	
	private static final Map<String, String[]> METHOD_MAPPING = Map.of(
			Annotations.SPRING_GET_MAPPING, new String[] { "GET" },
			Annotations.SPRING_POST_MAPPING, new String[] { "POST" },
			Annotations.SPRING_DELETE_MAPPING, new String[] { "DELETE" },
			Annotations.SPRING_PUT_MAPPING, new String[] { "PUT" },
			Annotations.SPRING_PATCH_MAPPING, new String[] { "PATCH" }
			);

	

	public static void indexRequestMappings(Bean controller, TypeDeclaration type, ITypeBinding annotationType, SpringIndexerJavaContext context, TextDocument doc) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(type);		

		boolean isWebController = annotationHierarchies.isAnnotatedWith(annotationType, Annotations.CONTROLLER);
		boolean isDataRestWebController = annotationHierarchies.isAnnotatedWith(annotationType, Annotations.DATA_REST_BASE_PATH_AWARE_CONTROLLER);
		boolean isFeignClient = annotationHierarchies.isAnnotatedWith(annotationType, Annotations.FEIGN_CLIENT);
		
		if (isWebController || isDataRestWebController || isFeignClient) {
			MethodDeclaration[] methods = type.getMethods();
			if (methods == null) {
				return;
			}
			
			for (int i = 0; i < methods.length; i++) {
				MethodDeclaration methodDecl = methods[i];
				Collection<Annotation> annotations = ASTUtils.getAnnotations(methodDecl);
				
				for (Annotation annotation : annotations) {
					ITypeBinding typeBinding = annotation.resolveTypeBinding();
					
					boolean isRequestMappingAnnotation = annotationHierarchies.isAnnotatedWith(typeBinding, Annotations.SPRING_REQUEST_MAPPING);
					if (isRequestMappingAnnotation) {
						RequestMappingIndexer.indexRequestMapping(controller, annotation, context, doc);
					}
				}
			}
		}
	}

	public static void indexRequestMapping(Bean controller, Annotation node, SpringIndexerJavaContext context, TextDocument doc) {

		if (node.getParent() instanceof MethodDeclaration) {
			try {
				String methodSignature = ASTUtils.getMethodSignature((MethodDeclaration) node.getParent(), true);
				
				Location location = new Location(doc.getUri(), doc.toRange(node.getStartPosition(), node.getLength()));
				String[] path = getPath(node, context);
				String[] parentPath = getParentPath(node, context);
				String[] methods = getMethod(node, context);
				String[] contentTypes = getContentTypes(node, context);
				String[] acceptTypes = getAcceptTypes(node, context);
				String version = getVersion(node, context);

				Stream<String> stream = parentPath == null ? Stream.of("") : Arrays.stream(parentPath);
				stream.filter(Objects::nonNull)
						.flatMap(parent -> (path == null ? Stream.<String>empty() : Arrays.stream(path))
								.filter(Objects::nonNull).map(p -> {
									return WebEndpointIndexer.combinePath(parent, p);
								}))
						.forEach(p -> {
							// symbol
							WorkspaceSymbol symbol = RouteUtils.createRouteSymbol(location, p, methods, contentTypes, acceptTypes, version);
	
							// index element for request mapping
							RequestMappingIndexElement requestMappingIndexElement =
									new RequestMappingIndexElement(p, methods, contentTypes, acceptTypes, version, location.getRange(), symbol.getName(), methodSignature);

							controller.addChild(requestMappingIndexElement);
						});

			} catch (Exception e) {
				log.error("problem occured while scanning for request mapping symbols from " + doc.getUri(), e);
			}
		}
	}

	public static String[] getPath(Annotation node, SpringIndexerJavaContext context) {
		return WebEndpointIndexer.getPath(node, context, ATTRIBUTE_NAME_VALUE_PATH);
	}

	public static String[] getParentPath(Annotation node, SpringIndexerJavaContext context) {
		return WebEndpointIndexer.getParentPath(node, context, ATTRIBUTE_NAME_VALUE_PATH, Annotations.SPRING_REQUEST_MAPPING);
	}
	
	public static String[] getAcceptTypes(Annotation node, SpringIndexerJavaContext context) {
		return WebEndpointIndexer.getAttributeValues(node, context, ATTRIBUTE_NAME_CONSUMES, Annotations.SPRING_REQUEST_MAPPING);
	}
	
	public static String[] getContentTypes(Annotation node, SpringIndexerJavaContext context) {
		return WebEndpointIndexer.getAttributeValues(node, context, ATTRIBUTE_NAME_PRODUCES, Annotations.SPRING_REQUEST_MAPPING);
	}

	public static String[] getMethod(Annotation node, SpringIndexerJavaContext context) {
		return WebEndpointIndexer.getMethod(node, context, ATTRIBUTE_NAME_METHOD, METHOD_MAPPING, Annotations.SPRING_REQUEST_MAPPING);
	}

	public static String getVersion(Annotation node, SpringIndexerJavaContext context) {
		String[] versions = WebEndpointIndexer.getAttributeValues(node, context, ATTRIBUTE_NAME_VERSION, Annotations.SPRING_REQUEST_MAPPING);
		return versions != null && versions.length == 1 ? versions[0] : null;
	}

}
