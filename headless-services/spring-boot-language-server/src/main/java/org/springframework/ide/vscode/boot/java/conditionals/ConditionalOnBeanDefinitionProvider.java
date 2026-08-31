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
package org.springframework.ide.vscode.boot.java.conditionals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.IJavaLocationLinksProvider;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils.AnnotationAttribute;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;

/**
 * @author Karthik Sankaranarayanan
 */
public class ConditionalOnBeanDefinitionProvider implements IJavaLocationLinksProvider {

    private final SpringMetamodelIndex springIndex;

    public ConditionalOnBeanDefinitionProvider(SpringMetamodelIndex springIndex) {
        this.springIndex = springIndex;
    }

    @Override
    public List<LocationLink> getLocationLinks(CancelChecker cancelToken, IJavaProject project, TextDocumentIdentifier docId, CompilationUnit cu, ASTNode n, int offset) {
    	return ASTUtils.resolveAnnotationAttributeAt(n)
    			.filter(attribute -> Annotations.CONDITIONAL_ON_BEAN.equals(attribute.annotationType())
    					|| Annotations.CONDITIONAL_ON_MISSING_BEAN.equals(attribute.annotationType()))
    			.map(attribute -> getDefinitions(project, attribute))
    			.orElse(Collections.emptyList());
    }

	private List<LocationLink> getDefinitions(IJavaProject project, AnnotationAttribute attribute) {
		String value = attribute.value();
		if (value == null || value.length() == 0) {
			return Collections.emptyList();
		}

		return switch (attribute.attributeName()) {
			case "name" -> findBeansWithName(project, value);
			case "type", "ignoredType" -> findBeanTypesWithName(project, value);
			default -> Collections.emptyList();
		};
	}

	private List<LocationLink> findBeanTypesWithName(IJavaProject project, String value) {
		// TODO
		return Collections.emptyList();
	}

	private List<LocationLink> findBeansWithName(IJavaProject project, String beanName) {
        Bean[] beans = this.springIndex.getBeansWithName(project.getElementName(), beanName);

        return Arrays.stream(beans)
                .map(bean -> {
                    return new LocationLink(bean.getLocation().getUri(), bean.getLocation().getRange(), bean.getLocation().getRange());
                })
                .collect(Collectors.toList());
    }

}
