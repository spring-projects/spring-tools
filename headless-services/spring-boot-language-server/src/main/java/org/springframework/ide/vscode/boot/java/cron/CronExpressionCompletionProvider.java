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
package org.springframework.ide.vscode.boot.java.cron;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationAttributeCompletionProvider;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationAttributeProposal;
import org.springframework.ide.vscode.commons.java.IJavaProject;

public class CronExpressionCompletionProvider implements AnnotationAttributeCompletionProvider {
	
	private static final List<AnnotationAttributeProposal> CRON_EXPRESSIONS_MAP = CronExpressionExamples.EXAMPLES.stream()
			.map(e -> new AnnotationAttributeProposal(e.expression(), e.description()))
			.toList();

    @Override
    public List<AnnotationAttributeProposal> getCompletionCandidates(IJavaProject project, ASTNode node) {
        return CRON_EXPRESSIONS_MAP;
    }
}
