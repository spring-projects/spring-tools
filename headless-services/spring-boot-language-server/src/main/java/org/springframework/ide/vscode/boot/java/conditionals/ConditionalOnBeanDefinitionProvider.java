/*******************************************************************************
 * Copyright (c) 2024 Broadcom
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
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.IJavaLocationLinksProvider;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
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
        if (n instanceof StringLiteral) {
            StringLiteral valueNode = (StringLiteral) n;

            ASTNode parent = ASTUtils.getNearestAnnotationParent(valueNode);

            if (parent != null && parent instanceof Annotation) {
                Annotation a = (Annotation) parent;
                IAnnotationBinding binding = a.resolveAnnotationBinding();

                if (binding != null) {
	                ITypeBinding annotationType = binding.getAnnotationType();
	                if (annotationType != null) {
	                	String annotationTypeQualifiedName = annotationType.getQualifiedName();
                
	                	if (Annotations.CONDITIONAL_ON_BEAN.equals(annotationTypeQualifiedName)
	                			|| Annotations.CONDITIONAL_ON_MISSING_BEAN.equals(annotationTypeQualifiedName))  {
	                		
	                		return getDefinitions(project, valueNode);
	                	}
	                }
                }
            }
        }
        return Collections.emptyList();
    }

    private List<LocationLink> getDefinitions(IJavaProject project, StringLiteral valueNode) {
		String value = ASTUtils.getLiteralValue(valueNode);

		if (value != null && value.length() > 0) {
			return getDefinitionsForValue(project, valueNode, value);
		}
		else {
			return Collections.emptyList();
		}
	}

	private List<LocationLink> getDefinitionsForValue(IJavaProject project, StringLiteral valueNode, String value) {
		ASTNode parent = valueNode.getParent();
		if (parent != null && !(parent instanceof MemberValuePair)) {
			parent = parent.getParent();
		}
		
		if (parent != null && parent instanceof MemberValuePair) {
			MemberValuePair pair = (MemberValuePair) parent;
			String name = pair.getName().toString();
			
			if ("name".equals(name)) {
				return findBeansWithName(project, value);
			}
			else if ("type".equals(name) ||"ignoredType".equals(name)) {
				return findBeanTypesWithName(project, value);
			}
		}

		return Collections.emptyList();
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
