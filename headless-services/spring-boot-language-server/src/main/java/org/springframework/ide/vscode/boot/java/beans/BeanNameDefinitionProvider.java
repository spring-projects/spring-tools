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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.IJavaLocationLinksProvider;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;

/**
 * Navigates from an annotation attribute that names a bean (like {@code @Qualifier},
 * {@code @DependsOn} or {@code @Resource}) to the definition of that bean. The attribute value is
 * resolved via {@link ASTUtils#resolveAnnotationAttributeAt(ASTNode)}, so concatenated values and
 * constant references work as well.
 *
 * @author Martin Lippert
 */
public class BeanNameDefinitionProvider implements IJavaLocationLinksProvider {

	private final SpringMetamodelIndex springIndex;
	private final Set<String> annotationTypes;

	public BeanNameDefinitionProvider(SpringMetamodelIndex springIndex, Set<String> annotationTypes) {
		this.springIndex = springIndex;
		this.annotationTypes = annotationTypes;
	}

	@Override
	public List<LocationLink> getLocationLinks(CancelChecker cancelToken, IJavaProject project, TextDocumentIdentifier docId, CompilationUnit cu, ASTNode n, int offset) {
		return ASTUtils.resolveAnnotationAttributeAt(n)
				.filter(attribute -> annotationTypes.contains(attribute.annotationType()))
				.map(attribute -> attribute.value())
				.filter(beanName -> beanName != null && beanName.length() > 0)
				.map(beanName -> findBeansWithName(project, beanName))
				.orElse(Collections.emptyList());
	}

	private List<LocationLink> findBeansWithName(IJavaProject project, String beanName) {
		Bean[] beans = this.springIndex.getBeansWithName(project.getElementName(), beanName);

		return Arrays.stream(beans)
				.map(bean -> new LocationLink(bean.getLocation().getUri(), bean.getLocation().getRange(), bean.getLocation().getRange()))
				.collect(Collectors.toList());
	}

}
