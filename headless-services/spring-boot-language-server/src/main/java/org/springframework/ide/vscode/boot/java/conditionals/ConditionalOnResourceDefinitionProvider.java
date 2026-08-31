/*******************************************************************************
 * Copyright (c) 2024, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.conditionals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.springframework.ide.vscode.boot.java.IJavaLocationLinksProvider;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;

public class ConditionalOnResourceDefinitionProvider implements IJavaLocationLinksProvider {

    @Override
    public List<LocationLink> getLocationLinks(CancelChecker cancelToken, IJavaProject project,
                                             TextDocumentIdentifier docId, CompilationUnit cu, ASTNode n, int offset) {

        Expression valueNode = ASTUtils.getValueExpressionAt(n);
        if (valueNode != null) {
            String value = ASTUtils.getExpressionValueAsString(valueNode);
            if (value != null && value.startsWith("classpath")) {
                return getDefinitionForClasspathResource(project, cu, valueNode, value);
            }
        }
        return Collections.emptyList();
    }


    private List<LocationLink> getDefinitionForClasspathResource(IJavaProject project, CompilationUnit cu, Expression valueNode, String literalValue) {
        literalValue = literalValue.substring("classpath:".length());

        List<LocationLink> result = new ArrayList<>();

        for (Path resource : findResources(project, literalValue)) {
            String uri = resource.toUri().toASCIIString();

            Position startPosition = new Position(cu.getLineNumber(valueNode.getStartPosition()) - 1,
                    cu.getColumnNumber(valueNode.getStartPosition()));
            Position endPosition = new Position(
                    cu.getLineNumber(valueNode.getStartPosition() + valueNode.getLength()) - 1,
                    cu.getColumnNumber(valueNode.getStartPosition() + valueNode.getLength()));
            Range nodeRange = new Range(startPosition, endPosition);

            LocationLink locationLink = new LocationLink(uri,
                    new Range(new Position(0, 0), new Position(0, 0)), new Range(new Position(0, 0), new Position(0, 0)),
                    nodeRange);

            result.add(locationLink);
        }

        return result;
    }

    private Path[] findResources(IJavaProject project, String resource) {
        return IClasspathUtil.getClasspathResourcesFullPaths(project.getClasspath())
                .filter(path -> path.toString().endsWith(resource))
                .toArray(Path[]::new);
    }

}