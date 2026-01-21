/*******************************************************************************
 * Copyright (c) 2023, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.validation.generations;

import java.util.Collection;
import java.util.List;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.springframework.ide.vscode.boot.app.BootLanguageServerInitializer;
import org.springframework.ide.vscode.boot.validation.generations.json.Generation;
import org.springframework.ide.vscode.boot.validation.generations.json.ResolvedSpringProject;
import org.springframework.ide.vscode.boot.validation.generations.preferences.VersionValidationProblemType;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.reconcile.DiagnosticSeverityProvider;

import com.google.common.collect.ImmutableList;

public class GenerationsValidator extends AbstractDiagnosticValidator {
	
	private static final String SPRING_COMMERCIAL_URL = "https://spring.io/support";
	
	private SpringProjectsProvider provider;

	public GenerationsValidator(DiagnosticSeverityProvider diagnosticSeverityProvider, SpringProjectsProvider provider) {
		super(diagnosticSeverityProvider);
		this.provider = provider;
	}
	
	public static Generation getGenerationForJavaProject(IJavaProject javaProject, ResolvedSpringProject springProject) throws Exception {
		List<Generation> genList = springProject.getGenerations();
		Version javaProjectVersion = SpringProjectUtil.getDependencyVersionByName(javaProject, springProject.getSlug());

		if (javaProjectVersion == null) {
			return null;
		}

		// Find the generation belonging to the dependency
		for (Generation gen : genList) {
			Version genVersion = SpringProjectUtil.getVersionFromGeneration(gen.getName());
			if (genVersion.getMajor() == javaProjectVersion.getMajor()
					&& genVersion.getMinor() == javaProjectVersion.getMinor()) {
				return gen;
			}
		}
		return null;
	}

	@Override
	public Collection<Diagnostic> validate(IJavaProject javaProject, Version javaProjectVersion) throws Exception {
		ResolvedSpringProject springProject = provider.getProject(SpringProjectUtil.SPRING_BOOT);
		Generation javaProjectGen = getGenerationForJavaProject(javaProject, springProject);
		ImmutableList.Builder<Diagnostic> b = ImmutableList.builder();
		
		boolean validCommercialSupport = VersionValidationUtils.isCommercialValid(javaProjectGen);

		
		if (VersionValidationUtils.isOssValid(javaProjectGen)) {
			StringBuilder message = new StringBuilder();
			message.append("OSS support for Spring Boot ");
			message.append(javaProjectGen.getName());
			message.append(" ends on: ");
			message.append(javaProjectGen.getOssSupportEndDate());
			Diagnostic d = createDiagnostic(VersionValidationProblemType.SUPPORTED_OSS_VERSION, message.toString());
			if (d != null) {
				b.add(d);
			}
		} else {
			StringBuilder message = new StringBuilder();
			message.append("OSS support for Spring Boot ");
			if (javaProjectGen == null) {
				message.append(javaProjectVersion);
				message.append(" not available!");
			} else {
				message.append(javaProjectGen.getName());
				message.append(" ended on ");
				message.append(javaProjectGen.getOssSupportEndDate());
				if (validCommercialSupport) {
					message.append(", get commercial support until ");
					message.append(javaProjectGen.getCommercialSupportEndDate());
					message.append(" via Tanzu Spring Runtime at " + SPRING_COMMERCIAL_URL);
				}
			}
			Diagnostic d = createDiagnostic(VersionValidationProblemType.UNSUPPORTED_OSS_VERSION, message.toString());
			if (validCommercialSupport) {
				d.setData(List.of(getCommercialSupportCodeAction()));
			}
			if (d != null) {
				b.add(d);
			}
		}

		if (validCommercialSupport) {
			StringBuilder message = new StringBuilder();
			message.append("Commercial support for Spring Boot ");
			message.append(javaProjectGen.getName());
			message.append(" ends on: ");
			message.append(javaProjectGen.getCommercialSupportEndDate());
			Diagnostic d = createDiagnostic(VersionValidationProblemType.SUPPORTED_COMMERCIAL_VERSION, message.toString());
			if (d != null) {
				b.add(d);
			}
		} else {
			StringBuilder message = new StringBuilder();
			message.append("Commercial support for Spring Boot ");
			if (javaProjectGen == null) {
				message.append(javaProjectVersion);
				message.append(" not available!");
			} else {
				message.append(javaProjectGen.getName());
				message.append(" ended on ");
				message.append(javaProjectGen.getCommercialSupportEndDate());
			}
			Diagnostic d = createDiagnostic(VersionValidationProblemType.UNSUPPORTED_COMMERCIAL_VERSION, message.toString());
			if (d != null) {
				b.add(d);
			}
		}

		return b.build();
	}

	@Override
	public boolean isEnabled() {
		return isEnabled(
				VersionValidationProblemType.SUPPORTED_OSS_VERSION,
				VersionValidationProblemType.UNSUPPORTED_OSS_VERSION,
				VersionValidationProblemType.SUPPORTED_COMMERCIAL_VERSION,
				VersionValidationProblemType.UNSUPPORTED_COMMERCIAL_VERSION
		);
	}
	
	private static CodeAction getCommercialSupportCodeAction() {
		CodeAction commercialSupportLink = new CodeAction();
		commercialSupportLink.setKind(CodeActionKind.QuickFix);
		commercialSupportLink.setTitle("Get commercial Spring Boot support via Tanzu Spring Runtime");
		ShowDocumentParams showDocumentParams = new ShowDocumentParams(SPRING_COMMERCIAL_URL);
		showDocumentParams.setExternal(true);
		showDocumentParams.setTakeFocus(true);
		showDocumentParams.setSelection(new Range());
		commercialSupportLink.setCommand(new Command("Get commercial Spring Boot support via Tanzu Spring Runtime", BootLanguageServerInitializer.CMD_SHOW_DOC,
				ImmutableList.of(showDocumentParams)));
		return commercialSupportLink;
	}


}
