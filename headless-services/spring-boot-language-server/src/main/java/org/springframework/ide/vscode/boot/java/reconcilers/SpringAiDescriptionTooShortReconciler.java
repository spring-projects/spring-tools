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
import org.springframework.ide.vscode.boot.app.ProblemParameterProvider;
import org.springframework.ide.vscode.boot.java.SpringAiProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;

public class SpringAiDescriptionTooShortReconciler extends AbstractSpringAiAnnotationReconciler {

	private static final String MIN_LENGTH_KEY = "minimum-length";

	private final ProblemParameterProvider parameterProvider;

	public SpringAiDescriptionTooShortReconciler(ProblemParameterProvider parameterProvider) {
		this.parameterProvider = parameterProvider;
	}

	@Override
	public ProblemType getProblemType() {
		return SpringAiProblemType.SPRING_AI_TOOL_DESCRIPTION_TOO_SHORT;
	}

	@Override
	protected void validateDescription(String description, Annotation node, IProblemCollector problemCollector) {
		int minLen = parameterProvider.getIntParameter(SpringAiProblemType.SPRING_AI_TOOL_DESCRIPTION_TOO_SHORT, MIN_LENGTH_KEY);
		if (description != null && !description.isBlank() && description.trim().length() < minLen) {
			problemCollector.accept(new ReconcileProblemImpl(getProblemType(),
					SpringAiProblemType.SPRING_AI_TOOL_DESCRIPTION_TOO_SHORT.getLabel(),
					node.getStartPosition(), node.getLength()));
		}
	}

}
