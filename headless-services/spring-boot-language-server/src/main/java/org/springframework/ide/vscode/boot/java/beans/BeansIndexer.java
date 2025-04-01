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
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.lsp4j.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.reconcilers.RequiredCompleteAstException;
import org.springframework.ide.vscode.boot.java.requestmapping.WebfluxRouterSymbolProvider;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.FunctionUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.InjectionPoint;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import reactor.util.function.Tuple2;

/**
 * @author Martin Lippert
 * @author Kris De Volder
 */
public class BeansIndexer {
	
	private static final Logger log = LoggerFactory.getLogger(BeansIndexer.class);

	public static void indexBeanMethod(SpringIndexElement parentNode, Annotation node, SpringIndexerJavaContext context, TextDocument doc) {
		if (node == null) return;
		
		ASTNode parent = node.getParent();
		if (parent == null || !(parent instanceof MethodDeclaration)) return;
		
		MethodDeclaration method = (MethodDeclaration) parent;
		if (isMethodAbstract(method)) return;

		boolean isWebfluxRouter = WebfluxRouterSymbolProvider.isWebfluxRouterBean(method);
		if (isWebfluxRouter) {
			if (!context.isFullAst()) {
				throw new RequiredCompleteAstException();
			}
		}
			
		boolean isFunction = isFunctionBean(method);
		ITypeBinding beanType = getBeanType(method);
		String markerString = getAnnotations(method);
		
		for (Tuple2<String, DocumentRegion> nameAndRegion : BeanUtils.getBeanNamesFromBeanAnnotationWithRegions(node, doc)) {
			try {
				Location location = new Location(doc.getUri(), doc.toRange(nameAndRegion.getT2()));

				String beanLabel = beanLabel(isFunction, nameAndRegion.getT1(), beanType.getName(), "@Bean" + markerString);

				InjectionPoint[] injectionPoints = ASTUtils.findInjectionPoints(method, doc);
				
				Set<String> supertypes = ASTUtils.findSupertypes(beanType);
				
				Collection<Annotation> annotationsOnMethod = ASTUtils.getAnnotations(method);
				AnnotationMetadata[] annotations = ASTUtils.getAnnotationsMetadata(annotationsOnMethod, doc);
				
				Bean beanDefinition = new Bean(nameAndRegion.getT1(), beanType.getQualifiedName(), location, injectionPoints, supertypes, annotations, false, beanLabel);
				
				if (isWebfluxRouter) {
					WebfluxRouterSymbolProvider.createWebfluxElements(beanDefinition, method, context, doc);
				}

				parentNode.addChild(beanDefinition);

			} catch (BadLocationException e) {
				log.error("", e);
			}
		}
	}

	public static String beanLabel(boolean isFunctionBean, String beanName, String beanType, String markerString) {
		StringBuilder symbolLabel = new StringBuilder();
		symbolLabel.append('@');
		symbolLabel.append(isFunctionBean ? '>' : '+');
		symbolLabel.append(' ');
		symbolLabel.append('\'');
		symbolLabel.append(beanName);
		symbolLabel.append('\'');

		markerString = markerString != null && markerString.length() > 0 ? " (" + markerString + ") " : " ";
		symbolLabel.append(markerString);

		symbolLabel.append(beanType);
		return symbolLabel.toString();
	}

	public static ITypeBinding getBeanType(MethodDeclaration method) {
		return method.getReturnType2().resolveBinding();
	}

	public static boolean isFunctionBean(MethodDeclaration method) {
		String returnType = null;

		if (method.getReturnType2().isParameterizedType()) {
			ParameterizedType paramType = (ParameterizedType) method.getReturnType2();
			Type type = paramType.getType();
			ITypeBinding typeBinding = type.resolveBinding();
			returnType = typeBinding.getBinaryName();
		}
		else {
			returnType = method.getReturnType2().resolveBinding().getQualifiedName();
		}

		return FunctionUtils.FUNCTION_FUNCTION_TYPE.equals(returnType) || FunctionUtils.FUNCTION_CONSUMER_TYPE.equals(returnType)
				|| FunctionUtils.FUNCTION_SUPPLIER_TYPE.equals(returnType);
	}

	public static String getAnnotations(MethodDeclaration method) {
		StringBuilder result = new StringBuilder();

		List<?> modifiers = method.modifiers();
		for (Object modifier : modifiers) {
			if (modifier instanceof Annotation) {
				Annotation annotation = (Annotation) modifier;
				IAnnotationBinding annotationBinding = annotation.resolveAnnotationBinding();
				String type = annotationBinding.getAnnotationType().getBinaryName();

				if (type != null && !Annotations.BEAN.equals(type)) {
					result.append(' ');
					result.append(annotation.toString());
				}
			}
		}
		return result.toString();
	}
	
	public static boolean isMethodAbstract(MethodDeclaration method) {
		List<?> modifiers = method.modifiers();
		for (Object modifier : modifiers) {
			if (modifier instanceof Modifier && ((Modifier) modifier).isAbstract()) {
				return true;
			}
		}
		return false;
	}

}
