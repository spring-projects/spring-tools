/*******************************************************************************
 * Copyright (c) 2018, 2025 Pivotal, Inc.
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
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.util.StringUtil;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.ImmutableList;

import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class BeanUtils {
	
	private static final String[] NAME_ATTRIBUTES = {"value", "name"};

	public static String getBeanNameFromComponentAnnotation(Annotation annotation, AbstractTypeDeclaration type) {
		Optional<Expression> attribute = ASTUtils.getAttribute(annotation, "value");
		if (attribute.isPresent()) {
			return ASTUtils.getExpressionValueAsString(attribute.get(), (a) -> {});
		}
		else {
			String beanType = type.getName().toString();
			return BeanUtils.getBeanNameFromType(beanType);
		}
	}
	
	public static Collection<String> getBeanNamesFromBeanAnnotation(Annotation node) {
		Collection<Expression> beanNameNodes = getBeanNameExpressions(node);

		if (beanNameNodes != null && !beanNameNodes.isEmpty()) {
			return beanNameNodes.stream().map(nameNode -> ASTUtils.getExpressionValueAsString(nameNode, v -> {}))
					.toList();
		}
		else {
			ASTNode parent = node.getParent();
			if (parent instanceof MethodDeclaration) {
				MethodDeclaration method = (MethodDeclaration) parent;
				return ImmutableList.of(method.getName().toString());
			}
			return ImmutableList.of();
		}
	}

	public static Collection<Tuple2<String, DocumentRegion>> getBeanNamesFromBeanAnnotationWithRegions(Annotation node, TextDocument doc) {
		Collection<Expression> beanNameNodes = getBeanNameExpressions(node);

		if (beanNameNodes != null && !beanNameNodes.isEmpty()) {
			ImmutableList.Builder<Tuple2<String,DocumentRegion>> namesAndRegions = ImmutableList.builder();
			for (Expression nameNode : beanNameNodes) {
				String name = ASTUtils.getExpressionValueAsString(nameNode, v -> {});

				DocumentRegion region = ASTUtils.nodeRegion(doc, nameNode);
				if (nameNode instanceof StringLiteral) {
					region = new DocumentRegion(region.getDocument(), region.getStart() + 1, region.getEnd() - 1);
				}
				namesAndRegions.add(Tuples.of(name, region));
			}
			return namesAndRegions.build();
		}
		else {
			ASTNode parent = node.getParent();
			if (parent instanceof MethodDeclaration) {
				MethodDeclaration method = (MethodDeclaration) parent;
				return ImmutableList.of(Tuples.of(
						method.getName().toString(),
						ASTUtils.nameRegion(doc, node)
				));
			}
			return ImmutableList.of();
		}
	}

	public static String getBeanNameFromType(String typeName) {
		if (StringUtil.hasText(typeName) && typeName.length() > 0 && Character.isUpperCase(typeName.charAt(0))) {
			// PT 162740382 - Special case: If more than one character is upper case, do not
			// convert bean name to starting lower case. only convert if name has one character
			// or the second character in the name is not upper case
			if (typeName.length() == 1 || !Character.isUpperCase(typeName.charAt(1))) {
				typeName = Character.toLowerCase(typeName.charAt(0)) + typeName.substring(1);
			}
		}
		return typeName;
	}
	
	public static String getBeanName(TypeDeclaration typeDeclaration) {
		String beanName = typeDeclaration.getName().toString();
		return BeanUtils.getBeanNameFromType(beanName);
	}

	private static Collection<Expression> getBeanNameExpressions(Annotation node) {
		ImmutableList.Builder<Expression> literals = ImmutableList.builder();
		for (String attrib : NAME_ATTRIBUTES) {
			ASTUtils.getAttribute(node, attrib).ifPresent((valueExp) -> {
				literals.addAll(ASTUtils.expandExpressionsFromPotentialArray(valueExp));
			});
		}
		return literals.build();
	}
	
}
