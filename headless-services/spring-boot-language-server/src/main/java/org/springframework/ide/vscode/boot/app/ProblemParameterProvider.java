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
package org.springframework.ide.vscode.boot.app;

import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemTypeParameter;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemTypeParameter.ValueType;
import org.springframework.stereotype.Component;

/**
 * Reads per-problem parameters from {@code spring-boot.ls.problem-parameters} (nested under
 * category and problem code). Severity stays on {@code spring-boot.ls.problem} so a code node
 * remains a string there.
 */
@Component
public class ProblemParameterProvider {

	public static final String SETTINGS_ROOT = "spring-boot";
	public static final String SETTINGS_SEGMENT = "ls";
	public static final String PARAMETERS_BRANCH = "problem-parameters";

	private final BootJavaConfig config;

	public ProblemParameterProvider(BootJavaConfig config) {
		this.config = config;
	}

	public int getIntParameter(ProblemType problem, String paramKey) {
		Integer v = config.getRawSettings().getInt(SETTINGS_ROOT, SETTINGS_SEGMENT, PARAMETERS_BRANCH,
				problem.getCategory().getId(), problem.getCode(), paramKey);
		if (v == null) {
			String s = config.getRawSettings().getString(SETTINGS_ROOT, SETTINGS_SEGMENT, PARAMETERS_BRANCH,
					problem.getCategory().getId(), problem.getCode(), paramKey);
			if (s != null && !s.isBlank()) {
				try {
					v = Integer.parseInt(s.trim());
				} catch (NumberFormatException ignored) {
					// fall through
				}
			}
		}
		if (v != null) {
			return v;
		}
		v = legacySpringAiMinimumLength(problem, paramKey);
		if (v != null) {
			return v;
		}
		for (ProblemTypeParameter p : problem.getParameters()) {
			if (paramKey.equals(p.getKey()) && p.getType() == ValueType.INTEGER) {
				return Integer.parseInt(p.getDefaultValue());
			}
		}
		throw new IllegalStateException("No integer parameter '" + paramKey + "' for " + problem.getCode());
	}

	private Integer legacySpringAiMinimumLength(ProblemType problem, String paramKey) {
		if (!"minimum-length".equals(paramKey)) {
			return null;
		}
		if (!"spring-ai".equals(problem.getCategory().getId())) {
			return null;
		}
		if (!"SPRING_AI_TOOL_DESCRIPTION_TOO_SHORT".equals(problem.getCode())) {
			return null;
		}
		return config.getRawSettings().getInt("boot-java", "spring-ai", "validation", "tool-description-minimum-length");
	}
}
