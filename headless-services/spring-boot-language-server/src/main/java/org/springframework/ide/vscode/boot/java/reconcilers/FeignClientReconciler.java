/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.BeanMethodContainerElement;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.util.UriUtil;

public class FeignClientReconciler implements JdtAstReconciler {
	
	private final SpringMetamodelIndex springIndex;

	public FeignClientReconciler(SpringMetamodelIndex springIndex) {
			this.springIndex = springIndex;
		}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return SpringProjectUtil.hasDependencyStartingWith(project, "spring-cloud-openfeign-core", null);
	}

	@Override
	public ProblemType getProblemType() {
		return Boot2JavaProblemType.MISSING_CONFIGURATION_ANNOTATION;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docURI, CompilationUnit cu, ReconcilingContext context) {
		return new ASTVisitor() {

			@Override
			public boolean visit(TypeDeclaration node) {
				AnnotationHierarchies annotationHierarchy = AnnotationHierarchies.get(node);

				if (!annotationHierarchy.isAnnotatedWith(node.resolveBinding(), Annotations.FEIGN_CLIENT)) {
					return true;
				}
				
				List<SpringIndexElement> elements = context.getCreatedIndexElements();

				List<String> configurationTypes = elements.stream()
					.filter(element -> element instanceof Bean)
					.map(element -> (Bean) element)
					.flatMap(bean -> Arrays.stream(bean.getAnnotations()))
					.filter(annotation -> annotation.getAnnotationType().equals(Annotations.FEIGN_CLIENT))
					.map(annotation -> annotation.getAttributes())
					.filter(attributes -> attributes.containsKey("configuration"))
					.flatMap(attributes -> Arrays.stream(attributes.get("configuration")))
					.map(attribute -> attribute.getName())
					.toList();
				
				List<BeanMethodContainerElement> beanMethodContainers = springIndex.getNodesOfType(BeanMethodContainerElement.class);

				beanMethodContainers.stream().filter(beanContainer -> configurationTypes.contains(beanContainer.getType()))
						.map(beanContainer -> beanContainer.getLocation().getUri()).map(docURI -> UriUtil.toFileString(docURI))
						.forEach(file -> context.markForAffetcedFilesIndexing(file));

				return true;
			}

		};
	}

}
