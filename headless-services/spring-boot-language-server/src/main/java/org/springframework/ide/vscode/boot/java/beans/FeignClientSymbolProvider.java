/*******************************************************************************
 * Copyright (c) 2023, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.beans;

import java.util.Collection;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.handlers.SpringComponentIndexer;
import org.springframework.ide.vscode.boot.java.requestmapping.RequestMappingIndexer;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.stereotype.Component;

@Component
public class FeignClientSymbolProvider implements SpringComponentIndexer {
	
	private static final Logger log = LoggerFactory.getLogger(FeignClientSymbolProvider.class);
	
	@Override
	public void index(TypeDeclaration typeDeclaration, SpringIndexerJavaContext context) {
		ITypeBinding binding = typeDeclaration.resolveBinding();
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(typeDeclaration);
		
		if (annotationHierarchies.isAnnotatedWith(binding, Annotations.FEIGN_CLIENT)) {
			Collection<Annotation> annotations = ASTUtils.getAnnotations(typeDeclaration);
			for (Annotation annotation : annotations) {
				ITypeBinding typeBinding = annotation.resolveTypeBinding();
				if (typeBinding != null && Annotations.FEIGN_CLIENT.equals(typeBinding.getQualifiedName())) {
					indexFeignClient(typeDeclaration, annotation, context);
				}
			}
		}
	}

	private void indexFeignClient(TypeDeclaration typeDeclaration, Annotation node, SpringIndexerJavaContext context) {
		try {
			Bean beanDefinition = ComponentSymbolProvider.createBean(typeDeclaration, context);
			RequestMappingIndexer.indexRequestMappings(beanDefinition, typeDeclaration, typeDeclaration.resolveBinding(), context, context.getDoc());
			
			context.getGeneratedIndexElements().add(new CachedIndexElement(context.getDocURI(), beanDefinition));
		}
		catch (BadLocationException e) {
			log.error("", e);
		}
	}



//	@Override
//	public void addSymbols(Annotation node, ITypeBinding annotationType, Collection<ITypeBinding> metaAnnotations, SpringIndexerJavaContext context) {
//		try {
//			if (node != null && node.getParent() != null && node.getParent() instanceof TypeDeclaration) {
//				Bean beanDefinition = createSymbol(node, annotationType, metaAnnotations, context, context.getDoc());
//				context.getGeneratedIndexElements().add(new CachedIndexElement(context.getDocURI(), beanDefinition));
//			}
//		}
//		catch (BadLocationException e) {
//			log.error("", e);
//		}
//	}

//	private Bean createSymbol(Annotation node, ITypeBinding annotationType, Collection<ITypeBinding> metaAnnotations, SpringIndexerJavaContext context, TextDocument doc) throws BadLocationException {
//		String annotationTypeName = annotationType.getName();
//		Collection<String> metaAnnotationNames = metaAnnotations.stream()
//				.map(ITypeBinding::getName)
//				.collect(Collectors.toList());
//		
//		TypeDeclaration type = (TypeDeclaration) node.getParent();
//
//		String beanName = getBeanName(node, type);
//		ITypeBinding beanType = type.resolveBinding();
//
//		Location location = new Location(doc.getUri(), doc.toRange(node.getStartPosition(), node.getLength()));
//		
//		String name = beanLabel("+", annotationTypeName, metaAnnotationNames, beanName, beanType == null ? "" : beanType.getName());
//		
//		InjectionPoint[] injectionPoints = ASTUtils.findInjectionPoints(type, doc);
//		Set<String> supertypes = ASTUtils.findSupertypes(beanType);
//		Collection<Annotation> annotationsOnType = ASTUtils.getAnnotations(type);
//		
//		AnnotationMetadata[] annotations = Stream.concat(
//				Arrays.stream(ASTUtils.getAnnotationsMetadata(annotationsOnType, doc))
//				,
//				metaAnnotations.stream()
//				.map(an -> new AnnotationMetadata(an.getQualifiedName(), true, null, null)))
//				.toArray(AnnotationMetadata[]::new);
//		
//		Bean beanDefinition = new Bean(beanName, beanType == null ? "" : beanType.getQualifiedName(), location, injectionPoints, supertypes, annotations, false, name);
//		RequestMappingIndexer.indexRequestMappings(beanDefinition, type, annotationType, context, doc);
//		
//		return beanDefinition;
//	}

//	protected String beanLabel(String searchPrefix, String annotationTypeName, Collection<String> metaAnnotationNames, String beanName, String beanType) {
//		StringBuilder symbolLabel = new StringBuilder();
//		symbolLabel.append("@");
//		symbolLabel.append(searchPrefix);
//		symbolLabel.append(' ');
//		symbolLabel.append('\'');
//		symbolLabel.append(beanName);
//		symbolLabel.append('\'');
//		symbolLabel.append(" (@");
//		symbolLabel.append(annotationTypeName);
//		if (!metaAnnotationNames.isEmpty()) {
//			symbolLabel.append(" <: ");
//			boolean first = true;
//			for (String ma : metaAnnotationNames) {
//				if (!first) {
//					symbolLabel.append(", ");
//				}
//				symbolLabel.append("@");
//				symbolLabel.append(ma);
//				first = false;
//			}
//		}
//		symbolLabel.append(") ");
//		symbolLabel.append(beanType);
//		return symbolLabel.toString();
//	}

//	private String getBeanName(Annotation node, TypeDeclaration typeDecl) {
//		if (node.isSingleMemberAnnotation()) {
//			Object o = ((SingleMemberAnnotation)node).getValue().resolveConstantExpressionValue();
//			if (o instanceof String) {
//				return (String) o;
//			}
//		} else if (node.isNormalAnnotation()) {
//			NormalAnnotation normalAnnotation = (NormalAnnotation) node;
//			for (Object o : normalAnnotation.values()) {
//				if (o instanceof MemberValuePair) {
//					MemberValuePair pair = (MemberValuePair) o;
//					switch (pair.getName().getIdentifier()) {
//					case "name":
//					case "value":
//						Object obj = pair.getValue().resolveConstantExpressionValue();
//						if (obj instanceof String) {
//							return (String) obj;
//						}
//					}
//				}
//			}
//		}
//		return BeanUtils.getBeanNameFromType(typeDecl.getName().getIdentifier());
//	}
	
}
