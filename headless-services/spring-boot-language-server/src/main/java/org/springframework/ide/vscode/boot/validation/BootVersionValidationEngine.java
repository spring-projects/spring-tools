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
package org.springframework.ide.vscode.boot.validation;

import java.util.Collections;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.common.IJavaProjectReconcileEngine;
import org.springframework.ide.vscode.boot.validation.generations.ProjectVersionDiagnosticProvider;
import org.springframework.ide.vscode.boot.validation.generations.ProjectVersionDiagnosticProvider.DiagnosticResult;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.ProgressService;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;

public class BootVersionValidationEngine implements IJavaProjectReconcileEngine {

	private static final Logger log = LoggerFactory.getLogger(BootVersionValidationEngine.class);

	private final SimpleTextDocumentService documentsService;
	private final BootJavaConfig config;
	private final ProjectVersionDiagnosticProvider diagnosticProvider;

	public BootVersionValidationEngine(SimpleTextDocumentService documentsService, BootJavaConfig config, ProjectObserver projectObserver, JavaProjectFinder projectFinder,
			ProjectVersionDiagnosticProvider diagnosticProvider) {
		this.documentsService = documentsService;
		this.config = config;
		this.diagnosticProvider = diagnosticProvider;
	}
	
	public void reconcile(IJavaProject project, ProgressService progressService) {
		if (config.isBootVersionValidationEnabled() && SpringProjectUtil.isBootProject(project)) {
			log.debug("validating Spring Boot version on project: " + project.getElementName());
			long start = System.currentTimeMillis();
			
			try {
				DiagnosticResult result = diagnosticProvider.getDiagnostics(project);
				if (result != null && !result.getDiagnostics().isEmpty()) {
					documentsService.publishDiagnostics(
							new TextDocumentIdentifier(result.getDocumentUri().toASCIIString()),
							result.getDiagnostics());

				}
			} catch (Exception e) {
				log.error("Failed validating Spring Project version", e);
			}
			
			long end = System.currentTimeMillis();
			log.info("validating Spring Boot version on project: " + project.getElementName() + " done in " + (end - start) + "ms");
		}
	}

	@Override
	public void clear(IJavaProject project) {
		// Build file
		if (project.getProjectBuild() != null && project.getProjectBuild().getBuildFile() != null) {
			documentsService.publishDiagnostics(
					new TextDocumentIdentifier(project.getProjectBuild().getBuildFile().toASCIIString()),
					Collections.emptyList());
		}
	}

}
