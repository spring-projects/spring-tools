/*******************************************************************************
 * Copyright (c) 2022, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.validation.generations;

import java.util.List;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.springframework.ide.vscode.boot.validation.generations.preferences.VersionValidationProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.DiagnosticSeverityProvider;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;


abstract public class AbstractDiagnosticValidator implements VersionValidator {
	
	private static final String BOOT_VERSION_VALIDATION_CODE = "BOOT_VERSION_VALIDATION_CODE";

	private final DiagnosticSeverityProvider diagnosticSeverityProvider;
	
	public AbstractDiagnosticValidator(DiagnosticSeverityProvider diagnosticSeverityProvider) {
		this.diagnosticSeverityProvider = diagnosticSeverityProvider;
	}

	protected Diagnostic createDiagnostic(List<CodeAction> actions, VersionValidationProblemType problemType, String diagnosticMessage) {
		DiagnosticSeverity severity = diagnosticSeverityProvider.getDiagnosticSeverity(problemType);

		// No severity means that this validator is set to "IGNORE" in the preferences
		if (severity == null) {
			return null;
		}
		
		Diagnostic diagnostic = new Diagnostic();
		diagnostic.setCode(BOOT_VERSION_VALIDATION_CODE);
		diagnostic.setMessage(diagnosticMessage.toString());

		Range range = new Range();
		Position start = new Position();
		start.setLine(0);
		start.setCharacter(0);
		range.setStart(start);
		Position end = new Position();
		end.setLine(0);
		end.setCharacter(1);
		range.setEnd(end);
		diagnostic.setRange(range);
		diagnostic.setSeverity(severity);
		
		if (actions != null) {
			for (CodeAction action : actions) {
				Diagnostic refDiagnostic = new Diagnostic(diagnostic.getRange(), diagnostic.getMessage().getLeft(),
						diagnostic.getSeverity(), diagnostic.getSource());
				action.setDiagnostics(List.of(refDiagnostic));
			}
			diagnostic.setData(actions);
		}
		return diagnostic;
	}
	
	protected Diagnostic createDiagnostic(VersionValidationProblemType problemType, String diagnosticMessage) {
		return createDiagnostic(null, problemType, diagnosticMessage);
	}
	
	protected boolean isEnabled(ProblemType... problemTypes) {
		for (ProblemType problemType : problemTypes) {
			DiagnosticSeverity severity = diagnosticSeverityProvider.getDiagnosticSeverity(problemType);
			if (severity != null) {
				return true;
			}
		}
		return false;
	}
}