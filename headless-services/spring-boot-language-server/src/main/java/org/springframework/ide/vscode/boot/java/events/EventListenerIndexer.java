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
package org.springframework.ide.vscode.boot.java.events;

import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.lsp4j.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Martin Lippert
 */
public class EventListenerIndexer {
	
	private static final Logger log = LoggerFactory.getLogger(EventListenerIndexer.class);

	public static void indexEventListener(Bean component, Annotation node, SpringIndexerJavaContext context, TextDocument doc) {
		if (node == null) return;
		
		ASTNode parent = node.getParent();
		if (parent == null || !(parent instanceof MethodDeclaration)) return;
		
		MethodDeclaration method = (MethodDeclaration) parent;
		
		try {
//			String symbolLabel = createEventListenerSymbolLabel(node, method);

			// index element for event listener
			Collection<Annotation> annotationsOnMethod = ASTUtils.getAnnotations(method);
			AnnotationMetadata[] annotations = ASTUtils.getAnnotationsMetadata(annotationsOnMethod, doc);
			
			ITypeBinding eventType = getEventType(node, method);
			DocumentRegion nodeRegion = ASTUtils.nodeRegion(doc, method.getName());
			Location location = new Location(doc.getUri(), nodeRegion.asRange());

			String containerBeanType = null;
			TypeDeclaration type = ASTUtils.findDeclaringType(method);
			if (type != null) {
				ITypeBinding binding = type.resolveBinding();
				if (binding != null) {
					containerBeanType = binding.getQualifiedName();
				}
			}

			context.markAsOwnIndexElement(method);
			EventListenerIndexElement eventListenerIndexElement = new EventListenerIndexElement(eventType != null ? eventType.getQualifiedName() : "", location, containerBeanType, annotations,
					ASTUtils.contentHash(doc, method));
			component.addChild(eventListenerIndexElement);
			
		} catch (BadLocationException e) {
			log.error("", e);
		}
	}
	
	public static String createEventListenerSymbolLabel(Annotation node, MethodDeclaration method) {
		// event listener annotation type
		String annotationTypeName = getAnnotationTypeName(node);
		ITypeBinding eventType = getEventType(node, method);
		
		if (annotationTypeName != null) {
			return "@" + annotationTypeName + (eventType != null ? " (" + eventType.getName() + ")" : "");
		}
		else {
			return node.toString();
		}
	}
	
	public static ITypeBinding getEventType(Annotation node, MethodDeclaration method) {
		List<?> parameters = method.parameters();
		if (parameters != null && parameters.size() == 1) {
			SingleVariableDeclaration param = (SingleVariableDeclaration) parameters.get(0);
			
			IVariableBinding paramBinding = param.resolveBinding();
			if (paramBinding != null) {
				ITypeBinding paramType = paramBinding.getType();
				return paramType != null ? paramType : null;
			}
		}

		return null;
	}

	public static String getAnnotationTypeName(Annotation node) {
		ITypeBinding typeBinding = node.resolveTypeBinding();
		if (typeBinding != null) {
			return typeBinding.getName();
		}
		else {
			return null;
		}
	}


}
