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
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
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
	
	private static final Set<String> ATTRIBUTE_NAME_VALUE_PATH = Set.of("value", "path");
	private static final Set<String> ATTRIBUTE_NAME_METHOD = Set.of("method");
	private static final Set<String> ATTRIBUTE_NAME_CONSUMES = Set.of("consumes");
	private static final Set<String> ATTRIBUTE_NAME_PRODUCES = Set.of("produces");
	
	private static final Logger log = LoggerFactory.getLogger(RequestMappingIndexer.class);
	

	public static void indexRequestMappings(Bean controller, TypeDeclaration type, ITypeBinding annotationType, SpringIndexerJavaContext context, TextDocument doc) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(type);		

		boolean isController = annotationHierarchies.isAnnotatedWith(annotationType, Annotations.CONTROLLER);
		boolean isFeignClient = annotationHierarchies.isAnnotatedWith(annotationType, Annotations.FEIGN_CLIENT);
		
		if (isController || isFeignClient) {
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
				Location location = new Location(doc.getUri(), doc.toRange(node.getStartPosition(), node.getLength()));
				String[] path = getPath(node, context);
				String[] parentPath = getParentPath(node, context);
				String[] methods = getMethod(node, context);
				String[] contentTypes = getContentTypes(node, context);
				String[] acceptTypes = getAcceptTypes(node, context);

				Stream<String> stream = parentPath == null ? Stream.of("") : Arrays.stream(parentPath);
				stream.filter(Objects::nonNull)
						.flatMap(parent -> (path == null ? Stream.<String>empty() : Arrays.stream(path))
								.filter(Objects::nonNull).map(p -> {
									return combinePath(parent, p);
								}))
						.forEach(p -> {
							// symbol
							WorkspaceSymbol symbol = RouteUtils.createRouteSymbol(location, p, methods, contentTypes, acceptTypes);
	
							// index element for request mapping
							RequestMappingIndexElement requestMappingIndexElement = new RequestMappingIndexElement(p, methods, contentTypes, acceptTypes, location.getRange(), symbol.getName());
							controller.addChild(requestMappingIndexElement);
						});

			} catch (Exception e) {
				log.error("problem occured while scanning for request mapping symbols from " + doc.getUri(), e);
			}
		}
	}

	public static String combinePath(String parent, String path) {
		String separator = !parent.endsWith("/") && !path.startsWith("/") && !path.isEmpty() ? "/" : "";
		String resultPath = parent + separator + path;

		String result = resultPath.startsWith("/") ? resultPath : "/" + resultPath;
		return result;
	}

	public static String[] getPath(Annotation node, SpringIndexerJavaContext context) {
		String[] result = getAttributeValuesFromAnnotation(node, context, ATTRIBUTE_NAME_VALUE_PATH);
		
		if (result == null && node.isSingleMemberAnnotation()) {
			SingleMemberAnnotation singleNode = (SingleMemberAnnotation) node;
			Expression expression = singleNode.getValue();
			result = ASTUtils.getExpressionValueAsArray(expression, context::addDependency);
		}

		return result != null ? result : new String[] { "" };
	}

	public static String[] getParentPath(Annotation node, SpringIndexerJavaContext context) {
		Annotation parentAnnotation = getAnnotationFromClassLevel(node);
		if (parentAnnotation != null) {
			return getPath(parentAnnotation, context);
		}
		else {
			return getAttributeValuesFromSupertypes(node, context, ATTRIBUTE_NAME_VALUE_PATH);
		}
	}
	
	public static String[] getAcceptTypes(Annotation node, SpringIndexerJavaContext context) {
		return getAttributeValues(node, context, ATTRIBUTE_NAME_CONSUMES);
	}
	
	public static String[] getContentTypes(Annotation node, SpringIndexerJavaContext context) {
		return getAttributeValues(node, context, ATTRIBUTE_NAME_PRODUCES);
	}

	public static String[] getMethod(Annotation node, SpringIndexerJavaContext context) {
		// extract from annotation type
		String[] methods = getRequestMethod(node);

		// extract from annotation params
		if (methods == null) {
			methods = getAttributeValues(node, context, ATTRIBUTE_NAME_METHOD);
		}
		return methods;
	}

	private static String[] getRequestMethod(Annotation annotation) {
		ITypeBinding type = annotation.resolveTypeBinding();
		if (type != null) {
			switch (type.getQualifiedName()) {
			case Annotations.SPRING_GET_MAPPING:
				return new String[] { "GET" };
			case Annotations.SPRING_POST_MAPPING:
				return new String[] { "POST" };
			case Annotations.SPRING_DELETE_MAPPING:
				return new String[] { "DELETE" };
			case Annotations.SPRING_PUT_MAPPING:
				return new String[] { "PUT" };
			case Annotations.SPRING_PATCH_MAPPING:
				return new String[] { "PATCH" };
			}
		}
		return null;
	}

	private static String[] getAttributeValues(Annotation node, SpringIndexerJavaContext context, Set<String> attributeNames) {
		String[] result = getAttributeValuesFromAnnotation(node, context, attributeNames);

		// extract from parent annotations
		if (result == null) {
			Annotation parentAnnotation = getAnnotationFromClassLevel(node);
			if (parentAnnotation != null) {
				result = getAttributeValuesFromAnnotation(parentAnnotation, context, attributeNames);
			}
			else {
				result = getAttributeValuesFromSupertypes(node, context, attributeNames);
			}
		}

		return result;
	}
	
	private static String[] getAttributeValuesFromAnnotation(Annotation node, SpringIndexerJavaContext context, Set<String> attributeNames) {
		if (node.isNormalAnnotation()) {
			NormalAnnotation normNode = (NormalAnnotation) node;
			List<?> values = normNode.values();
			for (Iterator<?> iterator = values.iterator(); iterator.hasNext();) {
				Object object = iterator.next();
				if (object instanceof MemberValuePair) {
					MemberValuePair pair = (MemberValuePair) object;
					String valueName = pair.getName().getIdentifier();
					if (valueName != null && attributeNames.contains(valueName)) {
						Expression expression = pair.getValue();
						return ASTUtils.getExpressionValueAsArray(expression, context::addDependency);
					}
				}
			}
		}
		return null;
	}

	private static String[] getAttributeValuesFromSupertypes(Annotation node, SpringIndexerJavaContext context, Set<String> attributeNames) {
		IAnnotationBinding annotationBinding = getAnnotationFromSupertypes(node, context);
		IMemberValuePairBinding valuePair = getValuePair(annotationBinding, attributeNames);
		
		ASTUtils.MemberValuePairAndType result = ASTUtils.getValuesFromValuePair(valuePair);

		if (result != null) {
			if (result.dereferencedType != null) {
				context.addDependency(result.dereferencedType);
			}
			return result.values;
		}
		else {
			return null;
		}
	}

	private static IMemberValuePairBinding getValuePair(IAnnotationBinding annotationBinding, Set<String> names) {
		if (annotationBinding != null) {
			IMemberValuePairBinding[] valuePairs = annotationBinding.getDeclaredMemberValuePairs();
			
			if (valuePairs != null ) {
				
				for (int j = 0; j < valuePairs.length; j++) {
					String valueName = valuePairs[j].getName();

					if (valueName != null && names.contains(valueName)) {
						return valuePairs[j];
					}
				}
			}
		}
		return null;
	}
	
	private static Annotation getAnnotationFromClassLevel(Annotation node) {
		// lookup class level request mapping annotation
		ASTNode parent = node.getParent() != null ? node.getParent().getParent() : null;
		while (parent != null && !(parent instanceof TypeDeclaration)) {
			parent = parent.getParent();
		}

		if (parent != null) {
			TypeDeclaration type = (TypeDeclaration) parent;
			List<?> modifiers = type.modifiers();
			Iterator<?> iterator = modifiers.iterator();
			while (iterator.hasNext()) {
				Object modifier = iterator.next();
				if (modifier instanceof Annotation) {
					Annotation annotation = (Annotation) modifier;
					ITypeBinding resolvedType = annotation.resolveTypeBinding();
					String annotationType = resolvedType.getQualifiedName();
					if (annotationType != null && Annotations.SPRING_REQUEST_MAPPING.equals(annotationType)) {
						return annotation;
					}
				}
			}
		}
		
		return null;
	}

	private static IAnnotationBinding getAnnotationFromSupertypes(Annotation node, SpringIndexerJavaContext context) {
		ASTNode parent = node.getParent() != null ? node.getParent().getParent() : null;
		while (parent != null && !(parent instanceof TypeDeclaration)) {
			parent = parent.getParent();
		}
		
		if (parent != null) {
			TypeDeclaration type = (TypeDeclaration) parent;
			ITypeBinding typeBinding = type.resolveBinding();
			if (typeBinding != null) {
				return findFirstRequestMappingAnnotation(typeBinding);
			}
		}
		
		return null;
	}
	
	private static IAnnotationBinding findFirstRequestMappingAnnotation(ITypeBinding start) {
		if (start == null) {
			return null;
		}
		
		IAnnotationBinding found = getRequestMappingAnnotation(start);
		if (found != null) {
			return found;
		}
		else {
			// search interfaces first
			ITypeBinding[] interfaces = start.getInterfaces();
			if (interfaces != null) {
				for (int i = 0; i < interfaces.length; i++) {
					found = findFirstRequestMappingAnnotation(interfaces[i]);
					if (found != null) {
						return found;
					}
				}
			}
			
			// search superclass second
			ITypeBinding superclass = start.getSuperclass();
			if (superclass != null) {
				return findFirstRequestMappingAnnotation(superclass);
			}
			
			// nothing found
			return null;
		}
	}

	private static IAnnotationBinding getRequestMappingAnnotation(ITypeBinding typeBinding) {
		IAnnotationBinding[] annotations = typeBinding.getAnnotations();
		for (int i = 0; i < annotations.length; i++) {
			if (annotations[i].getAnnotationType() != null && Annotations.SPRING_REQUEST_MAPPING.equals(annotations[i].getAnnotationType().getQualifiedName())) {
				return annotations[i];
			}
		}
		return null;
	}

}
