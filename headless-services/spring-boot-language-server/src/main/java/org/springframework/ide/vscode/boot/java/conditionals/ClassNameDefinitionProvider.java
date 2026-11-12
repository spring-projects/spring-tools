/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.conditionals;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.springframework.ide.vscode.boot.java.IJavaLocationLinksProvider;
import org.springframework.ide.vscode.boot.java.links.JavaElementLocationProvider;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils.AnnotationAttribute;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IType;

/**
 * Go to definition for fully qualified class names given as strings in annotation attributes,
 * e.g. {@code @ConditionalOnClass(name = "...")}, navigating to the referenced type in the
 * project or in a library. The annotation attributes holding class names are passed in.
 *
 * @author viniciusmachak
 */
public class ClassNameDefinitionProvider implements IJavaLocationLinksProvider {

	private final JavaElementLocationProvider locationProvider;
	private final Map<String, Set<String>> classNameAttributes;

	/**
	 * @param classNameAttributes fully qualified annotation type mapped to the names of its attributes that hold class names
	 */
	public ClassNameDefinitionProvider(JavaElementLocationProvider locationProvider, Map<String, Set<String>> classNameAttributes) {
		this.locationProvider = locationProvider;
		this.classNameAttributes = classNameAttributes;
	}

	@Override
	public List<LocationLink> getLocationLinks(CancelChecker cancelToken, IJavaProject project, TextDocumentIdentifier docId, CompilationUnit cu, ASTNode n, int offset) {
		return ASTUtils.resolveAnnotationAttributeAt(n)
				.filter(attribute -> classNameAttributes.getOrDefault(attribute.annotationType(), Set.of()).contains(attribute.attributeName()))
				.map(attribute -> getDefinitions(project, cu, attribute))
				.orElse(Collections.emptyList());
	}

	private List<LocationLink> getDefinitions(IJavaProject project, CompilationUnit cu, AnnotationAttribute attribute) {
		String fqName = attribute.value();
		if (fqName == null || fqName.isBlank()) {
			return Collections.emptyList();
		}

		IType type = project.getIndex().findType(fqName.trim());
		if (type == null) {
			return Collections.emptyList();
		}

		Location location = locationProvider.findLocation(project, type);
		if (location == null || location.getUri() == null || location.getRange() == null) {
			return Collections.emptyList();
		}

		return List.of(new LocationLink(location.getUri(), location.getRange(), location.getRange(),
				nodeRange(cu, attribute.valueExpression())));
	}

	private static Range nodeRange(CompilationUnit cu, Expression node) {
		int start = node.getStartPosition();
		int end = start + node.getLength();
		return new Range(
				new Position(cu.getLineNumber(start) - 1, cu.getColumnNumber(start)),
				new Position(cu.getLineNumber(end) - 1, cu.getColumnNumber(end)));
	}

}
