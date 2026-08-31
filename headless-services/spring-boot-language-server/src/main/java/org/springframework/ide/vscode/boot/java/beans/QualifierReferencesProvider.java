/*******************************************************************************
 * Copyright (c) 2024, 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.beans;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.handlers.ReferenceProvider;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Martin Lippert
 */
public class QualifierReferencesProvider implements ReferenceProvider {

	private static final Logger log = LoggerFactory.getLogger(QualifierReferencesProvider.class);

	private final SpringMetamodelIndex springIndex;

	public QualifierReferencesProvider(SpringMetamodelIndex springIndex) {
		this.springIndex = springIndex;
	}

	@Override
	public List<? extends Location> provideReferences(CancelChecker cancelToken, IJavaProject project, ASTNode node, Annotation annotation, ITypeBinding type, int offset) {

		cancelToken.checkCanceled();

		try {
			return ASTUtils.resolveAnnotationAttributeAt(node)
					.filter(attribute -> "value".equals(attribute.attributeName()))
					.map(attribute -> attribute.value())
					.filter(value -> value != null)
					.map(value -> provideReferences(project, value))
					.orElse(null);
		}
		catch (Exception e) {
			log.error("error finding references for qualifier value", e);
		}

		return null;
	}
	
	@Override
	public List<? extends Location> provideReferences(CancelChecker cancelToken, IJavaProject project, TextDocument doc, ASTNode node, int offset) {
		return null;
	}

	private List<? extends Location> provideReferences(IJavaProject project, String value) {
		Bean[] beans = this.springIndex.getBeans();
		
		// beans with name
		Stream<Location> beanLocations = Arrays.stream(beans)
				.filter(bean -> bean.getName().equals(value))
				.map(bean -> bean.getLocation());
		
		Stream<Location> qualifiersLocationFromBeans = Arrays.stream(beans)
				// annotations from beans themselves
				.flatMap(bean -> Arrays.stream(bean.getAnnotations()))
				.filter(annotation -> Annotations.QUALIFIER.equals(annotation.getAnnotationType()))
				.filter(annotation -> annotation.getAttributes() != null && annotation.getAttributes().containsKey("value") && annotation.getAttributes().get("value").length == 1)
				.filter(annotation -> annotation.getAttributes().get("value")[0].getName().equals(value))
				.map(annotation -> annotation.getAttributes().get("value")[0].getLocation());
		
		Stream<Location> qualifierLocationsFromInjectionPoints = Arrays.stream(beans)
				// annotations from injection points
				.filter(bean -> bean.getInjectionPoints() != null)
				.flatMap(bean -> Arrays.stream(bean.getInjectionPoints()))
				.filter(injectionPoint -> injectionPoint.getAnnotations() != null)
				.flatMap(injectionPoint -> Arrays.stream(injectionPoint.getAnnotations()))
				.filter(annotation -> Annotations.QUALIFIER.equals(annotation.getAnnotationType()))
				.filter(annotation -> annotation.getAttributes() != null && annotation.getAttributes().containsKey("value") && annotation.getAttributes().get("value").length == 1)
				.filter(annotation -> annotation.getAttributes().get("value")[0].getName().equals(value))
				.map(annotation -> annotation.getAttributes().get("value")[0].getLocation());
		
		return Stream.concat(beanLocations, Stream.concat(qualifiersLocationFromBeans, qualifierLocationsFromInjectionPoints)).toList();
	}

}
