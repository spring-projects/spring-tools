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

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;

/**
 * @author Martin Lippert
 */
public class WebEndpointIndexer {
	
	public static String combinePath(String parent, String path) {
		String separator = !parent.endsWith("/") && !path.startsWith("/") && !path.isEmpty() ? "/" : "";
		String resultPath = parent + separator + path;

		String result = resultPath.startsWith("/") ? resultPath : "/" + resultPath;
		return result;
	}

	public static String[] getPath(Annotation node, SpringIndexerJavaContext context, Set<String> attributeNames) {
		String[] result = getAttributeValuesFromAnnotation(node, context, attributeNames);
		
		if (result == null && node.isSingleMemberAnnotation()) {
			SingleMemberAnnotation singleNode = (SingleMemberAnnotation) node;
			Expression expression = singleNode.getValue();
			result = ASTUtils.getExpressionValueAsArray(expression, context::addDependency);
		}

		return result != null ? result : new String[] { "" };
	}

	public static String[] getParentPath(Annotation node, SpringIndexerJavaContext context, Set<String> attributeNames, String classLevelAnnotationType) {
		Annotation parentAnnotation = getAnnotationFromClassLevel(node, classLevelAnnotationType);
		if (parentAnnotation != null) {
			return getPath(parentAnnotation, context, attributeNames);
		}
		else {
			return getAttributeValuesFromSupertypes(node, context, attributeNames, classLevelAnnotationType);
		}
	}
	
	public static String[] getMethod(Annotation node, SpringIndexerJavaContext context, Set<String> attributeNames, Map<String, String[]> methodMapping, String classLevelAnnotationType) {
		// extract from annotation type
		String[] methods = getRequestMethod(node, methodMapping);

		// extract from annotation params
		if (methods == null) {
			methods = getAttributeValues(node, context, attributeNames, classLevelAnnotationType);
		}
		
		return methods;
	}

	private static String[] getRequestMethod(Annotation annotation, Map<String, String[]> methodMapping) {
		ITypeBinding type = annotation.resolveTypeBinding();
		if (type != null) {
			String qualifiedName = type.getQualifiedName();
			if (qualifiedName != null) {
				return methodMapping.get(qualifiedName);
			}
		}

		return null;
	}

	public static String[] getAttributeValues(Annotation node, SpringIndexerJavaContext context, Set<String> attributeNames, String classLevelAnnotationType) {
		String[] result = getAttributeValuesFromAnnotation(node, context, attributeNames);

		// extract from parent annotations
		if (result == null) {
			Annotation parentAnnotation = getAnnotationFromClassLevel(node, classLevelAnnotationType);
			if (parentAnnotation != null) {
				result = getAttributeValuesFromAnnotation(parentAnnotation, context, attributeNames);
			}
			else {
				result = getAttributeValuesFromSupertypes(node, context, attributeNames, classLevelAnnotationType);
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

	private static String[] getAttributeValuesFromSupertypes(Annotation node, SpringIndexerJavaContext context, Set<String> attributeNames, String classLevelAnnotationType) {
		IAnnotationBinding annotationBinding = getAnnotationFromSupertypes(node, context, classLevelAnnotationType);
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
	
	private static Annotation getAnnotationFromClassLevel(Annotation node, String classLevelAnnotationType) {
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
					if (annotationType != null && classLevelAnnotationType.equals(annotationType))  {
						return annotation;
					}
				}
			}
		}
		
		return null;
	}

	private static IAnnotationBinding getAnnotationFromSupertypes(Annotation node, SpringIndexerJavaContext context, String classLevelAnnotationType) {
		ASTNode parent = node.getParent() != null ? node.getParent().getParent() : null;
		while (parent != null && !(parent instanceof TypeDeclaration)) {
			parent = parent.getParent();
		}
		
		if (parent != null) {
			TypeDeclaration type = (TypeDeclaration) parent;
			ITypeBinding typeBinding = type.resolveBinding();
			if (typeBinding != null) {
				return findFirstRequestMappingAnnotation(typeBinding, classLevelAnnotationType);
			}
		}
		
		return null;
	}
	
	private static IAnnotationBinding findFirstRequestMappingAnnotation(ITypeBinding start, String classLevelAnnotationType) {
		if (start == null) {
			return null;
		}
		
		IAnnotationBinding found = getRequestMappingAnnotation(start, classLevelAnnotationType);
		if (found != null) {
			return found;
		}
		else {
			// search interfaces first
			ITypeBinding[] interfaces = start.getInterfaces();
			if (interfaces != null) {
				for (int i = 0; i < interfaces.length; i++) {
					found = findFirstRequestMappingAnnotation(interfaces[i], classLevelAnnotationType);
					if (found != null) {
						return found;
					}
				}
			}
			
			// search superclass second
			ITypeBinding superclass = start.getSuperclass();
			if (superclass != null) {
				return findFirstRequestMappingAnnotation(superclass, classLevelAnnotationType);
			}
			
			// nothing found
			return null;
		}
	}

	private static IAnnotationBinding getRequestMappingAnnotation(ITypeBinding typeBinding, String classLevelAnnotationType) {
		IAnnotationBinding[] annotations = typeBinding.getAnnotations();
		for (int i = 0; i < annotations.length; i++) {
			if (annotations[i].getAnnotationType() != null && classLevelAnnotationType.equals(annotations[i].getAnnotationType().getQualifiedName())) {
				return annotations[i];
			}
		}
		return null;
	}

}
