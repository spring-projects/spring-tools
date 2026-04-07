/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import org.eclipse.jdt.core.dom.Annotation;
import org.springframework.ide.vscode.boot.java.SpringAiProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;

public class SpringAiDescriptionExistsReconciler extends AbstractSpringAiAnnotationReconciler {

	@Override
	public ProblemType getProblemType() {
		return SpringAiProblemType.SPRING_AI_TOOL_MISSING_DESCRIPTION;
	}

	@Override
	protected void validateDescription(String description, Annotation node, IProblemCollector problemCollector) {
		if (description == null || description.isBlank()) {
			problemCollector.accept(new ReconcileProblemImpl(getProblemType(),
					SpringAiProblemType.SPRING_AI_TOOL_MISSING_DESCRIPTION.getLabel(),
					node.getStartPosition(), node.getLength()));
		}
	}

}
